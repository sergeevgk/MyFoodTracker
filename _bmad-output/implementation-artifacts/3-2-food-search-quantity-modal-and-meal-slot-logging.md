---
baseline_commit: bf7e0b31ebdb8428ab49b4f5416cf7ffb770b81a
---
# Story 3.2: Food Search Quantity Modal & Meal Slot Logging

Status: done

## Story

As a user,
I want to select a search result, enter serving weight/units, and assign it to a meal slot,
so that it calculates and logs exact macronutrients to my daily log.

## Acceptance Criteria

1. **Given** the dashboard (FAB `+` / Quick-Add entry point and/or meal-card `+ Add`), **When** the user opens food search, **Then** a bottom-sheet overlay opens (`rounded/lg` top corners, `surface-raised`) with a full-width search input, clear (`X`) button, and instant FTS5 typeahead results rendered in `< 50ms` via the existing `SearchFoodUseCase` (Story 3.1 — do NOT reimplement search).
2. **Given** a non-empty query, **When** results return, **Then** each row is two-line (food name top; brand + base macros bottom, `tnum` figures) with a `Custom` chip tag iff `FoodItem.isCustom`, separated by hairline dividers; empty query shows "Recent Foods" (last 10 logged items, one-tap re-log); zero results show `No foods matching '[query]'` plus a prominent `[+ Create Custom Food]` button (button surfaces the 3.3 entry point — creation itself is out of scope).
3. **Given** a tapped search result, **When** the quantity modal opens, **Then** it shows the food name/brand, a numeric quantity input, a unit picker (`g`, `ml`, `servings`), a meal-slot selector (`BREAKFAST`, `LUNCH`, `DINNER`, `SNACK`), a live-calculated macro preview, and `[Save]`; invalid input (blank/negative/non-numeric quantity, invalid slot) blocks save with an inline field error.
4. **Given** a valid quantity + unit + slot, **When** the user taps `[Save]`, **Then** macros are computed as `Base(per-100g) × QuantityInGrams / 100` (unit conversion: `g` 1:1; `ml` 1:1 to grams MVP; `servings` × `base_serving_size`, default 100 g), written as one `meals` row (`title` = slot, `date` = dashboard active date, `profile_id` = active profile) plus one `foods` row, and the modal/sheet closes returning to the dashboard.
5. **Given** a saved log, **When** the dashboard reloads, **Then** the meal appears in the day's log and macro rings/header update in `< 50ms` (same `selectDate()` refresh pattern as quick-add/delete); a Toast/Snackbar confirms e.g. `Logged 150g Chicken Breast to Breakfast`; end-to-end (open → search → weight → save) is completable in `< 15s`.
6. **Given** the new domain/data/presentation classes, **When** JVM unit-tested, **Then** macro math (g/ml/servings, incl. edge cases 0 / fractional / large), slot validation, and blank-query/escaping delegation are covered with hand-written fakes (JUnit 4, no MockK/Robolectric — project rule); DAO work, if any, is covered in `androidTest` with `Room.inMemoryDatabaseBuilder()`.
7. **Given** the full change, **When** verified, **Then** `./gradlew compileDebugSources` + `testDebugUnitTest` are green, `:app:assembleDebug` succeeds, all 146 existing JVM tests still pass, and zero hardcoded colors/strings/dimens are introduced (Story 2.6 resource discipline).

### Scope Boundary (explicit)

- **In scope:** search bottom sheet + adapter, quantity modal, `LogFoodEntryUseCase` (+ `MealRepository.logFoodEntry`), `FoodSearchViewModel`, Koin wiring, navigation/fab wiring, strings/colors/dimens resources, JVM + (if DAO touched) instrumented tests.
- **Out of scope (Story 3.3):** custom-food creation/storage UI, bm25/app-level relevance re-ranking boost for frequently-logged customs. Only surface the `[+ Create Custom Food]` button; do not implement the editor.
- **Out of scope:** per-food custom serving-unit picker rows from `catalog_food_serving_units` (optional enhancement only — see Dev Notes §6); barcode scanning; recipe logging (Epic 4).

## Tasks / Subtasks

