---
story_id: 6.2
story_key: 6-2-remap-custom-food-ids-after-restore
epic: 6
status: done
created: 2026-10-25
baseline_commit: 36b31b56a1c581e061a48aab45688b38364df35b
---

# Story 6.2: Remap Custom-Food IDs in recipe_ingredients After Restore

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user,
I want my recipes to still reference the correct custom foods after a database restore,
So that restoring a backup does not silently corrupt recipe macro data.

## Acceptance Criteria

1. **Given** a restore that re-inserts custom foods with freshly allocated IDs into the catalog database (the 5.2 story path via `FoodCatalogDao.replaceCustomFoods`), **When** the re-insert completes, **Then** every `recipe_ingredients.catalog_food_id` row that referenced a re-inserted custom food is remapped to the new ID — the old→new mapping is built **during re-insertion** (a backup-side rounded-trip id is carried per restored custom food) and consumed **before the first recipe read** (resolves deferred item **5.2-D1**).
2. **Given** a `recipe_ingredient` whose referenced catalog ID has no corresponding food in the rebuilt catalog — e.g. its mapped old ID had no matching re-inserted custom food (the food was deleted before the backup was made), or it is a legacy pre-6.2 backup without carried ids and the referenced id no longer exists — **Then** the link is **nulled explicitly** (`catalog_food_id = NULL`), never left dangling. Chosen decision: *null*, not *remove* — the ingredient row's macro/name snapshots stay intact and the recipe keeps its ingredients.
3. **Given** `recipe_ingredients.catalog_food_id` values that **do** resolve to valid catalog rows (seeded foods, or a coincidentally-equal re-allocated id), **When** the remap runs, **Then** those rows are left untouched.
4. **Given** the remap implementation, **When** an update is applied per mapping entry, **Then** it is immune to **value-chaining**: affected `recipe_ingredients` rows are captured by **primary key** (snapshot of target rows taken before any write), so mapping entry `A→B` can never cause rows already updated to `B` to be caught again by a later entry `B→C`.
5. **Given** the staged restore directory (`filesDir/pending_restore/`), **Then** it is excluded from platform auto-backup via `backup_rules.xml` (API < 31) **and** `data_extraction_rules.xml` (cloud-backup **and** device-transfer, API ≥ 31) — resolves deferred item **5.2-F4** (res/xml change; manifest already wires both files).
6. **Given** the codebase after this story, **When** reviewed, **Then** deferred entries **5.2-D1** and **5.2-F4** are annotated **RESOLVED** in `_bmad-output/implementation-artifacts/deferred-work.md`.
7. **Given** a restore performed with this story's code, **Then** the validation decision table and `FORMAT_VERSION = 1` are **unchanged** — backups created by pre-6.2 app versions still restore (read path degrades gracefully), and backups created by this version are still accepted by pre-6.2 app versions. No live-schema change: `AppDatabase` version stays `4`, `FoodCatalogDatabase` version stays `1`, `app/schemas/` untouched, no entity column changes in the live databases.

## Tasks / Subtasks

