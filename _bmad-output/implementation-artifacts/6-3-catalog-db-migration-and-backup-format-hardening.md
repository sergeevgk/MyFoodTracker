---
story_id: 6.3
story_key: 6-3-catalog-db-migration-and-backup-format-hardening
epic: 6
status: done
created: 2026-10-25
baseline_commit: e7a3545edb846f0f817f0e2282c0c300dbe61a44 (e7a3545 = Story 6.2 merged)
---

# Story 6.3: Catalog DB Migration & Backup-Format Hardening

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user,
I want a catalog app update (or a future backup format) to never delete my custom foods or their serving units,
So that my food library survives app upgrades and round-trips through backup/restore.

## Acceptance Criteria

1. **Given** any future `FoodCatalogDatabase` version bump, **When** the app opens the catalog database after the bump, **Then** a **destructive fallback can no longer happen silently**: `fallbackToDestructiveMigration()` is removed from the catalog builder (it stays on the main `meals_database` builder — that is the project-context rule) and an explicit migration path is required instead, so a bump without a proper `Migration` fails fast at first open rather than wiping every `is_custom = 1` row (and the ~50k seed rows) via the destructive re-copy of the bundled asset (resolves **3.1-F1**, `di/AppModule.kt` flagged in the register).
2. **Given** the future version bump, **When** the migration is written, **Then** it is built from a shipped, ready-to-use preservation helper: `is_custom = 1` `catalog_foods` rows **plus their `catalog_food_nutrients` and `catalog_food_serving_units` rows are preserved *in place* (same primary key ids)**, so restored `recipe_ingredients.catalog_food_id` links stay valid without any remap, FTS is re-indexed only through Room's `room_fts_content_sync_*` triggers (never write `catalog_foods_fts` directly), and no orphaned nutrient/unit children remain.
3. **Given** the backup format, **When** a backup is produced by this version, **Then** `myfoodtracker_backup_meta.format_version = 2` and the file additionally carries the `catalog_food_serving_units` rows of the custom foods, keyed by the backup-side catalog id (`source_catalog_id`, story 6.2's carried id) — the seeded catalog is still not backed up (resolves **5.2-F1**, `data/backup/DatabaseBackupManager.kt` flagged in the register).
4. **Given** restore validation (the `BackupErrorReason` decision table from story 5.2), **When** a v1 backup is validated, **Then** it remains valid (the format check accepts `format_version ∈ {1, 2}`); a v1 file simply has no serving-unit data to restore. A missing or unknown version (v0, v3, …) is still rejected with `UNSUPPORTED_BACKUP_VERSION`.
5. **Given** a restore performed with this story's code, **When** the restore completes, **Then** a v2 backup's serving-unit rows are applied to the rebuilt catalog in the same `@Transaction` re-insertion as the foods themselves, and the restored custom foods keep their serving units (format round-trip proven by tests at the DAO layer; see Dev Notes §Testing for what is JVM-/device-testable today).
6. **Given** the codebase after this story, **When** reviewed, **Then** register entries **3.1-F1** and **5.2-F1** are annotated **RESOLVED** in `_bmad-output/implementation-artifacts/deferred-work.md` (with a consolidated "Resolved by" section in the established 2-6/5-1/6-1/6-2 pattern).
7. **Given** the discipline bought in stories 3.1/5.2/6.2, **Then** no live schema changes in this story: `AppDatabase` version stays `4`, `FoodCatalogDatabase` version stays `1` (**no version bump happens in this story**), `app/schemas/**` untouched, no entity columns changed, and backups made by pre-6.3 app versions (v1, with or without `source_catalog_id`) still restore. Forward compatibility is explicitly *not* guaranteed: a pre-6.3 app reading a v2 backup fails cleanly with `UNSUPPORTED_BACKUP_VERSION` (accepted; that is the explicit-format-version contract from 5.2).

## Tasks / Subtasks

- [x] Task 1: Catalog migration guard — no silent destructive fallback (AC: #1, #7)
  - [x] `di/AppModule.kt` (catalog DB builder ONLY): replace `.fallbackToDestructiveMigration()` with `.addMigrations(*CatalogMigrations.ALL)` and a KDoc paragraph: *catalog schema is seeded from a bundled asset and extended by user data (`is_custom = 1` rows); a destructive fallback would re-copy or blank the asset and kill custom foods — a future version bump MUST ship an explicit `Migration`, and `CatalogCustomFoodPreserver` does the preservation work*. The main DB builder above keeps `fallbackToDestructiveMigration()` (project-context rule, untouched).
  - [x] NEW `data/db/CatalogMigrations.kt`: `object CatalogMigrations { val ALL: Array<Migration> = arrayOf() }` plus `object CatalogCustomFoodPreserver` — see Dev Notes §Preservation helper for the exact shape and SQL steps. Empty `ALL` compiles fine with `addMigrations(vararg)`; the guard is behavioral (fail-fast), not a registered migration.
  - [x] Verify the builder still compiles & opens: `./gradlew compileDebugSources` + existing suites (nothing in production code calls a catalog migration today, so the guard is invisible at version 1 — that is expected).
- [x] Task 2: Preservation helper for the future bump (AC: #2)
  - [x] In `CatalogMigrations.kt`, implement `CatalogCustomFoodPreserver.preserveThenRestore(connection: SQLiteConnection, rebuild: () -> Unit)` (pure kotlinx-free, no Android imports; Room 3 gives migrations an `androidx.sqlite.SQLiteConnection`) — snapshot → `rebuild()` → re-insert, with ids carried **in place**:
    1. `CREATE TEMP TABLE _catalog_custom_snapshot AS SELECT * FROM catalog_foods WHERE is_custom = 1`
    2. `CREATE TEMP TABLE _catalog_custom_nutrients AS SELECT n.* FROM catalog_food_nutrients AS n JOIN _catalog_custom_snapshot AS s ON s.id = n.food_id`
    3. `CREATE TEMP TABLE _catalog_custom_serving_units AS SELECT u.* FROM catalog_food_serving_units AS u JOIN _catalog_custom_snapshot AS s ON s.id = u.food_id`
    4. `rebuild()` (drop & recreate all four catalog tables from the **new** exported schema DDL, incl. FTS table + `room_fts_content_sync_*` triggers — copy them from `app/schemas/com.example.myfoodtracker.data.db.FoodCatalogDatabase/<new>.json`; the triggers are part of the exported schema, never hand-written).
    5. `INSERT INTO catalog_foods SELECT * FROM _catalog_custom_snapshot` (explicit id column copy → custom foods keep their ids → `recipe_ingredients.catalog_food_id` stays valid, **no 6.2-style remap needed for app updates**).
    6. `INSERT INTO catalog_food_nutrients SELECT * FROM _catalog_custom_nutrients`; same for `_catalog_custom_serving_units`.
    7. `DROP TABLE IF EXISTS` the three temp tables (finally / `use`-safe).
    - [x] KDoc must carry the step-by-step "how to write MIGRATION_N_(N+1)" recipe (object : Migration(N, N+1) { override suspend fun migrate(connection: SQLiteConnection) { CatalogCustomFoodPreserver.preserveThenRestore(connection) { /* new-schema DDL */ } } }).
  - [x] NEW androidTest `data/CatalogCustomFoodPreserverTest.kt` — raw-driver round trip (no Room): open an in-memory connection via `BundledSQLiteDriver().open(":memory:")` (fall back to a temp file path via `ApplicationProvider` context if the in-memory URI form is rejected), create the four v1-shaped tables + both `room_fts_content_sync_*` triggers verbatim from `app/schemas/.../1.json`, insert a custom food + nutrient + two serving units plus seeded rows, run `preserveThenRestore(connection) { drop/recreate same DDL }`, and assert: custom rows and **their ids** survive, seeded rows are gone after rebuild (the migration rebuild is the future dev's responsibility — the helper only guarantees custom survival), nutrient/unit children re-attached, temp tables dropped. Compiled via `:app:assembleDebugAndroidTest`; executed on device manually per policy (Dev Notes §Testing).
- [x] Task 3: Backup format v2 — write side (AC: #3)
  - [x] `domain/usecase/BackupDatabaseUseCase.kt`: `FORMAT_VERSION = 1 → 2` and add `val SUPPORTED_FORMAT_VERSIONS = listOf(1, 2)` (keeps `FORMAT_VERSION` as the writer's version; decision table change in Task 5). `EXPECTED_USER_VERSION`, meta keys, `REQUIRED_TABLES` untouched.
  - [x] `DomainModel`: NEW `domain/model/ServingUnitBackup(val unitName: String, val gramsPerUnit: Double)` (pure Kotlin, AD-1) and `CustomFoodBackup` gains `val servingUnits: List<ServingUnitBackup> = emptyList()` (nullable-defaulted — every existing construction site compiles; grep-verified sites: `MainApplication.kt`, `CustomFoodBackup.kt`, `PendingRestoreApplier.kt`, `DatabaseBackupManager.kt`).
  - [x] `FoodCatalogDao.kt`: add
    ```kotlin
    /** Serving-unit rows of the user's own foods — carried by backup v2 (story 6.3). */
    @Query("SELECT * FROM catalog_food_serving_units " +
        "WHERE food_id IN (SELECT id FROM catalog_foods WHERE is_custom = 1)")
    fun getCustomFoodServingUnits(): List<CatalogFoodServingUnitEntity>

    @Insert
    fun insertServingUnits(units: List<CatalogFoodServingUnitEntity>)
    ```
    (plain `@Insert` — ids are fresh `id = 0`; no REPLACE semantics needed; children go by the existing FK CASCADE when a food is deleted — nothing to clean by hand.)
  - [x] `DatabaseBackupManager.readCustomFoods()`: after the nutrients lookup, also read `foodCatalogDao.getCustomFoodServingUnits()` (guard with `if (foods.isEmpty()) return emptyList()` as today), group by `foodId`, and set `servingUnits = units[food.id].orEmpty().map { ServingUnitBackup(it.unitName, it.gramsPerUnit) }` on each backup row.
  - [x] `DatabaseBackupManager` (backup-only schema): add third backup table `CREATE_SERVING_UNITS_TABLE_SQL = CREATE TABLE IF NOT EXISTS myfoodtracker_custom_serving_units (source_catalog_id INTEGER NOT NULL, unit_name TEXT NOT NULL, grams_per_unit REAL NOT NULL)` — created in `appendBackupTables` next to the other two; do **not** carry the serving-unit row `id` (autoGenerate value has no consumer; restore re-allocates). `execSQL` inside the existing transaction: after `customFoods.forEach { insertCustomFood(...) }` write `customFoods.flatMap { it.servingUnits.map { u -> Triple(it.sourceCatalogId, u.unitName, u.gramsPerUnit) } }` — **skip rows whose `sourceCatalogId == null`** (defensive; a v2 backup always carries ids unless constructed manually) with a one-line comment. Write unit rows in the same `database.beginTransaction()` block so a partial v2 file is impossible.
  - [x] **Do not extend `BackupFacts`**: `format_version = 2` alone declares the file may carry unit rows; a v1 file has none. Extra count/fact checks would re-open the decision table for zero benefit.
- [x] Task 4: Restore side (AC: #4, #5, #7)
  - [x] `PendingRestoreApplier.prepareStagedFile`: extend the strip-down transaction to `DROP TABLE IF EXISTS myfoodtracker_custom_serving_units` (the third backup-only table; `IF EXISTS` keeps pre-5.2/v1 candidates working verbatim). Read the unit rows **before** the drop.
  - [x] `PendingRestoreApplier`: NEW private `readCustomServingUnits(database: SQLiteDatabase): List<ServingUnitBackupFoodKey>`-style helper — try `SELECT source_catalog_id, unit_name, grams_per_unit FROM myfoodtracker_custom_serving_units`; catch `SQLiteException` → `emptyList()` + `Log.w` (**a missing table is the v1 case and must degrade silently**, unlike 6.2's `readCustomFoods` where a missing *column* re-threw one tier: here there is no second tier — the table is simply absent in every pre-6.3 file). Model the intermediate as a tiny private `data class(val sourceCatalogId: Long, val unitName: String, val gramsPerUnit: Double)` inside the applier (data-layer-only projection row).
  - [x] `PendingRestoreApplier.applyCustomFoods`: attach to each `RestoredCustomFood(...)` the `servingUnits = units.filter { it.sourceCatalogId == food.sourceCatalogId }.map { CatalogFoodServingUnitEntity(foodId = 0, unitName = ..., gramsPerUnit = ...) }` — **only** for foods whose `sourceCatalogId != null`; units of a food with no carried id have no owner and are dropped (log the dropped count once at the end, `Log.w`, never crash — 5.2 invariant: a restore never crashes on startup).
  - [x] `FoodCatalogDao.replaceCustomFoods`: inside the existing `@Transaction`, after `insertNutrients(...)` insert `item.servingUnits.map { it.copy(foodId = id) }` via `insertServingUnits(...)` when non-empty; the returned mapping and `RestoredCustomFood.food/nutrient/sourceId` construction stay byte-for-byte identical (the 6.2 remap consumes it unchanged).
- [x] Task 5: Validation decision table accepts {1, 2} (AC: #4)
  - [x] `BackupDatabaseUseCase` check 3 → `if (facts.formatVersion == null || facts.formatVersion !in SUPPORTED_FORMAT_VERSIONS) return Invalid(UNSUPPORTED_BACKUP_VERSION)`; update the KDoc ("only format version 1 is understood" → "v1 and v2 are accepted; v1 files carry no serving-unit data"). Everything else in the fixed-order table unchanged.
- [x] Task 6: Tests (AC: #1–#5, #7)
  - [x] `BackupDatabaseUseCaseTest`: `wrongFormatVersion_returnsUnsupportedBackupVersion` switches `formatVersion = 2 → 3` (with v2 supported, 2 must move to rejection value); add `backupFormatVersion1_stillValid` (`copy(formatVersion = 1)` → Valid — the v1-acceptance guard), `formatVersionThree_returnsUnsupportedBackupVersion`, `missingFormatVersion_returnsUnsupportedBackupVersion` stays. `validFacts()` picks up `FORMAT_VERSION = 2` automatically.
  - [x] Fake updates forced by the new abstract DAO members (mechanical): `FoodCatalogRepositoryImplTest.FakeFoodCatalogDao` and `RecipeIngredientLinkRemapperTest.FakeFoodCatalogDao` both implement `FoodCatalogDao` — give each `getCustomFoodServingUnits()` (return `emptyList()` / configurable) and `insertServingUnits` (record or no-op). Before building, grep `: FoodCatalogDao` across both source sets to catch any other implementer.
  - [x] androidTest round-trip (compile-only per policy): extend `FoodCatalogFtsTest` (it already builds a real asset-backed catalog DB) with `replaceCustomFoods_roundTripsFoodsNutrientsAndServingUnits`: insert a custom food with nutrients + 2 serving units through the DAO, wipe & restore via `replaceCustomFoods(List<RestoredCustomFood>(... servingUnits ...))`, assert the foods' new ids, nutrient children, and serving-unit rows (unit names + grams, all pointing to the **new** food ids) come back — this is the AC #5 round-trip proof point. Run on device manually; never `connectedDebugAndroidTest` (policy).
  - [x] Red→green discipline: Task 5/6 validation tests must fail against the pre-task code first (with `FORMAT_VERSION = 1`, `formatVersion = 2 → Valid` is red because 2 was rejected), then go green after the use-case change. Tasks 1–4 land code without observable JVM behavior change except through the remapper/repository fakes compile — justify as compile-level additions in the story's Debug Log if the red-first rule does not apply.
  - [x] Verification commands (WSL per AGENTS.md): `./gradlew compileDebugSources` → `./gradlew testDebugUnitTest` → `./gradlew assembleDebug :app:assembleDebugAndroidTest` → `git status` shows **no diff under `app/schemas/`** (AC #7).
- [x] Task 7: Register + docs (AC: #6)
  - [x] `deferred-work.md`: annotate **3.1-F1** and **5.2-F1** RESOLVED inline (keep the original bullets), add consolidated "Resolved by: story 6-3-catalog-db-migration-and-backup-format-hardening (2026-10-25)" section: 3.1-F1 resolved as *fail-fast builder + shipped `CatalogCustomFoodPreserver` (no version bump made — the guarantee is that the destructive path is unreachable)*; 5.2-F1 resolved as *format v2 carries custom serving units, keyed by `source_catalog_id`, applied transactionally on restore*. Update 5.2-F1's existing 6.2-note to reflect that the planned v2 bump has now happened.

### Review Findings

- [x] [Review][Patch] Optimize serving unit association to O(1) lookup and log dropped unlinked units in PendingRestoreApplier [app/src/main/java/com/example/myfoodtracker/data/backup/PendingRestoreApplier.kt:101-109, 186-200]
- [x] [Review][Patch] Centralize SERVING_UNITS_TABLE constant in BackupDatabaseUseCase [app/src/main/java/com/example/myfoodtracker/domain/usecase/BackupDatabaseUseCase.kt:73, app/src/main/java/com/example/myfoodtracker/data/backup/DatabaseBackupManager.kt:593, app/src/main/java/com/example/myfoodtracker/data/backup/PendingRestoreApplier.kt:391]
- [x] [Review][Patch] Ensure CatalogCustomFoodPreserver clears existing temp tables defensively prior to snapshot [app/src/main/java/com/example/myfoodtracker/data/db/CatalogMigrations.kt:128-147]

## Dev Notes

### Current state of the code (verified, baseline e7a3545 = Story 6.2 merged)

**Catalog DB** (`data/db/FoodCatalogDatabase.kt`, `di/AppModule.kt:75-82`): version `1`, entities `catalog_foods` / `catalog_food_nutrients` / `catalog_food_serving_units` / `catalog_foods_fts`; builder sets `BundledSQLiteDriver()` + `createFromAsset("databases/food_catalog.db")` + `fallbackToDestructiveMigration()` + `allowMainThreadQueries()`. Exported schema `app/schemas/com.example.myfoodtracker.data.db.FoodCatalogDatabase/1.json` — verify it contains all four tables **and the two `room_fts_content_sync_*` triggers** before handing the DDL recipe to the future migration (it does; check the JSON's `setupQueries`-style entries / read it before writing the androidTest's DDL block).

**Why the destructive path still exists (3.1-F1 mechanics):** Room only copies `createFromAsset` when the database file is **first created** — but with a fallback enabled, a version bump without a migration either (a) re-copies a prepackaged file whose version number matches the target version (official docs: "if you include a prepackaged database file with the same number as the target version, Room populates the newly recreated database"), or (b) creates an **empty** re-created schema when it does not match. Both delete every `is_custom = 1` row and its children; (a) also re-seeds. Removing the fallback makes both impossible; the price is a fail-fast `IllegalStateException` if a future dev bumps the version without a migration — that is the intended contract, say so in the builder KDoc.

**Custom foods & serving units today:** `createCustomFood` (`FoodCatalogRepositoryImpl.kt:21-76`) writes food + nutrient through `insertFoodWithNutrients` and **no serving units** — the multi-unit table is only populated for seed foods by the asset. So `getCustomFoodServingUnits()` returns `emptyList()` on every real device today, and a v2 backup carries 0 unit rows **until a future story populates units for custom foods**. This story ships the *capability* (5.2-F1's "if custom foods ever gain serving units, the format must be ready") — the DAO round-trip test (Task 6) exercises it by inserting rows through the DAO directly, and the file-level round trip is manual-verified only (no-emulator policy; see §Testing).

**Backup format v1 as of 6.2** (`DatabaseBackupManager`): copy of `meals_database` + WAL checkpoint + journal-mode flip; then three backup-only tables appended inside one transaction: `myfoodtracker_backup_meta` (keys incl. `format_version`), `myfoodtracker_custom_foods` (15 base columns + 6.2's `, source_catalog_id INTEGER`, 16 bound params), and **nothing else**. `FORMAT_VERSION = 1` lives in `BackupDatabaseUseCase` — the single source of truth imported by the manager. Restore: `validateCandidate` → `collectFacts` → decision table → `stageRestore` → cold-start `applyPendingDatabaseSwap` (pre-Koin: journal flip, integrity, read custom foods, **drop the two backup tables**, swap file, clear session) → post-Koin `applyCustomFoods(foodCatalogDao, recipeDao, foods)` (re-insert → 6.2's remap). The two-phase order and the exception-swallowing remainder-normal-startup behavior must be preserved exactly.

**The place v2 plugs in:** unit rows key on `source_catalog_id` — the same carrier 6.2 introduced — because *that* is the id the restore path knows is "from this backup". The mapping old→new is *already returned* by `replaceCustomFoods` (6.2), so the unit rows could alternatively be keyed/consumed via the returned mapping; attaching units to each `RestoredCustomFood` inside the same transaction is simpler and keeps the mapping contract untouched — no change to `RecipeIngredientLinkRemapper` (it never hears about units). Pre-6.2 v1 backups (no ids at all) have no unit rows by construction, so nothing can strand.

### Preservation helper — design notes (AC: #2)

- `Migration.migrate` in **Room 3 (`androidx.room3`) is `suspend fun migrate(connection: SQLiteConnection)`** — imports `androidx.room3.migration.Migration`, `androidx.sqlite.SQLiteConnection` (+ `androidx.sqlite.execSQL` extension where handy). This is a framework-mandated signature, not a project-threading decision; note that explicitly next to it (project-context AD-2 forbids *project-introduced* suspend/coroutines for DB access; Room's own migration machinery is exempt — same shape as the 3.1 BundledSQLiteDriver exception).
- The helper operates purely on raw SQL over `SQLiteConnection` so it stays independent of any future target-version schema and is driver-testable (`BundledSQLiteDriver().open(...)`) without Room, which is what makes Task 2's androidTest possible.
- Ids are copied explicitly (`INSERT INTO ... SELECT *` over the snapshot keeps the `id` column) — SQLite accepts explicit values for `INTEGER PRIMARY KEY AUTOINCREMENT` columns and updates `sqlite_sequence` accordingly; that means **custom food ids survive an app update and 6.2's restore-remap is not needed for the migration path** — only restores re-allocate ids.
- `rebuild` is passed as a lambda and is **the future migration's job**: DROP-and-CREATE from the next exported schema JSON (tables + indices + FTS triggers). Do not try to be clever with `ALTER TABLE` guesses — destructive-but-preserved is exactly the one path that needs no knowledge of the delta and can never produce a schema/id mismatch. Record the order: snapshot → rebuild → re-insert, all three inside `migrate` = one transaction.
- Known limitation (document in the helper's KDoc): a future bump that *also* ships a new seed asset does not refresh the seed for existing installs (asset copy only happens on first create; the rebuild is code-run without the asset). Seed refresh + custom preservation combined would be an export→asset-reimport design and is out of scope until a bump actually needs it.

### What this story changes vs. what must be preserved

**Changes:** `di/AppModule.kt` (catalog builder only), new `data/db/CatalogMigrations.kt`, `BackupDatabaseUseCase.kt` (FORMAT_VERSION/SUPPORTED_FORMAT_VERSIONS + check 3 + KDoc), `DatabaseBackupManager.kt` (third backup table + a separate unit-row INSERT statement; the custom-food INSERT keeps its 16 bound params), `PendingRestoreApplier.kt` (unit read, drop 3rd table, attach units), `CustomFoodBackup.kt` (+ `servingUnits`), NEW `domain/model/ServingUnitBackup.kt`, `FoodCatalogDao.kt` (2 members + unit insert inside `replaceCustomFoods`), two test fakes, `BackupDatabaseUseCaseTest` (3 test edits), `FoodCatalogFtsTest` (1 addition), NEW `CatalogCustomFoodPreserverTest` (androidTest), `deferred-work.md`.

**Preserved (regression guards):**
- **6.2 remap path:** `replaceCustomFoods` return type, mapping semantics, `RecipeIngredientLinkRemapper` untouched — restore of a v1 *or* v2 backup remaps exactly as today; the units ride along inside the food items.
- **5.2 restore pipeline:** two-phase cold-start order, pre-Koin staging/drop/swap, post-Koin apply, `try/catch` → log → normal startup (never crash), `discardStagedRestore` semantics, remembered-session clear, `backup_rules.xml`/`data_extraction_rules.xml` (6.2) untouched.
- **Main DB:** `AppDatabase` v4 untouched, main builder keeps `fallbackToDestructiveMigration()` (project-context rule), `EXPECTED_USER_VERSION = 4` untouched — the SCHEMA_VERSION_MISMATCH decision-table branch is unaffected by the format bump.
- **Decision table order:** checks 1–8 and their precedence (first failure wins) unchanged; only check 3's predicate widens from `== 1` to `∈ {1, 2}`.
- **Meta table:** same keys, same `custom_foods_count` semantics; no new fact in `BackupFacts` (shape unchanged — `domain/model/BackupFacts.kt` untouched).
- **Seed catalog:** still never backed up; `getCustomFoodServingUnits` filters by `is_custom = 1` via subquery exactly like `getCustomFoodNutrients` — never read the whole ~10k-row seed unit space.
- **No emulator policy** and WSL build discipline per AGENTS.md (Linux `./gradlew`, JAVA_HOME/ANDROID_HOME check first, harmless Windows-path warning in `local.properties`, temp-swap/restore only if an SDK error appears).

### Testing (per project-context.md + repo convention)

- JVM tests: **hand-written fakes, not MockK** (repo convention across `MealRepositoryImplTest` / `RecipeRepositoryImplTest` / `FoodCatalogRepositoryImplTest` / `RecipeIngredientLinkRemapperTest` — do not introduce MockK despite project-context mentioning it).
- **JVM-testable:** the whole validation half (Task 5) — red→green; the fakes' compile behavior.
- **Not JVM-testable (established precedents):** `DatabaseBackupManager` (Context + platform `SQLiteDatabase`; 5.2/6.2 shipped it test-free at the file level), `PendingRestoreApplier` (Context, cold-start), the `Migration`-time preservation helper at the Room level (androidTest covers it at the raw-driver level instead — Task 2's test).
- **androidTest:** compile via `:app:assembleDebugAndroidTest`, execute manually on device eventually; state this explicitly in Completion Notes (manual-testing notice requirement).
- **Manual device checklist for Completion Notes:** (1) make a backup with the new code, confirm `format_version = 2` in the file and validity on restore; (2) restore the *old* v1 backup made before this story into the updated app; (3) (after any future custom-food-with-units data exists) full backup → restore → serving units preserved. Today step (3) needs a temporary DAO-inserted unit row or a future story's data — note that in the completion notes rather than bending the app.

### Regression watch (must stay green)

- `BackupDatabaseUseCaseTest` — the decision-table suite; expect the 2-planned edits plus consistent `validFacts`.
- `RecipeIngredientLinkRemapperTest` (8 cases) — fake compiles unchanged in behavior; remapper untouched.
- `FoodCatalogRepositoryImplTest` — fake compiles; search/create-custom-food paths untouched.
- `FoodCatalogFtsTest` (androidTest) — existing tests compile unchanged; only an addition.
- Any file constructing `CustomFoodBackup` (`MainApplication.kt`, `PendingRestoreApplier.kt`, `DatabaseBackupManager.kt`) — defaulted `servingUnits` keeps them compiling.
- Story 6.1 delegation guards (`MealRepositoryImplTest`, `RecipeRepositoryImplTest`) — untouched.

### Project Structure Notes

- New files: `data/db/CatalogMigrations.kt`, `domain/model/ServingUnitBackup.kt`, androidTest `data/CatalogCustomFoodPreserverTest.kt`.
- Modified files: `di/AppModule.kt`, `data/dao/FoodCatalogDao.kt`, `data/backup/DatabaseBackupManager.kt`, `data/backup/PendingRestoreApplier.kt`, `domain/model/CustomFoodBackup.kt`, `domain/usecase/BackupDatabaseUseCase.kt`, `app/src/androidTest/java/com/example/myfoodtracker/data/FoodCatalogFtsTest.kt`, `app/src/test/java/com/example/myfoodtracker/domain/usecase/BackupDatabaseUseCaseTest.kt`, the two repository/remapper test fakes, `deferred-work.md`, `sprint-status.yaml` (status transitions), and this story file.
- No changes to: any `data/entity/*` (no schema change — AC #7), `AppDatabase.kt`, `RestoredCustomFood`'s existing fields (only `servingUnits` appended with default), `RecipeDao.kt`/`RecipeIngredientLinkRemapper.kt`, `SettingsFragment`/`SettingsViewModel` (the UI flow is presentation-agnostic to version bumps), `res/xml` rules.
- Layering: `CatalogMigrations.kt` in `data/db/` is allowed Room/SQLite imports; `ServingUnitBackup.kt` in `domain/` is pure Kotlin; the applier's unit projection data class stays private inside the data layer.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Epic 6, Story 6.3] (AC wording: "is_custom = 1 rows plus their catalog_food_nutrients are preserved in place"; "backup format v2 carries catalog_food_serving_units rows of custom foods"; "format_version becomes 2"; "validation accepts v1 and v2")
- [Source: _bmad-output/implementation-artifacts/deferred-work.md#3.1-F1] ("New catalog version bump + fallbackToDestructiveMigration would wipe custom foods… export + re-import or separate table/DB") and **#5.2-F1** ("catalog_food_serving_units of custom foods are not backed up… if custom foods ever gain serving units, format_version must be bumped and the format extended") — with the 6-2 note that `source_catalog_id` did not pre-empt this v2 bump
- [Source: _bmad-output/implementation-artifacts/6-2-remap-custom-food-ids-after-restore.md] (verbatim precedent for two-tier reads, `source_catalog_id` carrier, restore pipeline phases, fakes, androidTest compile-only policy, register-resolution pattern, red→green)
- [Source: _bmad-output/implementation-artifacts/6-1-unify-atomic-room-transactions.md] (composite `@Transaction` default-body precedent upheld in `replaceCustomFoods`)
- [Source: _bmad-output/implementation-artifacts/5-2-offline-full-database-backup-and-restore.md] (backup file anatomy, decision-table contract `UNSUPPORTED_BACKUP_VERSION` on unknown versions, staging/drop semantics, never-crash-on-restore invariant)
- [Source: _bmad-output/project-context.md#Critical Implementation Rules / Testing Rules / Don't-Miss Rules] (synchronous DAO rule + "unless refactoring the Room database builder" AD-2 exception; DAO SQL isolation; `fallbackToDestructiveMigration` on main DB only; fakes-over-MockK reality; build & test verification commands)
- [Source: developer.android.com/training/data-storage/room/prepopulate#specify-the-migration-path-for-a-prepackaged-database] (destructive fallback + prepackaged interplay: same-target-version asset → re-populated rebuilt DB; else empty schema — the exact wipe mechanisms this story closes; fetched 2026-10-25)
- [Source: developer.android.com/jetpack/androidx/releases/room3 + /training/data-storage/room/migration-2-to-3] (Room 3 KMP: `Migration.migrate` is `suspend fun migrate(connection: SQLiteConnection)`; `androidx.room3:room3-sqlite-wrapper` exists for legacy interop but is NOT needed here; `addMigrations(vararg)` and `fallbackToDestructiveMigration()` both exist in room3.0.3 — fetched 2026-10-25)
- [Source: app/src/main/java/com/example/myfoodtracker/di/AppModule.kt (whole file), data/db/FoodCatalogDatabase.kt, data/dao/FoodCatalogDao.kt, data/backup/DatabaseBackupManager.kt (whole file), data/backup/PendingRestoreApplier.kt (whole file), domain/usecase/BackupDatabaseUseCase.kt (whole file), domain/model/CustomFoodBackup.kt, data/repository/FoodCatalogRepositoryImpl.kt, data/entity/CatalogFoodEntity.kt, data/entity/CatalogFoodServingUnitEntity.kt] (verified current code, baseline e7a3545)
- [Source: app/schemas/com.example.myfoodtracker.data.db.FoodCatalogDatabase/1.json] (version 1; contains catalog_foods, catalog_food_nutrients, catalog_food_serving_units, catalog_foods_fts — tables + FTS sync triggers are the DDL source for Task 2's androidTest)
- [Source: app/src/test/java/com/example/myfoodtracker/domain/usecase/BackupDatabaseUseCaseTest.kt (whole file — esp. line ~94 `formatVersion = 2` rejection test that will move), app/src/test/java/com/example/myfoodtracker/data/repository/FoodCatalogRepositoryImplTest.kt (FakeFoodCatalogDao), app/src/test/java/com/example/myfoodtracker/data/backup/RecipeIngredientLinkRemapperTest.kt (FakeFoodCatalogDao), app/src/androidTest/java/com/example/myfoodtracker/data/FoodCatalogFtsTest.kt] (test conventions + edit targets)

## Dev Agent Record

### Agent Model Used

GLM-5.3-Flash Free (opencode/glm-5.3-flash-free), OpenCode agent harness, dev-story workflow for story 6-3.

### Debug Log References

- ✅ Confirmed before any Gradle call (AGENTS.md): Linux Java 21 (`/usr/lib/jvm/java-21-openjdk-amd64`), `ANDROID_HOME=$HOME/Android/Sdk`. The `sdk.dir ... Directory does not exist` warning is the known harmless Windows-path fallback — `local.properties` left untouched.
- ✅ Room 3 API verified against the actual caches before writing code: `androidx.room3.migration.Migration` has the `suspend fun migrate(SQLiteConnection)` KMP signature; `RoomDatabase.Builder.addMigrations(vararg Migration)` and `fallbackToDestructiveMigration()` both exist in room3-runtime 3.0.3; `androidx.sqlite.execSQL` extension + `prepare()/step()` confirmed for the raw-driver preserver/androidTest.
- ✅ Red→green (Task 5/6): `git stash push` of `BackupDatabaseUseCase.kt` (restoring `FORMAT_VERSION = 1` + strict check) → `BackupDatabaseUseCaseTest` ran 25 tests, **1 failed** = `backupFormatVersion2_stillValid` (exactly the planned red: with v1-only code, `formatVersion = 2 → Valid` fails). Stash popped → full suite green.
- ✅ Red-first rule does not apply to Tasks 1–4 (compile-level additions, no observable JVM behavior change — pre-justified in the task list): verified as compile-only (Kotlin) + the fake-member additions compiled by the existing suites without asserting new behavior. The Tasks 1–2 androidTest is compile-and-execute-manually per policy.
- ✅ Verification chain (WSL): `:app:compileDebugKotlin` → `:app:compileDebugAndroidTestKotlin` → `:app:testDebugUnitTest` (**350 tests, 0 failed, 0 skipped** — includes the edited decision-table suite and both repository/remapper fakes) → `:app:compileDebugSources :app:assembleDebug :app:assembleDebugAndroidTest` → `git status`: no content diff under `app/schemas/` (the status noise is CRLF-only; `git diff --ignore-cr-at-eol -- app/schemas/` is empty — AC #7).
- Implementation note: `RestoredCustomFood` gained `servingUnits` with a default (`emptyList()`), so every existing construction site and the 6.2 remap contract compile unchanged; the applier now supplies the units only when they exist.

### Completion Notes List

- **Task 1 (AC #1, #7):** catalog builder in `di/AppModule.kt` now uses `.addMigrations(*CatalogMigrations.ALL)` — `fallbackToDestructiveMigration()` lives only on the main `meals_database` builder (project-context rule). `data/db/CatalogMigrations.kt` ships the empty `ALL` array (behavioral fail-fast guard at version 1) plus the full step-by-step `MIGRATION_N_(N+1)` recipe in KDoc.
- **Task 2 (AC #2):** `CatalogCustomFoodPreserver.preserveThenRestore(connection, rebuild)` implemented exactly per the 7-step Dev Notes recipe (temp-table snapshot → rebuild lambda → explicit-id re-insert → finally-drop). `CatalogCustomFoodPreserverTest` (androidTest, raw `BundledSQLiteDriver` + `:memory:`) builds the v1 DDL verbatim from the exported schema JSON (tables, indices, FTS5 table and all four `room_fts_content_sync_*` triggers), FK enforcement on, and asserts: custom rows survive with ids in place, children re-attach, seed rows dropped, temp tables gone, FTS match finds the re-inserted food.
- **Task 3 (AC #3):** `FORMAT_VERSION = 2` (+ `SUPPORTED_FORMAT_VERSIONS = listOf(1, 2)`), new `ServingUnitBackup` (pure Kotlin), `CustomFoodBackup.servingUnits` defaulted; `FoodCatalogDao` gains `getCustomFoodServingUnits()` (is_custom-subquery like the nutrients reader) + `insertServingUnits` (plain `@Insert`); `DatabaseBackupManager` writes the third backup table `myfoodtracker_custom_serving_units` and its rows inside the single backup transaction (null-`sourceCatalogId` rows skipped defensively). `BackupFacts` deliberately untouched.
- **Task 4 (AC #4, #5, #7):** applier reads the unit rows before the strip-down transaction drops the third backup-only table (`IF EXISTS` keeps v1 candidates working); missing unit table degrades silently to empty + `Log.w` (v1 case, no second tier); units attach to foods by `source_catalog_id` and are inserted inside `replaceCustomFoods`' existing `@Transaction`; units of id-less foods are dropped with a one-time `Log.w` count (5.2 never-crash invariant).
- **Task 5 (AC #4):** check 3 widened to `facts.formatVersion != null && facts.formatVersion in SUPPORTED_FORMAT_VERSIONS`; KDoc updated; fixed order checks 1–8 untouched.
- **Task 6 (AC #1–#5, #7):** decision-table tests updated + `backupFormatVersion2_stillValid` / `backupFormatVersion1_stillValid` / `formatVersionThree_...` added (red→green proven, see Debug Log); both `FakeFoodCatalogDao`s extended mechanically (grep confirmed no other implementers); androidTest round-trip `replaceCustomFoods_roundTripsFoodsNutrientsAndServingUnits` added as the AC #5 proof point.
- **Task 7 (AC #6):** `deferred-work.md` — 3.1-F1 and 5.2-F1 annotated RESOLVED inline (originals kept; 5.2-F1's 6.2-note updated for the now-real v2 bump) + consolidated "Resolved by: story 6-3-catalog-db-migration-and-backup-format-hardening (2026-10-25)" section in the established pattern.
- **⚠️ MANUAL DEVICE TESTING NOTICE (no-emulator policy):** the androidTests compile (`assembleDebugAndroidTest` ✅) but were NOT executed — run manually on a device when convenient; never `connectedDebugAndroidTest` automated:
  1. `CatalogCustomFoodPreserverTest` — preservation round trip (ids kept, children attached, temp tables dropped, FTS trigger reindex).
  2. `FoodCatalogFtsTest.replaceCustomFoods_roundTripsFoodsNutrientsAndServingUnits` — AC #5 DAO round trip (also re-runs the pre-existing FTS suite on the real asset).
  3. End-to-end backup/restore checklist: (a) make a backup with this code and confirm `format_version = 2` in the meta table + successful restore; (b) restore a pre-6.3 **v1** backup (made before this story) into the updated app — must still succeed, no unit data carried; (c) serving units only round-trip once a custom food actually has unit rows (today `createCustomFood` writes none, so a v2 backup carries 0 unit rows on a real device — a temporary DAO-inserted unit row can prove step (c) rather than bending the app).
  4. Forward-comgression check: a pre-6.3 app reading a v2 backup must show "unsupported backup version" (accepted contract).

### File List

New:
- app/src/main/java/com/example/myfoodtracker/data/db/CatalogMigrations.kt
- app/src/main/java/com/example/myfoodtracker/domain/model/ServingUnitBackup.kt
- app/src/androidTest/java/com/example/myfoodtracker/data/CatalogCustomFoodPreserverTest.kt

Modified:
- app/src/main/java/com/example/myfoodtracker/di/AppModule.kt
- app/src/main/java/com/example/myfoodtracker/domain/usecase/BackupDatabaseUseCase.kt
- app/src/main/java/com/example/myfoodtracker/domain/model/CustomFoodBackup.kt
- app/src/main/java/com/example/myfoodtracker/data/dao/FoodCatalogDao.kt
- app/src/main/java/com/example/myfoodtracker/data/db/RestoredCustomFood.kt
- app/src/main/java/com/example/myfoodtracker/data/backup/DatabaseBackupManager.kt
- app/src/main/java/com/example/myfoodtracker/data/backup/PendingRestoreApplier.kt
- app/src/androidTest/java/com/example/myfoodtracker/data/FoodCatalogFtsTest.kt
- app/src/test/java/com/example/myfoodtracker/domain/usecase/BackupDatabaseUseCaseTest.kt
- app/src/test/java/com/example/myfoodtracker/data/repository/FoodCatalogRepositoryImplTest.kt
- app/src/test/java/com/example/myfoodtracker/data/backup/RecipeIngredientLinkRemapperTest.kt
- _bmad-output/implementation-artifacts/deferred-work.md
- _bmad-output/implementation-artifacts/sprint-status.yaml
- _bmad-output/implementation-artifacts/6-3-catalog-db-migration-and-backup-format-hardening.md (this story)

## Change Log

- 2026-10-25: Story 6.3 implemented — destructive fallback removed from the catalog builder (fail-fast + `CatalogMigrations`/`CatalogCustomFoodPreserver` preservation helper), backup format v2 (custom-food serving units keyed by `source_catalog_id`, written & restored transactionally), validation accepts {1, 2} with red→green proof, register items 3.1-F1 and 5.2-F1 annotated RESOLVED. 350 JVM tests green; androidTests compiled, on-device execution deferred per policy.
