---
baseline_commit: 3361213396d8cb2646a38c0293028ec0effd4ad8
---

# Story 2.3: Quick Water Logging (+250ml)

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user,
I want a quick-add button to log 250ml of water with one tap on the dashboard,
so that I can track my hydration effortlessly.

## Acceptance Criteria

1. **Given** the dashboard surface with the water widget, **When** the user taps the `+250ml` button, **Then** a `water_logs` record (`+250ml`, active `profile_id`, active date `YYYY-MM-DD`) is persisted to Room DB synchronously (`< 50ms`, no spinner) and the water total + fill indicator update immediately without page reload. [Source: epics.md Story 2.3, PRD FR-7, addendum §1.1]
2. **Given** the water widget, **When** rendered, **Then** it is a light-blue surface container (`#E1F5FE` light / `#002F4B` dark, `rounded/md` 12dp) with a prominent `+250ml` button (`water-primary` `#0288D1` light / `#29B6F6` dark, white text, `≥ 48dp` touch target), a total hydration readout in tabular figures (`tnum`), and a progress/fill indicator that fills toward the daily target; tap triggers instant tactile feedback plus a subtle wave/scale animation (`< 50ms`, no loading spinner). [Source: epics.md UX-DR4, DESIGN.md §Components-2 + tokens, NFR-6]
3. **Given** a profile whose `DailyGoal.targetWaterMl` is `null` (skipped during Story 1.1 setup), **When** the widget renders, **Then** logging still works, the progress indicator is set to `View.GONE` (not `INVISIBLE`), and the readout shows value-only text (e.g. `"1,250 ml"`) with no divide-by-zero; when a target exists the readout shows `"consumed / target"` (e.g. `"1,250 / 2,000 ml"`) with fill `progress = (total / target).coerceIn(0.0, 1.0)` capped at 100%. [Source: PRD FR-1, EXPERIENCE.md §State-Patterns, Story 2.2 AC #3 precedent]
4. **Given** any date selection (week strip, prev/next week, Today, month picker), **When** the active date changes, **Then** the widget recomputes the total from `GetWaterTotalUseCase(date.toString())` for the newly selected date (`profile_id` + `YYYY-MM-DD` isolated) and re-renders; empty days show `0 ml` (never stale previous-day values); cross-profile leakage is zero. [Source: Story 2.1 `DashboardViewModel.selectDate`, PRD FR-2/FR-10, NFR-5]
5. **Given** a successful `+250ml` log, **When** the write completes, **Then** TalkBack announces `"+250 milliliters water added. Total: X milliliters"` (with target if set); the widget card carries full-sentence `contentDescription` (button `"+250ml"` is acceptable), contrast is `≥ 4.5:1` in light and dark mode, and layout survives 200% font scale with no clipping (`wrap_content`/`sp`). [Source: epics.md Story 2.3, EXPERIENCE.md §Accessibility-Floor, NFR-6]
6. **Given** the dashboard surface, **When** tapped repeatedly or after process death / null session, **Then** each tap creates exactly one record (no double-write from double-tap), the existing Story 2.1 session guard is preserved (`sessionRepository.getActiveProfile()` null → navigate to `PasscodeAuthFragment`, water shows `0`, no crash), and daily sums stay within 0.1% tolerance of the manual sum of that day's records. [Source: Story 2.1 AC #7, PRD NFR-4, addendum §1.1]
7. **Given** this story's scope, **When** implementing, **Then** NO custom-volume / long-press-to-change-amount UI is built (explicitly deferred post-MVP per EXPERIENCE.md); the increment is a hard-coded `250ml` constant. [Source: addendum deferred, EXPERIENCE.md `Post-MVP only: Long-press widget → custom volume`]

## Tasks / Subtasks

- [x] **Task 1: Data — `WaterLogEntity` + `WaterLogDao` + `AppDatabase` v3** (AC: #1, #4, #6)
  - [x] Subtask 1.1: NEW `data/db/WaterLogEntity.kt` — `@Entity(tableName = "water_logs")` with `@ForeignKey(UserProfileEntity id → profile_id, CASCADE)` + `indices = [Index("profile_id"), Index("profile_id", "logged_date")]`; fields `id: String (PK, UUID)`, `profileId (profile_id)`, `date (logged_date: String YYYY-MM-DD)`, `amountMl (amount_ml: Int)`, `loggedAt (logged_at: Long)`. Follow `MealEntryEntity.kt` FK/index pattern exactly. No other tables touched.
  - [x] Subtask 1.2: NEW `data/db/WaterLogDao.kt` — synchronous only, NO `suspend`: `getWaterLogsByProfileIdAndDate(profileId, date): List<WaterLogEntity>`, `getTotalWaterMlByProfileIdAndDate(profileId, date): Int?` (`SELECT COALESCE(SUM(amount_ml),0)` … — handle null → 0 in repo), `@Insert(REPLACE) insertWaterLog`. All `@Query` filter `profile_id = :profileId AND logged_date = :date`. Follow `MealDao.kt` style.
  - [x] Subtask 1.3: MODIFY `data/db/AppDatabase.kt` — add `WaterLogEntity::class` to `entities`, bump `version = 2 → 3`, add `abstract fun waterLogDao(): WaterLogDao`. Builder already has `.fallbackToDestructiveMigration().allowMainThreadQueries()` — DO NOT change builder. `exportSchema` stays `false`.

- [x] **Task 2: Domain — `WaterLog` model + `WaterRepository` + 2 UseCases** (AC: #1, #4, #6)
  - [x] Subtask 2.1: NEW `domain/model/WaterLog.kt` — pure Kotlin, zero `android.*`/Room imports: `data class WaterLog(val id: String = "", val profileId: String = "", val date: String = "", val amountMl: Int = 0, val loggedAt: Long = 0L)`.
  - [x] Subtask 2.2: NEW `domain/repository/WaterRepository.kt` — `fun getWaterLogs(date: String): List<WaterLog>`, `fun getWaterTotalMl(date: String): Int`, `fun logWater(amountMl: Int, date: String): Int` (returns new total for that date). All synchronous.
  - [x] Subtask 2.3: NEW `data/repository/WaterRepositoryImpl.kt` — inject `(waterLogDao, sessionRepository)`; every method starts `val profileId = sessionRepository.getActiveProfileId() ?: return 0/emptyList()`; `logWater` creates `WaterLogEntity(UUID.randomUUID().toString(), profileId, date, amountMl, System.currentTimeMillis())`, inserts, then returns `getWaterTotalMl(date)`. Private `fun WaterLogEntity.toDomain()` mapper (AD-3, follow `MealRepositoryImpl.toDomain()`). No SQL outside DAO.
  - [x] Subtask 2.4: NEW `domain/usecase/LogWaterUseCase.kt` — `class LogWaterUseCase(repo) { operator fun invoke(amountMl: Int = 250, date: String): Int = repo.logWater(amountMl, date) }` (follow `GetMealEntriesByDateUseCase` one-liner). NEW `domain/usecase/GetWaterTotalUseCase.kt` — `operator fun invoke(date: String): Int = repo.getWaterTotalMl(date)`. No coroutines.

- [x] **Task 3: Presentation state — `DashboardUiState` + `DashboardViewModel`** (AC: #1, #4, #6)
  - [x] Subtask 3.1: MODIFY `presentation/ui/dashboard/model/DashboardUiState.kt` — ADD `val waterTotalMl: Int = 0` with default (keep all 8 existing fields + defaults so existing constructions/tests don't break).
  - [x] Subtask 3.2: MODIFY `presentation/viewmodel/DashboardViewModel.kt` — extend constructor to `(sessionRepository, logoutUseCase, getMealEntriesByDateUseCase, getWaterTotalUseCase: GetWaterTotalUseCase, logWaterUseCase: LogWaterUseCase)`; in `selectDate()` after existing meal/summary/goal lines add `val waterTotal = getWaterTotalUseCase(date.toString())` and include `waterTotalMl = waterTotal` in the emitted `DashboardUiState`. `previousWeek()/nextWeek()/jumpToToday()` need NO changes (they delegate to `selectDate`). ADD `fun logWaterPlus250() { val date = _uiState.value?.activeDate ?: LocalDate.now(); val total = logWaterUseCase(250, date.toString()); _uiState.value = _uiState.value?.copy(waterTotalMl = total) }`. Null profile → repo returns 0 → state shows 0 (no crash). No `Context`/views in VM, no formatting in VM, synchronous only.

- [x] **Task 4: Koin — register water graph** (AC: #1)
  - [x] Subtask 4.1: MODIFY `di/AppModule.kt` ONLY — add `single { get<AppDatabase>().waterLogDao() }`, `single<WaterRepository> { WaterRepositoryImpl(get(), get()) }`, `factory { LogWaterUseCase(get()) }`, `factory { GetWaterTotalUseCase(get()) }`, and update `viewModel { DashboardViewModel(get(), get(), get(), get(), get()) }`. No scattered modules, `single` for DAO/repo, `factory` for UseCases per AD-4/AD-6.

- [x] **Task 5: Layout — water widget in `fragment_dashboard.xml`** (AC: #2, #3, #5)
  - [x] Subtask 5.1: MODIFY `app/src/main/res/layout/fragment_dashboard.xml` — insert new `MaterialCardView` `android:id="@+id/card_water_widget"` as a direct child of the outer vertical `LinearLayout`, BETWEEN `card_macro_header` close tag and the `<!-- Week-View Navigation Card -->` comment (2nd→3rd position). `app:cardBackgroundColor="#E1F5FE"`, `app:cardCornerRadius="12dp"`, `app:cardElevation="2dp"`, inner `LinearLayout` padding `20dp`.
  - [x] Subtask 5.2: Widget contents — title `TextView @+id/tv_water_title` text `"Hydration"` (16sp bold, `#2D3748`); value `TextView @+id/tv_water_value` (24sp bold, `#0288D1`, `android:fontFeatureSettings="tnum"`); target `TextView @+id/tv_water_target` (14sp, `#718096`, `tnum`); `LinearProgressIndicator @+id/progress_water` (indicator `#0288D1`, track `#B3E5FC`, height 8dp); `MaterialButton @+id/btn_add_water_250` text `"+250ml"`, `backgroundTint="#0288D1"`, white text, `minWidth/minHeight ≥ 48dp`, `contentDescription="+250 milliliters water"`. All numeric `TextView`s MUST have `android:fontFeatureSettings="tnum"`. Do NOT reuse any existing IDs (`card_macro_header`, `rv_week_days`, `btn_prev_week`, …). Do NOT alter week/meals card IDs.
  - [x] Subtask 5.3: NO new vector drawables, no settings-screen work, no FTS/search work. Dark-mode note: follow the repo's accepted inline-hex pattern for this story (deferred debt D-2) — use the light tokens above; do NOT invent a parallel theming system.

- [x] **Task 6: Fragment — bind widget + TalkBack + animation** (AC: #1–#6)
  - [x] Subtask 6.1: MODIFY `presentation/ui/dashboard/DashboardFragment.kt` — in `setupListeners()` add `binding.btnAddWater250.setOnClickListener { viewModel.logWaterPlus250(); playWaterTapFeedback() }`. In `observeViewModel()` after `bindMacroHeader(state)` add `bindWaterWidget(state)`. Preserve session-guard redirect, week strip, empty-meals toggle, UTC `MaterialDatePicker` conversion, logout wiring, `_binding = null` in `onDestroyView()`, `observe(viewLifecycleOwner)`.
  - [x] Subtask 6.2: ADD `private fun bindWaterWidget(state: DashboardUiState)` — `val total = state.waterTotalMl; val target = state.dailyGoal?.targetWaterMl`; format with `String.format(Locale.getDefault(), "%,d", …)` (Fragment-only formatting); if `target != null && target > 0` show `"1,250 / 2,000 ml"` + `progress_water.setProgress(((total.toDouble()/target).coerceIn(0.0,1.0)*100).toInt())` + `tv_water_target/progress_water VISIBLE`, else value-only `"1,250 ml"` + both `GONE` (never `INVISIBLE`, avoid div-by-zero). Card `contentDescription` = `"Water: X of Y milliliters consumed, Z percent"` or `"Water: X milliliters consumed, no target set"`.
  - [x] Subtask 6.3: TalkBack announce on tap — after `logWaterPlus250()` the next `uiState` emission updates `contentDescription`; additionally call `binding.cardWaterWidget.announceForAccessibility("+250 milliliters water added. Total: $total milliliters")` from the observer when `waterTotalMl` increases (track previous total in a Fragment field, announce only on increase to avoid spamming on date change). Exact template: `"+250 milliliters water added. Total: X milliliters"`.
  - [x] Subtask 6.4: ADD `private fun playWaterTapFeedback()` — lightweight `ViewPropertyAnimator` only (no new deps, no Lottie): `binding.progressWater.animate().scaleX(1.06f).scaleY(1.06f).setDuration(90).withEndAction { …scale back 1.0f 120ms }` + same pulse on `tv_water_value`; guard with `if (!isAdded) return`. This is the "subtle wave animation feedback" — do NOT add spinners or network calls.

- [x] **Task 7: Tests** (AC: #1–#6)
  - [x] Subtask 7.1: NEW `app/src/test/.../domain/usecase/LogWaterUseCaseTest.kt` — Fake `WaterRepository` (in-memory `MutableMap<String, MutableList<Int>>` keyed by date): `invoke` delegates amount+date to repo; default `250` used when caller passes 250; different dates isolated. NEW `GetWaterTotalUseCaseTest.kt` — delegates date, empty → 0, sums multiple logs. No MockK, no new deps.
  - [x] Subtask 7.2: MODIFY `app/src/test/.../presentation/viewmodel/DashboardViewModelTest.kt` — reuse existing `FakeSessionRepository` (`AtomicReference<UserProfile?>`) + `FakeMealRepository` + `InstantTaskExecutorRule`; ADD `FakeWaterRepository` with same isolation semantics (`getActiveProfileId() null → 0/empty`); new cases: `selectDate` populates `waterTotalMl` from stubbed water; `logWaterPlus250` inserts 250 for active date and updates `waterTotalMl` (+250 → +500 on two taps); date change reloads per-date total (not stale); null-profile → `waterTotalMl == 0` and no crash; skipped-target case (`dailyGoal` with `targetWaterMl == null`) still logs and state exposes total so Fragment hides progress (assert `state.waterTotalMl == 250`).
  - [x] Subtask 7.3: Verify with `./gradlew testDebugUnitTest` (Linux) / `.\gradlew.bat testDebugUnitTest` (Windows) — all suites green. No `androidTest` needed (no async, destructive migration covers schema change in dev).

### Review Findings

- [x] [Review][Patch] P-1: Simplify TalkBack announcement on tap (removed `previousWaterTotalMl`, announces on button click with optional target) [app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/DashboardFragment.kt:88]
- [x] [Review][Defer] P-3: Add tactile / haptic feedback to +250ml tap [app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/DashboardFragment.kt:89] — deferred, post-MVP polish
- [x] [Review][Patch] P-5: Guard WaterRepositoryImpl.logWater against non-positive amounts [app/src/main/java/com/example/myfoodtracker/data/repository/WaterRepositoryImpl.kt:24]
- Note: P-2 (formatting thousands in announcement) ignored per user instruction; P-4 (button contentDescription) relaxed per user instruction ("+250ml" acceptable).

## Dev Notes

- **Architecture invariants (must follow, AD-1..AD-7):**
  - `domain/` zero `android.*`/Room imports (AD-1). `WaterLog.kt`, `WaterRepository.kt`, both UseCases pure Kotlin.
  - Synchronous only — no `suspend`, no coroutines, no `LiveData` transforms; builder already `.allowMainThreadQueries()` (AD-2).
  - Entities never leak past `data/` — Fragment/VM only see `WaterLog` domain model + `Int` totals (AD-3). Private `toDomain()` in `WaterRepositoryImpl`, same as `MealRepositoryImpl`.
  - UseCase `operator fun invoke()` + Koin `factory`; DAO/Repo `single`; VM `viewModel` via `by viewModel()` — all centralized in `di/AppModule.kt` (AD-4/AD-6).
  - Fragment: `_binding = null` in `onDestroyView()`, observe with `viewLifecycleOwner` (AD-5). Already correct — preserve.
  - Child table FK `CASCADE` + indexed FK columns (AD-7). Schema change → `AppDatabase` v3 + existing `.fallbackToDestructiveMigration()` (local-dev simplicity per project-context).
- **Current state of files being modified (read before editing):**
  - `DashboardViewModel.selectDate()` builds Mon–Sun week (`previousOrSame(MONDAY)`), loads `mealEntries` via `GetMealEntriesByDateUseCase(date.toString())`, adds `DailySummary.summarize()` + `profile?.dailyGoal`, emits 8-field `DashboardUiState`. You ADD water total to this emission; change nothing else. Constructor is currently 3-arg — becomes 5-arg (update `AppModule` + tests).
  - `DashboardUiState` is an 8-field data class with defaults — ADD `waterTotalMl: Int = 0` with default so existing constructions don't break.
  - `DashboardFragment.observeViewModel()` binds welcome/profile/date-header, week adapter, empty-meals toggle (`tvEmptyMeals` vs `layoutMealEntries` + `tvMealCount`), then `bindMacroHeader(state)` with `GONE`-for-skipped + capped progress + `Locale.getDefault()` formatting + full-sentence TalkBack. APPEND `bindWaterWidget(state)`; same patterns apply. `showDatePicker` UTC conversion (`ZoneId.of("UTC")` both directions) and session-guard redirect must not change.
  - `DailyGoal.targetWaterMl: Int?` (+ alias `waterTargetMl`) — `null` = skipped → hide progress. `UserDailyGoalEntity.target_water_ml: Int?` already exists — NO goal-schema change, NO migration beyond the new `water_logs` table. `DailySummary` is macros-only — do NOT add water to it; water total is a separate `Int` in state.
  - `fragment_dashboard.xml` = `ScrollView > LinearLayout` with 4 cards (header, `card_macro_header`, week-nav, meals). Insert water card as new 3rd child (between macro card close and week-nav comment). Tokens: bg `#F8F9FA`, macro card `#FFFFFF` 12dp 2dp + 1dp `#E2E8F0` stroke 20dp padding, accent `#1B4D3E`, ink `#2D3748`/`#718096`; water adds `water-primary #0288D1` / container `#E1F5FE` (dark `#29B6F6`/`#002F4B`).
  - `AppDatabase` v2 with `[MealEntryEntity, FoodEntity, UserProfileEntity, UserDailyGoalEntity]`, `mealDao()` + `userProfileDao()` only. `MealDao` precedent: `@Transaction @Query("SELECT * FROM meals WHERE profile_id = :profileId AND date = :date")`, `@Insert(REPLACE)`, update/delete scoped by `profile_id`. Mirror for water.
- **What must be preserved (regression guard):** week-strip selection, prev/next/Today, `MaterialDatePicker` UTC conversion, session-guard + logout navigation, empty-meals text, Mon–Sun weeks, `Locale.getDefault()` formatters, macro header values/progress/TalkBack (Story 2.2), `DailySummary` precision (NFR-4). Do NOT touch `rvWeekDays/btnPrevWeek/btnNextWeek/btnToday/ibCalendarPicker` IDs or meal-entry bindings.
- **Anti-patterns / do NOT do:** No custom-volume UI / long-press (deferred v2 — AC #7 explicitly forbids). No FTS/search/recipe/export work (other stories). No `suspend`/coroutines, no new threads. No `Context`/views in ViewModel. No proportional-font numbers (all numeric `TextViews` need `tnum`). No `INVISIBLE` for skipped water goal (must be `GONE`). No manual cascade deletes (SQLite handles it). No parallel color/string theming system (follow inline-hex consistency per deferred D-2). No `allowMainThreadQueries` removal. No `AppDatabase` downgrade. No double-write on rapid tap (single `logWater` per click; disable or debounce is NOT required — just don't fan-out).
- **Colors — explicit to prevent invention:** water container `#E1F5FE` (dark `#002F4B`), water primary/button/ring `#0288D1` (dark `#29B6F6`), water track `#B3E5FC`; ink `#2D3748`/`#718096`; base `#F8F9FA`, raised `#FFFFFF`. Calorie `#1B4D3E`, protein `#2D6A4F`, carbs `#E9C46A`, fat `#E76F51`, macro track `#E2E8F0` (unchanged from 2.2).
- **Known open review items from 2.1/2.2 (do NOT regress, do NOT silently fix unless 1 line):** Fragment dual-injects `SessionRepository` (keep as-is); day-pill radius 24dp vs 28dp, unselected number color, logout double-tap, DatePicker double-show — out of scope. Deferred D-2 hardcoded-hex Debt — follow it, don't fix it here.
- **Previous-story intelligence (2.2 must-reuse):** `FakeSessionRepository`/`FakeMealRepository` + `InstantTaskExecutorRule` test pattern (no MockK); `DailySummary.summarize()` pure-function precedent for keeping logic out of VM; `bindMacroRow` GONE/cap/TalkBack/format helper shape — mirror it for water; `selectDate`-delegation so prev/next/Today inherit new behavior; Koin no-change-if-pure-function lesson does NOT apply here (new DAO/repo/UseCases REQUIRE `AppModule` edits + `DashboardViewModelTest` constructor updates everywhere it instantiates the VM).
- **Performance:** `COALESCE(SUM)` single-row read + one insert per tap, both main-thread local, `< 50ms` trivially met. No caching, no debounce.

### Project Structure Notes

- Base package: `com.example.myfoodtracker` under `app/src/main/java/com/example/myfoodtracker/`. Real tree differs from ARCHITECTURE-SPINE seed (`sergeevgk.myfoodtracker` + `IntakeLogDao`/`WaterLogDao`/`FoodSearchViewModel` don't exist yet) — follow the REAL tree below.
- Files:
  - NEW `data/db/WaterLogEntity.kt` (note: spine says `data/entity/` — real meal entities live in `data/db/`, so `data/db/` keeps water beside `MealEntryEntity`; either location compiles as long as `AppDatabase` imports it — use `data/db/`)
  - NEW `data/db/WaterLogDao.kt`
  - MODIFY `data/db/AppDatabase.kt` (v2 → v3 + `waterLogDao()`)
  - NEW `domain/model/WaterLog.kt`
  - NEW `domain/repository/WaterRepository.kt`
  - NEW `data/repository/WaterRepositoryImpl.kt`
  - NEW `domain/usecase/LogWaterUseCase.kt`
  - NEW `domain/usecase/GetWaterTotalUseCase.kt`
  - MODIFY `presentation/ui/dashboard/model/DashboardUiState.kt` (add `waterTotalMl`)
  - MODIFY `presentation/viewmodel/DashboardViewModel.kt` (5-arg ctor, `selectDate` + `logWaterPlus250`)
  - MODIFY `di/AppModule.kt` (DAO + repo + 2 UseCase factories + VM update)
  - MODIFY `presentation/ui/dashboard/DashboardFragment.kt` (listener + `bindWaterWidget` + announce + pulse animation)
  - MODIFY `app/src/main/res/layout/fragment_dashboard.xml` (insert `card_water_widget`)
  - NEW `app/src/test/.../domain/usecase/LogWaterUseCaseTest.kt`
  - NEW `app/src/test/.../domain/usecase/GetWaterTotalUseCaseTest.kt`
  - MODIFY `app/src/test/.../presentation/viewmodel/DashboardViewModelTest.kt` (5-arg VM ctor + `FakeWaterRepository` + new cases)
- Naming: layouts `fragment_*`/`item_*`, IDs `snake_case` (`card_water_widget`, `tv_water_value`, `progress_water`, `btn_add_water_250`), VMs `*ViewModel`, UseCases `*UseCase` per spine conventions.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story-2.3] — story statement + AC (water_log record, wave animation, TalkBack template)
- [Source: _bmad-output/planning-artifacts/epics.md#UX-DR4] — water widget container/button/readout spec
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/prd.md#4.3-FR-7] — tap increments 250ml, total updates immediately
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/prd.md#4.1-FR-1] — blank water target saved as null, UI hides skipped progress
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/addendum.md#1.1] — `water_logs` / `user_daily_goals` schema, `YYYY-MM-DD`, deferred custom volume
- [Source: _bmad-output/planning-artifacts/architecture/architecture-MyFoodTracker-2026-09-06/ARCHITECTURE-SPINE.md#Invariants] — AD-1..AD-7; #Capability-Map `WaterLogDao` + `LogWaterUseCase`, dashboard `DashboardViewModel` water total
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-MyFoodTracker-2026-09-06/DESIGN.md#Components-2] — water widget surface/control/feedback + `water-primary` tokens
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-MyFoodTracker-2026-09-06/EXPERIENCE.md#Flow-2] — tap → ring fills + `1,000ml → 1,250ml`; #Accessibility-Floor TalkBack template; #State-Patterns skipped-target hiding
- [Source: _bmad-output/project-context.md] — sync Room, entity→domain private mapping, UseCase `operator fun invoke`, Koin `single`/`factory`/`viewModel`, `_binding=null` + `viewLifecycleOwner`, `tnum`, `fallbackToDestructiveMigration`
- [Source: app/.../data/db/MealEntryEntity.kt + MealDao.kt + AppDatabase.kt] — FK CASCADE + `Index(profile_id)` + `profile_id/date` query precedent to mirror
- [Source: app/.../data/repository/MealRepositoryImpl.kt] — `getActiveProfileId() ?: return emptyList()` isolation + private `toDomain()` precedent
- [Source: app/.../domain/usecase/GetMealEntriesByDateUseCase.kt] — single-line `operator fun invoke` precedent
- [Source: app/.../presentation/viewmodel/DashboardViewModel.kt + presentation/ui/dashboard/model/DashboardUiState.kt] — `selectDate` emission point + 8-field state to extend
- [Source: app/.../presentation/ui/dashboard/DashboardFragment.kt + app/src/main/res/layout/fragment_dashboard.xml] — binding/insertion point (between `card_macro_header` and week-nav card)
- [Source: app/.../domain/model/DailyGoal.kt + data/entity/UserDailyGoalEntity.kt] — nullable `targetWaterMl` / `target_water_ml`, no goal-schema change needed
- [Source: _bmad-output/implementation-artifacts/2-2-macro-progress-header-card-and-dynamic-target-display.md#Dev-Notes] — GONE/cap/TalkBack/`Locale`/`tnum`/Fake-repo test patterns to reuse; 5-arg VM ctor is the one breaking change 2.2 warns about

## Dev Agent Record

### Agent Model Used

muse-spark-1.3-contributor-free (OpenCode)

### Debug Log References

- RED: wrote `LogWaterUseCaseTest` (3 cases) + `GetWaterTotalUseCaseTest` (3 cases) before `WaterRepository`/`WaterLog`/UseCases existed — compilation failure confirmed the test target.
- GREEN: created `data/db/WaterLogEntity.kt` (`water_logs`, FK CASCADE + `profile_id`/`profile_id+logged_date` indices) + `WaterLogDao.kt` (sync, `COALESCE(SUM)` total, `REPLACE` insert); bumped `AppDatabase` v2 → v3 + `waterLogDao()` (builder already `fallbackToDestructiveMigration` + `allowMainThreadQueries`, untouched).
- Created pure-Kotlin `domain/model/WaterLog.kt`, `domain/repository/WaterRepository.kt`, `data/repository/WaterRepositoryImpl.kt` (`getActiveProfileId() ?: return 0/empty` isolation, UUID + `System.currentTimeMillis()`, private `toDomain()`), `LogWaterUseCase` (default `250`) + `GetWaterTotalUseCase` one-liners.
- Extended `DashboardUiState` with defaulted `waterTotalMl: Int = 0`; extended `DashboardViewModel` to 5-arg ctor, `selectDate()` emits water total, added `logWaterPlus250()` via `copy()` (week-nav methods delegate untouched).
- Koin: `single` water DAO/repo + `factory` 2 UseCases + 5-arg VM in `AppModule.kt` only.
- Layout: inserted `card_water_widget` (`#E1F5FE`, 12dp, 20dp padding) between macro header and week-nav with `tv_water_title/value/target` (`tnum`), `progress_water` (`#0288D1` on `#B3E5FC`, 8dp), `btn_add_water_250` (`#0288D1`, white text, 48dp min).
- Fragment: `btnAddWater250` listener + `bindWaterWidget` append (combined `"consumed / target"` text, `GONE` for null/zero target, capped progress, `Locale.getDefault()` `"%,d"` formatting, full-sentence TalkBack, announce-on-increase only via `previousWaterTotalMl` tracker, `ViewPropertyAnimator` pulse with `isAdded` guards + `@Suppress("DEPRECATION")` for the minSdk-35 announce API).
- Tests: extended `DashboardViewModelTest` with `FakeWaterRepository` (profile+date keyed, null-profile → 0) + 5 new cases; full `gradlew.bat testDebugUnitTest` green — 14 suites, 77 tests, 0 failures (was 66 before this story; +11 new).

### Completion Notes List

- Implemented quick water logging: each `+250ml` tap writes one `water_logs` record synchronously and updates total + fill indicator instantly (AC #1, #6).
- Water widget follows UX-DR4 tokens with `tnum` readouts, `≥ 48dp` button, instant pulse feedback, no spinners (AC #2).
- Skipped (null/zero) water target hides progress via `GONE`, value-only text, no divide-by-zero; targets show capped fill (AC #3).
- Water total recomputes on every date change via `GetWaterTotalUseCase`; empty days show `0 ml`, queries isolated by `profile_id + logged_date` (AC #4).
- TalkBack announces `"+250 milliliters water added. Total: X milliliters"` only on increase; card/button carry full-sentence descriptions; `wrap_content`/`sp` preserves 200% font scale (AC #5).
- One record per tap, session-guard + all Story 2.1/2.2 behaviors preserved, sums exact (AC #6). No custom-volume UI built per scope ban (AC #7).

### File List

- `app/src/main/java/com/example/myfoodtracker/data/db/WaterLogEntity.kt` [NEW]
- `app/src/main/java/com/example/myfoodtracker/data/db/WaterLogDao.kt` [NEW]
- `app/src/main/java/com/example/myfoodtracker/data/db/AppDatabase.kt` [MODIFIED]
- `app/src/main/java/com/example/myfoodtracker/domain/model/WaterLog.kt` [NEW]
- `app/src/main/java/com/example/myfoodtracker/domain/repository/WaterRepository.kt` [NEW]
- `app/src/main/java/com/example/myfoodtracker/data/repository/WaterRepositoryImpl.kt` [NEW]
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/LogWaterUseCase.kt` [NEW]
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/GetWaterTotalUseCase.kt` [NEW]
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/model/DashboardUiState.kt` [MODIFIED]
- `app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/DashboardViewModel.kt` [MODIFIED]
- `app/src/main/java/com/example/myfoodtracker/di/AppModule.kt` [MODIFIED]
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/DashboardFragment.kt` [MODIFIED]
- `app/src/main/res/layout/fragment_dashboard.xml` [MODIFIED]
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/LogWaterUseCaseTest.kt` [NEW]
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/GetWaterTotalUseCaseTest.kt` [NEW]
- `app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/DashboardViewModelTest.kt` [MODIFIED]

### Change Log

- 2026-09-30: Implemented Story 2.3 (Quick Water Logging +250ml) — water_logs table + DAO (AppDatabase v3), WaterRepository + 2 UseCases, DashboardUiState/ViewModel extension, water widget layout with tnum + dynamic target hiding, Fragment binding with TalkBack announce + pulse animation, 11 new tests; full suite green (77/77). Status moved to review.
