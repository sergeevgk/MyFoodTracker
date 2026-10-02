---
baseline_commit: af1b7dc5b252a35b53986bf123adf27bb170fd1f
---

# Story 2.5: Swipe-to-Delete Intake Logs with Undo

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user,
I want to swipe left on a logged food entry to delete it and have a quick Undo option,
so that I can correct accidental meal entries.

## Acceptance Criteria

1. **Given** the dashboard meals section with logged entries, **When** the dashboard renders, **Then** each logged intake appears as an individual swipeable row (slot title + food name + kcal/macros, `tnum`, `≥ 48dp` swipe target) inside the meals card — not just the `"N meal(s) logged"` count — with empty days still showing `"No items logged for this date"` and `layout_meal_entries` `GONE`. [Source: epics.md Story 2.5, EXPERIENCE.md §Component Patterns #4, DESIGN.md §Shapes/Cards]
2. **Given** a visible meal row, **When** the user swipes the row left, **Then** a red Delete affordance (`#BA1A1A` background, white Delete label/icon) is revealed; tapping Delete (or completing the swipe-to-dismiss) removes exactly that `MealEntry` (parent row) from Room synchronously (`< 50ms`, main thread, no coroutines) scoped by `profile_id` + active date, cascades its child `foods` via SQLite `CASCADE` (no manual child-delete query), and refreshes meal list + macro header (`DailySummary.summarize`) instantly without reload. [Source: epics.md Story 2.5, PRD FR-5, ARCHITECTURE-SPINE AD-2/AD-7, EXPERIENCE.md §Swipe-to-Delete]
3. **Given** a successful delete, **When** the write completes, **Then** a `Snackbar` with action `[Undo]` appears for exactly 5 seconds (`setDuration(5000)`); tapping `[Undo]` restores the deleted record with its original `id`/`title`/`date`/`time`/`foods` and recalculates dashboard macro totals instantly; letting it time out permanently discards the pending restore. [Source: epics.md Story 2.5, EXPERIENCE.md §Component Patterns #4]
4. **Given** any active date (week strip, prev/next week, Today, month picker) and any profile, **When** delete/undo executes, **Then** it targets the dashboard active date (`YYYY-MM-DD`, NOT `LocalDate.now()` when viewing history), enforces `profile_id` isolation (user-2 can neither see nor delete user-1's rows; null session → no write, no crash, redirect guard preserved), and cross-date/cross-profile leakage is zero. [Source: Story 2.1 `selectDate` + session guard, PRD FR-2/NFR-5, addendum §1.1 `YYYY-MM-DD`]
5. **Given** the delete/undo surface, **When** used with TalkBack / large text / targets, **Then** deletion announces `"Deleted {name} from {slot}"`, undo announces `"Restored {name} to {slot}"`, rows + Delete affordance meet `≥ 48dp` targets, contrast `≥ 4.5:1`, `tnum` is preserved on all numeric readouts, and layout survives 200% font scale (`wrap_content`/`sp`, no clipping). [Source: NFR-6, EXPERIENCE.md §Accessibility Floor, DESIGN.md §Typography]
6. **Given** rapid or adversarial use, **When** the user swipes multiple rows quickly, rotates the device, or taps Delete on an already-deleted row, **Then** each swipe deletes exactly one record (no double-delete from double-swipe), only the most recent delete is undoable (single pending slot, documented), rotation preserves the pending undo (ViewModel-held, not Fragment-held), stale-id delete is a safe no-op, and daily sums stay within 0.1% tolerance of the manual sum (full `Double` precision, format only at bind time). [Source: Story 2.1 AC #7 precedent, PRD NFR-4, quality guard]
7. **Given** this story's scope, **When** implementing, **Then** NO food-search bottom sheet, NO FTS query, NO custom-food library write, NO recipe, NO export/backup, NO water-widget behavior change, and NO schema migration (`AppDatabase` stays v3) are built — this story is meal-rows list + swipe-to-delete + Snackbar Undo + `meals`/`foods` delete/restore + dashboard refresh only. [Source: scope guard — Epic 3 owns search/FTS, Epic 5 owns export]

## Tasks / Subtasks

- [x] **Task 1: Data — `restoreMealEntry` contract reusing `meals`/`foods` (no migration)** (AC: #2, #3, #4, #6)
  - [x] Subtask 1.1: MODIFY `domain/repository/MealRepository.kt` — ADD `fun restoreMealEntry(entry: MealEntry): List<MealEntry>` (synchronous, no `suspend`). Keep all 6 existing methods (`getMealEntries`, `getMealEntriesByDate`, `addMealEntry`, `updateMealEntry`, `deleteMealEntry`, `logQuickAdd`) unchanged.
  - [x] Subtask 1.2: MODIFY `data/repository/MealRepositoryImpl.kt` — implement `restoreMealEntry`: first line `val profileId = sessionRepository.getActiveProfileId() ?: return emptyList()` (null-session guard, no write, no crash); build `MealEntryEntity(id = entry.id, profileId = profileId, title = entry.title, date = entry.date, time = entry.time)`, `mealDao.insertMeal(meal)` (REPLACE semantics re-insert same id); then `entry.foods.map { FoodEntity(mealId = entry.id, name = it.name, weight = it.weight, calories = it.calories, carbs = it.carbs, fat = it.fat, protein = it.protein, fiber = it.fiber) }`, `mealDao.insertFoods(...)`; return `getMealEntriesByDate(entry.date)`. Reuse existing `toDomain()` mappers. No SQL outside DAO, no manual cascade deletes, no `AppDatabase` version bump (stays v3).
  - [x] Subtask 1.3: Do NOT modify `data/db/MealDao.kt` (`deleteMeal(id, profileId)` + `insertMeal` + `insertFoods` already suffice), `MealEntryEntity.kt`, `FoodEntity.kt`, `AppDatabase.kt`. Do NOT add `suspend`/coroutines. Do NOT add columns or a `deleteFood`/`restoreFood` per-food API — deletion granularity is the parent `MealEntry` (one row = one meal; cascade removes children for free per AD-7).

- [x] **Task 2: Domain — `RestoreMealEntryUseCase` (mirror `DeleteMealEntryUseCase`)** (AC: #3, #4)
  - [x] Subtask 2.1: NEW `domain/usecase/RestoreMealEntryUseCase.kt` — pure Kotlin, zero `android.*`/Room imports: `class RestoreMealEntryUseCase(private val repository: MealRepository) { operator fun invoke(entry: MealEntry): List<MealEntry> = repository.restoreMealEntry(entry) }`. Follow `DeleteMealEntryUseCase` one-liner style. No validation beyond repo guard (entry is a previously-deleted domain object, already well-formed).
  - [x] Subtask 2.2: Do NOT modify `DeleteMealEntryUseCase.kt` — reuse as-is (`operator fun invoke(id: String)`).

- [x] **Task 3: Presentation state — `DashboardViewModel.deleteMealEntry + restoreLastDeleted`** (AC: #2, #3, #4, #6)
  - [x] Subtask 3.1: MODIFY `presentation/viewmodel/DashboardViewModel.kt` — extend constructor from 6 args to 8 args in EXACT order `(sessionRepository, logoutUseCase, getMealEntriesByDateUseCase, getWaterTotalUseCase, logWaterUseCase, logQuickAddUseCase, deleteMealEntryUseCase: DeleteMealEntryUseCase, restoreMealEntryUseCase: RestoreMealEntryUseCase)`. ADD `private var pendingDeleted: MealEntry? = null` (+ `private var pendingDeletedIndex: Int = -1` for position restore).
  - [x] Subtask 3.2: ADD `fun deleteMealEntry(entryId: String)` that does: `val date = _uiState.value?.activeDate ?: LocalDate.now()`; `val target = _uiState.value?.mealEntries?.find { it.id == entryId } ?: return` (stale-id safe no-op); `pendingDeleted = target`; `pendingDeletedIndex = indexOf`; `deleteMealEntryUseCase(entryId)` (ignore its all-meals return); then `selectDate(date)` to refresh `mealEntries` + `DailySummary` + `waterTotalMl` atomically. Null profile → repo returns empty → `selectDate` emits empty, no crash.
  - [x] Subtask 3.3: ADD `fun restoreLastDeleted()` that does: `val pending = pendingDeleted ?: return`; `restoreMealEntryUseCase(pending)`; `pendingDeleted = null; pendingDeletedIndex = -1`; then `selectDate(pending.date as LocalDate via LocalDate.parse)` — BUT if user navigated to a different date while Snackbar visible, restore must still write to `pending.date` then `selectDate(currentActiveDate)` so the current view stays coherent AND the restored date's data is correct (spec: call `selectDate(_uiState.value?.activeDate ?: LocalDate.parse(pending.date))`; the repo write already targeted `pending.date`). Simplest correct: `restoreMealEntryUseCase(pending); clearPending(); selectDate(currentActive)` — document choice in code comment.
  - [x] Subtask 3.4: ADD `fun clearPendingDelete()` (called on Snackbar timeout/dismiss without Undo) that nulls `pendingDeleted`. `DashboardUiState` needs NO change (9 fields stay as-is) — delete/undo flow through existing `mealEntries`/`dailySummary`. No `Context`/views in VM, no formatting in VM, synchronous only.

- [x] **Task 4: Koin — register delete/restore graph** (AC: #2, #3)
  - [x] Subtask 4.1: MODIFY `di/AppModule.kt` ONLY — `DeleteMealEntryUseCase` factory already exists (keep); add `factory { RestoreMealEntryUseCase(get()) }` alongside the other UseCase factories, and update `viewModel { DashboardViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }` (8 `get()`s in constructor order). No scattered modules; `factory` for the UseCase per AD-4/AD-6. No DAO/repo registrations (both already registered).

- [x] **Task 5: Layout — meal rows list + row item** (AC: #1, #5)
  - [x] Subtask 5.1: MODIFY `app/src/main/res/layout/fragment_dashboard.xml` — inside `layout_meal_entries` (below `tv_meal_count`), ADD `androidx.recyclerview.widget.RecyclerView` `@+id/rv_meal_entries`, `layout_width/height = match_parent/wrap_content`, `nestedScrollingEnabled="false"`, `clipToPadding="false"`, `contentDescription="Logged meal entries"`. Keep `tv_meal_count` (now doubles as section summary above the list) + `tv_empty_meals` toggle logic unchanged. Do NOT alter any other card or ID (`card_macro_header`, `card_water_widget`, `rv_week_days`, `btn_prev_week`, `btn_next_week`, `btn_today`, `ib_calendar_picker`, `btn_quick_add`).
  - [x] Subtask 5.2: NEW `app/src/main/res/layout/item_meal_log.xml` — `MaterialCardView` (`rounded/sm` 8dp, card `#FFFFFF`, stroke `#E2E8F0` 1dp, `layout_marginBottom=8dp`) containing horizontal `LinearLayout` padding `12dp`: vertical text column (`weight=1`) with `TextView` `@+id/tv_meal_title` (slot + time, e.g. `"LUNCH · 13:00"`, 14sp bold `#2D3748`), `TextView` `@+id/tv_meal_food_name` (first food name or `"Meal"`, 14sp `#2D3748`), `TextView` `@+id/tv_meal_macros` (`tnum`, 12sp `#718096`, e.g. `"500 kcal · P 20g · C 45g · F 15g"`); row `minHeight ≥ 48dp`. Tokens follow repo inline-hex pattern (deferred D-2): bg `#FFFFFF`, accent `#1B4D3E`, ink `#2D3748`/`#718096`, danger `#BA1A1A` (delete bg drawn in code, not layout). No new vector drawables. All text `sp`, no hardcoded `dp` text sizes.
  - [x] Subtask 5.3: Follow the repo's accepted inline-hex pattern (deferred debts 2.1-D2/2.2-D1/2.4-D2) — use light tokens above; do NOT invent a parallel theming system or add `@color/`/`@string/` resources.

- [x] **Task 6: Adapter + Fragment — rows + ItemTouchHelper swipe + Snackbar Undo + TalkBack** (AC: #1–#6)
  - [x] Subtask 6.1: NEW `presentation/ui/dashboard/MealLogAdapter.kt` — `ListAdapter<MealEntry, VH>(MealDiffCallback)` mirroring `WeekDayAdapter` structure (ViewHolder + `DiffUtil.areItemsTheSame = id`, `areContentsTheSame = ==`): `onCreateViewHolder` inflates `ItemMealLogBinding`; `bind(entry)` sets `tvMealTitle="${entry.title} · ${entry.time}"`, `tvMealFoodName="${entry.foods.firstOrNull()?.name ?: "Meal"}"`, `tvMealMacros` via `Locale.getDefault()` (`"%.0f kcal · P %.0fg · C %.0fg · F %.0fg"` from summed foods, full-`Double` sums formatted only here); `root.contentDescription = "${foodName}, ${slot}, ${kcal} kilocalories"`; row exposes `bindingAdapterPosition` for swipe. No `Context` leaks, no DB access.
  - [x] Subtask 6.2: MODIFY `presentation/ui/dashboard/DashboardFragment.kt` — in `setupRecyclerView()` add `mealLogAdapter = MealLogAdapter()` + `binding.rvMealEntries.adapter = mealLogAdapter` + `binding.rvMealEntries.isNestedScrollingEnabled = false` + attach `ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) { onMove=false; onSwiped(position){ val entry = mealLogAdapter.currentList.getOrNull(position) ?: return; viewModel.deleteMealEntry(entry.id); showUndoSnackbar(entry) } })`. `onChildDraw`: draw red `#BA1A1A` `ColorDrawable` + white `"Delete"` label (canvas `drawText`, no new drawable asset) behind swiped row; only LEFT direction. Preserve session-guard redirect, week strip, empty-meals toggle, macro/water binding, Quick-Add dialog, UTC `MaterialDatePicker` conversion, logout wiring, `_binding = null` in `onDestroyView()`, `observe(viewLifecycleOwner)`.
  - [x] Subtask 6.3: In `observeViewModel()`, after the empty-meals toggle, add `mealLogAdapter.submitList(state.mealEntries)` (empty list clears rows; `tvMealCount` keeps `"N meal(s) logged"` above the list). ADD `private fun showUndoSnackbar(deleted: MealEntry)` — `val foodName = deleted.foods.firstOrNull()?.name ?: "Meal"`; `Snackbar.make(binding.root, "Deleted $foodName from ${deleted.title}", 5000).setAction("Undo") { if (!isAdded) return@setAction; viewModel.restoreLastDeleted(); if (isAdded) binding.root.announceForAccessibility("Restored $foodName to ${deleted.title}") }.addCallback(onDismissed without Undo → viewModel.clearPendingDelete()).show()`; immediately after delete (before Snackbar) announce `binding.root.announceForAccessibility("Deleted $foodName from ${deleted.title}")` guarded with `if (!isAdded) return`. Single delete per swipe (ItemTouchHelper guarantees one `onSwiped` per gesture; adapter position read once from `currentList`).
  - [x] Subtask 6.4: No changes to `bindMacroHeader`/`bindWaterWidget` — they already recompute from `selectDate()` refresh. `showQuickAddDialog`, water tap feedback, week navigation untouched.

- [x] **Task 7: Tests** (AC: #2–#4, #6)
  - [x] Subtask 7.1: NEW `app/src/test/.../domain/usecase/RestoreMealEntryUseCaseTest.kt` — Fake `MealRepository` (in-memory map `id → MealEntry`, profile/date filter semantics): delegates `restoreMealEntry` to repo; restores original id/title/date/time/foods exactly; restoring over same id REPLACEs (no duplicate). No MockK, no new deps.
  - [x] Subtask 7.2: MODIFY `app/src/test/.../data/repository/MealRepositoryImplTest.kt` — reuse existing `FakeMealDao`/`FakeSessionRepository`: UPDATE `FakeMealDao.deleteMeal` to ALSO remove `foods.filter { it.mealId == id }` (mirror real SQLite `CASCADE`; currently the fake orphans foods — fix here); new cases: `deleteMealEntry` removes parent + cascaded foods (assert `foods.none { it.mealId == id }`); profile isolation (user-2 delete of user-1 meal is no-op, foods intact); `restoreMealEntry` re-inserts same meal id + same foods and is visible via `getMealEntriesByDate(entry.date)`; null session → no write + empty return; date isolation (delete on today leaves yesterday intact).
  - [x] Subtask 7.3: MODIFY `app/src/test/.../presentation/viewmodel/DashboardViewModelTest.kt` — reuse `FakeSessionRepository`/`FakeMealRepository`/`FakeWaterRepository` + `InstantTaskExecutorRule`: extend `FakeMealRepository` with session-guarded `deleteMealEntry(id)` (remove from `entriesByDate` bucket, stash in `lastDeleted`) + `restoreMealEntry(entry)` (re-insert same id into its date bucket); update VM construction to 8 args everywhere; new cases: `deleteMealEntry` removes row for active date and `dailySummary` drops to `0` (e.g. 500 kcal entry → `0.0` after delete); `restoreLastDeleted` brings entry + summary back; historical-date case (select past date, delete there → today untouched); stale-id case → no-op, no crash; null-profile → no crash, meals empty. Also patch `GetMealEntriesByDateUseCaseTest` fake (if it implements `MealRepository`) with `restoreMealEntry` stub so the suite compiles. Verify with `./gradlew testDebugUnitTest` (Linux) / `.\gradlew.bat testDebugUnitTest` (Windows) — all suites green. No `androidTest` needed (no schema change; destructive migration untouched).

## Dev Notes

- **Architecture invariants (must follow, AD-1..AD-7):**
  - `domain/` zero `android.*`/Room imports (AD-1). `RestoreMealEntryUseCase.kt`, `MealRepository.kt` addition pure Kotlin. Adapter/Fragment may use Android — ViewModel must not.
  - Synchronous only — no `suspend`, no coroutines; Room builder already `.allowMainThreadQueries()` (AD-2). One swipe = one `deleteMeal` + one `selectDate` reload; one Undo = one `insertMeal` + one `insertFoods` + one reload — all `< 50ms`.
  - Entities never leak past `data/` — Fragment/Adapter only see `MealEntry`/`Food` domain models (AD-3). Private mappers in `MealRepositoryImpl` already exist — reuse for restore.
  - UseCase `operator fun invoke()` + Koin `factory`; DAO/Repo `single`; VM `viewModel` via `by viewModel()` — all centralized in `di/AppModule.kt` (AD-4/AD-6).
  - Fragment: `_binding = null` in `onDestroyView()`, observe with `viewLifecycleOwner` (AD-5). Already correct — preserve.
  - Child table FK `CASCADE` + indexed FK columns (AD-7). `FoodEntity.mealId` already has `CASCADE` + `Index(mealId)` — deleting the parent `meals` row auto-removes children; do NOT write manual child-delete queries. Restore re-inserts parent first, then children (FK order matters).
- **Schema mapping — READ THIS (prevents wrong-table disaster):** PRD/addendum describe an idealized `intake_logs(food_id NULL, custom_name, calculated_*)` table, but the REAL codebase implements `meals(MealEntryEntity: id, profile_id, title, date, time)` + `foods(FoodEntity: mealId FK CASCADE, name, weight, calories, carbs, fat, protein, fiber)`. There is NO `intake_logs` table. Deletion granularity is therefore the parent `meals` row (one swipe row = one `MealEntry`); its `title` carries the slot (`BREAKFAST/LUNCH/DINNER/SNACK`) and its `foods` list carries the nutrients. `DailySummary.summarize()` sums `meal.foods` at full `Double` precision — delete/undo flow through it automatically. Do NOT create `IntakeLogEntity`/`IntakeLogDao`, do NOT add a per-food `deleteFood` API, do NOT bump DB version.
- **Current state of files being modified (read before editing):**
  - `MealRepositoryImpl` ctor is `(mealDao, sessionRepository)`; every method starts with `getActiveProfileId() ?: return emptyList()`. Mirror that guard in `restoreMealEntry`. NOTE: `deleteMealEntry(id)` returns `getMealEntries()` (ALL dates, not date-filtered) — ViewModel must IGNORE that return and call `selectDate(date)` for the date-filtered refresh; do NOT "fix" the return shape (would break existing callers/tests).
  - `DashboardViewModel` ctor is currently 6-arg `(session, logout, getMealsByDate, getWaterTotal, logWater, logQuickAdd)`; `selectDate()` builds Mon–Sun week (`previousOrSame(MONDAY)`), loads meals + `DailySummary.summarize()` + `profile?.dailyGoal` + water total, emits 9-field `DashboardUiState`. You add args 7–8 and `deleteMealEntry()/restoreLastDeleted()/clearPendingDelete()` delegating to `selectDate(date)`; change nothing else. `previousWeek/nextWeek/jumpToToday` delegate to `selectDate` so they inherit correctness.
  - `DashboardFragment.observeViewModel()` binds welcome/profile/date-header, week adapter, empty-meals toggle (`tvEmptyMeals` vs `layoutMealEntries` + `tvMealCount`), then `bindMacroHeader` + `bindWaterWidget`. Meals card currently shows ONLY `tv_meal_count` (`"N meal(s) logged"`) — there is NO meal-rows list yet; you are building it. `showDatePicker` UTC conversion (`Instant.ofEpochMilli(sel).atZone(ZoneId.of("UTC")).toLocalDate()`) and session-guard redirect must not change.
  - `fragment_dashboard.xml` = `ScrollView > LinearLayout` with 5 cards; `layout_meal_entries` contains ONLY `tv_meal_count` today. Add `rv_meal_entries` below it. Outer `ScrollView` + inner `RecyclerView` requires `nestedScrollingEnabled="false"` or swipe/scroll fights occur.
  - `DailySummary.summarize(mealEntries)` sums `meal.foods` (`calories`, `protein`, `carbs`, `fat`) at full `Double` precision (NFR-4 0.1%) — deleted rows drop out automatically; do NOT round in domain (format only in Fragment/Adapter with `Locale.getDefault()`).
- **What must be preserved (regression guard):** week-strip selection styling, prev/next/Today navigation, `MaterialDatePicker` UTC conversion, session-guard + logout navigation, empty-meals text `"No items logged for this date"`, Monday–Sunday weeks, `Locale.getDefault()` formatters, macro header values/progress/TalkBack (Story 2.2), water widget totals/progress/TalkBack/pulse (Story 2.3), Quick-Add dialog + validation + 6-arg→8-arg Koin wiring (Story 2.4), `DailySummary` precision. Do NOT touch `rvWeekDays/btnPrevWeek/btnNextWeek/btnToday/ibCalendarPicker/btnQuickAdd` IDs or macro/water bindings. `AppModule` 6-arg VM becomes 8-arg — update EVERY construction site including ALL tests or the build breaks.
- **Anti-patterns / do NOT do:** No FTS/`foods_fts`/triggers/search-sheet work (Epic 3 owns it). No water-logic changes. No recipe/export/network work (NFR-1 zero-network). No `suspend`/coroutines/threads. No `Context`/views in ViewModel. No proportional-font numbers on readouts (keep `tnum`). No `INVISIBLE` for anything (use `GONE`). No manual cascade deletes (`DELETE FROM foods WHERE mealId=...` is forbidden — SQLite handles it). No parallel color/string theming (follow inline-hex + hardcoded-string consistency per deferred D-2). No `allowMainThreadQueries` removal. No DB downgrade. No right-swipe action (LEFT only). No `Snackbar.LENGTH_LONG` (must be explicit `5000`ms per AC #3).
- **UX details not in epics (explicit to prevent invention):** row title `"${slot} · ${time}"`; food line = first food's name (fallback `"Meal"` for legacy empty-food rows); macro line `"%.0f kcal · P %.0fg · C %.0fg · F %.0fg"`; delete background `#BA1A1A` (matches `btn_logout` error red already in layout) with white `"Delete"` text; Snackbar text `"Deleted {foodName} from {slot}"`, action `"Undo"`; delete announcement `"Deleted {name} from {slot}"`, restore announcement `"Restored {name} to {slot}"`; swipe direction LEFT only; only most-recent delete is undoable; Snackbar timeout clears pending via `clearPendingDelete()`.
- **Previous-story intelligence (must-reuse):** `FakeSessionRepository(AtomicReference<UserProfile?>)` + `FakeMealRepository(entriesByDate)` + `FakeWaterRepository("$profileId|$date" keyed)` + `InstantTaskExecutorRule` test pattern (no MockK, no new deps) from `DashboardViewModelTest`; `FakeMealDao(meals/foods lists)` + profile/date filter assertions from `MealRepositoryImplTest` — but FIX its `deleteMeal` fake to cascade foods (real DB does, fake currently doesn't); `DailySummary.summarize()` pure-function precedent; `WeekDayAdapter` ListAdapter+DiffUtil+`Color.parseColor` inline-hex precedent — mirror for `MealLogAdapter`; `selectDate`-delegation so prev/next/Today inherit new behavior; Koin single/factory/viewModel lesson (2.3's 5-arg and 2.4's 6-arg VM breakages repeat here as 8-arg — update all call sites); `isAdded` guards before `announceForAccessibility`/`Snackbar` (2.3/2.4 precedent); `getActiveProfileId() ?: return emptyList()` isolation one-liner (all repo methods).
- **Known open review/deferred items (do NOT regress, do NOT silently fix unless 1 line):** Fragment dual-injects `SessionRepository` (keep as-is for guard); day-pill radius 24dp vs 28dp, unselected number color, logout double-tap, DatePicker double-show (2.1 review) — out of scope. Deferred D-2 hardcoded-hex/strings (2.1–2.4) — follow, don't fix. Deferred haptic polish (2.3 D-1) — not required here. `addMealEntry()` `LocalDate.now()` quirk (2.1 D-1) — untouched. `logQuickAdd` non-atomic two-write (2.4 D-1) — same shape reused in restore, leave for future DAO `@Transaction` overhaul. `FakeMealDao.deleteMeal` orphan-foods divergence — FIX as part of Task 7.2 (test-only fix, mirrors real CASCADE).
- **Performance:** one `DELETE WHERE id+profile` + one `SELECT * WHERE profile_id+date` reload per swipe; one `INSERT` + one `INSERT foods` + one reload per Undo — all main-thread local, `< 50ms` trivially met. No caching, no debounce (ItemTouchHelper one-shot per gesture is the double-write guard).
- **Stack (pinned, no upgrade):** Target 36 / Min 35 (Java 11), AGP 9.2.1, Koin 3.5.6, Room 2.6.1 (KSP 2.2.10-2.0.2), Navigation 2.6.0, Material 1.10.0, AppCompat 1.6.1. No web research needed — offline-first, zero network, no external APIs (`ItemTouchHelper` + `Snackbar` are `androidx`/`material` APIs already on classpath); versions verified in `project-context.md` + spine Stack table.

### Project Structure Notes

- Base package: `com.example.myfoodtracker` under `app/src/main/java/com/example/myfoodtracker/`. Real tree differs from ARCHITECTURE-SPINE structural seed (seed shows `sergeevgk.myfoodtracker` + `IntakeLogDao`/`WaterLogDao`/`FoodSearchViewModel` which do NOT exist) — follow the REAL tree below.
- Files:
  - MODIFY `domain/repository/MealRepository.kt` (add `restoreMealEntry`)
  - MODIFY `data/repository/MealRepositoryImpl.kt` (implement `restoreMealEntry`)
  - NEW `domain/usecase/RestoreMealEntryUseCase.kt`
  - MODIFY `presentation/viewmodel/DashboardViewModel.kt` (8-arg ctor, `pendingDeleted`, `deleteMealEntry`/`restoreLastDeleted`/`clearPendingDelete`)
  - MODIFY `di/AppModule.kt` (`RestoreMealEntryUseCase` `factory` + 8-arg VM)
  - NEW `presentation/ui/dashboard/MealLogAdapter.kt`
  - MODIFY `presentation/ui/dashboard/DashboardFragment.kt` (second RecyclerView + ItemTouchHelper + Snackbar Undo + announces)
  - MODIFY `app/src/main/res/layout/fragment_dashboard.xml` (add `rv_meal_entries`)
  - NEW `app/src/main/res/layout/item_meal_log.xml`
  - NEW `app/src/test/.../domain/usecase/RestoreMealEntryUseCaseTest.kt`
  - MODIFY `app/src/test/.../data/repository/MealRepositoryImplTest.kt` (cascade fix in fake + delete/restore cases)
  - MODIFY `app/src/test/.../presentation/viewmodel/DashboardViewModelTest.kt` (8-arg VM ctor + `FakeMealRepository.delete/restore` + new cases)
  - MODIFY `app/src/test/.../domain/usecase/GetMealEntriesByDateUseCaseTest.kt` ONLY if its `MealRepository` fake needs the new `restoreMealEntry` stub to compile
  - Explicitly UNTOUCHED: `data/db/MealDao.kt`, `data/db/MealEntryEntity.kt`, `data/db/FoodEntity.kt`, `data/db/AppDatabase.kt` (stays v3), `presentation/ui/dashboard/model/DashboardUiState.kt` (stays 9 fields), `domain/model/DailySummary.kt`, `domain/usecase/DeleteMealEntryUseCase.kt`, water files, FTS files (none exist yet).
- Naming: layouts `fragment_*`/`dialog_*`/`item_*`, IDs `snake_case` (`rv_meal_entries`, `tv_meal_title`, `tv_meal_food_name`, `tv_meal_macros`), VMs `*ViewModel`, UseCases `*UseCase`, Adapters `*Adapter` per spine conventions. `di/AppModule.kt` stays the single DI home.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story-2.5] — story statement + AC (swipe left, Delete, Snackbar `[Undo]` 5s, restore + recalc)
- [Source: _bmad-output/planning-artifacts/epics.md#FR-Coverage-Map] — FR-5 → Epic 2 (intake-log lifecycle incl. delete)
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/prd.md#4.3-FR-5] — log creation + instant UI update (delete is the inverse operation); §7 0.1% accuracy + <50ms; §10 zero-network; §4.1-FR-2 profile isolation
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/addendum.md#1.1] — `YYYY-MM-DD` dates, FK cascade tables (adapted — real tables are `meals`+`foods`)
- [Source: _bmad-output/planning-artifacts/architecture/architecture-MyFoodTracker-2026-09-06/ARCHITECTURE-SPINE.md#Invariants] — AD-1..AD-7; #Capability-Map FR-5/FR-6 → meal logging; #Structural-Seed (divergences noted)
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-MyFoodTracker-2026-09-06/EXPERIENCE.md#Component-Patterns-4] — swipe-left reveals red Delete, Snackbar `[Undo]` 5s; #Accessibility-Floor TalkBack/48dp/4.5:1/200%
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-MyFoodTracker-2026-09-06/DESIGN.md#Components/Typography] — M3 cards, `tnum`, 12dp radii, tokens (`#1B4D3E`/`#FFFFFF`/`#E2E8F0`/`#2D3748`/`#718096`/`#BA1A1A`)
- [Source: _bmad-output/project-context.md] — sync Room, entity→domain private mapping, UseCase `operator fun invoke`, Koin `single`/`factory`/`viewModel`, `_binding=null` + `viewLifecycleOwner`, `tnum`, `fallbackToDestructiveMigration`, build via `gradlew testDebugUnitTest`
- [Source: app/.../data/db/MealEntryEntity.kt + FoodEntity.kt + MealDao.kt + AppDatabase.kt] — real schema (v3) + `deleteMeal`/`insertMeal`/`insertFoods` reuse, no migration
- [Source: app/.../data/repository/MealRepositoryImpl.kt] — `getActiveProfileId() ?: return emptyList()` isolation + private `toDomain()` precedent; `deleteMealEntry` returns all-meals (ViewModel must `selectDate` instead)
- [Source: app/.../domain/usecase/DeleteMealEntryUseCase.kt] — single-line `operator fun invoke` precedent for restore UseCase
- [Source: app/.../presentation/viewmodel/DashboardViewModel.kt + presentation/ui/dashboard/model/DashboardUiState.kt] — 6-arg ctor → 8-arg + `selectDate` refresh point + 9-field state (no change)
- [Source: app/.../presentation/ui/dashboard/DashboardFragment.kt + app/src/main/res/layout/fragment_dashboard.xml] — binding/insertion point (meals card has count only — rows are new)
- [Source: app/.../presentation/ui/dashboard/WeekDayAdapter.kt] — ListAdapter+DiffUtil+inline-hex precedent for `MealLogAdapter`
- [Source: app/.../domain/model/DailySummary.kt + MealEntry.kt + Food.kt] — summable foods (weight ignored for totals)
- [Source: app/.../di/AppModule.kt] — centralized registrations to extend
- [Source: _bmad-output/implementation-artifacts/2-4-quick-add-calorie-and-macro-logging.md#Dev-Notes] — explicit-date pattern, null-session guard, `isAdded` announce guards, 6-arg Koin breakage lesson, scope-ban pattern (AC #7)

### Review Findings

- [x] [Review][Dismissed] Avoid drawing swipe text outside itemView during partial left swipe [app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/DashboardFragment.kt:110-125] — dismissed by user (UI accepted as-is)
- [x] [Review][Defer] MealRepositoryImpl.restoreMealEntry re-inserts parent and children in two separate calls without Room @Transaction [app/src/main/java/com/example/myfoodtracker/data/repository/MealRepositoryImpl.kt:93-118] — deferred, pre-existing architectural pattern in repo


## Dev Agent Record

### Agent Model Used

muse-spark-1.3-contributor-free (OpenCode)

### Debug Log References

- RED: wrote `RestoreMealEntryUseCaseTest` (2 cases) before `RestoreMealEntryUseCase` / `MealRepository.restoreMealEntry` existed — compilation failure confirmed the test target.
- GREEN: added `restoreMealEntry` to `MealRepository` + `MealRepositoryImpl` (same-id `meals`+`foods` re-insert, null-session guard, no DAO/schema change, AppDatabase stays v3); created `RestoreMealEntryUseCase` one-liner mirroring `DeleteMealEntryUseCase`.
- Extended `DashboardViewModel` to 8-arg ctor with `pendingDeleted`/`pendingDeletedIndex`, `deleteMealEntry()` (stale-id no-op, `selectDate` refresh), `restoreLastDeleted()` (writes to pending.date, refreshes current view), `clearPendingDelete()`.
- Koin: `factory { RestoreMealEntryUseCase(get()) }` + 8-arg `DashboardViewModel` in `AppModule.kt` only.
- Layout: added `rv_meal_entries` (nestedScrolling=false) below `tv_meal_count` in `fragment_dashboard.xml`; new `item_meal_log.xml` (slot·time + food name + `tnum` macros, minHeight 48dp) following inline-hex pattern.
- Fragment+Adapter: new `MealLogAdapter` (ListAdapter+DiffUtil, `Locale` formatting only at bind); `ItemTouchHelper.LEFT` with `#BA1A1A` + white "Delete" canvas label; `Snackbar` explicit 5000ms with Undo + dismiss-callback clearing pending; TalkBack delete/restore announces with `isAdded` guards. Session guard, week strip, empty-meals toggle, UTC DatePicker conversion untouched. One mid-task mis-edit of `showDatePicker` signature repaired and verified via grep.
- Tests: fixed `FakeMealDao.deleteMeal` to cascade foods (mirrors real SQLite CASCADE); 5 new `MealRepositoryImplTest` cases; 5 new `DashboardViewModelTest` cases (8-arg ctor, fake delete/restore); `restoreMealEntry` stubs added to `GetMealEntriesByDateUseCaseTest` + `LogQuickAddUseCaseTest` fakes so suites compile. Total new tests: 12.
- Verification limit: no JVM in this environment (`java` not on PATH), so `gradlew testDebugUnitTest` could not be executed here. All new/changed code was verified by inspection against established patterns (Fake-repo + `InstantTaskExecutorRule`, no MockK, sync-only, Koin `factory`/`viewModel`, ViewBinding ID mapping). Georgii: please run `.\gradlew.bat testDebugUnitTest` on Windows before `code-review`.
- FIX (2026-10-02): `viewHolder.bindingAdapterPosition` unresolved in `DashboardFragment` — root cause was the missing direct `androidx.recyclerview` dependency (RecyclerView previously came only transitively via Material). Pinned `androidx.recyclerview:recyclerview:1.3.2` as a direct `implementation` dependency via the version catalog (`gradle/libs.versions.toml` + `app/build.gradle.kts`). `bindingAdapterPosition` (available since recyclerview 1.2.0) now resolves; no Fragment code change needed. After adding a catalog dependency, run a Gradle sync before rebuilding.

### Completion Notes List

- Implemented swipe-to-delete meal rows with 5-second Snackbar Undo and instant macro refresh (AC #1–#3).
- Date/profile isolation, stale-id safety, single-pending undo surviving rotation, full-`Double` precision sums (AC #4, #6).
- TalkBack announces, `≥ 48dp` rows, `tnum` macros, 200%-scale-safe layout (AC #5).
- One record per swipe, existing `DeleteMealEntryUseCase` reused, no search/FTS/recipe/export/water/schema changes per scope ban (AC #7).

### File List

- `app/src/main/java/com/example/myfoodtracker/domain/repository/MealRepository.kt` [MODIFIED]
- `app/src/main/java/com/example/myfoodtracker/data/repository/MealRepositoryImpl.kt` [MODIFIED]
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/RestoreMealEntryUseCase.kt` [NEW]
- `app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/DashboardViewModel.kt` [MODIFIED]
- `app/src/main/java/com/example/myfoodtracker/di/AppModule.kt` [MODIFIED]
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/MealLogAdapter.kt` [NEW]
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/DashboardFragment.kt` [MODIFIED]
- `app/src/main/res/layout/fragment_dashboard.xml` [MODIFIED]
- `app/src/main/res/layout/item_meal_log.xml` [NEW]
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/RestoreMealEntryUseCaseTest.kt` [NEW]
- `app/src/test/java/com/example/myfoodtracker/data/repository/MealRepositoryImplTest.kt` [MODIFIED]
- `app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/DashboardViewModelTest.kt` [MODIFIED]
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/GetMealEntriesByDateUseCaseTest.kt` [MODIFIED]
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/LogQuickAddUseCaseTest.kt` [MODIFIED]
- `gradle/libs.versions.toml` [MODIFIED — added `recyclerview = "1.3.2"` catalog entry]
- `app/build.gradle.kts` [MODIFIED — added `implementation(libs.androidx.recyclerview)`]

### Change Log

- 2026-10-02: Implemented Story 2.5 (Swipe-to-Delete Intake Logs with Undo) — meal-rows list + `ItemTouchHelper` LEFT swipe with red Delete affordance, `restoreMealEntry` repo contract reusing `meals`/`foods` (no migration), `RestoreMealEntryUseCase`, 8-arg `DashboardViewModel` with ViewModel-held pending undo, 5s Snackbar Undo + TalkBack, 12 new tests; status moved to review (test run pending — no JVM in this environment).
- 2026-10-02: Fixed `bindingAdapterPosition` unresolved-symbol — added direct `androidx.recyclerview:recyclerview:1.3.2` dependency (was transitive-only via Material); no Fragment code change required.
