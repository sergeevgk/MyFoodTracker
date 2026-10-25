---
story_id: 6.1
story_key: 6-1-unify-atomic-room-transactions
epic: 6
status: done
created: 2026-10-25
baseline_commit: 7fee17042735dbaf77040d24d5776cb22c976e4b
---

# Story 6.1: Unify Atomic Room Transactions for Multi-Table Writes

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user,
I want multi-step data writes to either fully succeed or fully fail,
So that a crash or disk error can never leave my logs half-recorded.

## Acceptance Criteria

1. **Given** `MealRepositoryImpl.logQuickAdd`, **When** it writes a meal and its foods, **Then** both rows are committed by a single Room `@Transaction` DAO method (resolves **2.4-D1**, MealRepositoryImpl.kt:78-89).
2. **Given** `MealRepositoryImpl.logFoodEntry`, **When** it writes a meal and its foods, **Then** the same single `@Transaction` DAO method is used (resolves **3.2-D1**, MealRepositoryImpl.kt:115-126).
3. **Given** `MealRepositoryImpl.restoreMealEntry` (undo path), **When** it re-inserts a meal and its foods, **Then** the write is atomic — a failure restores nothing, not a bare meal row (resolves **2.5-D1**, MealRepositoryImpl.kt:140-153).
4. **Given** `RecipeRepositoryImpl.saveRecipe`, **When** it writes the recipe and its ingredients, **Then** insertion is wrapped in one `@Transaction` method on the recipe DAO (resolves **4.1-D4**, RecipeRepositoryImpl.kt:23-47).
5. **Given** the codebase after this story, **When** reviewed, **Then** no repository method performs two dependent DAO inserts outside a `@Transaction`, and the deferred entries 2.4-D1 / 2.5-D1 / 3.2-D1 / 4.1-D4 are annotated **RESOLVED** in deferred-work.md.

## Tasks / Subtasks

