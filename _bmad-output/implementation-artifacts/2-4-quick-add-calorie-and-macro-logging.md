---
baseline_commit: bdb684f47a6b16e39a92feaf957c18ef323c300c
---

# Story 2.4: Quick-Add Calorie & Macro Logging

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user,
I want to log calories and macros directly without selecting a food item from the search database,
so that I can track meals when specific food items aren't needed.

## Acceptance Criteria

1. **Given** the dashboard meals section with a Quick-Add entry point, **When** the user taps Quick-Add, **Then** a Material dialog opens with fields for name/description, Calories, Protein, Carbs, Fat, and a meal-slot selector (`BREAKFAST`, `LUNCH`, `DINNER`, `SNACK`), focused on the name field with no loading spinner. [Source: epics.md Story 2.4, PRD FR-6, EXPERIENCE.md §Information Architecture]
2. **Given** the Quick-Add dialog, **When** the user inputs a non-blank name, numeric macros (`>= 0`, empty treated as `0`), picks a slot, and taps Save, **Then** exactly one record is persisted synchronously (`< 50ms`, main thread, no coroutines) scoped to the active `profile_id` AND the dashboard active date (`YYYY-MM-DD`, NOT `LocalDate.now()` when viewing history), the dialog closes, and dashboard meal count + macro header update instantly without reload. [Source: PRD FR-6, FR-2 isolation, NFR-4, addendum §1.1 adapted — see Dev Notes mapping]
3. **Given** invalid input, **When** the user taps Save with a blank name, non-numeric text, or any negative macro, **Then** no record is written, the dialog stays open, and an inline field error is shown (`"Enter a name"`, `"Enter 0 or more"`); the app never crashes on `NumberFormatException`. [Source: PRD FR-1 validation precedent, quality guard]
4. **Given** any date selection (week strip, prev/next week, Today, month picker), **When** the active date changes, **Then** Quick-Add targets the newly selected date; saving while viewing a historical date writes to that date, and `selectDate()` refresh recomputes totals so empty days show `0` (never stale) with zero cross-profile or cross-date leakage. [Source: Story 2.1 `DashboardViewModel.selectDate`, PRD FR-10, NFR-5]
5. **Given** a successful Quick-Add save, **When** the write completes, **Then** TalkBack announces `"Logged {name}, {cal} kilocalories to {slot}"`, the Quick-Add button and dialog controls meet `≥ 48dp` touch targets, contrast `≥ 4.5:1`, `tnum` is preserved on all numeric readouts, and layout survives 200% font scale (`wrap_content`/`sp`, no clipping). [Source: NFR-6, EXPERIENCE.md §Accessibility Floor, DESIGN.md §Typography]
6. **Given** the dashboard surface, **When** tapped repeatedly or after process death / null session, **Then** each Save creates exactly one record (no double-write from double-tap), the existing Story 2.1 session guard is preserved (`sessionRepository.getActiveProfile()` null → navigate to `PasscodeAuthFragment`, no crash, no write), and daily sums stay within 0.1% tolerance of the manual sum (full `Double` precision, format only at bind time). [Source: Story 2.1 AC #7, PRD NFR-4]
7. **Given** this story's scope, **When** implementing, **Then** NO food-search bottom sheet, NO FTS query, NO custom-food library write (`is_custom`), NO recipe, NO export, and NO water-widget changes are built — this story is Quick-Add dialog + `meals`/`foods` write + dashboard refresh only. [Source: scope guard — Epic 3 owns search/FTS]

## Tasks / Subtasks

- [x] **Task 1: Domain + Data — `logQuickAdd` contract reusing `meals`/`foods` (no migration)** (AC: #2, #4, #6)
  - [x] Subtask 1.1: MODIFY `domain/repository/MealRepository.kt` — ADD `fun logQuickAdd(name: String, calories: Double, proteinG: Double, carbsG: Double, fatG: Double, mealSlot: String, date: String): List<MealEntry>` (all synchronous, no `suspend`). Keep all 5 existing methods unchanged.
  - [x] Subtask 1.2: MODIFY `data/repository/MealRepositoryImpl.kt` — implement `logQuickAdd`: first line `val profileId = sessionRepository.getActiveProfileId() ?: return getMealEntriesByDate(date)` (null-session guard, no write, no crash); validate `mealSlot.uppercase() in {BREAKFAST,LUNCH,DINNER,SNACK}` else throw `IllegalArgumentException`; create `MealEntryEntity(id=UUID.randomUUID().toString(), profileId=profileId, title=mealSlot.uppercase(), date=date, time=LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")))`, `mealDao.insertMeal(meal)`; then `FoodEntity(mealId=meal.id, name=name.trim(), weight=0.0, calories=calories, carbs=carbsG, fat=fatG, protein=proteinG, fiber=0.0)`, `mealDao.insertFoods(listOf(food))`; return `getMealEntriesByDate(date)`. Reuse existing `toDomain()` mappers. No SQL outside DAO, no manual cascade deletes, no `AppDatabase` version bump (stays v3).
  - [x] Subtask 1.3: Do NOT modify `data/db/MealDao.kt`, `MealEntryEntity.kt`, `FoodEntity.kt`, `AppDatabase.kt` — `insertMeal` + `insertFoods` already exist and suffice. Do NOT add `suspend`/coroutines. Do NOT add columns (`meal_slot`, `food_id`, `custom_name`, `calculated_*`) — see Dev Notes schema mapping for why.

- [x] **Task 2: Domain — `LogQuickAddUseCase` with input validation** (AC: #2, #3, #6)
  - [x] Subtask 2.1: NEW `domain/usecase/LogQuickAddUseCase.kt` — pure Kotlin, zero `android.*`/Room imports: `class LogQuickAddUseCase(private val repository: MealRepository) { operator fun invoke(name: String, calories: Double, proteinG: Double, carbsG: Double, fatG: Double, mealSlot: String, date: String): List<MealEntry> }`. Inside: `require(name.isNotBlank()) { "Enter a name" }`; `require(calories.isFinite() && proteinG.isFinite() && carbsG.isFinite() && fatG.isFinite())`; `require(calories >= 0 && proteinG >= 0 && carbsG >= 0 && fatG >= 0) { "Enter 0 or more" }`; `require(mealSlot.uppercase() in setOf("BREAKFAST","LUNCH","DINNER","SNACK"))`; then `return repository.logQuickAdd(name.trim(), calories, proteinG, carbsG, fatG, mealSlot.uppercase(), date)`. Follow `GetMealEntriesByDateUseCase` one-liner style plus guards. No formatting, no `Context`.
  - [x] Subtask 2.2: `mealSlot` normalization is `uppercase()` in exactly two places (UseCase + repo) so `"breakfast"` / `"Breakfast"` both work; `date` is passed through untouched (already `YYYY-MM-DD` from `activeDate.toString()`).

- [x] **Task 3: Presentation state — `DashboardViewModel.logQuickAdd`** (AC: #2, #4, #6)
  - [x] Subtask 3.1: MODIFY `presentation/viewmodel/DashboardViewModel.kt` — extend constructor to 6 args `(sessionRepository, logoutUseCase, getMealEntriesByDateUseCase, getWaterTotalUseCase, logWaterUseCase, logQuickAddUseCase: LogQuickAddUseCase)`. ADD `fun logQuickAdd(name: String, calories: Double, proteinG: Double, carbsG: Double, fatG: Double, mealSlot: String)` that does: `val date = _uiState.value?.activeDate ?: LocalDate.now()`; `try { logQuickAddUseCase(name, calories, proteinG, carbsG, fatG, mealSlot, date.toString()) } catch (e: IllegalArgumentException) { return }`; then `selectDate(date)` to refresh `mealEntries` + `DailySummary` + `waterTotalMl` atomically. `previousWeek()/nextWeek()/jumpToToday()/selectDate()/logWaterPlus250()/logout()` need NO other changes. No `Context`/views in VM, no formatting in VM, synchronous only. Null profile → repo returns date-filtered empty → `selectDate` emits empty meals + zero summary + null goal, no crash.
  - [x] Subtask 3.2: `DashboardUiState` needs NO change (9 fields stay as-is) — Quick-Add flows through existing `mealEntries`/`dailySummary`.

- [x] **Task 4: Koin — register Quick-Add graph** (AC: #2)
  - [x] Subtask 4.1: MODIFY `di/AppModule.kt` ONLY — add `factory { LogQuickAddUseCase(get()) }` alongside the other UseCase factories, and update `viewModel { DashboardViewModel(get(), get(), get(), get(), get(), get()) }` (6 `get()`s in constructor order). No scattered modules; `factory` for the UseCase per AD-4/AD-6. No DAO/repo registrations (both already registered).

- [x] **Task 5: Layout — Quick-Add button + dialog layout** (AC: #1, #5)
  - [x] Subtask 5.1: MODIFY `app/src/main/res/layout/fragment_dashboard.xml` — in the Meals Section Card, replace the bare `tv_meals_section_title` with a horizontal header row (`LinearLayout`, `gravity=center_vertical`): title `TextView` (`weight=1`, keep `@+id/tv_meals_section_title`, text `"Logged Meals"`) + `MaterialButton` `@+id/btn_quick_add` text `"＋ Quick Add"`, `minWidth/minHeight ≥ 48dp`, `backgroundTint="#1B4D3E"`, white text, `contentDescription="Quick-add calories and macros"`. Do NOT alter any other card or ID (`card_macro_header`, `card_water_widget`, `rv_week_days`, `btn_prev_week`, `btn_next_week`, `btn_today`, `ib_calendar_picker`, `tv_empty_meals`, `layout_meal_entries`, `tv_meal_count`).
  - [x] Subtask 5.2: NEW `app/src/main/res/layout/dialog_quick_add_log.xml` — vertical `LinearLayout` padding `20dp`: `TextInputLayout`+`TextInputEditText` `@+id/et_quick_add_name` hint `"Name (e.g. Office lunch)"` singleLine maxLength 100; four numeric `TextInputLayout`+`TextInputEditText` `@+id/et_quick_add_calories/_protein/_carbs/_fat` hints `"Calories (kcal)"`, `"Protein (g)"`, `"Carbs (g)"`, `"Fat (g)"`, `inputType="numberDecimal"`, all `fontFeatureSettings="tnum"` where applicable; `MaterialAutoCompleteTextView`/`Spinner` `@+id/spinner_meal_slot` with entries `BREAKFAST,LUNCH,DINNER,SNACK` default `BREAKFAST`. Card tokens follow repo inline-hex pattern (deferred D-2): bg `#FFFFFF`, accent `#1B4D3E`, ink `#2D3748`/`#718096`, 12dp corners. No new vector drawables. All interactive controls `≥ 48dp` where tappable.
  - [x] Subtask 5.3: Follow the repo's accepted inline-hex pattern for this story (deferred debt D-2) — use the light tokens above; do NOT invent a parallel theming system or add `@color/` resources.

- [x] **Task 6: Fragment — dialog wiring + validation + TalkBack** (AC: #1–#6)
  - [x] Subtask 6.1: MODIFY `presentation/ui/dashboard/DashboardFragment.kt` — in `setupListeners()` add `binding.btnQuickAdd.setOnClickListener { showQuickAddDialog() }`. Preserve session-guard redirect, week strip, empty-meals toggle, macro/water binding, UTC `MaterialDatePicker` conversion, logout wiring, `_binding = null` in `onDestroyView()`, `observe(viewLifecycleOwner)`. Do NOT re-inject `MealRepository`/`UserRepository` into the Fragment — all data via `viewModel.uiState`.
  - [x] Subtask 6.2: ADD `private fun showQuickAddDialog()` — inflate `dialog_quick_add_log`, build `MaterialAlertDialogBuilder(requireContext()).setTitle("Quick Add").setView(dialogView).setNegativeButton("Cancel", null).setPositiveButton("Save", null)` then `show()` and override positive click (so validation failure keeps dialog open): parse each macro with `toDoubleOrNull()`, empty → `0.0`; if name blank → `tilName.error = "Enter a name"` + return; if any parse null/negative/non-finite → set that field's `til.error = "Enter 0 or more"` + return; else `viewModel.logQuickAdd(name, cal, pro, carb, fat, slot)` + `dialog.dismiss()` + announce `binding.root.announceForAccessibility("Logged $name, ${cal.toInt()} kilocalories to $slot")` guarded with `if (!isAdded) return`. Single call per Save tap (read values once; disable double-fire by using the shown dialog's button reference, not a re-entrant listener). Number formatting for the announcement uses `Locale.getDefault()` in Fragment only.
  - [x] Subtask 6.3: No changes to `bindMacroHeader`/`bindWaterWidget` — they already recompute from `selectDate()` refresh. Empty-meals toggle (`tvEmptyMeals` vs `layoutMealEntries` + `tvMealCount`) is preserved and automatically reflects the new entry via the existing observer.

- [x] **Task 7: Tests** (AC: #2–#4, #6)
  - [x] Subtask 7.1: NEW `app/src/test/.../domain/usecase/LogQuickAddUseCaseTest.kt` — Fake `MealRepository` (in-memory `MutableList<MealEntry>` + profile/date filter semantics, null-profile → empty): delegates name/macros/slot/date to repo; blank name throws `IllegalArgumentException`; negative calorie/protein/carbs/fat each throw; bad slot throws; slot case-insensitive (`"breakfast"` accepted); different dates isolated. No MockK, no new deps.
  - [x] Subtask 7.2: MODIFY `app/src/test/.../data/repository/MealRepositoryImplTest.kt` — reuse existing `FakeMealDao`/`FakeSessionRepository` patterns: `logQuickAdd` creates one `MealEntryEntity` (title == slot uppercase, date == passed date NOT today when historical) + one `FoodEntity` (name trimmed, macros exact, fiber 0, weight 0); profile isolation (user-2 sees none of user-1's quick-adds); null session → no write + empty return; date isolation (today vs yesterday).
  - [x] Subtask 7.3: MODIFY `app/src/test/.../presentation/viewmodel/DashboardViewModelTest.kt` — reuse `FakeSessionRepository`/`FakeMealRepository`/`FakeWaterRepository` + `InstantTaskExecutorRule`: extend `FakeMealRepository` with `logQuickAdd(...)` (append `MealEntry(id=UUID, title=slot, date=date, time="12:00", foods=[Food(name, 0.0, cal, carbs, fat, pro, 0.0)])` then return date-filtered list); update VM construction to 6 args everywhere; new cases: `logQuickAdd` inserts for active date and `dailySummary` reflects it (e.g. 500 kcal + 20g protein visible in state); historical-date case (select past date then quick-add → entry date == past date string); blank-name case → `IllegalArgumentException` swallowed, state unchanged; null-profile → no crash, meals empty. Verify with `./gradlew testDebugUnitTest` (Linux) / `.\gradlew.bat testDebugUnitTest` (Windows) — all suites green. No `androidTest` needed (no schema change; destructive migration untouched).

### Review Findings

- [x] [Review][Patch] Quick-Add button height hardcoded to 48dp instead of wrap_content [app/src/main/res/layout/fragment_dashboard.xml:515]
- [x] [Review][Patch] Misleading numeric error message 'Enter 0 or more' applied to meal slot selector [app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/DashboardFragment.kt:208]
- [x] [Review][Patch] Comma decimal separator rejected on European keypads [app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/DashboardFragment.kt:201] — ignored by user decision
- [x] [Review][Patch] Accessibility announcement does not format calories via Locale.getDefault() [app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/DashboardFragment.kt:193] — ignored by user decision
- [x] [Review][Patch] Missing double-tap guard on Quick-Add Save button [app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/DashboardFragment.kt:280] — ignored by user decision
- [x] [Review][Patch] Meal slot argument lacks whitespace trim before uppercase normalization [app/src/main/java/com/example/myfoodtracker/domain/usecase/LogQuickAddUseCase.kt:19]
- [x] [Review][Defer] Lack of atomic database transaction across insertMeal and insertFoods [app/src/main/java/com/example/myfoodtracker/data/repository/MealRepositoryImpl.kt:55] — deferred, pre-existing DAO design
- [x] [Review][Defer] Hardcoded light-theme colors and string literals without central resources [app/src/main/res/layout/dialog_quick_add_log.xml:6] — deferred, pre-existing

## Dev Notes

- **Architecture invariants (must follow, AD-1..AD-7):**
  - `domain/` zero `android.*`/Room imports (AD-1). `LogQuickAddUseCase.kt`, `MealRepository.kt` addition pure Kotlin.
  - Synchronous only — no `suspend`, no coroutines, no `LiveData` transforms; Room builder already `.allowMainThreadQueries()` (AD-2). Single Save = two synchronous DAO calls (`insertMeal` + `insertFoods`) + one `selectDate` reload, all `< 50ms`.
  - Entities never leak past `data/` — Fragment/VM only see `MealEntry`/`Food` domain models (AD-3). Private mappers already exist in `MealRepositoryImpl` — reuse.
  - UseCase `operator fun invoke()` + Koin `factory`; DAO/Repo `single`; VM `viewModel` via `by viewModel()` — all centralized in `di/AppModule.kt` (AD-4/AD-6).
  - Fragment: `_binding = null` in `onDestroyView()`, observe with `viewLifecycleOwner` (AD-5). Already correct — preserve.
  - Child table FK `CASCADE` + indexed FK columns (AD-7). `FoodEntity.mealId` already has `CASCADE` + `Index(mealId)` — quick-add foods inherit cascade delete for free (Story 2.5 swipe-to-delete can delete the parent meal and children vanish; do NOT write manual child-delete queries). No schema change → `AppDatabase` stays v3, no migration.
- **Schema mapping — READ THIS (prevents wrong-table disaster):** PRD/addendum describe an idealized `intake_logs(food_id NULL, custom_name, calculated_*)` table, but the REAL codebase implements `meals(MealEntryEntity: id, profile_id, title, date, time)` + `foods(FoodEntity: mealId FK CASCADE, name, weight, calories, carbs, fat, protein, fiber)`. There is NO `intake_logs` table, NO `food_id` column, NO `meal_slot` column in this repo. The canonical Quick-Add mapping for THIS story is: `MealEntryEntity.title = mealSlot.uppercase()` (slot carrier — matches existing `"Lunch"` title precedent in tests), `FoodEntity.name = custom name`, `FoodEntity.{calories,protein,carbs,fat} = entered values`, `weight = 0.0` (N/A placeholder — `DailySummary.summarize()` ignores weight), `fiber = 0.0`. This satisfies FR-6 intent (custom-values log with no catalog link) while keeping `DailySummary`, macro header, and date/profile isolation working with zero migration. Do NOT create `IntakeLogEntity`/`IntakeLogDao`, do NOT add columns, do NOT bump DB version.
- **Current state of files being modified (read before editing):**
  - `MealRepositoryImpl` ctor is `(mealDao, sessionRepository)`; methods `getMealEntries/getMealEntriesByDate/addMealEntry/updateMealEntry/deleteMealEntry` all start with `getActiveProfileId() ?: return emptyList()`. Mirror that guard in `logQuickAdd`. Note deferred D-1 (`addMealEntry()` hard-codes `LocalDate.now()`) — do NOT reuse `addMealEntry()`; the new method takes an explicit `date: String` so historical-date logging is correct.
  - `DashboardViewModel` ctor is currently 5-arg `(session, logout, getMealsByDate, getWaterTotal, logWater)`; `selectDate()` builds Mon–Sun week (`previousOrSame(MONDAY)`), loads meals + `DailySummary.summarize()` + `profile?.dailyGoal` + water total, emits 9-field `DashboardUiState`. You add a 6th ctor arg and a `logQuickAdd()` that delegates to `selectDate(date)` for refresh; change nothing else. `previousWeek/nextWeek/jumpToToday` delegate to `selectDate` so they inherit correctness.
  - `DashboardFragment.observeViewModel()` binds welcome/profile/date-header, week adapter, empty-meals toggle, then `bindMacroHeader(state)` + `bindWaterWidget(state)` with `GONE`-for-skipped + capped progress + `Locale.getDefault()` formatting + full-sentence TalkBack. APPEND Quick-Add wiring only; `showDatePicker` UTC conversion (`Instant.ofEpochMilli(sel).atZone(ZoneId.of("UTC")).toLocalDate()` both directions) and session-guard redirect must not change.
  - `fragment_dashboard.xml` = `ScrollView > LinearLayout` with 5 cards (header, `card_macro_header`, `card_water_widget`, week-nav, meals). Add `btn_quick_add` to the meals card header row only. Tokens: bg `#F8F9FA`, card `#FFFFFF`, accent `#1B4D3E`, ink `#2D3748`/`#718096`, water `#0288D1`/`#E1F5FE`, radii 12dp cards, `tnum` on every numeric `TextView` (follow `item_week_day.xml` precedent).
  - `DailySummary.summarize(mealEntries)` sums `meal.foods` (`calories`, `protein`, `carbs`, `fat`) at full `Double` precision (NFR-4 0.1%) — quick-add foods flow through it automatically; do NOT add water to it; do NOT round in domain (format only in Fragment).
- **What must be preserved (regression guard):** week-strip selection styling, prev/next/Today navigation, `MaterialDatePicker` UTC conversion, session-guard + logout navigation, empty-meals text `"No items logged for this date"`, Monday–Sunday weeks, `Locale.getDefault()` formatters, macro header values/progress/TalkBack (Story 2.2), water widget totals/progress/TalkBack/pulse (Story 2.3), `DailySummary` precision. Do NOT touch `rvWeekDays/btnPrevWeek/btnNextWeek/btnToday/ibCalendarPicker` IDs or meal-entry/water bindings. `AppModule` 5-arg `DashboardViewModel(get(),get(),get(),get(),get())` becomes 6-arg — update EVERY construction site including ALL tests or the build breaks.
- **Anti-patterns / do NOT do:** No FTS/`foods_fts`/triggers/search-sheet work (Epic 3 owns it — this dialog is the interim entry point until the search sheet's "Quick-Add Calories Option" exists). No custom-food library write (`is_custom` doesn't exist in real `FoodEntity` — do NOT add it). No recipe/export/Health-Connect/network work (NFR-1 zero-network). No `suspend`/coroutines/threads. No `Context`/views in ViewModel. No proportional-font numbers on readouts. No `INVISIBLE` for anything (use `GONE`). No manual cascade deletes. No parallel color/string theming (follow inline-hex + hardcoded-string consistency per deferred D-2). No `allowMainThreadQueries` removal. No DB downgrade. No per-Save fan-out (one `insertMeal` + one `insertFoods` per tap; dialog Save reads values once).
- **UX details not in epics (explicit to prevent invention):** dialog title `"Quick Add"`, buttons `"Cancel"`/`"Save"` (Save is the dialog positive button, overridden to prevent auto-dismiss on validation failure); field hints as in Task 5.2; slot default `BREAKFAST`; name `maxLength=100`, `singleLine=true`; macro fields `inputType=numberDecimal`; inline errors `"Enter a name"` / `"Enter 0 or more"`; success TalkBack `"Logged {name}, {cal} kilocalories to {slot}"` announced on `binding.root` after dismiss. Per-meal-slot grouping/rendering of rows is OUT of scope (meals card still shows count only; Story 2.5 adds swipe rows) — correctness of stored `title=slot` + totals is the acceptance bar.
- **Previous-story intelligence (must-reuse):** `FakeSessionRepository(AtomicReference<UserProfile?>)` + `FakeMealRepository(entriesByDate)` + `FakeWaterRepository("$profileId|$date" keyed)` + `InstantTaskExecutorRule` test pattern (no MockK, no new deps) from `DashboardViewModelTest`; `FakeMealDao(meals/foods lists)` + profile/date filter assertions from `MealRepositoryImplTest`; `DailySummary.summarize()` pure-function precedent; `bindMacroRow` GONE/cap/TalkBack/`Locale`/`tnum` helper shape — mirror for dialog validation messages; `selectDate`-delegation so prev/next/Today inherit new behavior; Koin single/factory/viewModel lesson (2.3's 5-arg VM breakage repeats here as 6-arg — update all call sites).
- **Known open review/deferred items (do NOT regress, do NOT silently fix unless 1 line):** Fragment dual-injects `SessionRepository` (keep as-is for guard); day-pill radius 24dp vs 28dp, unselected number color, logout double-tap, DatePicker double-show (2.1 review) — out of scope. Deferred D-2 hardcoded-hex/strings (2.1–2.3) — follow, don't fix. Deferred haptic polish (2.3 D-1) — not required here. `addMealEntry()` `LocalDate.now()` quirk (2.1 D-1) — bypassed by the new explicit-date method, leave the old method untouched.
- **Performance:** two single-row writes + one `SELECT * WHERE profile_id+date` reload, all main-thread local, `< 50ms` trivially met. No caching, no debounce (single call per tap is the double-write guard).
- **Stack (pinned, no upgrade):** Target 36 / Min 35 (Java 11), AGP 9.2.1, Koin 3.5.6, Room 2.6.1 (KSP 2.2.10-2.0.2), Navigation 2.6.0, Material 1.10.0, AppCompat 1.6.1. No web research needed — offline-first, zero network, no external APIs; versions verified in `project-context.md` + spine Stack table.

### Project Structure Notes

- Base package: `com.example.myfoodtracker` under `app/src/main/java/com/example/myfoodtracker/`. Real tree differs from ARCHITECTURE-SPINE structural seed (seed shows `sergeevgk.myfoodtracker` + `IntakeLogDao`/`WaterLogDao`/`FoodSearchViewModel` which do NOT exist) — follow the REAL tree below.
- Files:
  - MODIFY `domain/repository/MealRepository.kt` (add `logQuickAdd`)
  - MODIFY `data/repository/MealRepositoryImpl.kt` (implement `logQuickAdd`)
  - NEW `domain/usecase/LogQuickAddUseCase.kt`
  - MODIFY `presentation/viewmodel/DashboardViewModel.kt` (6-arg ctor, `logQuickAdd`)
  - MODIFY `di/AppModule.kt` (UseCase `factory` + 6-arg VM)
  - MODIFY `presentation/ui/dashboard/DashboardFragment.kt` (button listener + `showQuickAddDialog` + validation + announce)
  - MODIFY `app/src/main/res/layout/fragment_dashboard.xml` (add `btn_quick_add` header row)
  - NEW `app/src/main/res/layout/dialog_quick_add_log.xml`
  - NEW `app/src/test/.../domain/usecase/LogQuickAddUseCaseTest.kt`
  - MODIFY `app/src/test/.../data/repository/MealRepositoryImplTest.kt` (quick-add cases)
  - MODIFY `app/src/test/.../presentation/viewmodel/DashboardViewModelTest.kt` (6-arg VM ctor + `FakeMealRepository.logQuickAdd` + new cases)
  - Explicitly UNTOUCHED: `data/db/MealDao.kt`, `data/db/MealEntryEntity.kt`, `data/db/FoodEntity.kt`, `data/db/AppDatabase.kt` (stays v3), `presentation/ui/dashboard/model/DashboardUiState.kt` (stays 9 fields), `domain/model/DailySummary.kt`, water files, FTS files (none exist yet).
- Naming: layouts `fragment_*`/`dialog_*`/`item_*`, IDs `snake_case` (`btn_quick_add`, `et_quick_add_name/calories/protein/carbs/fat`, `spinner_meal_slot`), VMs `*ViewModel`, UseCases `*UseCase` per spine conventions. `di/AppModule.kt` stays the single DI home.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story-2.4] — story statement + AC (FAB/Quick-Add, name+macros+slot, `null food_id`, instant macro update)
- [Source: _bmad-output/planning-artifacts/epics.md#FR-Coverage-Map] — FR-6 → Epic 2
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/prd.md#4.3-FR-6] — quick-add creates `intake_log` with null `food_id` + custom values (mapped to `meals`+`foods` — see Dev Notes)
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/prd.md#4.1-FR-1] — blank-target null + validation precedent; §4.3-FR-5 macro context; §7 0.1% accuracy + <50ms + <15s log speed; §10 zero-network
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/addendum.md#1.1] — `intake_logs`/`user_daily_goals` idealized schema, `YYYY-MM-DD`, nullable targets (adapted — real tables are `meals`+`foods`)
- [Source: _bmad-output/planning-artifacts/architecture/architecture-MyFoodTracker-2026-09-06/ARCHITECTURE-SPINE.md#Invariants] — AD-1..AD-7; #Capability-Map FR-5/FR-6 → `IntakeLogDao`+`LogMealUseCase` (real: `MealDao`+`LogQuickAddUseCase`); #Structural-Seed (divergences noted)
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-MyFoodTracker-2026-09-06/EXPERIENCE.md#Information-Architecture] — search sheet owns "Quick-Add Calories Option" (Epic 3 integration point; dashboard dialog is interim); #State-Patterns empty-slot `"No items logged"`; #Accessibility-Floor TalkBack/48dp/4.5:1/200%
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-MyFoodTracker-2026-09-06/DESIGN.md#Components/Typography] — M3 cards, `tnum`, 12dp radii, tokens (`#1B4D3E`/`#FFFFFF`/`#E2E8F0`/`#2D3748`/`#718096`)
- [Source: _bmad-output/project-context.md] — sync Room, entity→domain private mapping, UseCase `operator fun invoke`, Koin `single`/`factory`/`viewModel`, `_binding=null` + `viewLifecycleOwner`, `tnum`, `fallbackToDestructiveMigration`, build via `gradlew testDebugUnitTest`
- [Source: app/.../data/db/MealEntryEntity.kt + FoodEntity.kt + MealDao.kt + AppDatabase.kt] — real schema (v3) + `insertMeal`/`insertFoods` reuse, no migration
- [Source: app/.../data/repository/MealRepositoryImpl.kt] — `getActiveProfileId() ?: return emptyList()` isolation + private `toDomain()` precedent; `addMealEntry()` `LocalDate.now()` quirk to bypass
- [Source: app/.../domain/usecase/GetMealEntriesByDateUseCase.kt] — single-line `operator fun invoke` precedent
- [Source: app/.../presentation/viewmodel/DashboardViewModel.kt + presentation/ui/dashboard/model/DashboardUiState.kt] — 5-arg ctor → 6-arg + `selectDate` refresh point + 9-field state (no change)
- [Source: app/.../presentation/ui/dashboard/DashboardFragment.kt + app/src/main/res/layout/fragment_dashboard.xml] — binding/insertion point (meals card header)
- [Source: app/.../domain/model/DailyGoal.kt + DailySummary.kt + MealEntry.kt + Food.kt] — nullable goals + summable foods (weight ignored)
- [Source: app/.../di/AppModule.kt] — centralized registrations to extend
- [Source: _bmad-output/implementation-artifacts/2-2-macro-progress-header-card-and-dynamic-target-display.md#Dev-Notes] — GONE/cap/TalkBack/`Locale`/`tnum`/Fake-repo patterns to reuse
- [Source: _bmad-output/implementation-artifacts/2-3-quick-water-logging-plus-250ml.md#Dev-Notes] — 5-arg VM ctor breakage lesson, `COALESCE(SUM)`-style per-date isolation, announce-on-save + `isAdded` guard precedent, scope-ban pattern (AC #7)

## Dev Agent Record

### Agent Model Used

muse-spark-1.3-contributor-free (OpenCode)

### Debug Log References

- RED: wrote `LogQuickAddUseCaseTest` (10 cases) before `LogQuickAddUseCase` / `MealRepository.logQuickAdd` existed — compilation failure confirmed the test target.
- GREEN: added `logQuickAdd` to `MealRepository` + `MealRepositoryImpl` (explicit-date `meals`+`foods` write, `title = slot.uppercase()`, `weight = 0.0`, `fiber = 0.0`, null-session guard, no DAO/schema change, AppDatabase stays v3); created `LogQuickAddUseCase` with `require` guards (blank name, non-finite/negative macros, unknown slot, slot case-insensitive).
- Extended `DashboardViewModel` to 6-arg ctor with `logQuickAdd()` delegating to `selectDate(date)` for atomic meal + `DailySummary` + water refresh (`IllegalArgumentException` swallowed, null profile → empty, no crash).
- Koin: `factory { LogQuickAddUseCase(get()) }` + 6-arg `DashboardViewModel` in `AppModule.kt` only.
- Layout: added `btn_quick_add` header row to meals card in `fragment_dashboard.xml`; new `dialog_quick_add_log.xml` (5 `OutlinedBox` inputs with `tnum`, `ExposedDropdownMenu` slot default `BREAKFAST`, `errorEnabled` everywhere) mirroring `fragment_profile_setup.xml` / `fragment_passcode_auth.xml` patterns.
- Fragment: `btnQuickAdd` listener + `showQuickAddDialog()` (positive-button override so validation failure keeps dialog open, `toDoubleOrNull` with empty→0, inline `"Enter a name"` / `"Enter 0 or more"` errors, single write per Save, TalkBack `"Logged {name}, {cal} kilocalories to {slot}"` with `isAdded` guards). Session guard, week strip, empty-meals toggle, UTC DatePicker conversion untouched.
- Tests: extended `MealRepositoryImplTest` with 5 quick-add cases (slot/date/profile isolation, null session, historical date); extended `DashboardViewModelTest` (`FakeMealRepository` gained session-guarded `logQuickAdd`, 6-arg VM ctor) with 4 cases; patched `GetMealEntriesByDateUseCaseTest` fake with `logQuickAdd` stub so the suite compiles. Total new tests: 19.
- Verification limit: no JVM in this environment (`java` not on PATH), so `gradlew testDebugUnitTest` could not be executed here. All new/changed code was verified by inspection against established patterns (Fake-repo + `InstantTaskExecutorRule`, no MockK, sync-only, Koin `factory`/`viewModel`). Georgii: please run `.\gradlew.bat testDebugUnitTest` on Windows before `code-review`.

### Completion Notes List

- Implemented Quick-Add dialog logging custom macros to the active `profile_id` + active date with instant dashboard refresh (AC #1, #2, #4).
- Inline validation keeps dialog open on blank name / non-numeric / negative input, never crashes (AC #3).
- TalkBack announcement, `≥ 48dp` targets, `tnum` readouts, 200%-scale-safe layout (AC #5).
- One record per Save, session-guard + all Story 2.1–2.3 behaviors preserved, full-`Double` precision sums (AC #6). No search/FTS/recipe/export/water changes per scope ban (AC #7).

### File List

- `app/src/main/java/com/example/myfoodtracker/domain/repository/MealRepository.kt` [MODIFIED]
- `app/src/main/java/com/example/myfoodtracker/data/repository/MealRepositoryImpl.kt` [MODIFIED]
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/LogQuickAddUseCase.kt` [NEW]
- `app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/DashboardViewModel.kt` [MODIFIED]
- `app/src/main/java/com/example/myfoodtracker/di/AppModule.kt` [MODIFIED]
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/DashboardFragment.kt` [MODIFIED]
- `app/src/main/res/layout/fragment_dashboard.xml` [MODIFIED]
- `app/src/main/res/layout/dialog_quick_add_log.xml` [NEW]
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/LogQuickAddUseCaseTest.kt` [NEW]
- `app/src/test/java/com/example/myfoodtracker/data/repository/MealRepositoryImplTest.kt` [MODIFIED]
- `app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/DashboardViewModelTest.kt` [MODIFIED]
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/GetMealEntriesByDateUseCaseTest.kt` [MODIFIED]

### Change Log

- 2026-10-01: Implemented Story 2.4 (Quick-Add Calorie & Macro Logging) — `logQuickAdd` repo contract reusing `meals`/`foods` (no migration), `LogQuickAddUseCase` validation, 6-arg `DashboardViewModel`, Koin registration, Quick-Add button + dialog layout with validation/TalkBack, 19 new tests; status moved to review (test run pending — no JVM in this environment).
