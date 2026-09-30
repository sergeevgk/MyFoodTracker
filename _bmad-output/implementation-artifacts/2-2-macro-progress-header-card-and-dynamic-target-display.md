---
baseline_commit: 3361213396d8cb2646a38c0293028ec0effd4ad8
---
# Story 2.2: Macro Progress Header Card & Dynamic Target Display

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user,
I want to view my total calorie and macronutrient intake against my daily targets in a progress card on the dashboard,
so that I can monitor my nutrition status at a glance.

## Acceptance Criteria

1. **Given** the dashboard active date context with intake logs for the day, **When** the dashboard loads or the date changes, **Then** the macro header card displays summed totals for Calories, Protein, Carbs, and Fats computed from that date's `MealEntry.foods` (sum across all meals, `profile_id` + `YYYY-MM-DD` isolated), updating synchronously in `< 50ms` with no loading spinner. [Source: epics.md Story 2.2, PRD FR-5, NFR-4]
2. **Given** the macro header card, **When** rendered, **Then** all numeric macro readouts use monospace tabular figures (`android:fontFeatureSettings="tnum"` in XML for every numeric `TextView`) so digits do not shift on update, using M3 type scales (Headline Small 24sp for calories, Body Small/Label 12sp for P/C/F). [Source: epics.md Story 2.2, DESIGN.md §Typography, UX-DR2]
3. **Given** a profile whose `DailyGoal` has a `null` target (skipped during Story 1.1 setup), **When** the header card renders, **Then** the corresponding progress indicator row is set to `View.GONE` (not INVISIBLE) and only active goals are displayed; totals without a target show value-only text (e.g. `"450 kcal"`) with no progress bar and no divide-by-zero. [Source: epics.md Story 2.2, PRD FR-1, EXPERIENCE.md §State Patterns]
4. **Given** active goals are present, **When** totals are displayed, **Then** each row shows `"consumed / target"` (e.g. `"1,450 / 2,000 kcal"`, `"30 / 120g"`) plus a `LinearProgressIndicator` with `progress = (total / target).coerceIn(0.0, 1.0)` (cap over-consumption at 100% fill) and TalkBack `contentDescription` `"Calories: 1,450 of 2,000 kilocalories consumed, 72 percent"` (same pattern for protein/carbs/fat). [Source: DESIGN.md §1, EXPERIENCE.md §Accessibility Floor]
5. **Given** any date selection (week strip, prev/next week, Today, month picker), **When** the active date changes, **Then** the header card recomputes from `GetMealEntriesByDateUseCase(date.toString())` for the newly selected date and re-renders; empty days show `0` totals against targets (not stale previous-day values). [Source: Story 2.1 `DashboardViewModel.selectDate`, PRD FR-10]
6. **Given** the header card, **When** viewed in light and dark mode at 200% font scale, **Then** contrast ratio is `≥ 4.5:1`, touch-adjacent controls remain `≥ 48dp` where applicable, layout uses `wrap_content`/`sp` with no clipping, and the card surface is `surface-raised` (`#FFFFFF` light / `#1A1C1E` dark) with `rounded/md` 12dp corners and 1dp hairline border `#E2E8F0` / `#2C3036`. [Source: NFR-6, DESIGN.md §Colors/Shapes]
7. **Given** process death or null session, **When** the dashboard renders, **Then** existing Story 2.1 session guard is preserved (`sessionRepository.getActiveProfile()` null → navigate to `PasscodeAuthFragment`, no crash, header shows nothing). [Source: Story 2.1 AC #7]

## Tasks / Subtasks

- [x] **Task 1: Domain — Daily totals model + pure calculator** (AC: #1, #3, #4)
  - [x] Subtask 1.1: Create `domain/model/DailySummary.kt` as pure Kotlin (zero `android.*` imports): `data class DailySummary(val totalCalories: Double = 0.0, val totalProteinG: Double = 0.0, val totalCarbsG: Double = 0.0, val totalFatG: Double = 0.0)` + companion/pure function `fun summarize(mealEntries: List<MealEntry>): DailySummary` summing `meal.foods` (`calories`, `protein`, `carbs`, `fat`). No rounding inside — keep full `Double` precision (NFR-4 0.1% tolerance).
  - [x] Subtask 1.2: Do NOT create a new DAO query — reuse `GetMealEntriesByDateUseCase`. Do NOT create new repository methods. Do NOT add `suspend`/coroutines.

- [x] **Task 2: Presentation state — extend DashboardUiState** (AC: #1, #3, #4, #5)
  - [x] Subtask 2.1: MODIFY `presentation/ui/dashboard/model/DashboardUiState.kt` — add `dailySummary: DailySummary = DailySummary()` and `dailyGoal: DailyGoal? = null` (nullable). Keep all existing fields (`activeDate`, `formattedDateHeader`, `weekDays`, `mealEntries`, `username`, `profileId`) unchanged.
  - [x] Subtask 2.2: MODIFY `presentation/viewmodel/DashboardViewModel.kt` `selectDate()`: after `val mealEntries = getMealEntriesByDateUseCase(date.toString())`, compute `val summary = DailySummary.summarize(mealEntries)` and `val goal = profile?.dailyGoal`, then include both in `_uiState.value = DashboardUiState(...)`. `previousWeek()`/`nextWeek()`/`jumpToToday()` need no changes (they delegate to `selectDate`). Keep `Locale.getDefault()` formatters, Monday–Sunday week logic, synchronous execution.

- [x] **Task 3: Layout — macro header card in fragment_dashboard.xml** (AC: #2, #3, #4, #6)
  - [x] Subtask 3.1: MODIFY `app/src/main/res/layout/fragment_dashboard.xml` — insert new `MaterialCardView` with `android:id="@+id/card_macro_header"` BETWEEN the top header card and the week-view card: `app:cardBackgroundColor="#FFFFFF"`, `app:cardCornerRadius="12dp"`, `app:cardElevation="2dp"`, inner `LinearLayout` padding `20dp`, title `TextView` (`@+id/tv_macro_title`, text `"Daily Progress"`, 16sp bold, `#2D3748`).
  - [x] Subtask 3.2: Calorie block: `tv_calories_value` (24sp bold, `#1B4D3E`, `fontFeatureSettings="tnum"`), `tv_calories_target` (14sp, `#718096`, `tnum`), `LinearProgressIndicator` `@+id/progress_calories` (indicator `#1B4D3E`, track `#E2E8F0`, height 8dp).
  - [x] Subtask 3.3: Three macro rows, each a vertical `LinearLayout` with IDs `layout_protein_row` / `layout_carbs_row` / `layout_fat_row`; each row contains label (`"Protein"` etc., 12sp `#718096`), value `TextView` (`tv_protein_value` / `tv_carbs_value` / `tv_fat_value`, 12sp bold `#2D3748`, `tnum`), target `TextView` (`tv_protein_target` etc.), and `LinearProgressIndicator` (`progress_protein` indicator `#2D6A4F`, `progress_carbs` indicator `#E9C46A`, `progress_fat` indicator `#E76F51`, all track `#E2E8F0`). All numeric TextViews MUST have `android:fontFeatureSettings="tnum"`.
  - [x] Subtask 3.4: No new vector drawables. No settings-screen work. Do NOT alter week-strip (`rvWeekDays`, `btnPrevWeek`, `btnNextWeek`, `btnToday`, `ibCalendarPicker`) or meals-section IDs.

- [x] **Task 4: Fragment binding + TalkBack** (AC: #1, #3, #4, #5, #7)
  - [x] Subtask 4.1: MODIFY `presentation/ui/dashboard/DashboardFragment.kt` `observeViewModel()` only — after existing week/meals binding, bind header: format `"1,450 / 2,000 kcal"` when target non-null else `"450 kcal"`; set `progress*` via `(total/target).coerceIn(0,1)` → `(ratio*100).toInt()` for the indicator; `layout_*_row.visibility = if (target == null) GONE else VISIBLE`; handle `target == 0.0` as hidden (avoid div-by-zero).
  - [x] Subtask 4.2: Set `card_macro_header.contentDescription` to `"Calories: X of Y kilocalories consumed, Z percent"` pattern (compute percent only when target non-null and > 0); set per-row `contentDescription` similarly for protein/carbs/fat. Preserve `viewLifecycleOwner` observation, `_binding = null` in `onDestroyView()`, null-session redirect, logout wiring, `MaterialDatePicker` UTC conversion. Do NOT re-inject `UserRepository` into the Fragment — all data comes from `viewModel.uiState`.
  - [x] Subtask 4.3: Number formatting: use `String.format(Locale.getDefault(), "%,.0f", value)` for calories and `"%.0f"` for grams in Fragment (or `NumberFormat`); keep full-precision Doubles in state, format only at bind time.

- [x] **Task 5: Koin — no new registrations unless UseCase added** (AC: #1)
  - [x] Subtask 5.1: If implementation is ViewModel + `DailySummary.summarize()` pure function (recommended), `di/AppModule.kt` needs NO change. If a `GetDailySummaryUseCase` is introduced instead, register as `factory` in `AppModule.kt` per AD-4/AD-6 and inject into `DashboardViewModel`. Do NOT use `single` for the calculator, do NOT scatter modules.

- [x] **Task 6: Tests** (AC: #1–#5)
  - [x] Subtask 6.1: NEW `app/src/test/.../domain/model/DailySummaryTest.kt` — empty list → zeros; multi-meal multi-food sums exact (e.g. 2 meals × foods sum to expected doubles within 0.001); foods with zeros handled.
  - [x] Subtask 6.2: MODIFY `app/src/test/.../presentation/viewmodel/DashboardViewModelTest.kt` — reuse existing `FakeSessionRepository`/`FakeMealRepository` + `InstantTaskExecutorRule` patterns (no MockK, no new deps): `selectDate` populates `dailySummary` totals from stubbed meals; `dailyGoal` mirrors `testUser.dailyGoal`; null-profile → empty summary + null goal; date with no meals → zero totals (not stale); skipped-target case (goal with null protein) exposes null so Fragment hides row (assert `state.dailyGoal?.targetProteinG == null`).
  - [x] Subtask 6.3: Verify with `.\gradlew.bat testDebugUnitTest` (or `./gradlew testDebugUnitTest` on Linux) — all suites green. No `androidTest` needed (no DAO changes).

### Review Findings

- [x] [Review][Defer] Hardcoded light-theme colors in layout XML [app/src/main/res/layout/fragment_dashboard.xml:211] — deferred, pre-existing
- [x] [Review][Defer] Hardcoded UI string literals without `@string/` resources [app/src/main/res/layout/fragment_dashboard.xml:228] — deferred, pre-existing

## Dev Notes

- **Architecture invariants (must follow, AD-1..AD-6):**
  - `domain/` zero `android.*`/Room imports (AD-1). `DailySummary.kt` pure Kotlin.
  - Synchronous only — no `suspend`, no coroutines, no `LiveData` async transforms; Room builder already uses `.allowMainThreadQueries()` (AD-2).
  - Entities never leak past `data/` — this story only consumes existing `MealEntry`/`Food` domain models + `DailyGoal` from `UserProfile.dailyGoal` (AD-3). Note: `SessionRepositoryImpl` holds the full `UserProfile` in memory including `dailyGoal` set at login — reuse it, do NOT query `UserRepository`/`UserProfileDao` again.
  - Koin centralized in `di/AppModule.kt`: `single` repos/DB, `factory` UseCases, `viewModel` VMs via `by viewModel()` (AD-6). No change expected.
  - Fragment: `_binding = null` in `onDestroyView()`, observe with `viewLifecycleOwner` (AD-5). Already correct in `DashboardFragment.kt` — preserve.
- **Current state of files being modified (read before editing):**
  - `DashboardViewModel.selectDate()` builds week days (Mon–Sun via `previousOrSame(MONDAY)`), loads `mealEntries` via `GetMealEntriesByDateUseCase(date.toString())`, emits `DashboardUiState`. You ADD summary + goal to this emission; change nothing else.
  - `DashboardUiState` is a 6-field data class (`activeDate`, `formattedDateHeader`, `weekDays`, `mealEntries`, `username`, `profileId`) — ADD 2 fields with defaults so existing tests/constructions don't break.
  - `DashboardFragment.observeViewModel()` binds welcome banner, profile, date header, week adapter, empty-meals toggle (`tvEmptyMeals` vs `layoutMealEntries` + `tvMealCount`). APPEND header binding; do NOT remove empty-state logic. `MealEntry` shape: `{id, title, date, time, foods: List<Food>}`; `Food` shape: `{name, weight, calories, carbs, fat, protein, fiber}` — sum the four macros only.
  - `DailyGoal` nullable fields: `targetCalories: Double?`, `targetProteinG/Carbs/Fat: Double?`, `targetWaterMl: Int?` (+ alias getters `calorieTarget` etc.). `null` = skipped → hide. `UserDailyGoalEntity` mirrors these as nullable columns — no DB migration needed (no schema change; `AppDatabase` stays version 2).
  - `fragment_dashboard.xml` is a `ScrollView > LinearLayout` with 3 cards (header, week-nav, meals). Insert macro card as 2nd child. Existing tokens: bg `#F8F9FA`, card `#FFFFFF`, accent `#1B4D3E`, ink `#2D3748`/`#718096`, radii 12dp cards / 24–28dp pills. `item_week_day.xml` already uses `fontFeatureSettings="tnum"` — follow that precedent.
- **What must be preserved (regression guard):** week-strip selection styling, prev/next/Today navigation, `MaterialDatePicker` UTC conversion (`Instant.ofEpochMilli(sel).atZone(ZoneId.of("UTC")).toLocalDate()`), session-guard redirect, logout navigation, empty-meals text `"No items logged for this date"`, Monday–Sunday weeks, `Locale.getDefault()` formatters.
- **Anti-patterns / do NOT do:** No FTS/network/Health Connect work (NFR-1 zero-network). No water widget (Story 2.3), no quick-add (2.4), no swipe-delete (2.5) — this story is header-only. No new DAO methods, no `allowMainThreadQueries` changes, no `AppDatabase` version bump, no cascade/manual-delete queries. No `Context`/views in ViewModel — pass nothing Android into VM; formatting lives in Fragment. No proportional-font numbers. No `INVISIBLE` for skipped goals (must be `GONE` so layout collapses).
- **Colors — explicit to prevent invention:** calories `#1B4D3E`, protein `#2D6A4F`, carbs `#E9C46A`, fat `#E76F51` (coral; DESIGN.md names "Coral" without hex — this is the chosen value, use consistently for light/dark in this story), tracks `#E2E8F0`, ink `#2D3748`/`#718096`. Note deferred debt D-2 (hardcoded hex without `@color/` resources) is a known pre-existing pattern — follow inline-hex consistency for this story; do NOT introduce a parallel theming system.
- **Known open review items from 2.1 (do NOT regress, do NOT silently fix unless 1 line):** Fragment dual-injects `SessionRepository` (keep as-is for guard; do NOT add more direct repo reads); day-pill radius 24dp vs spec 28dp, unselected number color, logout double-tap, DatePicker double-show — out of scope, leave unless the header work touches the same lines.
- **Performance:** recompute is O(foods) sum on main thread; `< 50ms` trivially met. No caching needed. No debounce.

### Project Structure Notes

- Base package: `com.example.myfoodtracker` under `app/src/main/java/com/example/myfoodtracker/`. Actual tree differs slightly from ARCHITECTURE-SPINE structural seed (seed shows `sergeevgk.myfoodtracker` + `IntakeLogDao`/`WaterLogDao`/`FoodSearchViewModel` which do NOT exist yet) — follow the REAL tree: `data/db/MealDao.kt`, `data/repository/MealRepositoryImpl.kt`, `domain/model/{MealEntry,Food,DailyGoal,UserProfile}.kt`, `domain/usecase/GetMealEntriesByDateUseCase.kt`, `presentation/viewmodel/DashboardViewModel.kt`, `presentation/ui/dashboard/{DashboardFragment,WeekDayAdapter,model/}`.
- Files:
  - NEW `domain/model/DailySummary.kt`
  - MODIFY `presentation/ui/dashboard/model/DashboardUiState.kt`
  - MODIFY `presentation/viewmodel/DashboardViewModel.kt`
  - MODIFY `presentation/ui/dashboard/DashboardFragment.kt`
  - MODIFY `app/src/main/res/layout/fragment_dashboard.xml`
  - NEW `app/src/test/.../domain/model/DailySummaryTest.kt`
  - MODIFY `app/src/test/.../presentation/viewmodel/DashboardViewModelTest.kt`
  - `di/AppModule.kt` MODIFY only if a new UseCase is introduced (prefer no change).
- Naming: layouts `fragment_*`/`item_*`, IDs `snake_case`, VMs `*ViewModel`, UseCases `*UseCase` per spine conventions.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story-2.2] — story statement + AC (tnum, hide skipped rings)
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/prd.md#4.1-FR-1] — blank targets saved as null, UI hides skipped metrics
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/prd.md#4.3-FR-5] — macro calc context; §7 0.1% accuracy, <50ms
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/addendum.md#1.1] — `intake_logs`/`user_daily_goals` schema, nullable targets, `YYYY-MM-DD`
- [Source: _bmad-output/planning-artifacts/architecture/architecture-MyFoodTracker-2026-09-06/ARCHITECTURE-SPINE.md#Invariants] — AD-1..AD-6; #Capability-Map FR-10/FR-11 dashboard
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-MyFoodTracker-2026-09-06/DESIGN.md#Components-1] — macro header card surface/behavior; #Typography tnum
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-MyFoodTracker-2026-09-06/EXPERIENCE.md#State-Patterns] — skipped-target ring hidden; #Accessibility-Floor TalkBack strings, 48dp, 4.5:1, 200% scaling
- [Source: _bmad-output/project-context.md] — sync Room, no entities past data/, UseCase `operator fun invoke`, Koin `single`/`factory`/`viewModel`, `_binding=null` + `viewLifecycleOwner`, `tnum`, build via `gradlew testDebugUnitTest`
- [Source: app/.../presentation/viewmodel/DashboardViewModel.kt] — `selectDate` emission point to extend
- [Source: app/.../presentation/ui/dashboard/model/DashboardUiState.kt] — 6-field state to extend with defaults
- [Source: app/.../domain/model/DailyGoal.kt + MealEntry.kt + Food.kt] — nullable goals + summable foods
- [Source: app/.../presentation/ui/dashboard/DashboardFragment.kt + app/src/main/res/layout/fragment_dashboard.xml] — binding + insertion point

## Dev Agent Record

### Agent Model Used

muse-spark-1.3-contributor-free (OpenCode)

### Debug Log References

- RED: wrote `DailySummaryTest` (4 cases) before `DailySummary` existed — compilation failure confirmed the test target.
- GREEN: created pure-Kotlin `domain/model/DailySummary.kt` (`summarize()` double-precision sum over `MealEntry.foods`); extended `DashboardUiState` with defaulted `dailySummary` + nullable `dailyGoal`; extended `DashboardViewModel.selectDate()` to emit both (no changes to week-nav methods).
- Layout: inserted `card_macro_header` `MaterialCardView` (12dp, `#FFFFFF`, 1dp `#E2E8F0` stroke, 20dp padding) between header and week-nav cards with calorie block + 3 macro rows; all numeric `TextView`s carry `android:fontFeatureSettings="tnum"`; `LinearProgressIndicator` colors per spec (`#1B4D3E`/`#2D6A4F`/`#E9C46A`/`#E76F51` on `#E2E8F0` track).
- Fragment: `observeViewModel()` appends `bindMacroHeader(state)` + `bindMacroRow()` helper — combined `"consumed / target"` text, `GONE` (not `INVISIBLE`) for null/zero targets, capped progress, `Locale.getDefault()` formatting, full-sentence TalkBack descriptions; session guard, week strip, empty-meals toggle, UTC DatePicker conversion untouched.
- Koin: verified no registration change needed (pure function, no new UseCase) — `AppModule.kt` untouched.
- Tests: extended `DashboardViewModelTest` with 4 cases reusing Fake repos; full `gradlew.bat testDebugUnitTest` green — 12 suites, 66 tests, 0 failures (DailySummaryTest 4/4, DashboardViewModelTest 12/12).

### Completion Notes List

- Implemented macro progress header card computing totals synchronously from date-filtered `MealEntry.foods` (AC #1, #5).
- Tabular figures on every numeric readout; M3 type scales preserved (AC #2).
- Skipped (null/zero) targets hide their row via `GONE`; value-only text with no progress bar and no divide-by-zero (AC #3).
- Consumed/target text, capped progress fill, TalkBack announcements per accessibility floor (AC #4).
- Recompute on every date change; empty days show zeros, never stale values (AC #5).
- Card tokens (surface, 12dp radius, hairline border, `wrap_content`/`sp`) preserve contrast and 200% font-scale safety (AC #6).
- Null-session guard and all Story 2.1 behaviors preserved (AC #7).

### File List

- `app/src/main/java/com/example/myfoodtracker/domain/model/DailySummary.kt` [NEW]
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/model/DashboardUiState.kt` [MODIFIED]
- `app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/DashboardViewModel.kt` [MODIFIED]
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/DashboardFragment.kt` [MODIFIED]
- `app/src/main/res/layout/fragment_dashboard.xml` [MODIFIED]
- `app/src/test/java/com/example/myfoodtracker/domain/model/DailySummaryTest.kt` [NEW]
- `app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/DashboardViewModelTest.kt` [MODIFIED]

### Change Log

- 2026-09-30: Implemented Story 2.2 (Macro Progress Header Card & Dynamic Target Display) — DailySummary domain calculator, DashboardUiState/ViewModel extension, macro header card layout with tnum + dynamic target hiding, Fragment binding with TalkBack, 8 new tests; full suite green (66/66). Status moved to review.