- [x] Task 1: Add `@Transaction` composite DAO methods (AC: #1–#4)
  - [x] `MealDao`: add one default-body interface method `insertMealWithFoods(meal: MealEntryEntity, foods: List<FoodEntity>)` annotated `@Transaction`, whose body calls the existing `insertMeal(meal)` then `insertFoods(foods)`. Do **not** create per-shape variants — all three meal write paths (`logQuickAdd`, `logFoodEntry`, `restoreMealEntry`) reduce to "one meal + N foods" and one signature serves them all.
  - [x] `RecipeDao`: add equivalent `@Transaction` method `insertRecipeWithIngredients(recipe: RecipeEntity, ingredients: List<RecipeIngredientEntity>)` whose body calls the existing `insertRecipe` then `insertIngredients`.
  - [x] Keep the four primitive methods (`insertMeal`, `insertFoods`, `insertRecipe`, `insertIngredients`) — they are the insert engines inside the transaction wrapper and are used by `RecipeDaoTest` (androidTest) and test fakes.
- [x] Task 2: Repository refactor (AC: #1–#4, #5)
  - [x] `MealRepositoryImpl.logQuickAdd` (lines 78-89): replace `mealDao.insertMeal(...)` + `mealDao.insertFoods(...)` with a single `mealDao.insertMealWithFoods(meal, listOf(food))`.
  - [x] `MealRepositoryImpl.logFoodEntry` (lines 115-126): same replacement.
  - [x] `MealRepositoryImpl.restoreMealEntry` (lines 140-153): replace the pair with a single `mealDao.insertMealWithFoods(meal, foods)`; preserve the exact REPLACE/parent-id semantics (comment on line 132 stays accurate).
  - [x] `RecipeRepositoryImpl.saveRecipe` (lines 23-47): replace the pair with a single `recipeDao.insertRecipeWithIngredients(recipe, mappedIngredients)`.
  - [x] `addMealEntry` (line 42) does a single-table insert — leave as-is. `AppModule.kt` wiring needs **no** change (DAO injections unchanged, only new method on existing DAO interface).
- [x] Task 3: Tests (AC: #1–#4 red → green)
  - [x] `FakeMealDao` / `FakeRecipeDao` in the existing JVM test files implement the DAO interfaces. If the new composite methods are default-body interface methods, the fakes inherit the transaction wrapper automatically and **compile untouched** — verify this at compile time; add tracking only where a test needs to prove delegation (below).
  - [x] Extend `MealRepositoryImplTest`: for each of `logQuickAdd`, `logFoodEntry`, `restoreMealEntry`, add/adjust one assertion proving the transactional method is used — e.g. fake counters `transactionalInsertCalls` (incremented in an overridden default method) vs `directInsertMealCalls` (incremented in the primitive override) — assert callers went through the composite (`transactionalInsertCalls == 1`, `directInsertMealCalls == 0`).
  - [x] Extend `RecipeRepositoryImplTest.saveRecipe_*` with the same delegation guard for `insertRecipeWithIngredients`.
  - [x] Extend `RecipeDaoTest` (androidTest): one round-trip test through `insertRecipeWithIngredients` proving recipe + ingredients land in the same transaction (mirror `insertAndQuery_roundTripsRecipeWithIngredients`; build/compile only — see Testing rules).
  - [x] Run and green: `./gradlew testDebugUnitTest` — all pre-existing tests must pass (see Regression Watch).
- [x] Task 4: Register update (AC: #5)
  - [x] Annotate 2.4-D1, 2.5-D1, 3.2-D1, 4.1-D4 **RESOLVED** in `_bmad-output/implementation-artifacts/deferred-work.md` (follow the "Resolved by" pattern of story 2-6 / 5-1 entries in that file; update this story's own section reference at the end).

### Review Findings

- [x] [Review][Defer] Missing instrumented test coverage for MealDao.insertMealWithFoods [app/src/main/java/com/example/myfoodtracker/data/db/MealDao.kt:29] — deferred, pre-existing (no MealDaoTest exists in androidTest; on-device verification covered under manual testing / no-emulator policy)

## Dev Notes

### Current state of the code (verified, baseline commit 7fee170)

`MealRepositoryImpl` (app/src/main/java/com/example/myfoodtracker/data/repository/MealRepositoryImpl.kt) contains three dependent write pairs, all on a real `MealDao`:

| Path | Fed parent row | Fed child rows | Today's calls |
|------|----------------|----------------|---------------|
| `logQuickAdd` (58-91) | `MealEntryEntity` (`title = slot`, `weight=0` food) | 1 × `FoodEntity` | `insertMeal` (78) → `insertFoods` (89) |
| `logFoodEntry` (93-128) | `MealEntryEntity` (`title = slot`) | 1 × `FoodEntity` (real `weight`) | `insertMeal` (115) → `insertFoods` (126) |
| `restoreMealEntry` (130-155) | re-inserts **the same `entry.id`** | 0..N × `FoodEntity` | `insertMeal` (140) → `insertFoods` (153) |

`RecipeRepositoryImpl.saveRecipe` (19-49) does `recipeDao.insertRecipe(...)` (23) → `recipeDao.insertIngredients(...)` (32) for a fresh `UUID.randomUUID()` recipe id.

All three MealRepositoryImpl paths then end with `return getMealEntriesByDate(date)` — this read-back-and-return behavior is part of the established contract, keep it exactly as is.

**What this story changes:** only the two-call sequences become one `@Transaction` DAO call. **What must be preserved:**
- `UUID.randomUUID()` meal/recipe id allocation in the repository (DAO stays id-agnostic).
- `@Insert(onConflict = OnConflictStrategy.REPLACE)` semantics — `restoreMealEntry` relies on REPLACE to re-insert the exact deleted row id (`MealDao.kt:19-23`). The composite method must inherit this via the primitives, do not change conflict strategies.
- FK shape: `FoodEntity.mealId` → parent `meals` id, `onDelete = ForeignKey.CASCADE` + index on `mealId` (FoodEntity.kt:11-18). Parent-first insert order is already what the primitives produce; keep it.
- Slot validation (`require(slot in setOf("BREAKFAST", ...))`) and the no-active-profile early-return behavior (writes nothing) on every path.
- Signature, return types and the trailing `getMealEntriesByDate(...)` read of all four repository methods. **Neither repository interface nor any use-case or ViewModel changes** — grep confirms repositories are the only DAO callers for these tables (deleteRecipe/deleteIngredient/meal titles cascade unchanged).

### Implementation guidance (make these exact decisions)

- **Design decision — one generic composite, not per-shape variants.** Quick-add, food-entry and restore differ only in the food row construction (weight 0 vs real weight, 1 vs N foods), which stays in the repository. The DAO only needs the shape `(meal, foods)`. Cascading FKs and REPLACE cover the restore shape automatically.
- **Room 3 default interface method + `@Transaction` is the chosen pattern.** Kotlin bytecode-level default implementations calling annotated primitives are the documented multi-operation transaction approach in Room ("Methods annotated with `@Transaction`, if they wrap multiple DAO operations, run inside a transaction"). The generated override wraps the default body in `beginTransaction…endTransaction`. Room 3 permits blocking (non-suspend) DAO methods in an Android-only module — this project already relies on that under AD-2 (architecture spine, AD-2 "Room 3 amendment"; verified in story 3.1). Do **NOT** add `suspend`, do **NOT** use `RoomDatabase.withWriteTransaction` / `Transactor` — those APIs are coroutine-based and violate AD-2 (project-context.md "Synchronous Execution").
- **DAO SQL isolation rule:** everything that touches SQL lives in the DAO interfaces (project-context.md "DAO SQL Isolation"). The composite methods introduce no new SQL — they delegate to the annotated primitives, so the rule holds.
- **No schema change:** entities, `AppDatabase` version (1), and `app/schemas/` stay untouched — no migration needed.
- **Koin:** no wiring change; `MealDao`/`RecipeDao`/repositories are launched with the same constructors in `AppModule.kt` (lines 69-101).
- Do not touch reads (`getMealsWithFoods*`, `getRecipesWithIngredients*`) — they are already `@Transaction`-annotated for the `@Relation` projections.
- `MealDao.kt` uses the wildcard import `androidx.room3.*`; `RecipeDao.kt` imports annotations explicitly (Dao, Insert, OnConflictStrategy, Query, Transaction). Follow each file's existing import style.
- `restoreMealEntry`'s "Parent first (FK order), same id so REPLACE re-inserts the deleted row exactly" comment (MealRepositoryImpl.kt:132) must remain true after the refactor.

### Testing requirements (per project-context.md testing rules + AGENTS.md WSL/emulator policy)

- JVM tests use **hand-written Fakes, not MockK** — `MealRepositoryImplTest.FakeMealDao` and `RecipeRepositoryImplTest.FakeRecipeDao` implement the DAO interfaces and a `FakeSessionRepository`. Keep this established pattern; despite project-context.md mentioning MockK, the uniform convention in this repo is fakes — do not introduce MockK here.
- Default-body DAO methods keep the fakes working via inherited default implementations (the fake's overridden primitives get called — matching Room behavior, which is exactly what delegation tests want).
- Helper for delegation tests: override the composite method in the fakes to record the call, then call super/forward to primitives (Kotlin interface default method: `override fun insertMealWithFoods(meal: MealEntryEntity, foods: List<FoodEntity>) { transactionalInsertCalls++; <fake-impl> }` — or record-and-default using the same body pattern).
- **Never** use a concrete DB component in JVM tests (project-context.md). Real atomicity under concurrent failure is a Room runtime guarantee, exercised only in `androidTest`.
- **No emulator policy (AGENTS.md):** do not run `connectedDebugAndroidTest`. Verify with Linux-side Gradle (WSL environment — `./gradlew`, not `gradlew.bat`):
  - `./gradlew compileDebugSources`
  - `./gradlew testDebugUnitTest`
  - `./gradlew assembleDebug :app:assembleDebugAndroidTest` (compiles the extended `RecipeDaoTest` without running it).
  - If the build fails with an SDK error, follow the AGENTS.md temp-swap procedure (`local.properties` → `$HOME/Android/Sdk`, restore immediately, never commit).
- Explicitly state in the completion notes that transaction atomicity re-verification on device is deferred to manual testing (no-emulator policy).

### Regression watch (must stay green)

- `MealRepositoryImplTest` (385 lines): all 15 tests, especially `restoreMealEntry_reinsertsSameIdAndFoods`, `logQuickAdd_historicalDate_writesToGivenDateNotToday`, profile/date isolation suite.
- `RecipeRepositoryImplTest`: `saveRecipe_persistsRecipeAndIngredients_scopedToProfile` and the `getRecipes_noActiveProfile_returnsEmpty_neverTouchesDao` guard.
- `RecipeDaoTest` (androidTest): existing three tests compile and run unchanged under the new interface methods.
- Use-case tests (`RestoreMealEntryUseCaseTest`, `SaveRecipeUseCaseTest`) — no signature changes, but they exercise these repo paths; keep untouched and green.

### Previous-epic / git intelligence

- Story 6.x is opening Epic 6 (sprint-status: epic-6 backlog → in-progress on story creation; first story).
- Git log: last 5 commits are Story 5.2 (backup/restore), 5.1 (export), 4.2, 4.1, 3.5 — repo convention: single "Implement Story X.Y" commit per story, manual fakes, repository-level delegation tests, JVM-verification-only for Android components.
- Deferred-work.md register is the source of truth; entries 2.4-D1 / 2.5-D1 / 3.2-D1 / 4.1-D4 each carry the exact original line citations matching today's code (verified above — the line refs in the AC header text have been refreshed against the current file; use the AC-cited line numbers in this file, not the older deferred-work numbers).
- Cross-story check (do NOT scope-creep): 6.2 (custom-food ID remap after restore) and 6.3 (catalog migration / backup v2) are **separate** follow-up stories — none of their catalog/backup behavior belongs in this story. `deleteRecipe`/`deleteIngredient` cascade paths and enum-less slot strings are out of scope. Story 8.1 (`LocalDate.now()` hardcoding in `addMealEntry`, line 33) is also explicitly out of scope here.

### Project Structure Notes

- All edits stay inside the established Clean-Architecture layering (project-context.md): DAO changes in `data/db/`, repository changes in `data/repository/`, test changes in `app/src/test/.../data/repository/` and `app/src/androidTest/.../data/`. No domain/ or presentation/ changes.
- `MealDao.kt` sits in `data/db/` (26 lines); `RecipeDao.kt` similarly (35 lines). Add the composite methods adjacent to their primitives.
- No new files. No new Gradle dependencies (functional fakes exist; no MockK, no arch core testing needed).

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Epic 6, Story 6.1] (epic statement + AC wording)
- [Source: _bmad-output/implementation-artifacts/deferred-work.md#2.4-D1, #2.5-D1, #3.2-D1, #4.1-D4] (original register entries)
- [Source: _bmad-output/planning-artifacts/architecture/architecture-MyFoodTracker-2026-09-06/ARCHITECTURE-SPINE.md#AD-2] (synchronous Room rule + Room 3 blocking-DAO amendment)
- [Source: _bmad-output/project-context.md#Critical Implementation Rules / Testing Rules / Development Workflow Rules]
- [Source: developer.android.com/training/data-storage/room/accessing-data — DAO transactions, `@Transaction`, Room 3 blocking-DAO constraints]
- [Source: app/src/main/java/com/example/myfoodtracker/data/repository/MealRepositoryImpl.kt, RecipeRepositoryImpl.kt, data/db/MealDao.kt, RecipeDao.kt, FoodEntity.kt, di/AppModule.kt] (verified current code)
- [Source: app/src/test/java/com/example/myfoodtracker/data/repository/MealRepositoryImplTest.kt, RecipeRepositoryImplTest.kt; app/src/androidTest/.../data/RecipeDaoTest.kt] (test conventions to extend)

## Dev Agent Record

### Agent Model Used

GLM-5.3-Flash Free (opencode) via bmad-dev-story workflow, 2026-10-25

### Debug Log References

- RED run (`testDebugUnitTest --tests ...repository.*`): 58 tests, 4 new delegation tests failed as expected (`logQuickAdd/logFoodEntry/restoreMealEntry_writesThroughTransactionalCompositeMethod`, `saveRecipe_writesThroughTransactionalCompositeMethod`); 54 pre-existing tests passed.
- First GREEN attempt: 4 failures — fake composite overrides forwarded to the counting primitives (double-counted `directInsert*Calls`) and the restore test's own setup (logQuickAdd) passed through the composite. Fixed by inlining fake-impl state changes in the composite overrides and asserting a delta on the restore test. All green on rerun.
- Final full runs: `./gradlew testDebugUnitTest` (58/58 pass), `./gradlew compileDebugSources`, `./gradlew assembleDebug`, `./gradlew :app:assembleDebugAndroidTest` — all BUILD SUCCESSFUL.

### Completion Notes List

- **What was implemented:** Added one generic `@Transaction` default-body composite per DAO — `MealDao.insertMealWithFoods(meal, foods)` and `RecipeDao.insertRecipeWithIngredients(recipe, ingredients)` — delegating to the existing REPLACE-annotated primitives (no new SQL, no schema change, no DI/signature changes, AD-2 synchronous rule respected: no `suspend`, no `withWriteTransaction`).
- **Repository refactor:** `logQuickAdd`, `logFoodEntry`, `restoreMealEntry` now call `insertMealWithFoods`; `saveRecipe` calls `insertRecipeWithIngredients`. `addMealEntry` (single-table insert) intentionally left as-is. UUID id allocation, REPLACE semantics, parent-first FK order, slot validation, no-active-profile early returns, and trailing `getMealEntriesByDate`/`getRecipes` read-backs all preserved; the restore path's "Parent first (FK order), same id so REPLACE re-inserts the deleted row exactly" comment remains accurate.
- **Tests (red → green):** New delegation guards in `MealRepositoryImplTest` (3) and `RecipeRepositoryImplTest` (1) proved red against the old two-call code, green after refactor. Fakes override the composite to count `transactionalInsertCalls` and inline the same state changes the primitives make, so `directInsertMealCalls`/`directInsertRecipeCalls` count only direct primitive calls (0 asserted). `restoreMealEntry` guard asserts a +1 delta since its setup also uses the composite. Fakes compile untouched otherwise (inherited default bodies), confirming the Task 3 prediction.
- **androidTest:** Added `insertRecipeWithIngredients_roundTripsRecipeAndIngredients` to `RecipeDaoTest` (mirrors the existing round-trip); compiled via `assembleDebugAndroidTest` but NOT executed — see manual-testing notice below.
- **Manual testing notice (no-emulator policy):** Real transaction atomicity (rollback on failure mid-composite) is a Room runtime guarantee and cannot be exercised by JVM tests or compiled-only androidTests. On-device verification of `RecipeDaoTest` (4 tests incl. the new one) and of quick-add / food-entry logging / swipe-delete-undo / recipe-save flows is explicitly deferred to manual testing.
- **Deferred register:** 2.4-D1, 2.5-D1, 3.2-D1, 4.1-D4 annotated RESOLVED inline and consolidated in a new "Resolved by: story 6-1-unify-atomic-room-transactions (2026-10-25)" section in deferred-work.md, following the story 2-6 / 5-1 pattern.
- AC #5 verified structurally by grep: the only remaining direct primitive insert call in repositories is `addMealEntry`'s single-table `insertMeal` (nothing to make atomic); no two dependent inserts exist outside a `@Transaction` anywhere in the codebase.

### File List

- app/src/main/java/com/example/myfoodtracker/data/db/MealDao.kt (modified — added `@Transaction insertMealWithFoods`)
- app/src/main/java/com/example/myfoodtracker/data/db/RecipeDao.kt (modified — added `@Transaction insertRecipeWithIngredients`)
- app/src/main/java/com/example/myfoodtracker/data/repository/MealRepositoryImpl.kt (modified — 3 write paths through composite)
- app/src/main/java/com/example/myfoodtracker/data/repository/RecipeRepositoryImpl.kt (modified — saveRecipe through composite)
- app/src/test/java/com/example/myfoodtracker/data/repository/MealRepositoryImplTest.kt (modified — counters in FakeMealDao + 3 delegation tests)
- app/src/test/java/com/example/myfoodtracker/data/repository/RecipeRepositoryImplTest.kt (modified — counters in FakeRecipeDao + 1 delegation test)
- app/src/androidTest/java/com/example/myfoodtracker/data/RecipeDaoTest.kt (modified — added composite round-trip test)
- _bmad-output/implementation-artifacts/deferred-work.md (modified — 4 entries RESOLVED + new "Resolved by" section)
- _bmad-output/implementation-artifacts/sprint-status.yaml (modified — story status tracking)
- _bmad-output/implementation-artifacts/6-1-unify-atomic-room-transactions.md (modified — story tracking sections)

### Change Log

- 2026-10-25: Story 6.1 implemented — unified all four multi-table repository writes (quick-add, food-entry, restore, recipe-save) behind single `@Transaction` DAO composite methods; added 4 JVM delegation tests (red→green) and 1 androidTest round-trip; resolved deferred entries 2.4-D1 / 2.5-D1 / 3.2-D1 / 4.1-D4 in deferred-work.md. No regressions (58/58 unit tests pass); builds compile clean.