- [x] Task 1: Carry the backup-side catalog id in the backup format (AC: #1, #7)
  - [x] `domain/model/CustomFoodBackup.kt`: add field `val sourceCatalogId: Long? = null` (nullable, defaulted — keeps every existing construction site compiling; the JVM default makes the use-case/validator side untouched).
  - [x] `DatabaseBackupManager.kt` — **backup side**: in `readCustomFoods()` set `sourceCatalogId = food.id` (the `CatalogFoodEntity.id` from `foodCatalogDao.getCustomFoods()` is already in hand — this is the "old id" the restored `recipe_ingredients` rows reference). In `CREATE_CUSTOM_FOODS_SQL` append the column `, source_catalog_id INTEGER` (nullable, no `NOT NULL`, placed after the last existing column). In `insertCustomFood` add the 16th bound parameter. Keep `FORMAT_VERSION` and the `myfoodtracker_backup_meta` row set untouched (AC #7): the column is additive and probe-compatible, and story 6.3's "format_version becomes 2" scope (serving units) is **not** pre-empted.
  - [x] `PendingRestoreApplier.kt` — **restore side**: `readCustomFoods(database)` first attempts the existing column list **plus** `source_catalog_id`; if that `rawQuery` throws `SQLiteException` (a pre-6.2 backup whose `myfoodtracker_custom_foods` has no such column), fall back to the legacy 15-column read with `sourceCatalogId = null`. Keep the outer existing catch-and-emptyList semantics ("staged backup carries no readable custom foods") for the *table itself* being absent — a missing id **column** must degrade to `null` ids, not empty list. Cite this two-tier fallback in a comment. (The backup made by this version always has the column, so the happy path never takes the fallback.)
- [x] Task 2: Capture the old→new mapping during re-insertion (AC: #1)
  - [x] NEW `data/db/RestoredCustomFood.kt` (pure data row, `FoodSearchRow` precedent): `data class RestoredCustomFood(val food: CatalogFoodEntity, val nutrient: CatalogFoodNutrientEntity, val sourceId: Long?)`.
  - [x] `FoodCatalogDao.kt`: re-shape the single-caller transaction to return the mapping —
    ```kotlin
    /** Consumed only by the restore path; the applier needs sourceId→newId to remap recipe_ingredients. */
    @Transaction
    fun replaceCustomFoods(foods: List<RestoredCustomFood>): List<Pair<Long, Long>> {
        deleteCustomFoods()
        val mapping = mutableListOf<Pair<Long, Long>>()
        foods.forEach { item ->
            val id = insertFood(item.food)
            insertNutrients(item.nutrient.copy(foodId = id))
            item.sourceId?.let { mapping.add(it to id) }
        }
        return mapping
    }
    ```
    Old custom foods are deleted by the existing `deleteCustomFoods()` (nutrient children go by FK CASCADE — never by hand). The new ids are allocated by `@Insert(onConflict = REPLACE) insertFood` with `id = 0` → SQLite auto-generation — **never** reuse the backed-up ids (5.2 invariant kept). Grep-verified (baseline 36b31b5): `PendingRestoreApplier.applyCustomFoods` is the **only** production caller of `replaceCustomFoods`, so the signature change breaks nothing else; the `FakeFoodCatalogDao` in `FoodCatalogRepositoryImplTest` inherits the default body and compiles untouched.
  - [x] `FoodCatalogDao.kt`: add `@Query("SELECT id FROM catalog_foods WHERE id IN (:ids)") fun getExistingFoodIds(ids: List<Long>): List<Long>` — used by the dangling pass with the (small) candidate list; never load the whole ~10k-row seed id space.
- [x] Task 3: Chain-safe remap engine (AC: #1, #2, #3, #4)
  - [x] NEW `data/db/RecipeIngredientLinkRow.kt` (projection row, data layer only): `data class RecipeIngredientLinkRow(val id: Long, @ColumnInfo(name = "catalog_food_id") val catalogFoodId: Long)`.
  - [x] `RecipeDao.kt` additions:
    ```kotlin
    @Query("SELECT id, catalog_food_id FROM recipe_ingredients WHERE catalog_food_id IS NOT NULL")
    fun getIngredientsWithCatalogLinks(): List<RecipeIngredientLinkRow>

    @Query("UPDATE recipe_ingredients SET catalog_food_id = :newFoodId WHERE id IN (:ingredientIds)")
    fun updateCatalogFoodLink(ingredientIds: List<Long>, newFoodId: Long)

    @Query("UPDATE recipe_ingredients SET catalog_food_id = NULL WHERE id IN (:ingredientIds)")
    fun clearCatalogFoodLink(ingredientIds: List<Long>)

    /** Applies the restore-time remap in one transaction (row-id based — chain-safe; see Dev Notes §Remap engine). */
    @Transaction
    fun remapAndClearCatalogFoodLinks(remaps: Map<Long, List<Long>>, clearedIngredientIds: List<Long>) {
        remaps.forEach { (newFoodId, ingredientIds) -> updateCatalogFoodLink(ingredientIds, newFoodId) }
        if (clearedIngredientIds.isNotEmpty()) clearCatalogFoodLink(clearedIngredientIds)
    }
    ```
    (`updateRecipeName` is not affected; `recipe_ingredients` has no FK to `catalog` — the id is a soft cross-database reference by design, which is exactly why it goes stale.)
  - [x] NEW `data/backup/RecipeIngredientLinkRemapper.kt` — pure-orchestration, DAO-only class (no `Context`, JVM-testable with fakes):
    ```kotlin
    /**
     * Makes restored recipe_ingredients links survive the fresh id allocation of a
     * restore. Remap first (authoritative mapping from the backup), then a dangling
     * sweep against the rebuilt catalog.
     */
    class RecipeIngredientLinkRemapper(
        private val recipeDao: RecipeDao,
        private val catalogDao: FoodCatalogDao
    ) {
        fun remapAfterRestore(mapping: List<Pair<Long, Long>>) {
            // Capture target rows BEFORE any write so mapping entry A→B can never
            // re-catch rows that a later entry B→C has just written (chain-safety).
            val links = recipeDao.getIngredientsWithCatalogLinks()
            if (links.isEmpty()) return

            val mappingByOld = mapping.associate { it }
            val remaps = linkedMapOf<Long, MutableList<Long>>() // newId → ingredient row ids
            val candidateIds = linkedSetOf<Long>()
            links.forEach { link ->
                val newFoodId = mappingByOld[link.catalogFoodId]
                if (newFoodId != null && newFoodId != link.catalogFoodId) {
                    remaps.getOrPut(newFoodId) { mutableListOf() }.add(link.id)
                } else {
                    // Unmapped: either a legacy/id-less backup reference or a valid
                    // seeded reference — decide via the rebuilt catalog (Task 2 helper).
                    candidateIds.add(link.catalogFoodId)
                }
            }
            if (candidateIds.isNotEmpty()) {
                val existing = catalogDao.getExistingFoodIds(candidateIds.toList()).toSet()
                val cleared = candidateIds.filterNot { it in existing }
                recipeDao.remapAndClearCatalogFoodLinks(remaps, cleared)
            } else if (remaps.isNotEmpty()) {
                recipeDao.remapAndClearCatalogFoodLinks(remaps, emptyList())
            }
        }
    }
    ```
    Keep the `candidateIds`-only probing (never `SELECT id FROM catalog_foods` wholesale — AC-scoped note in Task 2). Constructed on the fly by the applier (stateless); **not** a Koin binding (see Dev Notes §Wiring).
- [x] Task 4: Wire into the cold-start restore (AC: #1, #2)
  - [x] `PendingRestoreApplier.applyCustomFoods(foodCatalogDao: FoodCatalogDao, recipeDao: RecipeDao, foods: List<CustomFoodBackup>)`: build `RestoredCustomFood(food, nutrient, sourceCatalogId)` pairs (re-using the current `CatalogFoodEntity`/`CatalogFoodNutrientEntity` construction verbatim), run `foodCatalogDao.replaceCustomFoods(...)` → mapping, then `RecipeIngredientLinkRemapper(recipeDao, foodCatalogDao).remapAfterRestore(mapping)` — the remap consumes the mapping **before the method returns**, i.e. before any UI exists to read recipes.
  - [x] `MainApplication.onCreate`: resolve the extra dependency — `restoreApplier.applyCustomFoods(koinApplication.koin.get<FoodCatalogDao>(), koinApplication.koin.get<RecipeDao>(), restoredCustomFoods)`. `RecipeDao` is already a Koin `single` (`AppModule.kt:70`), and opening it does not touch the catalog asset (it opens the already-swapped main DB — correct post-Koin timing).
  - [x] Failure semantics preserved: `MainApplication`'s existing `try/catch (Log.e)` around `applyCustomFoods` stays — a remap failure logs and proceeds with a normal startup (possibly with stale links; content snapshots keep the app correct), never crashes (5.2 invariant: a failing restore must never crash the app on startup).
- [x] Task 5: Exclude the staging directory from platform auto-backup (AC: #5)
  - [x] `res/xml/backup_rules.xml`: inside `<full-backup-content>` add
    ```xml
    <exclude domain="file" path="pending_restore/" />
    ```
  - [x] `res/xml/data_extraction_rules.xml`: add the same `<exclude domain="file" path="pending_restore/" />` inside BOTH `<cloud-backup>` and `<device-transfer>`; replace the placeholder comments only where needed. `domain="file"` = `filesDir` (where `PendingRestoreApplier.stagingDirectory()` lives); the restore **candidate** (`cacheDir/restore/`) and the cached backup (`cacheDir/backups/`) need no exclusion — the system never auto-backs-up `cacheDir`. Do not touch `file_paths.xml` / the `FileProvider` scope (5.2 kept backups out of the provider on purpose).
- [x] Task 6: Tests (AC: #1–#3 red → green)
  - [x] NEW `app/src/test/java/com/example/myfoodtracker/data/backup/RecipeIngredientLinkRemapperTest.kt` — hand-written `FakeRecipeDao` + `FakeFoodCatalogDao` (repo convention: fakes, no MockK). Required cases:
    1. `remapAfterRestore_reallocatedFood_rewritesExistingRows` — ingredient rows referencing old id 3001/3002; mapping `[(3001→9001),(3002→9002)]`; assert exact final `catalog_food_id` per row (row-id asserted, not count).
    2. `remapAfterRestore_chainScenario_neverDoubleMaps` — rows `{id1→3001, id2→3050}` with mapping `[(3001→3050),(3050→3060)]`: snapshot capture must yield `{id1→3050, id2→3060}`; a naive sequential `WHERE catalog_food_id = :old` implementation would produce `{id1→3060, id2→3060}` — this test is the AC #4 regression guard.
    3. `remapAfterRestore_danglingReference_setToNull` — a row referencing id 9999 absent from `getExistingFoodIds` → `catalog_food_id` NULL (AC #2; resolves 5.2-D1's "deleted before backup" case).
    4. `remapAfterRestore_validSeededReference_leftUntouched` — a row referencing seeded id 5 that exists in the rebuilt catalog → unchanged (AC #3).
    5. `remapAfterRestore_legacyBackupWithoutIds_danglingNulledSeededKept` — empty mapping: pre-6.2 backup case; only the dangling branch acts.
    6. `remapAfterRestore_noLinkedIngredients_makesNoWrites` — empty links → zero DAO writes (early-out).
    7. `remapAfterRestore_mappingMatchesAllocatedId_noOp` — mapping `[(3001→3001)]` (same-device-same-asset allocation): row untouched (the `!=` branch), no spurious writes.
  - [x] Fake updates forced by interface changes (keep them minimal & mechanical):
    - `FoodCatalogRepositoryImplTest.FakeFoodCatalogDao`: add `getExistingFoodIds` override (return `tracked ids ∩ requested`, or trivially `emptyList()` — but tests in Task 6's remapper fake need meaningful behavior there; both fakes exist independently).
    - `RecipeRepositoryImplTest.FakeRecipeDao`: add stubs for `getIngredientsWithCatalogLinks` (empty), `updateCatalogFoodLink`, `clearCatalogFoodLink`, and the `remapAndClearCatalogFoodLinks` default (overridden to record) — the new `@Query` primitives are abstract and the fake must compile.
    - Also confirm `FakeMealDao` and any other `RecipeDao`/`FoodCatalogDao` implementers still compile (grep `: RecipeDao` / `FoodCatalogDao` in test sources before building).
  - [x] androidTest (build-compile only, policy below): extend `RecipeDaoTest` with `remapAndClearCatalogFoodLinks_roundTripsRemapAndClear` (mirror the 6.1 composite round-trip pattern) — compiled via `:app:assembleDebugAndroidTest`, executed on device manually.
  - [x] Run green: `./gradlew testDebugUnitTest` — all pre-existing tests stay green (see Regression Watch). New tests must be shown RED first against the pre-task code (or justified as new-file pure additions when the original code did not have the remapper to fail against).
- [x] Task 7: Register update (AC: #6)
  - [x] Annotate **5.2-D1** and **5.2-F4** **RESOLVED** in `_bmad-output/implementation-artifacts/deferred-work.md` inline AND add a consolidated "Resolved by: story 6-2-remap-custom-food-ids-after-restore (2026-10-25)" section, following the story 2-6 / 5-1 / 6-1 pattern in that file. Keep **5.2-F1** (`catalog_food_serving_units` not backed up — story 6.3) untouched/open, and explicitly note in it that the backup format change made here (`source_catalog_id` column, format version still 1) does **not** pre-empt 6.3's v2 for serving units.

### Review Findings

- [x] [Review][Patch] Avoid empty Room database transaction in `RecipeIngredientLinkRemapper` when no links are remapped or cleared [`app/src/main/java/com/example/myfoodtracker/data/backup/RecipeIngredientLinkRemapper.kt:72`]
- [x] [Review][Patch] Simplify two-tier fallback and broaden exception handling in `PendingRestoreApplier.readCustomFoods` [`app/src/main/java/com/example/myfoodtracker/data/backup/PendingRestoreApplier.kt:188`]
- [x] [Review][Patch] Clarify diagnostic log message in `MainApplication.kt` when restore application or remap fails [`app/src/main/java/com/example/myfoodtracker/MainApplication.kt:47`]
- [x] [Review][Patch] Update story subheading status from `ready-for-dev` to `review` [`_bmad-output/implementation-artifacts/6-2-remap-custom-food-ids-after-restore.md:12`]

## Dev Notes

### Current state of the code (verified, baseline commit 36b31b5 = Story 6.1 merged)

`PendingRestoreApplier.applyCustomFoods` (`data/backup/PendingRestoreApplier.kt:81-108`) re-inserts each backed-up custom food as a fresh `CatalogFoodEntity(isCustom = 1)` through `FoodCatalogDao.replaceCustomFoods` (`data/dao/FoodCatalogDao.kt:80-87`), which `deleteCustomFoods()`s then `insertFood(...)`s each row and **discards the Long returned by `insertFood`** — the root cause of 5.2-D1. `RecipeIngredientEntity.catalogFoodId` (`data/db/RecipeIngredientEntity.kt:24`) is already `Long?` and has **no FK constraint** to the catalog (different database!), so nothing protects the reference.

The restore pipeline's order (must be preserved):
1. Pre-Koin, `MainApplication` → `PendingRestoreApplier.applyPendingDatabaseSwap()`: staged file prepared (journal-mode flip, integrity check, `myfoodtracker_custom_foods` rows read, both backup tables dropped) → live `meals_database` file replaced including sidecar deletion → remembered session cleared → staging dir deleted. Returns `List<CustomFoodBackup>?` (`null` restores nothing; a restore with an empty list still replaces custom foods per AC #7 consistency — keep).
2. Post-Koin, only `if (restoredCustomFoods != null)`: `applyCustomFoods(foodCatalogDao, foods)` — this story's remap lands inside this second phase.

The backup file (`DatabaseBackupManager.CREATE_CUSTOM_FOODS_SQL`, lines 559-577) currently carries **15 columns and no id**. That is *why* the mapping cannot be built today — the "old id" the restored `recipe_ingredients` reference is only known to `foodCatalogDao.getCustomFoods().id` **on the backup-writing device**, so it must travel in the backup (Task 1's `source_catalog_id`).

### Remap engine — why row-id capture (put this in the code review, not just code)

Naive implementation — `@Query("UPDATE recipe_ingredients SET catalog_food_id = :new WHERE catalog_food_id = :old")` per mapping entry — is **wrong** in two ways:
1. **Chaining**: after applying `3001→3050`, a later entry `3050→3060` matches the *just-updated* rows and double-advances them (_recipe rows that really referenced old custom food 3050 get moved too — and then some rows get moved twice_). Restored `recipe_ingredients` can legitimately contain references to ids that other mapping entries target, because a re-insert's fresh id can equal *another* food's old id whenever the catalog asset on the restoring device has differerent seed row counts.
2. **Cross-target contamination**: `WHERE catalog_food_id = :old` also matches rows that never referenced the deleted custom food but were written post-restore with the fresh id — impossible here (remap runs before any UI write) but the snapshot discipline makes that reasoning unnecessary because it is structurally safe.

Snapshot discipline (AC #4): read the affected rows' **primary keys first**, then write by `id IN (...)`. Captured-row writes are keyed on `RecipeIngredientEntity.id` (autoGenerate PK) and cannot be re-matched by an id *value* that another update later produces.

**Bound-variable cap**: `candidateIds` (and each `remaps` group) feed `IN (...)` clauses. SQLite's default limit is 999 bound variables per statement. A legacy restore over a large recipe library could in theory exceed it — chunk `candidateIds` in batches of **500** when calling `getExistingFoodIds(ids)` (mapped ids are per-food groups, small by construction, but a defensive `.chunked(500)` on `candidateIds.toList()` before the existence query is required; collect the batch results into one set). Keep `updateCatalogFoodLink`/`clearCatalogFoodLink` per-batch within the same cap.

### Order-of-operations subtleties (make these exact decisions)

- **Remap Before dangling sweep, interleaved data**: remap consumes the authoritative mapping first; the dangling pass then evaluates unmapped rows against the **rebuilt** catalog (`getExistingFoodIds(candidateIds)` runs after `replaceCustomFoods` committed). Both passes are inside `remapAfterRestore`, executed on the main thread at `Application.onCreate`, before any Fragment/ViewModel could read recipes — satisfying "before the first recipe read".
- **Same-id mapping entries**: if `newFoodId == link.catalogFoodId` (same device + same catalog asset ⇒ allocation repeats), skip the write (branch `!=`). The dangling sweep must still *not* fire for them — they are valid ids of the re-inserted food. The Task 6 skeleton handles this by only adding to `remaps` when `newFoodId != link.catalogFoodId`, *and* treating mapped rows as decided (they are not added to `candidateIds`).
- **Legacy pre-6.2 backups** (`sourceCatalogId == null` for every food): mapping empty → all rows go to `candidateIds`; valid ids survive, stale ids are nulled. This is a **graceful degradation**, consistent with AC #2/#7 — old backups already produced silently-dangling links (5.2-D1 behavior); this story makes them explicit NULLs and covers them with the same sweep.
- **Residual limitation (known, out of scope)**: a restored `recipe_ingredients.catalog_food_id` that collides with a *different, existing* catalog row (e.g. old-device **seeded** id re-used by a different food in a newer catalog asset version) cannot be detected by content — the seeded catalog is not backed up by design (5.2). Content snapshots (name/macros in the ingredient row) keep every recipe **log** correct regardless; only the soft link may be wrong. Do **not** attempt a content-based match — it is heuristic and out of scope; defer to future work if a real device case surfaces.
- **NULL, not REMOVE (AC #2 decision)**: `LogRecipeUseCase` (`domain/usecase/LogRecipeUseCase.kt:48-60`) forwards each ingredient's snapshot fields (`foodName`, `quantityGrams`, macros) — it never dereferences `catalogFoodId`. A nulled link loses nothing at log time; removing the row would silently shrink user-authored recipes. Nulling is the information-preserving choice.

### What this story changes vs. what must be preserved

**Changes:** `FoodCatalogDao.replaceCustomFoods` signature/return (single caller), the backup-only `myfoodtracker_custom_foods` CREATE/INSERT (backup file only, not the live schema), `CustomFoodBackup` model, `PendingRestoreApplier` read/write paths + new remap call, `MainApplication` resolution, `RecipeDao` new members, two `res/xml` rule files, plus one new data row class + one new backup class + one new test file.

**Preserved (regression guards):**
- The two-database discipline: the main DB is a **file** swap via platform SQLite, the catalog is only ever written through the Room DAO (`FoodCatalogDao`) with the `BundledSQLiteDriver` (AD-8 FTS5) — the remap's recipe-side writes go through `RecipeDao` (main DB, Room), never through `SQLiteDatabase`. No cross-DB SQL joins (impossible + against DAO SQL isolation rule).
- `replaceCustomFoods` semantics: delete-then-insert, FK-cascade nutrient cleanup, REPLACE insert semantics, fresh-id allocation — only the *return mapping* is new.
- `applyPendingDatabaseSwap` behavior untouched (validation→swap→session clear→discard, exception-swallowing logging).
- `BackupDatabaseUseCase` decision table, `FORMAT_VERSION = 1`, `EXPECTED_USER_VERSION = 4`, `BackupFacts`/`BackupValidation` models, and `BackupDatabaseUseCaseTest` — untouched.
- Entity classes: **no schema change**, no entity column change (the `sourceCatalogId` lives only in `CustomFoodBackup` + backup file). `AppDatabase` version 4, `FoodCatalogDatabase` version 1, `app/schemas/**` untouched — verify no KSP schema regen diff appears in `git status` after building.
- `AppModule.kt`: **no wiring change** — `RecipeDao` is already a `single`; the remapper is constructed inline (AD-6 exception, same as the applier itself, but inverted: post-Koin here — inline construction is fine because it is stateless and needs no context).
- The no-emulator policy (AGENTS.md): JVM tests + compile/build checks via Linux-side Gradle (`./gradlew`, **not** `gradlew.bat` — opencode runs in Ubuntu WSL; if an SDK error appears, follow the AGENTS.md temp-swap procedure for `local.properties` and restore it immediately).
- `MainActivity`/dashboard reading recipes later must see remapped ids — no caching layer exists over `RecipeDao` reads (grep: only `RecipeRepositoryImpl.getRecipes`/`getRecipeById`), so once remap finishes, reads are consistent. No `Room.invalidation` concerns.

### Testing requirements (per project-context.md + repo convention)

- JVM tests: **hand-written fakes, not MockK** (established convention in `MealRepositoryImplTest`/`RecipeRepositoryImplTest`/`FoodCatalogRepositoryImplTest`; project-context.md mentions MockK but the uniform pattern here is fakes — do not introduce MockK).
- `RecipeIngredientLinkRemapper` is pure-orchestration: its tests need no Android, no Robolectric, no Room — just fakes recording calls.
- `PendingRestoreApplier.readCustomFoods` (platform `SQLiteDatabase`) cannot be JVM-tested (5.2-F2 precedent); cover it with (a) a compile check via `./gradlew compileDebugSources`, (b) the *unit-testable* degradation intent expressed by the remapper test #5 (legacy/id-less case), and (c) manual device verification (explicitly deliverable — see Completion Notes requirement).
- `PendingRestoreApplier.applyCustomFoods` logic **is now testable** if you want extra credit — but the Android `Context`-holding class still makes a JVM test heavy; the remapper class carries all logic worth testing. Do not attempt to JVM-test the applier itself.
- androidTest: per 6.1 precedent, add one round-trip test, compile via `:app:assembleDebugAndroidTest`, do **not** run `connectedDebugAndroidTest` (no emulator). State in completion notes that on-device verification of the remap (a real restore carrying id-linked recipes) is deferred to manual testing per policy.
- Verification commands (WSL): `./gradlew compileDebugSources` → `./gradlew testDebugUnitTest` → `./gradlew assembleDebug :app:assembleDebugAndroidTest`.

### Regression watch (must stay green)

- `BackupDatabaseUseCaseTest` (validation decision table — untouched but adjacent).
- `FoodCatalogRepositoryImplTest` (fake compiles after `getExistingFoodIds` override; search/create paths untouched).
- `RecipeRepositoryImplTest` (fake compiles with new stubs; save/log paths untouched).
- `RecipeDaoTest` (androidTest) — existing tests compile unchanged; only an addition, not a modification.
- Anything relying on `CustomFoodBackup` construction elsewhere — grep found exactly four usage files (`MainApplication.kt`, `CustomFoodBackup.kt`, `PendingRestoreApplier.kt`, `DatabaseBackupManager.kt`); the defaulted new field keeps all four compiling.
- Story 6.1's delegation-guard tests (`MealRepositoryImplTest`, `RecipeRepositoryImplTest`): untouched and green.

### Project Structure Notes

- New files: `data/db/RestoredCustomFood.kt`, `data/db/RecipeIngredientLinkRow.kt` (projection-row precedent: `data/db/FoodSearchRow.kt` "Flat query projection, data layer only."), `data/backup/RecipeIngredientLinkRemapper.kt`, and the JVM test `app/src/test/java/com/example/myfoodtracker/data/backup/RecipeIngredientLinkRemapperTest.kt` (new package under test — mirrors `data/backup`).
- Modified files: `data/dao/FoodCatalogDao.kt`, `data/db/RecipeDao.kt`, `data/backup/PendingRestoreApplier.kt`, `data/backup/DatabaseBackupManager.kt`, `domain/model/CustomFoodBackup.kt`, `MainApplication.kt`, `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`, plus two test fakes and `deferred-work.md`.
- No changes to: `RecipeIngredientEntity` (column already exists), any entity, `AppDatabase`/`FoodCatalogDatabase` versions, `AppModule.kt`, `BackupDatabaseUseCase`, `SettingsViewModel`/`SettingsFragment` (the UI restore flow is already fully built in 5.2 and does not observe remap details), `AndroidManifest.xml` (both rule files are already wired via `android:fullBackupContent`/`android:dataExtractionRules`).
- Layering: everything stays in `data/` (+ one domain-model field with default). No new Android imports in `domain/`.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Epic 6, Story 6.2] (AC wording; "old→new mapping built during re-insertion, before the first recipe read"; "removed or nulled explicitly — never left dangling"; 5.2-F4 reference)
- [Source: _bmad-output/implementation-artifacts/deferred-work.md#5.2-D1, #5.2-F4, #5.2-F1] (original register entries + open 6.3-scope note)
- [Source: _bmad-output/implementation-artifacts/6-1-unify-atomic-room-transactions.md] (precedent: default-body `@Transaction` composites, red→green JVM guards, androidTest build-only policy, register-resolution pattern)
- [Source: _bmad-output/implementation-artifacts/5-2-offline-full-database-backup-and-restore.md] (restore pipeline design invariants: fresh ids never reused, REPLACE collision rationale, no-`SQLiteDatabase` on catalog, `applyCustomFoods` post-Koin timing, backup-only tables dropped from staged copy)
- [Source: _bmad-output/project-context.md#Critical Implementation Rules / Testing Rules] (synchronous DAO rule — no `suspend`/coroutines; DAO SQL isolation; fakes; manual-testing notice requirement)
- [Source: developer.android.com/guide/topics/data/autobackup] (`full-backup-content` `<exclude domain="file" path="...">`; `data-extraction-rules` `<cloud-backup>`/`<device-transfer>` scopes — stable API since 31/build AAGP 9.2.1, no version drift)
- [Source: app/src/main/java/com/example/myfoodtracker/data/backup/PendingRestoreApplier.kt (whole file), DatabaseBackupManager.kt (whole file)] (verified current code, baseline 36b31b5)
- [Source: app/src/main/java/com/example/myfoodtracker/data/dao/FoodCatalogDao.kt, data/db/RecipeDao.kt, data/db/RecipeIngredientEntity.kt, data/entity/CatalogFoodEntity.kt, data/db/FoodSearchRow.kt, domain/model/CustomFoodBackup.kt, MainApplication.kt, di/AppModule.kt] (verified current code)
- [Source: app/src/main/res/xml/backup_rules.xml, data_extraction_rules.xml, main/AndroidManifest.xml (lines 7-9)] (files this story edits, current state: placeholder/sample content)
- [Source: app/src/test/java/com/example/myfoodtracker/data/repository/FoodCatalogRepositoryImplTest.kt (FakeFoodCatalogDao), .../RecipeRepositoryImplTest.kt (FakeRecipeDao pattern), domain/usecase/LogRecipeUseCase.kt:48-60] (test + consumption conventions)

## Dev Agent Record

### Agent Model Used

GLM-5.3-Flash Free (opencode)

### Debug Log References

- `./gradlew :app:compileDebugSources :app:compileDebugUnitTestSources` — first run RED: `RecipeIngredientLinkRemapperTest` did not compile (`filterNotNullValues` unresolved on a `MutableMap<Long, Long?>`); fixed by an explicit `entries.filter { it.value != null }` projection (test-fake-side compile issue, no product change).
- `./gradlew testDebugUnitTest` — second run RED with 4 failures confined to the new remapper tests: the Task 3 skeleton's `cleared = candidateIds.filterNot { it in existing }` passed dangling **food ids** where `clearCatalogFoodLink` expects **ingredient row ids** (`UPDATE … WHERE id IN (:ingredientIds)`). Real bug against AC #2 semantics (one dangling food id can be referenced by several ingredient rows). Fixed in the remapper (see Completion Notes); third run GREEN.
- One wrong assertion in my own test (`validSeededReference` expected a write where untouched rows correctly produce zero writes) — corrected the test.
- Final: `./gradlew :app:testDebugUnitTest` GREEN (all pre-existing suites + 8 new tests); `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest` GREEN; `git diff` over `app/schemas/` empty (AC #7).
- Environment: WSL Ubuntu Gradle per AGENTS.md; `JAVA_HOME`/`ANDROID_HOME` verified, no `local.properties` swap needed (its Windows path warning is the known harmless case).

### Completion Notes List

**Manual testing notice (no-emulator policy):** the SQLite-layer restore behaviors this story extends — `PendingRestoreApplier.readCustomFoods` two-tier column fallback (new-format vs legacy-backup reads), the full cold-start `applyCustomFoods` + remap sequence, and the androidTest `RecipeDaoTest.remapAndClearCatalogFoodLinks_roundTripsRemapAndClear` — cannot be exercised by JVM tests or compiled-only androidTests, and `connectedDebugAndroidTest` / emulator runs are forbidden by project policy. On-device verification of these paths (restore a 6.2-made backup; restore a pre-6.2-made backup; a recipe whose ingredient references a deleted-before-backup custom food) is explicitly deferred to manual testing.

**Implementation decisions worth review attention:**
1. **Deliberate deviation from the Task 3 skeleton (the red→green catch):** the skeleton computed `cleared = candidateIds.filterNot { it in existing }` and passed that **food-id list** as `remapAndClearCatalogFoodLinks`'s `clearedIngredientIds`, which are **ingredient primary keys** (`UPDATE recipe_ingredients … WHERE id IN`). Implemented correctly: unmapped links are grouped `foodId → [ingredientRowId]`, existence is probed on the distinct food ids (`.chunked(500)`), and only the rows pointing at missing foods are flattened into the cleared **row-id** list. The JVM fakes apply true DAO semantics and caught this immediately — the AC #4/regression guard worked as designed. Chain-safety and all other skeleton logic are implemented as specified.
2. **Bound-variable cap:** chunking lives in two places — the remapper probes `getExistingFoodIds` in batches of 500 (distinct food ids only), and the `RecipeDao.remapAndClearCatalogFoodLinks` default body chunks every `updateCatalogFoodLink`/`clearCatalogFoodLink` write group by `RecipeDao.BOUND_VARIABLE_BATCH = 500` (companion const; Room/KSP accepted).
3. **Same-id mapped entries** (`newFoodId == link.catalogFoodId`) are decided without any write and are *not* probed in the dangling sweep (test `mappingMatchesAllocatedId_noOp` asserts zero DAO calls, existence probes included).
4. **Two-tier read:** `readCustomFoods(database)` first tries the 16-column list (+ `source_catalog_id`), falls back to the legacy 15-column read with `sourceCatalogId = null` on `SQLiteException`; only the *table* being absent yields `emptyList()`. The two-tier fallback is cited in the KDoc.
5. **Backup format:** `source_catalog_id INTEGER` is additive/nullable, appended after `updated_at`; `FORMAT_VERSION`, meta row, and the validation decision table untouched (AC #7 verified: `BackupDatabaseUseCaseTest` green, no schema diff in `git status`).
6. **Other fakes:** `FakeMealDao` does not implement `RecipeDao` (grep of `: RecipeDao` / `: FoodCatalogDao` implementers before building) — only `RecipeRepositoryImplTest.FakeRecipeDao` (minimal recording stubs) and `FoodCatalogRepositoryImplTest.FakeFoodCatalogDao` (records the probe query, returns a configurable result) needed new members; the fake in `FakeFoodCatalogDao` inherits the default `replaceCustomFoods` body and compiled untouched.
7. `RecipeIngredientLinkRemapper` is constructed inline by the applier and consumed before `applyCustomFoods` returns (post-Koin, pre-UI), preserving the 5.2 restore-pipeline order; `MainApplication`'s try/catch semantics preserved — a remap failure logs and proceeds with a normal startup, never crashing (5.2 invariant).

### File List

- `app/src/main/java/com/example/myfoodtracker/MainApplication.kt` (modified — passes `RecipeDao` into `applyCustomFoods`)
- `app/src/main/java/com/example/myfoodtracker/data/backup/DatabaseBackupManager.kt` (modified — `source_catalog_id` column, 16th bound parameter, `sourceCatalogId = food.id`)
- `app/src/main/java/com/example/myfoodtracker/data/backup/PendingRestoreApplier.kt` (modified — two-tier `readCustomFoods`, `applyCustomFoods(foodCatalogDao, recipeDao, foods)` + remap call)
- `app/src/main/java/com/example/myfoodtracker/data/backup/RecipeIngredientLinkRemapper.kt` (NEW)
- `app/src/main/java/com/example/myfoodtracker/data/dao/FoodCatalogDao.kt` (modified — `replaceCustomFoods(List<RestoredCustomFood>)` returns the mapping; `getExistingFoodIds`)
- `app/src/main/java/com/example/myfoodtracker/data/db/RecipeDao.kt` (modified — link projection, update/clear primitives, `remapAndClearCatalogFoodLinks`, `BOUND_VARIABLE_BATCH`)
- `app/src/main/java/com/example/myfoodtracker/data/db/RecipeIngredientLinkRow.kt` (NEW)
- `app/src/main/java/com/example/myfoodtracker/data/db/RestoredCustomFood.kt` (NEW)
- `app/src/main/java/com/example/myfoodtracker/domain/model/CustomFoodBackup.kt` (modified — `sourceCatalogId: Long? = null`)
- `app/src/main/res/xml/backup_rules.xml` (modified — exclude `pending_restore/`)
- `app/src/main/res/xml/data_extraction_rules.xml` (modified — exclude `pending_restore/` in cloud-backup and device-transfer)
- `app/src/androidTest/java/com/example/myfoodtracker/data/RecipeDaoTest.kt` (modified — `remapAndClearCatalogFoodLinks_roundTripsRemapAndClear`)
- `app/src/test/java/com/example/myfoodtracker/data/backup/RecipeIngredientLinkRemapperTest.kt` (NEW — 8 cases)
- `app/src/test/java/com/example/myfoodtracker/data/repository/FoodCatalogRepositoryImplTest.kt` (modified — fake gains `getExistingFoodIds`)
- `app/src/test/java/com/example/myfoodtracker/data/repository/RecipeRepositoryImplTest.kt` (modified — fake gains remap members)
- `_bmad-output/implementation-artifacts/deferred-work.md` (modified — 5.2-D1 / 5.2-F4 annotated RESOLVED + consolidated section; 5.2-F1 note)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (modified — story status transitions)
- `_bmad-output/implementation-artifacts/6-2-remap-custom-food-ids-after-restore.md` (story file itself)

## Change Log

- 2026-10-25: Implemented story 6.2 — carried `source_catalog_id` in the backup format (additive, `FORMAT_VERSION` still 1); `FoodCatalogDao.replaceCustomFoods` now returns the old→new id mapping during re-insertion; new chain-safe `RecipeIngredientLinkRemapper` rewrites mapped `recipe_ingredients.catalog_food_id` links and nulls dangling ones by primary key before the first recipe read; wired into the post-Koin cold-start path via `MainApplication`; `filesDir/pending_restore/` excluded from platform auto-backup (API < 31 and ≥ 31 rules); 5.2-D1 and 5.2-F4 marked RESOLVED in `deferred-work.md`. Note: implemented one deliberate deviation from the Task 3 skeleton — dangling rows are cleared by ingredient **row id**, not by dangling food id (see Completion Notes #1). Tests: 8 new JVM cases (red→green; the fakes caught the skeleton bug), fake updates in 2 existing suites, 1 androidTest round-trip addition (compile-only per the no-emulator policy).

- 2026-10-25: Code review completed. Applied 4 patches (empty transaction guard in RecipeIngredientLinkRemapper, two-tier fallback simplification and broader exception catch in PendingRestoreApplier, improved startup log in MainApplication, status alignment). All tests green. Story status transitioned to done.

## Status

Status: done
