---
baseline_commit: 3ebf0339ab1a7e9a5d9020eef771e0ad9fbe2c70
---
# Story 3.3: Custom Food Creation & Relevance Ranking

Status: done

## Story

As a user,
I want to create custom food entries with brand names and macro specs, and have frequently logged custom items rank higher in search results,
so that my personal food items are prioritized.

## Acceptance Criteria

1. **Given** the food search bottom sheet with zero results (`No foods matching '[query]'` + `[+ Create Custom Food]`, Story 3.2), **When** the user taps `[+ Create Custom Food]`, **Then** a Custom Food editor opens (dialog or bottom sheet following the `LogFoodQuantityDialogFragment` / `showQuickAddDialog` pattern) with fields: food name (pre-filled with the search query), brand (optional), base serving size + unit (default 100 g), and macros per 100 g (Calories, Protein, Carbs, Fat; Fiber/Sugar/Sodium optional, default 0.0); invalid input (blank name, non-positive serving size, negative/non-numeric macros) blocks save with an inline `TextInputLayout` field error.
2. **Given** valid custom-food input, **When** the user taps `[Save]`, **Then** the food is persisted to `catalog_foods` with `is_custom = 1` plus its `catalog_food_nutrients` row via the existing `@Transaction insertFoodWithNutrients` path (never write to `catalog_foods_fts` directly), Room's `room_fts_content_sync_*` triggers index it immediately, and a follow-up `SearchFoodUseCase` query for the new name returns it with zero app restart.
3. **Given** a newly saved custom food, **When** the save completes, **Then** the editor closes, the search list refreshes showing the new item with the `Custom` chip tag, and (per EXPERIENCE.md Flow 3 "Save to Custom Library & Log") the item is handed to the existing quantity-modal / `LogFoodEntryUseCase` path for immediate logging — do NOT reimplement logging math.
4. **Given** a non-empty search query matching both custom and pre-seeded foods, **When** results render, **Then** frequently logged custom items rank above pre-seeded items with an otherwise identical prefix-match score (FR-4 testable bar: a custom food logged 5 times in the last week ranks higher than a pre-seeded food with the same token prefix match); `bm25()` remains the base ordering signal and the boost is applied as a stable app-level re-rank, never as hand-written FTS `MATCH` strings in UI.
5. **Given** the ranking boost, **When** measured on-device, **Then** end-to-end search (type → rendered re-ranked list) still completes in `< 50ms` average (NFR-2); frequency counting reuses the in-memory meal-history path (`MealRepository.getMealEntries()`, profile-scoped) with no new suspend/coroutine machinery and no N+1 DAO queries per result row.
6. **Given** the new domain/data/presentation classes, **When** JVM unit-tested, **Then** creation validation (blank name, bad serving size, negative/NaN macros), `is_custom = 1` persistence + FTS-delegation, and ranking rules (custom + frequency beats seeded on tie; seeded order preserved when no history; empty/blank query unaffected) are covered with hand-written fakes (JUnit 4, no MockK/Robolectric — project rule); DAO work, if any, is covered in `androidTest` with `Room.inMemoryDatabaseBuilder()`.
7. **Given** the full change, **When** verified, **Then** `./gradlew testDebugUnitTest` + `:app:assembleDebug` are green, all 168 existing JVM tests still pass, `FoodCatalogDatabase` stays at version 1 (no schema change, no exported-JSON churn), and zero hardcoded colors/strings/dimens are introduced (Story 2.6 resource discipline).

### Scope Boundary (explicit)

- **In scope:** custom-food editor UI, `CreateCustomFoodUseCase` (+ `FoodCatalogRepository.createCustomFood`), app-level relevance re-ranking for custom/frequent items, wiring the 3.2 `[+ Create Custom Food]` placeholder button (currently a Toast) to the real editor, resources, JVM + (if DAO touched) instrumented tests.
- **Out of scope (Story 3.5):** German/European catalog seed pipeline changes — do NOT rebuild or modify `food_catalog.db` asset contents in this story.
- **Out of scope (Story 5.2):** backup/restore of the catalog DB — record any new need as a `deferred-work.md` note (3.1-F2 already covers two-DB backup), do not implement.
- **Out of scope:** catalog schema migration or version bump — 3.1-F1 warns a bump + `fallbackToDestructiveMigration()` would wipe `is_custom = 1` rows; this story MUST NOT bump the version. Custom serving-unit rows for the new food are optional (only if one extra DAO insert fits without risk).