- [x] **Task 1: Domain — food-entry logging contract + math** (AC: #4, #6)
  - [x] Subtask 1.1: MODIFY `domain/repository/MealRepository.kt` — add `logFoodEntry(foodName: String, quantityGrams: Double, calories: Double, proteinG: Double, carbsG: Double, fatG: Double, fiberG: Double, mealSlot: String, date: String): List<MealEntry>` (mirrors `logQuickAdd` signature style; caller passes already-scaled macros).
  - [x] Subtask 1.2: NEW `domain/usecase/LogFoodEntryUseCase.kt` — `operator fun invoke(food: FoodItem, quantity: Double, unit: ServingUnit, mealSlot: String, date: String): List<MealEntry>`; owns validation (quantity finite + > 0, slot in 4-set) + macro scaling (`base × grams/100`); converts `unit` → grams (`G` 1:1, `ML` 1:1, `SERVINGS` × `food.baseServingGrams` — see §6: `FoodItem` needs `baseServingSize/Unit` fields OR pass grams directly; preferred: extend `FoodItem` with `baseServingSize: Double = 100.0, baseServingUnit: String = "g"` mapped from `CatalogFoodEntity` in `FoodCatalogRepositoryImpl`).
  - [x] Subtask 1.3: Reuse `require(...)` guard style from `LogQuickAddUseCase.kt:16-20` (`Enter a name` / `Enter 0 or more` / `Invalid meal slot` message pattern).
- [x] **Task 2: Data — repository implementation** (AC: #4)
  - [x] Subtask 2.1: MODIFY `data/repository/MealRepositoryImpl.kt` — implement `logFoodEntry` following the `logQuickAdd` body (`MealEntryEntity` UUID + `title = slot` + `date` param + `FoodEntity(weight = quantityGrams, ...scaled macros...)`); MUST use the passed `date` (dashboard active date — never `LocalDate.now()`; lesson 2.1-D1 midnight bug); MUST scope by `sessionRepository.getActiveProfileId()` (profile isolation, FR-2).
  - [x] Subtask 2.2: MODIFY `data/repository/FoodCatalogRepositoryImpl.kt` — extend the `FoodSearchRow→FoodItem` mapper with base-serving fields (requires adding `base_serving_size/unit` to the `FoodCatalogDao.search` projection or a follow-up lookup; cheapest: extend `FoodSearchRow` + SELECT with `f.base_serving_size, f.base_serving_unit`).
  - [x] Subtask 2.3: Update every hand-written fake of `MealRepository` in tests when the interface grows (lesson 2.4/2.5 — grep `implements MealRepository` / `: MealRepository` first).
- [x] **Task 3: Presentation — `FoodSearchViewModel` + bottom sheet + quantity modal** (AC: #1, #2, #3, #5)
  - [x] Subtask 3.1: NEW `presentation/viewmodel/FoodSearchViewModel.kt` — exposes `LiveData<List<FoodItem>> results` + `LiveData<String?> error`; `fun search(query: String)` delegates synchronously to `SearchFoodUseCase(query)` (no coroutines/suspend — AD-2); blank/noise query → `emptyList()` (repository already returns empty via `FtsQueryBuilder` null path).
  - [x] Subtask 3.2: NEW `presentation/ui/search/FoodSearchBottomSheet.kt` (a `BottomSheetDialogFragment`) + NEW layouts `fragment_food_search_bottom_sheet.xml` (search input + clear button + RecyclerView + empty/zero states + `[+ Create Custom Food]` button) and `item_food_search_result.xml` (two-line + `Custom` chip). Rounded `lg` top corners (16dp `@dimen/radius_card_medium` — add `radius_bottom_sheet` only if needed), `surface_card_bg`, hairline dividers `@color/surface_stroke`.
  - [x] Subtask 3.3: NEW quantity modal — either a second `BottomSheetDialogFragment` or a `MaterialAlertDialogBuilder` dialog reusing the `dialog_quick_add_log.xml` pattern (slot `AutoCompleteTextView` + numeric inputs + validation errors on `TextInputLayout`). MUST include: quantity `EditText` (decimal input), unit dropdown (`g`/`ml`/`servings`), slot dropdown (4 slots, default `BREAKFAST`), live macro preview (`tnum`, `@string/meal_macros_format`-style), `[Save]`.
  - [x] Subtask 3.4: MODIFY `presentation/ui/dashboard/DashboardFragment.kt` — wire entry point(s): FAB `btn_quick_add` (currently opens quick-add dialog) MUST offer/open the search sheet (keep quick-add accessible — e.g. search sheet hosts a "Quick-Add" option per UX sheet spec, or FAB opens a chooser); follow existing dialog patterns (`showQuickAddDialog` lines 350-402) for validation + `announceForAccessibility` on save.
  - [x] Subtask 3.5: MODIFY `presentation/viewmodel/DashboardViewModel.kt` — add `fun logFoodEntry(food: FoodItem, quantity: Double, unit: ServingUnit, mealSlot: String)` using `_uiState.activeDate.toString()` as date, then `selectDate(date)` to refresh (same pattern as `logQuickAdd` lines 98-113); catch `IllegalArgumentException` → no-op (dialog shows the error).
  - [x] Subtask 3.6: MODIFY `di/AppModule.kt` — `factory { LogFoodEntryUseCase(get()) }`, `viewModel { FoodSearchViewModel(get()) }`, extend `DashboardViewModel` definition with the new use-case param (AD-6 centralized DI; note Koin constructor changes break every call site/test — update fakes).
- [x] **Task 4: Resources (Story 2.6 discipline — zero hardcodes)** (AC: #7)
  - [x] Subtask 4.1: All new user-visible strings → `res/values/strings.xml` (`@string/` refs only in layouts/code, incl. format templates with `%1$s` placeholders); all colors → `@color/` refs; radii/touch targets → `@dimen/` refs (`min_touch_target` 48dp minimum on every button/row).
  - [x] Subtask 4.2: TalkBack: search rows + quantity save announce via `announceForAccessibility` with `@string/a11y_*` templates (follow `showQuickAddDialog`/`showUndoSnackbar` precedent); macro preview uses `fontFeatureSettings="tnum"`.
- [x] **Task 5: Tests** (AC: #6, #7)
  - [x] Subtask 5.1: NEW `app/src/test/.../domain/usecase/LogFoodEntryUseCaseTest.kt` — hand-written fake `MealRepository`: scaling math per unit (g 150→1.5×; ml 1:1; servings ×base), 0/negative/NaN quantity → `IllegalArgumentException`, bad slot → IAE, date passed through, profile-scoped delegation.
  - [x] Subtask 5.2: NEW `app/src/test/.../presentation/viewmodel/FoodSearchViewModelTest.kt` — fake `SearchFoodUseCase` repo: blank → empty, results propagate, limit default 30 (needs `androidx.arch.core:core-testing` `InstantTaskExecutorRule` for LiveData — allowed per project-context testing rules; check gradle first).
  - [x] Subtask 5.3: If `MealRepository` fake exists in test sources, update it for `logFoodEntry` (lesson 2.4/2.5).
  - [x] Subtask 5.4: RUN `./gradlew testDebugUnitTest :app:assembleDebug` green (expect 146 pre-existing + new); `:app:assembleDebugAndroidTest` green if androidTest touched. Do NOT run the emulator (project rule — flag instrumented verification for manual on-device testing).
- [x] **Task 6: Navigation wiring** (AC: #1)
  - [x] Subtask 6.1: Bottom sheet shown via `parentFragmentManager` from `DashboardFragment` (no nav-graph destination needed for dialogs); if a full-screen search destination is preferred instead, add it to `res/navigation/nav_graph.xml` with dashboard action.

### Review Findings

- [x] [Review][Decision] Recent Foods taps open quantity modal vs immediate one-tap re-log — Resolved: Keep quantity modal to verify portion/slot per user decision.
- [x] [Review][Patch] Replace mojibake characters (`┬╖`) with proper middle dots (`·`) [app/src/main/res/values/strings.xml:107, 116]
- [x] [Review][Patch] Wire clear button content description in food search input [app/src/main/res/layout/fragment_food_search_bottom_sheet.xml:46]
- [x] [Review][Patch] Add space delimiter for 'servings' unit in confirmation Snackbar [app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/DashboardFragment.kt:385]
- [x] [Review][Patch] Add `food.name.isNotBlank()` validation guard in `LogFoodEntryUseCase` [app/src/main/java/com/example/myfoodtracker/domain/usecase/LogFoodEntryUseCase.kt:16]
- [x] [Review][Patch] Support decimal comma separator in quantity dialog for international locales [app/src/main/java/com/example/myfoodtracker/presentation/ui/search/LogFoodQuantityDialogFragment.kt:71]
- [x] [Review][Patch] Dismiss soft keyboard when closing food search bottom sheet [app/src/main/java/com/example/myfoodtracker/presentation/ui/search/FoodSearchBottomSheet.kt:51]
- [x] [Review][Patch] Add fallback Listener resolution for dialog recreation lifecycles [app/src/main/java/com/example/myfoodtracker/presentation/ui/search/LogFoodQuantityDialogFragment.kt:88]
- [x] [Review][Patch] Extend recent foods query scope to historical entries across dates [app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/FoodSearchViewModel.kt:27]
- [x] [Review][Patch] Synchronize LiveData observer and recent foods in `FoodSearchBottomSheet` to prevent state overwrite [app/src/main/java/com/example/myfoodtracker/presentation/ui/search/FoodSearchBottomSheet.kt:55]
- [x] [Review][Patch] Add JVM unit test coverage for `FoodSearchViewModel.recentFoods` [app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/FoodSearchViewModelTest.kt:37]
- [x] [Review][Patch] Add overflow and non-finite number guard in live quantity preview [app/src/main/java/com/example/myfoodtracker/presentation/ui/search/LogFoodQuantityDialogFragment.kt:111]
- [x] [Review][Patch] Clean up or wire unused string resource `food_search_row_macros_format` [app/src/main/res/values/strings.xml:107]
- [x] [Review][Defer] Wrap composite meal logging in a database transaction [app/src/main/java/com/example/myfoodtracker/data/repository/MealRepositoryImpl.kt:80] — deferred, pre-existing
- [x] [Review][Defer] Add instrumented verification test for `FoodCatalogDao` projection additions [app/src/androidTest/java/com/example/myfoodtracker/data/FoodCatalogFtsTest.kt:50] — deferred, pre-existing

## Dev Notes

### 0. Do NOT reinvent — reuse inventory (read these files first)

| Reuse | File | What to reuse |
|---|---|---|
| Search engine | `data/dao/FtsQueryBuilder.kt`, `domain/usecase/SearchFoodUseCase.kt`, `data/repository/FoodCatalogRepositoryImpl.kt` | Entire query path; call `SearchFoodUseCase(query)` — never write FTS `MATCH` strings in UI |
| Domain food model | `domain/model/FoodItem.kt` | Nutrients are **per 100 g** (seeded `base_serving_size=100.0, unit='g'`); extend with `baseServingSize/Unit` rather than inventing a second model (note: distinct from meal-line `domain/model/Food.kt` — do not merge them) |
| Log-write pattern | `data/repository/MealRepositoryImpl.kt:58-91` (`logQuickAdd`) | `insertMeal` + `insertFoods` sequence, UUID meal id, `title = slot`; known non-atomic debt 2.4-D1 — follow the same pattern, do not redesign DAO transactions in this story |
| Validation style | `domain/usecase/LogQuickAddUseCase.kt:16-20` | `require(...)` guards + message strings |
| Dialog/validation/a11y | `presentation/ui/dashboard/DashboardFragment.kt:350-450` | `MaterialAlertDialogBuilder` + `setPositiveButton` override pattern, `parseQuickAddMacro` error handling, `announceForAccessibility`, Snackbar conventions |
| VM refresh | `presentation/viewmodel/DashboardViewModel.kt:98-113` | `activeDate`-driven `selectDate(date)` refresh after every write |
| Resource discipline | `res/values/strings.xml`, `colors.xml`, `dimens.xml` | Zero hardcoded hex/strings/dimens (Story 2.6 resolved 2.1-D2/2.2-D1/2.2-D2/2.4-D2) |
| Recent-foods source | `MealRepository.getMealEntriesByDate` / `getMealEntries` | Derive "last 10 logged" from existing queries — no new DAO needed |

### 1. Macro math (normative)

```
grams = when (unit) { G -> quantity; ML -> quantity; SERVINGS -> quantity * food.baseServingSize }
scaled = food.<macroPer100g> * grams / 100.0
```

- `ml` 1:1 to grams is the MVP simplification (PRD FR-5 lists `g`/`ml`/`servings` without density tables; water-like density assumed). Record any density-table ambition as a forward note, do not implement.
- `servings` uses the food's own `base_serving_size` (seeded 100.0 g for USDA rows; custom foods in 3.3 may define their own — the `FoodItem` extension in Task 1.2 future-proofs this).
- Accuracy bar: NFR-4 0.1% tolerance — use `Double` throughout, no premature rounding; formatting only at display (`%.0f` templates).
- `FoodEntity.weight` stores `quantityGrams` (so meal rows remain comparable); `fiber` from `FoodItem.fiberG` (quick-add hardcodes 0.0 — catalog path must pass the real value).

### 2. UX contract (binding)

- Sheet: `rounded/lg` (16dp) top corners, `surface-raised` (`@color/surface_card_bg`), full-width search input + clear `X`, hairline dividers `@color/surface_stroke`, two-line rows (name / brand + base macros), `Custom` chip iff `isCustom`, max height 85vh, 16dp internal padding. [Source: DESIGN.md §Food Search & Autocomplete Sheet; EXPERIENCE.md §Search Autocomplete List, §State Patterns]
- Empty query → "Recent Foods" (last 10); zero results → `No foods matching '[query]'` + `[+ Create Custom Food]` (navigates nowhere functional yet — 3.3; a Toast/disabled state is acceptable if explicitly noted).
- Quantity modal: unit picker (`g`, `ml`, `servings`) + slot picker (`BREAKFAST/LUNCH/DINNER/SNACK`) + live preview + `[Save]`; full key flow in EXPERIENCE.md Flow 1 (type `chick` → tap → `150` g → Breakfast → rings animate). [Source: EXPERIENCE.md#Flow 1, #Log Food Quantity Modal]
- Microcopy: `Logged 150g Chicken Breast.` style (no gamification). [Source: EXPERIENCE.md#Voice and Tone]

### 3. Architecture compliance guardrails

- `domain/` stays pure Kotlin — no `android.*`/`androidx.room3` imports (AD-1); entities/`FoodSearchRow` never cross `data/` — map with private extensions (AD-3).
- Synchronous only: no `suspend`/`Flow`/`runBlocking` in app code; DAO calls on main thread via `allowMainThreadQueries()` (AD-2 as amended for Room 3).
- New `ServingUnit` enum lives in `domain/model/` (pure Kotlin); UI maps dropdown strings → enum, never passes raw strings into the use case.
- DI centralized in `di/AppModule.kt`: `single` repos, `factory` use cases, `viewModel` VMs, injection via `by viewModel()`; Fragments clear `_binding = null` in `onDestroyView()` and observe with `viewLifecycleOwner` (AD-5/AD-6).
- `MealRepository.logFoodEntry` enforces `profile_id` isolation (FR-2/NFR-5); `date` = dashboard active date string `YYYY-MM-DD`.
- Zero network: no HTTP imports anywhere (NFR-1).

### 4. Testing standards (must follow)

- JUnit 4 + hand-written fakes only; no MockK/Mockito/Robolectric (none in catalog — do not add for this story). `InstantTaskExecutorRule` (`androidx.arch.core:core-testing`) only for LiveData VM tests.
- Environment preflight per `AGENTS.md`: check `JAVA_HOME`/`ANDROID_HOME`, expect Java 21 Linux; `local.properties` SDK warning is harmless — do not "fix" it.
- Verify: `./gradlew testDebugUnitTest :app:assembleDebug` (+ `:app:assembleDebugAndroidTest` if androidTest touched). No emulator runs — flag on-device checks for manual testing.

### 5. Git intelligence / previous-story learnings

- Branch `develop`; commits `Implement Story 3.2. <desc>`; merge to `main` via PR. Story file + `sprint-status.yaml` + tests committed with code.
- **CRLF trap:** working tree shows phantom "modified" files — always stage via `git diff --ignore-cr-at-eol --name-only`; never `git add -A`.
- Koin constructor changes break every call site/test (lesson 2.4) — grep all `DashboardViewModel(` instantiations after adding the use-case param.
- Tests asserting seeded-fake state were flagged in review (3.4) — write real assertions on math/validation/delegation.
- `.agents/project_structure.md` does not exist (deferred 3.4-D1, dropped) — do not fail over it. Architecture spine `sergeevgk.*` paths are stale — actual package is `com.example.myfoodtracker`.
- Forward-note candidates for `deferred-work.md`: catalog version-bump wiping customs (3.1-F1 still open — this story writes only to `meals`/`foods`, so no new risk); per-food `catalog_food_serving_units` picker integration; `ml` density tables.

### 6. Serving-unit enhancement (optional, only if cheap)

- `catalog_food_serving_units` rows exist (up to 8/food, Story 3.1 seed). If a one-method DAO addition (`@Query("SELECT * FROM catalog_food_serving_units WHERE food_id = :id") fun getUnits(foodId: Long)`) plus picker rows fit without jeopardizing ACs, include them as extra picker entries (`unit_name` → `grams_per_unit` conversion). Otherwise defer with a `deferred-work.md` note. The 3 fixed units (`g`/`ml`/`servings`) are the normative requirement.

### Project Structure Notes

- New files (under `app/src/main/java/com/example/myfoodtracker/` unless noted):
  - `domain/model/ServingUnit.kt` (enum `G, ML, SERVINGS`), `domain/usecase/LogFoodEntryUseCase.kt`
  - `presentation/viewmodel/FoodSearchViewModel.kt`, `presentation/ui/search/FoodSearchBottomSheet.kt` (+ quantity modal fragment/dialog), adapter `presentation/ui/search/FoodSearchAdapter.kt`
  - Layouts: `res/layout/fragment_food_search_bottom_sheet.xml`, `res/layout/item_food_search_result.xml`, `res/layout/dialog_log_food_quantity.xml` (or bottom-sheet variant)
  - Tests: `app/src/test/.../domain/usecase/LogFoodEntryUseCaseTest.kt`, `app/src/test/.../presentation/viewmodel/FoodSearchViewModelTest.kt`
- Modified: `domain/repository/MealRepository.kt`, `data/repository/MealRepositoryImpl.kt`, `data/repository/FoodCatalogRepositoryImpl.kt` (+ `FoodSearchRow`/DAO projection if base-serving fields added), `domain/model/FoodItem.kt` (base-serving fields), `presentation/ui/dashboard/DashboardFragment.kt`, `presentation/viewmodel/DashboardViewModel.kt`, `di/AppModule.kt`, `res/values/strings.xml` (+ colors/dimens only if new tokens needed), `res/navigation/nav_graph.xml` (only if full-screen destination chosen), `deferred-work.md` (forward notes).
- Variances: spine structural seed shows `presentation/ui/search/` + `FoodSearchViewModel.kt` as planned but unimplemented — this story implements them as specified. Spine `sergeevgk` package prefix is stale.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 3.2] — story statement + AC (macro formula, units, slots).
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/prd.md#FR-5, #UJ-1, #7-Success Metrics] — intake log creation, 15s/50ms/0.1% bars.
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/addendum.md#1.2] — `food_serving_units` / `intake_logs` (`meal_slot`, `serving_unit_name`, `quantity`) table intent.
- [Source: _bmad-output/planning-artifacts/architecture/.../ARCHITECTURE-SPINE.md#AD-1..AD-8, #Capability Map, #Structural Seed] — layering, sync access, DI, FTS engine, search-file layout.
- [Source: _bmad-output/planning-artifacts/ux-designs/.../DESIGN.md#Food Search & Autocomplete Sheet] — sheet visual spec.
- [Source: _bmad-output/planning-artifacts/ux-designs/.../EXPERIENCE.md#Flow 1, #Search Autocomplete List, #State Patterns, #Voice and Tone] — key flow, recent-foods, zero-result, microcopy.
- [Source: _bmad-output/project-context.md] — stack (Room 3.0.3, Koin 3.5.6), sync-execution, mapping, DI, testing, resource rules.
- [Source: _bmad-output/implementation-artifacts/3-1-*.md#Dev Notes, #File List] — engine reuse contract, schema, out-of-scope handoff to 3.2.
- [Source: app/src/main/java/.../MealRepositoryImpl.kt#logQuickAdd] — write pattern to mirror.
- [Source: app/src/main/java/.../DashboardFragment.kt#showQuickAddDialog] — dialog/validation/a11y precedent.
- [Source: _bmad-output/implementation-artifacts/deferred-work.md#2.4-D1, #2.1-D1, #3.1-F1/F2] — open debts and forward notes.

## Dev Agent Record

### Agent Model Used

Muse Spark 1.3 Contributor (opencode-go)

### Debug Log References

- AGP `mergeDebugResources` NPE on first incremental build after adding layouts — stale merge blob, resolved by `./gradlew clean` (not a code issue).
- `R.string.food_search_create_custom_food` unresolved — string key created as `food_search_create_custom`; renamed to match layout + code refs.
- CRLF phantom modifications present in working tree — story/sprint edits applied directly; commit must stage via `git diff --ignore-cr-at-eol --name-only`.

### Completion Notes List

- `ServingUnit` enum (G/ML/SERVINGS) in `domain/model`; `FoodItem` extended with `baseServingSize=100.0`/`baseServingUnit="g"` defaults (old constructions still compile).
- `LogFoodEntryUseCase` owns validation (`quantity finite + > 0`, 4-slot set, `require` style mirroring `LogQuickAddUseCase`) + scaling `base × grams/100` (ml 1:1 MVP).
- `MealRepository.logFoodEntry` + `MealRepositoryImpl` mirror `logQuickAdd` (UUID meal, `title=slot`, passed `date`, `weight=quantityGrams`, real `fiber`, profile-scoped).
- Catalog projection extended with `f.base_serving_size/unit`; mapper passes them through (`FoodSearchRow` defaults keep old fakes compiling).
- `FoodSearchViewModel` (search delegation + `recentFoods(date)` via weight-normalized per-100g reconstruction, zero-weight quick-adds excluded, deduped, max 10).
- `FoodSearchBottomSheet` (BottomSheetDialogFragment via `childFragmentManager`, header quick-add escape hatch, clear-text input, recent/zero states, 3.3 button = Toast placeholder) + `FoodSearchAdapter` (ListAdapter, two-line rows, Custom chip, hairline dividers, TalkBack descriptions) + `LogFoodQuantityDialogFragment` (args-Bundle FoodItem, live preview with `tnum`, inline errors blocking save).
- `DashboardViewModel.logFoodEntry` (active-date, IAE no-op, `selectDate` refresh); FAB now opens search sheet; Snackbar `Logged 150g Chicken Breast to Breakfast` + `announceForAccessibility`.
- Koin: `factory LogFoodEntryUseCase`, `viewModel FoodSearchViewModel(get(), get())`, `DashboardViewModel` +1 param.
- Tests: 168 JVM tests green (146 pre-existing + 22 new: 13 `LogFoodEntryUseCaseTest`, 4 `FoodSearchViewModelTest`, 3 `DashboardViewModel.logFoodEntry`, 2 catalog serving-mapper); 4 existing `MealRepository` fakes extended. `assembleDebug` + `assembleDebugAndroidTest` green. No emulator — on-device timing (<50ms refresh, <15s e2e) flagged for manual testing.

### File List

New:
- `app/src/main/java/com/example/myfoodtracker/domain/model/ServingUnit.kt`
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/LogFoodEntryUseCase.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/FoodSearchViewModel.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/search/FoodSearchAdapter.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/search/FoodSearchBottomSheet.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/search/LogFoodQuantityDialogFragment.kt`
- `app/src/main/res/layout/fragment_food_search_bottom_sheet.xml`
- `app/src/main/res/layout/item_food_search_result.xml`
- `app/src/main/res/layout/dialog_log_food_quantity.xml`
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/LogFoodEntryUseCaseTest.kt`
- `app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/FoodSearchViewModelTest.kt`

Modified:
- `app/src/main/java/com/example/myfoodtracker/domain/model/FoodItem.kt`
- `app/src/main/java/com/example/myfoodtracker/domain/repository/MealRepository.kt`
- `app/src/main/java/com/example/myfoodtracker/data/repository/MealRepositoryImpl.kt`
- `app/src/main/java/com/example/myfoodtracker/data/repository/FoodCatalogRepositoryImpl.kt`
- `app/src/main/java/com/example/myfoodtracker/data/db/FoodSearchRow.kt`
- `app/src/main/java/com/example/myfoodtracker/data/dao/FoodCatalogDao.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/DashboardViewModel.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/DashboardFragment.kt`
- `app/src/main/java/com/example/myfoodtracker/di/AppModule.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/DashboardViewModelTest.kt`
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/LogQuickAddUseCaseTest.kt`
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/GetMealEntriesByDateUseCaseTest.kt`
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/RestoreMealEntryUseCaseTest.kt`
- `app/src/test/java/com/example/myfoodtracker/data/repository/FoodCatalogRepositoryImplTest.kt`