## Tasks / Subtasks

- [x] **Task 1: Domain — custom-food creation contract + ranking rules** (AC: #1, #2, #4, #6)
  - [x] Subtask 1.1: MODIFY `domain/repository/FoodCatalogRepository.kt` — add `createCustomFood(name: String, brand: String?, baseServingSize: Double, baseServingUnit: String, calories: Double, proteinG: Double, carbsG: Double, fatG: Double, fiberG: Double = 0.0, sugarG: Double = 0.0, sodiumMg: Double = 0.0): FoodItem` (returns the persisted item with its generated id and `isCustom = true`).
  - [x] Subtask 1.2: NEW `domain/usecase/CreateCustomFoodUseCase.kt` — `operator fun invoke(...): FoodItem`; owns validation with `require(...)` guard style from `LogQuickAddUseCase.kt:16-20` / `LogFoodEntryUseCase.kt:16` (name `isNotBlank`, serving size finite + > 0, every macro finite + >= 0); trims name/brand; defaults serving unit `"g"` when blank.
  - [x] Subtask 1.3: MODIFY `domain/usecase/SearchFoodUseCase.kt` (preferred) or NEW `RankedSearchFoodUseCase` — apply the app-level re-rank AFTER `repository.search()`: boost = custom-first, then log-frequency (count from `MealRepository.getMealEntries()` grouped by normalized `name.trim().lowercase()`), then stable preservation of the DAO's `bm25(), name` order. Keep signature `operator fun invoke(query: String, limit: Int = 30): List<FoodItem>`; no `suspend`/`Flow` (AD-2). Blank/noise query still returns `emptyList()` via the existing `FtsQueryBuilder` null path.
  - [x] Subtask 1.4: Reuse `require(...)` message-string pattern from `LogQuickAddUseCase` (`Enter a name` / `Enter 0 or more` / serving-size equivalent); UI maps caught `IllegalArgumentException` to inline field errors (never crash).
- [x] **Task 2: Data — repository implementation (no schema change)** (AC: #2, #4)
  - [x] Subtask 2.1: MODIFY `data/repository/FoodCatalogRepositoryImpl.kt` — implement `createCustomFood` via existing `FoodCatalogDao.insertFoodWithNutrients(CatalogFoodEntity(isCustom = 1, name, brand, barcode = null, baseServingSize/Unit, isDeleted = 0, createdAt/updatedAt = System.currentTimeMillis()), CatalogFoodNutrientEntity(...))`; map the generated row id back to `FoodItem(isCustom = true)`. All writes go through `catalog_foods` so Room FTS triggers fire — never insert into `catalog_foods_fts`.
  - [x] Subtask 2.2: DO NOT modify `FoodCatalogDatabase` version, entities, or the exported schema JSON (`app/schemas/.../FoodCatalogDatabase/1.json` stays untouched); DO NOT add `LIKE`-scan queries — FTS `MATCH` stays the only search path.
  - [x] Subtask 2.3: If ranking needs frequency inside `data/` (alternative to UseCase counting): prefer the UseCase path using the existing `MealRepository` interface — do NOT add a `COUNT/GROUP BY` DAO on `MealDao` unless profiling shows the in-memory count breaks the 50ms bar; `foods` rows carry free-text `name` with no `catalog_id` FK, so any counting is name-normalized by definition.
  - [x] Subtask 2.4: Update every hand-written fake of `FoodCatalogRepository` in tests when the interface grows (lesson 2.4/2.5/3.2 — grep `FoodCatalogRepository` / `: FoodCatalogRepository` first).
- [x] **Task 3: Presentation — custom-food editor + ranking wiring** (AC: #1, #3, #4, #5)
  - [x] Subtask 3.1: NEW editor — either a second `BottomSheetDialogFragment` (`presentation/ui/search/CreateCustomFoodBottomSheet.kt`) or a `MaterialAlertDialogBuilder` dialog reusing the `dialog_quick_add_log.xml` / `LogFoodQuantityDialogFragment` pattern (slot-style dropdowns not needed; numeric `EditText`s with `TextInputLayout` errors, live macro preview with `tnum`, `[Save]`). MUST include: name (prefill from search query), brand (optional), serving size (decimal, default 100) + unit (`g`/`ml`/`servings`, default `g`), Calories/Protein/Carbs/Fat per 100 g (+ optional Fiber/Sugar/Sodium, default 0.0).
  - [x] Subtask 3.2: MODIFY `presentation/ui/search/FoodSearchBottomSheet.kt` — replace the Toast placeholder in `btnCreateCustomFood.setOnClickListener` with opening the editor (pass `lastQuery` as the prefill arg); after save, refresh `viewModel.search(lastQuery)` so the new row appears immediately with the `Custom` chip (adapter already supports it — `FoodSearchAdapter.kt:51`); then hand the created `FoodItem` to the existing `LogFoodQuantityDialogFragment` for the Save-&-Log handoff (Flow 3).
  - [x] Subtask 3.3: MODIFY `presentation/viewmodel/FoodSearchViewModel.kt` — add `fun createCustomFood(...): FoodItem?` delegating synchronously to `CreateCustomFoodUseCase` (no coroutines — AD-2); catch `IllegalArgumentException` → expose via existing `error` LiveData (dialog shows inline error, list untouched). Constructor gains the new use-case param — grep all `FoodSearchViewModel(` call sites + fakes (Koin lesson 2.4/3.2).
  - [x] Subtask 3.4: MODIFY `di/AppModule.kt` — `factory { CreateCustomFoodUseCase(get()) }`, extend the `viewModel { FoodSearchViewModel(...) }` definition (AD-6 centralized DI).
  - [x] Subtask 3.5: Follow `DashboardFragment.showQuickAddDialog` precedent for `parseDouble`-style error handling, `announceForAccessibility` on save, and Snackbar confirmation (e.g. `Saved <name> to Custom Library`); keep `Custom` chip rendering as-is (`chipCustom` iff `item.isCustom`).
- [x] **Task 4: Resources (Story 2.6 discipline — zero hardcodes)** (AC: #7)
  - [x] Subtask 4.1: All new user-visible strings → `res/values/strings.xml` (`@string/` refs only, incl. editor labels, error templates with `%1$s` placeholders); all colors → `@color/` refs; radii/touch targets → `@dimen/` refs (`min_touch_target` 48 dp minimum on every button/row).
  - [x] Subtask 4.2: TalkBack: editor save + new-row announcement via `announceForAccessibility` with `@string/a11y_*` templates; macro preview uses `fontFeatureSettings="tnum"`.
- [x] **Task 5: Tests** (AC: #6, #7)
  - [x] Subtask 5.1: NEW `app/src/test/.../domain/usecase/CreateCustomFoodUseCaseTest.kt` — hand-written fake `FoodCatalogRepository`: valid input persists `isCustom = true` with trimmed name/brand + defaults; blank name → `IllegalArgumentException`; zero/negative/NaN serving size → IAE; negative/NaN macro → IAE; brand null/blank preserved.
  - [x] Subtask 5.2: NEW or EXTEND ranking test (`SearchFoodUseCaseTest` or `RankedSearchFoodUseCaseTest`) — fake catalog repo + fake `MealRepository.getMealEntries()`: custom logged 5× outranks seeded on identical prefix score (FR-4 bar); seeded-seeded order stable when no history; more-frequent custom outranks less-frequent custom; blank query → empty (no ranking crash).
  - [x] Subtask 5.3: NEW or EXTEND `FoodSearchViewModelTest` — fake `CreateCustomFoodUseCase`: save success refreshes/delegates, validation failure surfaces `error` LiveData (needs `androidx.arch.core:core-testing` `InstantTaskExecutorRule` — already used in 3.2, check gradle first).
  - [x] Subtask 5.4: RUN `./gradlew testDebugUnitTest :app:assembleDebug` green (expect 168 pre-existing + new); `:app:assembleDebugAndroidTest` green only if androidTest touched (prefer not touching it). Do NOT run the emulator (project rule — flag on-device `<50ms` timing for manual testing).
- [x] **Task 6: Forward notes** (AC: #2, #7)
  - [x] Subtask 6.1: If anything new is learned about 3.1-F1 (catalog bump wiping customs) or 3.1-F2 (two-DB backup), append to `_bmad-output/implementation-artifacts/deferred-work.md`; do NOT implement backup/migration in this story.

### Review Findings

- [x] [Review][Patch] Anchor 'Saved to Custom Library' Snackbar and TalkBack announcement to Activity root view before dismissing search sheet [app/src/main/java/com/example/myfoodtracker/presentation/ui/search/FoodSearchBottomSheet.kt:115]
- [x] [Review][Patch] Short-circuit on empty meal history bypasses custom food ranking boost [app/src/main/java/com/example/myfoodtracker/domain/usecase/SearchFoodUseCase.kt:25]
- [x] [Review][Patch] Serving unit reset to "g" on configuration change / dialog recreation [app/src/main/java/com/example/myfoodtracker/presentation/ui/search/CreateCustomFoodDialogFragment.kt:41]
- [x] [Review][Patch] Meal food entries with blank names not filtered before frequency grouping [app/src/main/java/com/example/myfoodtracker/domain/usecase/SearchFoodUseCase.kt:23]
- [x] [Review][Patch] Add domain validation guards for serving units, string lengths, and numeric upper bounds [app/src/main/java/com/example/myfoodtracker/domain/usecase/CreateCustomFoodUseCase.kt:21-31]
- [x] [Review][Patch] Distinguish empty required macro fields from non-negative validation errors [app/src/main/java/com/example/myfoodtracker/presentation/ui/search/CreateCustomFoodDialogFragment.kt:135]
- [x] [Review][Patch] Add missing IME actions and consistent bottom spacing on sodium input [app/src/main/res/layout/dialog_create_custom_food.xml:246]
- [x] [Review][Patch] Add unit test verifying custom food ordering when meal history is empty [app/src/test/java/com/example/myfoodtracker/domain/usecase/SearchFoodUseCaseTest.kt]
- [x] [Review][Defer] Main-thread sync MealRepository meal scan on every keystroke [app/src/main/java/com/example/myfoodtracker/domain/usecase/SearchFoodUseCase.kt:21] — deferred, anticipated by Dev Notes §2 until measured on-device

## Dev Notes

### 0. Do NOT reinvent — reuse inventory (read these files first)

| Reuse | File | What to reuse |
|---|---|---|
| Search engine | `data/dao/FtsQueryBuilder.kt`, `domain/usecase/SearchFoodUseCase.kt`, `data/repository/FoodCatalogRepositoryImpl.kt` | Entire query path; UI calls the use case — never build FTS `MATCH` strings in UI |
| Custom-write path | `data/dao/FoodCatalogDao.kt:insertFoodWithNutrients` (`@Transaction`, `REPLACE`) | Multi-table insert used by tests/seed; call it from the new repo method — never write `catalog_foods_fts` directly (Room triggers sync it) |
| Domain food model | `domain/model/FoodItem.kt` | Nutrients are **per 100 g**; new foods use `baseServingSize = 100.0, baseServingUnit = "g"` defaults (Story 3.2 extended this model — do not add a second model; distinct from meal-line `domain/model/Food.kt`) |
| Log-write / Save-&-Log | `domain/usecase/LogFoodEntryUseCase.kt`, `data/repository/MealRepositoryImpl.kt:logFoodEntry` | After custom save, hand the `FoodItem` to the existing quantity modal + `logFoodEntry` — do not recompute macro scaling |
| Validation style | `domain/usecase/LogQuickAddUseCase.kt:16-20`, `domain/usecase/LogFoodEntryUseCase.kt:16` | `require(...)` guards + message strings; `food.name.isNotBlank()` guard precedent (3.2 review patch) |
| Editor precedent | `presentation/ui/search/LogFoodQuantityDialogFragment.kt`, `presentation/ui/dashboard/DashboardFragment.kt:350-450` (`showQuickAddDialog`) | `MaterialAlertDialogBuilder` + `setPositiveButton` override pattern, decimal-comma parsing (`LogFoodQuantityDialogFragment.kt:71` review patch), `parseQuickAddMacro` error handling, `announceForAccessibility`, Snackbar conventions, keyboard dismissal (`FoodSearchBottomSheet.kt:51` review patch), dialog-recreation listener fallback (`LogFoodQuantityDialogFragment.kt:88` review patch) |
| VM pattern | `presentation/viewmodel/FoodSearchViewModel.kt` | `search(query)` delegation + `recentFoods(date)` (recency-only today — ranking boost is layered on `search()`, recent list stays as-is); `error` LiveData pattern |
| Sheet placeholder | `presentation/ui/search/FoodSearchBottomSheet.kt:btnCreateCustomFood` | Currently a Toast (`food_search_create_custom_food`) shown only in the zero-results state — this story replaces the Toast with the editor; keep the recent/zero visibility logic (`renderResults`) unchanged |
| Chip rendering | `presentation/ui/search/FoodSearchAdapter.kt:51` | `chipCustom` iff `item.isCustom` — already implemented, reuse as-is |
| Resource discipline | `res/values/strings.xml` (`food_search_*`), `colors.xml`, `dimens.xml` | Zero hardcoded hex/strings/dimens (Story 2.6); existing keys `food_search_recent_title`, `food_search_no_results_format`, `food_search_create_custom_food` |
| Recent-foods source | `domain/repository/MealRepository.kt:getMealEntries()` | Profile-scoped full history is the frequency source — reuse it, no new DAO needed |

### 1. Creation contract (normative)

```
food = CatalogFoodEntity(name = trimmed, brand = trimmedOrNull, barcode = null,
  baseServingSize (> 0), baseServingUnit (default "g"),
  isCustom = 1, isDeleted = 0, createdAt/UpdatedAt = now)
nutrients = CatalogFoodNutrientEntity(foodId = generatedId, calories, proteinG, carbsG, fatG,
  fiberG = 0.0, sugarG = 0.0, sodiumMg = 0.0)
insert via insertFoodWithNutrients (atomic) → triggers populate catalog_foods_fts → return FoodItem(isCustom = true)
```

- All nutrient fields are **per 100 g** (matches `FoodItem` semantics and the editor's "per 100g" labels). Do NOT accept per-serving values with a different base without converting — the editor collects per-100g only (MVP simplification, mirrors the 3.2 `ml` 1:1 decision).
- Barcode: `null` for user foods (no scanner in scope). `is_deleted`: `0`. Timestamps: `System.currentTimeMillis()`.
- NFR-4 0.1% accuracy: `Double` throughout, no premature rounding; formatting only at display.
- FR-8 testable consequence: `is_custom = 1` AND immediately searchable — assert both in tests (fake asserts the flag; instrumented/manual check asserts the FTS round-trip).

### 2. Ranking contract (normative, FR-4)

```
ordered = dao.search(matchQuery, limit)               // bm25 ASC, name ASC — unchanged
freq = mealRepository.getMealEntries()                // profile-scoped, sync
  .flatMap { it.foods }.groupingBy { it.name.trim().lowercase() }.eachCount()
ranked = ordered.sortedWith(
  compareByDescending<FoodItem> { it.isCustom }       // customs first on ties…
    .thenByDescending { freq[it.name.trim().lowercase()] ?: 0 }  // …then by log frequency
) // stable sort preserves bm25/name order within equal (custom, freq) buckets
```

- FR-4 bar ("custom logged 5× in last week outranks seeded with same prefix score") falls out of this comparator; the test constructs exactly that fixture.
- Frequency source limitation (known): `foods` rows store free-text `name` with no `catalog_id` FK, so matching is name-normalized. Case/whitespace variants count together; this is accepted MVP behavior — document, do not add a migration.
- Performance: history is small (hundreds of rows); grouping is O(n) in memory. No per-row DAO calls. If manual on-device timing ever exceeds 50 ms average, the deferred fix is a `COUNT/GROUP BY` DAO — not this story.
- Do NOT change `FtsQueryBuilder`, the DAO `ORDER BY`, or `FoodSearchRow` for ranking. Do NOT add `LIKE` fallbacks.

### 3. UX contract (binding)

- Editor entry: zero-results state only (`No foods matching '[query]'` + `[+ Create Custom Food]`); non-empty queries with results keep the button hidden (3.2 `renderResults` logic unchanged). Pre-fill name with the query. [Source: EXPERIENCE.md#Search Autocomplete List, #State Patterns; 3.2 story §2]
- Editor fields: Name, Brand (optional), Serving size + unit (`g`/`ml`/`servings`, default 100 g), Calories/Protein/Carbs/Fat per 100 g (+ optional Fiber/Sugar/Sodium). Save → `Saved <name> to Custom Library`-style confirmation (microcopy: plain, no gamification). [Source: EXPERIENCE.md#Custom Food Editor Screen, #Flow 3, #Voice and Tone]
- New row renders two-line (name / brand + base macros) with the `Custom` chip; customs are visually distinct, never silently mixed. [Source: DESIGN.md#Food Search & Autocomplete Sheet, Do's and Don'ts]
- Save-&-Log handoff: after save, open the existing quantity modal for the new item (Flow 3 step 6: persisted + indexed + logged). [Source: EXPERIENCE.md#Flow 3; PRD #UJ-3]
- Sheet chrome unchanged: `rounded/lg` 16dp top corners, `surface-raised`, full-width input + clear `X`, hairline dividers, max height 85vh, 16dp padding, `tnum` figures, 48dp touch targets, TalkBack announcements. [Source: DESIGN.md; 3.2 story §2]

### 4. Architecture compliance guardrails

- `domain/` stays pure Kotlin — no `android.*`/`androidx.room3` imports (AD-1); entities/`FoodSearchRow` never cross `data/` — map with private extensions (AD-3).
- Synchronous only: no `suspend`/`Flow`/`runBlocking` in app code; DAO calls on main thread via `allowMainThreadQueries()` (AD-2 as amended for Room 3).
- `FoodCatalogDatabase` version stays 1: no entity change, no new table, no exported-schema churn. Customs live in `catalog_foods` (`is_custom = 1`) per the addendum table spec — no second store.
- DI centralized in `di/AppModule.kt`: `single` repos, `factory` use cases, `viewModel` VMs, injection via `by viewModel()`; Fragments clear `_binding = null` in `onDestroyView()` and observe with `viewLifecycleOwner` (AD-5/AD-6).
- Profile isolation (FR-2/NFR-5): creation writes carry no profile id (catalog is shared on-device); frequency counting reads only the active profile's meals via `MealRepository` (already profile-scoped in `MealRepositoryImpl`).
- Zero network: no HTTP imports anywhere (NFR-1).

### 5. Testing standards (must follow)

- JUnit 4 + hand-written fakes only; no MockK/Mockito/Robolectric (none in catalog — do not add for this story). `InstantTaskExecutorRule` (`androidx.arch.core:core-testing`) only for LiveData VM tests (already used in 3.2).
- When the `FoodCatalogRepository` interface grows, update every fake (lesson 2.4/2.5/3.2 — `FoodCatalogRepositoryImplTest`, `SearchFoodUseCaseTest`, `FoodSearchViewModelTest` fakes).
- Write real assertions on flags/math/ordering — never "seed the fake into the asserted state" (flagged in 3.4 review).
- Environment preflight per `AGENTS.md`: check `JAVA_HOME`/`ANDROID_HOME`, expect Java 21 Linux; `local.properties` SDK warning is harmless — do not "fix" it.
- Verify: `./gradlew testDebugUnitTest :app:assembleDebug` (+ `:app:assembleDebugAndroidTest` only if androidTest touched). No emulator runs — flag on-device checks (`<50ms` search timing, FTS round-trip of a saved custom, Save-&-Log e2e `<15s`) for manual testing.

### 6. Git intelligence / previous-story learnings

- Branch `develop`; commits `Implement Story 3.3. <desc>`; merge to `main` via PR. Story file + `sprint-status.yaml` + tests committed with code.
- **CRLF trap:** working tree shows phantom "modified" files — always stage via `git diff --ignore-cr-at-eol --name-only`; never `git add -A`.
- Koin constructor changes break every call site/test (lessons 2.4/3.2) — grep all `FoodSearchViewModel(` instantiations after adding the use-case param.
- 3.2 review patches to carry forward: decimal-comma quantity parsing, keyboard dismissal on sheet close, dialog-recreation listener fallback, overflow/non-finite guards in live previews, `food.name.isNotBlank()` guard, proper `·` separators (no mojibake), clear-button content description, recent-foods LiveData sync.
- 3.4 review lessons: non-null DI deps (fail closed), use-case layer not bypassed from ViewModels, explicit (not default-false) wipe semantics, real test assertions, `commit()` for durable prefs (N/A here — no prefs in this story).
- `.agents/project_structure.md` does not exist (deferred 3.4-D1, dropped) — do not fail over it. Architecture spine `sergeevgk.*` paths are stale — actual package is `com.example.myfoodtracker`.
- Forward-note candidates for `deferred-work.md`: any new 3.1-F1/F2 learning (customs + version bumps / two-DB backup); per-food `catalog_food_serving_units` rows for custom foods; `ml` density tables (3.2 note).

### Project Structure Notes

- New files (under `app/src/main/java/com/example/myfoodtracker/` unless noted):
  - `domain/usecase/CreateCustomFoodUseCase.kt`
  - `presentation/ui/search/CreateCustomFoodBottomSheet.kt` (or `.../CreateCustomFoodDialogFragment.kt` if the dialog pattern is chosen — pick one, do not create both)
  - Layout: `res/layout/dialog_create_custom_food.xml` (or `fragment_create_custom_food_bottom_sheet.xml` to match the chosen container)
  - Tests: `app/src/test/.../domain/usecase/CreateCustomFoodUseCaseTest.kt`, ranking test (`SearchFoodUseCaseTest` extension or new `RankedSearchFoodUseCaseTest.kt`), `FoodSearchViewModelTest` extension
- Modified: `domain/repository/FoodCatalogRepository.kt`, `data/repository/FoodCatalogRepositoryImpl.kt`, `domain/usecase/SearchFoodUseCase.kt` (or new ranked use case + Koin wiring instead), `presentation/viewmodel/FoodSearchViewModel.kt`, `presentation/ui/search/FoodSearchBottomSheet.kt`, `di/AppModule.kt`, `res/values/strings.xml` (+ colors/dimens only if new tokens needed), `deferred-work.md` (forward notes only).
- Explicitly NOT modified: `data/db/FoodCatalogDatabase.kt` (version stays 1), `data/entity/*.kt`, `data/dao/FoodCatalogDao.kt` (unless a proven-measured perf fix needs a count query), `data/dao/FtsQueryBuilder.kt`, `app/schemas/**/FoodCatalogDatabase/1.json`, `app/src/main/assets/databases/food_catalog.db`, `res/navigation/nav_graph.xml` (dialogs need no destination).
- Variances: spine structural seed shows `presentation/ui/customfood/CustomFoodEditorFragment` as the planned container — a `BottomSheetDialogFragment`/dialog under `presentation/ui/search/` next to the search sheet is the accepted equivalent (same rationale as 3.2 implementing the planned-but-missing search files in place).

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 3.3] — story statement + AC (`is_custom = 1`, FTS triggers, frequency ranking).
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/prd.md#FR-4, #FR-8, #UJ-3] — ranking bar (5 logs/week), creation consequence, Save-to-Library user journey.
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/addendum.md#1.1, #2] — `foods` table spec (`is_custom`), FTS5 DDL/trigger reference (now Room `@Fts5`).
- [Source: _bmad-output/planning-artifacts/architecture/.../ARCHITECTURE-SPINE.md#AD-1..AD-8, #Capability Map, #Structural Seed] — layering, sync access, DI, FTS engine, custom-food file layout.
- [Source: _bmad-output/planning-artifacts/ux-designs/.../DESIGN.md#Food Search & Autocomplete Sheet] — sheet + `Custom` chip spec.
- [Source: _bmad-output/planning-artifacts/ux-designs/.../EXPERIENCE.md#Flow 3, #Search Autocomplete List, #State Patterns, #Voice and Tone] — editor fields, ranking rule, zero-state, microcopy.
- [Source: _bmad-output/project-context.md] — stack (Room 3.0.3, Koin 3.5.6), sync-execution, mapping, DI, testing, resource rules.
- [Source: _bmad-output/implementation-artifacts/3-1-*.md#Dev Notes] — schema, triggers, `insertFoodWithNutrients`, 3.1-F1/F2 forward notes.
- [Source: _bmad-output/implementation-artifacts/3-2-*.md#Dev Notes, #File List, #Review Findings] — reuse inventory, macro/editor/VM precedent, review patches to carry forward, placeholder button handoff.
- [Source: _bmad-output/implementation-artifacts/deferred-work.md#3.1-F1, #3.1-F2] — no-bump and two-DB-backup constraints.
- [Source: app/src/main/java/.../FoodCatalogDao.kt] — query shape + `insertFoodWithNutrients` to reuse.
- [Source: app/src/main/java/.../FoodSearchBottomSheet.kt] — Toast placeholder to replace, `renderResults` logic to preserve.
- [Source: app/src/main/java/.../FoodSearchViewModel.kt] — `search()`/`recentFoods()` patterns to extend.

## Dev Agent Record

### Agent Model Used

Muse Spark 1.3 Contributor (opencode-go)

### Debug Log References

- AGP `mergeDebugResources`/`packageDebugResources` NPE on first build after adding layouts — same stale-merge blob as Story 3.2, resolved by `./gradlew clean` (not a code issue).
- `testDebugUnitTest` compile error: `Double?` from safe-call on captured `lastInsertedNutrients` in new repo test — fixed with `!!` assertions.
- `local.properties` SDK warning is harmless per AGENTS.md — ignored, no file change.

### Completion Notes List

- `CreateCustomFoodUseCase` owns validation (`require` style mirroring `LogQuickAddUseCase`/`LogFoodEntryUseCase`: blank name, non-finite/non-positive serving size, non-finite/negative macros across all 7 fields); trims name/brand, blanks brand→null, blanks unit→`"g"`.
- `FoodCatalogRepository.createCustomFood` + `FoodCatalogRepositoryImpl` persist via existing `@Transaction insertFoodWithNutrients` (`is_custom = 1`, barcode null, timestamps now); Room FTS triggers index immediately; generated id mapped back to `FoodItem(isCustom = true)`.
- `SearchFoodUseCase` now takes `MealRepository`: blank → empty (no history read); empty results → passthrough; seeded-only lists keep DAO `bm25()` order untouched (no history read); lists containing customs get a stable app-level re-rank (custom-first, then name-normalized log frequency). No `suspend`/`Flow`, no DAO change, no `LIKE` fallback.
- `FoodSearchViewModel.createCustomFood(...)` delegates synchronously, IAE → `error` LiveData, returns `FoodItem?`; constructor + Koin `viewModel` extended with the new use case (only 4 call sites total: AppModule ×2, 2 test files).
- `CreateCustomFoodDialogFragment` (`MaterialAlertDialogBuilder` + `setPositiveButton` override, decimal-comma parsing, per-field `TextInputLayout` errors, name prefill from search query, default serving 100 g) + `dialog_create_custom_food.xml` (ScrollView, `tnum` numerics, existing color/dimen tokens only).
- `FoodSearchBottomSheet` Toast placeholder replaced with the editor; on save: `viewModel.search(lastQuery)` refresh, Snackbar `Saved <name> to Custom Library` + `announceForAccessibility` (`a11y_custom_food_saved`), Save-&-Log handoff via existing `onFoodSelected` → quantity modal; `DashboardFragment` needed zero changes.
- Tests: 192 JVM tests green (168 pre-existing + 24 new: 11 `CreateCustomFoodUseCaseTest`, 5 ranking in `SearchFoodUseCaseTest`, 2 `FoodCatalogRepositoryImplTest` create, 3 `FoodSearchViewModelTest` create, plus fake updates); `assembleDebug` green; androidTest untouched. No emulator — on-device timing (`<50ms` re-ranked search, FTS round-trip of a saved custom, Save-&-Log e2e `<15s`) flagged for manual testing.
- Task 6: no new 3.1-F1/F2 learning — customs stored in `catalog_foods` exactly as foreseen, no version bump, no `deferred-work.md` change needed.

### File List

New:
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/CreateCustomFoodUseCase.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/search/CreateCustomFoodDialogFragment.kt`
- `app/src/main/res/layout/dialog_create_custom_food.xml`
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/CreateCustomFoodUseCaseTest.kt`

Modified:
- `app/src/main/java/com/example/myfoodtracker/domain/repository/FoodCatalogRepository.kt`
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/SearchFoodUseCase.kt`
- `app/src/main/java/com/example/myfoodtracker/data/repository/FoodCatalogRepositoryImpl.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/FoodSearchViewModel.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/search/FoodSearchBottomSheet.kt`
- `app/src/main/java/com/example/myfoodtracker/di/AppModule.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/SearchFoodUseCaseTest.kt`
- `app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/FoodSearchViewModelTest.kt`
- `app/src/test/java/com/example/myfoodtracker/data/repository/FoodCatalogRepositoryImplTest.kt`
