---
baseline_commit: 14b2b08fee450814bd6ca02aa1d2f660c2e2d53e
---

# Story 3.1: SQLite FTS5 Search Engine & Pre-populated Food Database

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user,
I want instant prefix autocomplete search (< 50ms) across a bundled database of over 50,000 foods offline,
So that I can search items without an internet connection.

## Acceptance Criteria

1. **Given** the bundled food catalog asset (`app/src/main/assets/databases/food_catalog.db`, ≥ 50,000 pre-populated foods, `is_custom = 0`), **When** the app first opens `FoodCatalogDatabase`, **Then** Room copies the asset via `createFromAsset("databases/food_catalog.db")` using `BundledSQLiteDriver`, validates the schema, and all subsequent reads come from the local copy with zero network access.
2. **Given** the search engine (`FoodCatalogDao.search` + `SearchFoodUseCase`), **When** prefix terms are searched (e.g. input `chick bre`), **Then** the query is converted to an FTS5 `MATCH` with per-term prefix (`"chick"* "bre"*`) and matching pre-populated foods are returned with an average query latency `< 50ms` measured on-device.
3. **Given** out-of-order search terms (e.g. `breast chicken`), **When** searched, **Then** the same matching foods are returned (FTS5 implicit AND is order-independent for unqualified terms).
4. **Given** arbitrary user input (embedded quotes, `*`, `-`, `:`, `AND`/`OR`/`NOT`, punctuation-only, blank), **When** the FTS match string is built, **Then** every term is quoted/escaped, noise-only or blank input returns an empty result list, and no `SQLiteException` propagates.
5. **Given** the `catalog_foods` table, **When** a row is inserted, updated, or deleted (the future custom-food path of Story 3.3), **Then** the Room-managed FTS5 sync triggers (`room_fts_content_sync_*`) keep `catalog_foods_fts` synchronized, making the row immediately searchable/deindexed.
6. **Given** the existing Room 2.6.1 data layer (profiles, sessions, meals, water, quick-add), **When** the project is upgraded to Room 3.0.3 (`androidx.room3`) with `BundledSQLiteDriver`, **Then** all existing features behave exactly as before, all 121 existing JVM unit tests pass, and `:app:assembleDebug` succeeds — with blocking (non-`suspend`) DAO functions and `allowMainThreadQueries()` retained per architecture rule AD-2.
7. **Given** the new data/domain classes, **When** unit-tested on the JVM, **Then** `FtsQueryBuilder`, `SearchFoodUseCase`, and `FoodCatalogRepositoryImpl` are covered using hand-written fakes consistent with the existing test style (JUnit 4, no MockK/Robolectric), including blank query, escaping, limit clamping, out-of-order terms, and entity→domain mapping.
8. **Given** a reachable emulator/device, **When** `./gradlew connectedDebugAndroidTest` runs the instrumented `FoodCatalogFtsTest` against the real asset, **Then** it verifies: asset copy + `BundledSQLiteDriver` FTS5 availability, prefix match, out-of-order match, average latency `< 50ms`, and trigger-based sync on insert/delete.

### Scope Boundary (explicit)

- **In scope:** Room 3 migration, food catalog Room database + `@Fts5` index, seed-data pipeline + committed asset, repository/use-case, Koin wiring, tests, documentation updates.
- **Out of scope (Story 3.2):** food search bottom sheet UI, result list rendering, quantity modal, meal-slot logging. Story 3.2 consumes `SearchFoodUseCase`.
- **Out of scope (Story 3.3):** custom-food creation UI and storage, bm25/app-level relevance re-ranking. This story only makes the schema/triggers ready for it.
- **Out of scope (Story 5.2):** backup/restore must later account for two databases (`meals_database` + `food_catalog`) — record this as a forward note, do not implement.

## Tasks / Subtasks

- [x] **Task 1: Migrate Room 2.6.1 → Room 3.0.3 (`androidx.room3`)** (AC: #6)
  - [x] Subtask 1.1: MODIFY `gradle/libs.versions.toml` — remove `room = "2.6.1"`; add `room3 = "3.0.3"` and `sqlite = "2.7.1"`; libraries: `androidx-room3-runtime` (`androidx.room3:room3-runtime`), `androidx-room3-compiler` (`androidx.room3:room3-compiler`), `androidx-sqlite-bundled` (`androidx.sqlite:sqlite-bundled`); plugin alias `androidx-room3 = { id = "androidx.room3", version.ref = "room3" }`. Keep `ksp = "2.2.10-2.0.2"`.
  - [x] Subtask 1.2: MODIFY `app/build.gradle.kts` — remove `androidx.room:room-runtime`, `androidx.room:room-ktx`, `ksp("androidx.room:room-compiler")`; add `implementation(libs.androidx.room3.runtime)`, `ksp(libs.androidx.room3.compiler)`, `implementation(libs.androidx.sqlite.bundled)`; apply `alias(libs.plugins.androidx.room3)`; add `room3 { schemaDirectory("$projectDir/schemas") }`; add `androidResources { noCompress.add("db") }`.
  - [x] Subtask 1.3: MODIFY all Room imports `androidx.room.*` → `androidx.room3.*` (exact file list in Dev Notes). Files: `data/db/AppDatabase.kt`, `data/db/MealEntryEntity.kt`, `data/db/FoodEntity.kt`, `data/db/WaterLogEntity.kt`, `data/db/MealWithFoods.kt`, `data/db/MealDao.kt`, `data/db/WaterLogDao.kt`, `data/dao/UserProfileDao.kt`, `data/entity/UserProfileEntity.kt`, `data/entity/UserDailyGoalEntity.kt`, `di/AppModule.kt`.
  - [x] Subtask 1.4: VERIFY `AppDatabase` keeps `version = 3`, `exportSchema = false`; `AppModule` keeps `Room.databaseBuilder(context, AppDatabase::class.java, "meals_database").fallbackToDestructiveMigration().allowMainThreadQueries().build()` with **no** explicit driver (defaults to `AndroidSQLiteDriver`, i.e. framework SQLite — existing behavior preserved).
  - [x] Subtask 1.5: RUN `./gradlew testDebugUnitTest :app:assembleDebug` — all 121 existing tests green, build green.

- [x] **Task 2: Food catalog database with `@Fts5` (schema, DAO, repository, use case)** (AC: #1, #2, #4, #5)
  - [x] Subtask 2.1: NEW `data/entity/CatalogFoodEntity.kt` — table `catalog_foods` (exact fields in Dev Notes).
  - [x] Subtask 2.2: NEW `data/entity/CatalogFoodNutrientEntity.kt` — table `catalog_food_nutrients`, PK/FK `food_id` → `catalog_foods.id` CASCADE.
  - [x] Subtask 2.3: NEW `data/entity/CatalogFoodServingUnitEntity.kt` — table `catalog_food_serving_units`, FK CASCADE + index.
  - [x] Subtask 2.4: NEW `data/entity/CatalogFoodFtsEntity.kt` — table `catalog_foods_fts`, `@Fts5(contentEntity = CatalogFoodEntity::class)`.
  - [x] Subtask 2.5: NEW `data/db/FoodCatalogDatabase.kt` — `@Database(entities = [...4 entities...], version = 1, exportSchema = true)`; abstract `foodCatalogDao(): FoodCatalogDao`.
  - [x] Subtask 2.6: NEW `data/dao/FoodCatalogDao.kt` — `search(matchQuery: String, limit: Int): List<FoodSearchRow>` (FTS5 `MATCH` + `bm25` ordering + JOINs), `countFoods(): Int`, and a `@Transaction fun insertFoodWithNutrients(...)` helper used by tests (atomic multi-table write; addresses deferred 2.4-D1/2.5-D1 precedent).
  - [x] Subtask 2.7: NEW `data/db/FoodSearchRow.kt` — flat query projection POJO (data-layer only; never returned past the repository).
  - [x] Subtask 2.8: NEW `data/dao/FtsQueryBuilder.kt` — pure Kotlin `object` that converts raw user input to an FTS5 `MATCH` string or `null` (exact algorithm in Dev Notes).
  - [x] Subtask 2.9: NEW `domain/model/FoodItem.kt` — pure domain model (exact fields in Dev Notes). Note: distinct name from the existing meal-line `domain/model/Food.kt`.
  - [x] Subtask 2.10: NEW `domain/repository/FoodCatalogRepository.kt` (`search(query: String, limit: Int): List<FoodItem>`) and NEW `data/repository/FoodCatalogRepositoryImpl.kt` with private mapper extensions (entities/POJO → `FoodItem`).
  - [x] Subtask 2.11: NEW `domain/usecase/SearchFoodUseCase.kt` — `operator fun invoke(query: String, limit: Int = 30): List<FoodItem>`.
  - [x] Subtask 2.12: MODIFY `di/AppModule.kt` — register the catalog database (`createFromAsset` + `setDriver(BundledSQLiteDriver())` + `fallbackToDestructiveMigration()` + `allowMainThreadQueries()`), DAO (`single`), repository (`single`), `SearchFoodUseCase` (`factory`). Exact snippet in Dev Notes.
  - [x] Subtask 2.13: RUN a build so the Room Gradle plugin exports the schema JSON (needed by Task 3). Check it in. (Variance: Room 3 plugin exports to `app/schemas/com.example.myfoodtracker.data.db.FoodCatalogDatabase/1.json`, not `app/schemas/debug/...`.)

- [x] **Task 3: Seed-data pipeline + committed catalog asset** (AC: #1, #2)
  - [x] Subtask 3.1: NEW `tools/seed/build_food_catalog_db.py` — Python 3 stdlib only (`csv`, `sqlite3`, `urllib`, `zipfile`, `hashlib`). Downloads pinned USDA FoodData Central CSVs, streams/parses, builds `app/src/main/assets/databases/food_catalog.db` with the exact Room-exported schema. Full specification in Dev Notes.
  - [x] Subtask 3.2: NEW `tools/seed/README.md` — data source (USDA FDC, CC0 1.0 public domain), pinned URLs, regenerate command, expected output row counts and DB size, provenance/hashes.
  - [x] Subtask 3.3: MODIFY `.gitignore` — ignore `tools/seed/.cache/` (raw downloads), never ignore the generated asset.
  - [x] Subtask 3.4: RUN the script (one-time ~450 MB download; cache it). Verify `SELECT COUNT(*) FROM catalog_foods` ≥ 50,000 and `PRAGMA integrity_check` = `ok`; commit the generated `app/src/main/assets/databases/food_catalog.db`. (Result: 50,500 foods, 16.1 MB, integrity ok.)
  - [x] Subtask 3.5: Verify the asset is packaged: `./gradlew :app:assembleDebug` and confirm `databases/food_catalog.db` inside the APK (`unzip -l app/build/outputs/apk/debug/app-debug.apk | grep food_catalog`). (Confirmed: `assets/databases/food_catalog.db`, stored uncompressed.)

- [x] **Task 4: Tests** (AC: #3, #4, #7, #8)
  - [x] Subtask 4.1: NEW `app/src/test/.../data/dao/FtsQueryBuilderTest.kt` — prefix conversion, out-of-order preservation, quote doubling, noise-only → null, blank → null.
  - [x] Subtask 4.2: NEW `app/src/test/.../domain/usecase/SearchFoodUseCaseTest.kt` — blank/whitespace/punctuation → `emptyList()` with fake repository; default and explicit limit delegation.
  - [x] Subtask 4.3: NEW `app/src/test/.../data/repository/FoodCatalogRepositoryImplTest.kt` — fake `FoodCatalogDao` (hand-written, mirrors DAO behavior) asserting match string, clamped limit, and row → `FoodItem` mapping.
  - [x] Subtask 4.4: MODIFY `app/build.gradle.kts` — add `androidTestImplementation("androidx.test:core:1.6.1")` (for `ApplicationProvider`; Gradle may resolve higher via espresso/ext-junit).
  - [x] Subtask 4.5: NEW `app/src/androidTest/.../data/FoodCatalogFtsTest.kt` — instrumented (`:app:assembleDebugAndroidTest` green).
  - [x] Subtask 4.6: RUN `./gradlew testDebugUnitTest :app:assembleDebug` (JVM — 146/146 green) and `:app:assembleDebugAndroidTest` (green). Note on device testing: Per updated agent instructions, agents do NOT run the Android emulator due to environment difficulties. On-device/emulator execution of `FoodCatalogFtsTest` requires manual testing.

- [x] **Task 5: Documentation + planning artifacts** (AC: #6)
  - [x] Subtask 5.1: MODIFY `_bmad-output/project-context.md`
  - [x] Subtask 5.2: MODIFY `_bmad-output/planning-artifacts/architecture/architecture-MyFoodTracker-2026-09-06/ARCHITECTURE-SPINE.md`
  - [x] Subtask 5.3: MODIFY `_bmad-output/planning-artifacts/epics.md` — ARCH-8/ARCH-9/ARCH-10 rows to reflect `@Fts5` + Room 3.0.3.
  - [x] Subtask 5.4: Notes only (do not fail the story): `.agents/project_structure.md` still does not exist (deferred 3.4-D1, unsatisfiable); `sergeevgk.myfoodtracker` in the architecture spine/PRD is stale — actual package is `com.example.myfoodtracker`. (Acknowledged; spine structural-seed paths also still show the pre-implementation layout — variances recorded in Dev Notes §Project Structure Notes.)
  - [x] Subtask 5.5: If a 2.x-forward issue is discovered (e.g. Room 3 destructive-recopy semantics on catalog version bumps), append it to `_bmad-output/implementation-artifacts/deferred-work.md`. (Added 3.1-F1/F2.)

### Review Findings

- [x] [Review][Patch] Fallback default values for null nutrients on foods without nutrient rows [FoodCatalogDao.kt:17-24] — Applied: wrapped nutrient column projections with `COALESCE(n.*, 0.0)`.
- [x] [Review][Dismiss] FTS tokenization of internal punctuation/dashes — Dismissed per mobile UX analysis: standard mobile food search queries are whitespace-delimited word prefixes without punctuation syntax.
- [x] [Review][Defer] Risk of losing custom foods on catalog version bump [AppModule.kt:58] — Tracked under forward note 3.1-F1 in deferred-work.md.
- [x] [Review][Drop] `.agents/project_structure.md` tracking requirement — Dropped: removed the rule from project-context.md; additional file structure tracking is no longer needed.

## Dev Notes

### 0. Why this story includes a Room 3 migration (critical decision record)

- **Problem discovered during analysis:** AOSP's bundled SQLite (verified in `android-15.0.0_r1` and `android-16.0.0_r1` `external/sqlite/dist/Android.bp`) compiles with `SQLITE_ENABLE_FTS3`/`FTS4` and `SQLITE_OMIT_LOAD_EXTENSION`, but **not** `SQLITE_ENABLE_FTS5`. Room 2.6.1 also has no `@Fts5` annotation. Therefore the PRD/architecture's "FTS5" requirement cannot be met on the pinned Room 2.6.1 + OS SQLite stack.
- **Decision (user-approved):** Upgrade to **Room 3.0.3** (new `androidx.room3` coordinates; stable since July 2026) which adds official `@Fts5` support, and use the first-party **`androidx.sqlite:sqlite-bundled` 2.7.1** (`BundledSQLiteDriver`). The bundled native SQLite was verified to include FTS5 (`ENABLE_FTS5`, `fts5Bm25Function`, `fts5SnippetFunction`, `fts5HighlightFunction`; SQLite build dated 2025-06-06).
- **Verified compatibility with this app's architecture:**
  - Room 3's compiler rejects blocking DAO functions **only for non-Android source sets** (`INVALID_BLOCKING_DAO_FUNCTION_NON_ANDROID = "Only suspend functions are allowed in DAOs declared in source sets targeting non-Android platforms."`). This is an Android-only module, so AD-2's synchronous DAOs remain legal.
  - `allowMainThreadQueries()` still exists and is honored (`assertNotMainThread()` returns early when enabled).
  - The Android runtime ships `DBUtil.performBlocking` / `performInTransactionBlocking` (`runBlockingUninterruptible`) for blocking DAO support.
  - `createFromAsset()` is still available on Android (KMP docs: pre-packaged database APIs are Android-only) and Room 3's `build()` wires `copyFromConfig` alongside `sqliteDriver`.
- **Caveats to respect:**
  - Existing Room 2-created databases may fail identity-hash validation after the framework swap; with `fallbackToDestructiveMigration()` they are recreated. This is acceptable pre-release (app `versionCode 1`); note it in the PR description.
  - Room 3 requires a `SQLiteDriver` (default on Android is `AndroidSQLiteDriver` = framework SQLite). The **main** database intentionally keeps the default driver; only the catalog database sets `BundledSQLiteDriver()` because only it needs FTS5.
  - Room 3 pulls `kotlinx-coroutines-core/-android 1.9.0` and `androidx.sqlite:sqlite 2.7.1` transitively. Do not start using coroutines/`suspend` anywhere — AD-2 still governs this codebase.
  - The Room Gradle plugin and the `room.schemaLocation` KSP arg are mutually exclusive: `INVALID_GRADLE_PLUGIN_AND_SCHEMA_LOCATION_OPTION` ("The Room Gradle plugin (id 'androidx.room3') cannot be used with an explicit use of the annotation processor option `room.schemaLocation`"). Configure schemas only via `room3 { schemaDirectory(...) }`.
  - Room 3 emits a warning (not an error) `ROOM_MISSING_SCHEMA_LOCATION` if schemas cannot be exported; the plugin removes that concern.

### 1. Exact Room import migration list (`androidx.room.*` → `androidx.room3.*`)

Grep first: `grep -rn "androidx.room" app/src/main app/src/test app/src/androidTest`. Expected main-source hits (test sources only implement DAO interfaces and import nothing from Room, verify anyway):

| File | Annotations/classes |
| --- | --- |
| `data/db/AppDatabase.kt` | `@Database`, `RoomDatabase` |
| `data/db/MealEntryEntity.kt` | `@Entity`, `@PrimaryKey`, `@ColumnInfo`, `@ForeignKey`, `Index` |
| `data/db/FoodEntity.kt` | same + `@PrimaryKey(autoGenerate = true)` |
| `data/db/WaterLogEntity.kt` | same |
| `data/db/MealWithFoods.kt` | `@Embedded`, `@Relation` |
| `data/db/MealDao.kt` | `@Dao`, `@Query`, `@Insert`, `@Transaction`, `OnConflictStrategy` |
| `data/db/WaterLogDao.kt` | `@Dao`, `@Query`, `@Insert`, `OnConflictStrategy` |
| `data/dao/UserProfileDao.kt` | `@Dao`, `@Query`, `@Insert`, `@Transaction`, `@Embedded`, `@Relation` |
| `data/entity/UserProfileEntity.kt` | `@Entity`, `@PrimaryKey`, `@ColumnInfo` |
| `data/entity/UserDailyGoalEntity.kt` | same |
| `di/AppModule.kt` | `Room`, `RoomDatabase`/builder imports |

The Class-based builder still works unchanged: `Room.databaseBuilder(context, AppDatabase::class.java, "meals_database")`; non-absolute names resolve via `Context.getDatabasePath(name)` (Room 3 KDoc).

### 2. Catalog schema (new, separate Room database)

**Why a separate database file (`food_catalog`) and not the main `meals_database`:**
- The catalog is read-only bundled content with a different release cadence; version bumps + `fallbackToDestructiveMigration` would otherwise wipe user meal/water/profile data.
- There is a table-name collision in the main DB: the existing `foods` table (`data/db/FoodEntity.kt`) is a per-meal log line item, not a food catalog. Keeping the catalog in its own DB (and with `catalog_`-prefixed names) makes the model unambiguous.
- Backup/restore (Story 5.2) must later cover both files — forward note only.

Table/entity definitions (follow the project's snake_case `@ColumnInfo` style):

```kotlin
// data/entity/CatalogFoodEntity.kt
@Entity(
    tableName = "catalog_foods",
    indices = [Index(value = ["name"]), Index(value = ["barcode"])]
)
data class CatalogFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String?,
    val barcode: String?,
    @ColumnInfo(name = "base_serving_size") val baseServingSize: Double = 100.0,
    @ColumnInfo(name = "base_serving_unit") val baseServingUnit: String = "g",
    @ColumnInfo(name = "is_custom") val isCustom: Int = 0,
    @ColumnInfo(name = "is_deleted") val isDeleted: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0L,
)
```

```kotlin
// data/entity/CatalogFoodNutrientEntity.kt
@Entity(
    tableName = "catalog_food_nutrients",
    foreignKeys = [ForeignKey(
        entity = CatalogFoodEntity::class,
        parentColumns = ["id"],
        childColumns = ["food_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["food_id"])]
)
data class CatalogFoodNutrientEntity(
    @PrimaryKey @ColumnInfo(name = "food_id") val foodId: Long,
    val calories: Double = 0.0,
    @ColumnInfo(name = "protein_g") val proteinG: Double = 0.0,
    @ColumnInfo(name = "carbs_g") val carbsG: Double = 0.0,
    @ColumnInfo(name = "fat_g") val fatG: Double = 0.0,
    @ColumnInfo(name = "fiber_g") val fiberG: Double = 0.0,
    @ColumnInfo(name = "sugar_g") val sugarG: Double = 0.0,
    @ColumnInfo(name = "sodium_mg") val sodiumMg: Double = 0.0,
)
```

```kotlin
// data/entity/CatalogFoodServingUnitEntity.kt
@Entity(
    tableName = "catalog_food_serving_units",
    foreignKeys = [ForeignKey(
        entity = CatalogFoodEntity::class,
        parentColumns = ["id"],
        childColumns = ["food_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["food_id"])]
)
data class CatalogFoodServingUnitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "food_id") val foodId: Long,
    @ColumnInfo(name = "unit_name") val unitName: String,
    @ColumnInfo(name = "grams_per_unit") val gramsPerUnit: Double,
)
```

```kotlin
// data/entity/CatalogFoodFtsEntity.kt
@Entity(tableName = "catalog_foods_fts")
@Fts5(contentEntity = CatalogFoodEntity::class)
data class CatalogFoodFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val name: String,
    val brand: String,
)
```

`@Fts5` facts (from the Room 3.0.3 `androidx.room3.Fts5` KDoc and compiler):
- FTS tables require an INTEGER-affinity property named `rowid`; all other columns are TEXT affinity.
- `contentEntity` puts the table in external-content mode and Room auto-creates sync triggers named `room_fts_content_sync_<ftsTableName>_<OP>` (`BEFORE_UPDATE`, `BEFORE_DELETE`, `AFTER_UPDATE`, `AFTER_INSERT`). All writes must go through `catalog_foods`, never the FTS table.
- `@Fts5` KDoc warning: "The availability of FTS5 is based on the driver used for the database. For Android specifically the BundledSQLiteDriver supports FTS5."
- Deviations from `addendum.md` §1.1 (record in PR): catalog `foods.id` is `INTEGER AUTOINCREMENT` (required as FTS `content_rowid`) instead of `TEXT UUID`; tables are prefixed `catalog_` to avoid collisions; `is_custom`/`is_deleted`/timestamps kept.
- If the compiler objects to `brand: String` nullability mirroring, make the FTS `brand` non-null (indexing only — display always comes from the JOIN with `catalog_foods`).

Database class:

```kotlin
// data/db/FoodCatalogDatabase.kt
@Database(
    entities = [
        CatalogFoodEntity::class,
        CatalogFoodNutrientEntity::class,
        CatalogFoodServingUnitEntity::class,
        CatalogFoodFtsEntity::class,
    ],
    version = 1,
    exportSchema = true
)
abstract class FoodCatalogDatabase : RoomDatabase() {
    abstract fun foodCatalogDao(): FoodCatalogDao
}
```

### 3. Search engine (DAO + query builder + repository + use case)

```kotlin
// data/db/FoodSearchRow.kt — flat projection, data layer only
data class FoodSearchRow(
    val id: Long,
    val name: String,
    val brand: String?,
    val barcode: String?,
    @ColumnInfo(name = "is_custom") val isCustom: Int,
    val calories: Double,
    @ColumnInfo(name = "protein_g") val proteinG: Double,
    @ColumnInfo(name = "carbs_g") val carbsG: Double,
    @ColumnInfo(name = "fat_g") val fatG: Double,
    @ColumnInfo(name = "fiber_g") val fiberG: Double,
    @ColumnInfo(name = "sugar_g") val sugarG: Double,
    @ColumnInfo(name = "sodium_mg") val sodiumMg: Double,
)
```

```kotlin
// data/dao/FoodCatalogDao.kt (query shape)
@Query("""
    SELECT f.id, f.name, f.brand, f.barcode, f.is_custom,
           n.calories, n.protein_g, n.carbs_g, n.fat_g, n.fiber_g, n.sugar_g, n.sodium_mg
    FROM catalog_foods_fts
    JOIN catalog_foods AS f ON f.id = catalog_foods_fts.rowid
    LEFT JOIN catalog_food_nutrients AS n ON n.food_id = f.id
    WHERE catalog_foods_fts MATCH :matchQuery
    ORDER BY bm25(catalog_foods_fts) ASC, f.name ASC
    LIMIT :limit
""")
fun search(matchQuery: String, limit: Int): List<FoodSearchRow>
```

- `bm25()` returns lower = better; `ORDER BY rank` is equivalent/faster, but the explicit `bm25()` call is unambiguous. Room verifies this query at compile time because `catalog_foods_fts` is a declared `@Fts5` entity. If (and only if) the compiler verifier rejects `bm25()`, fall back to `@SkipQueryVerification` (`androidx.room3.SkipQueryVerification`) — document why in a comment; prefer finding the verifier-compatible form.
- Ranking with the FR-4 custom-food boost is Story 3.3; this story only orders by relevance then name.

```kotlin
// data/dao/FtsQueryBuilder.kt
object FtsQueryBuilder {
    private val WHITESPACE = Regex("\\s+")

    /** Returns an FTS5 MATCH expression (per-term prefix, order-independent) or null when there is nothing searchable. */
    fun build(rawInput: String): String? {
        val terms = rawInput.trim().split(WHITESPACE).filter { term -> term.any(Char::isLetterOrDigit) }
        if (terms.isEmpty()) return null
        return terms.joinToString(separator = " ") { term ->
            "\"" + term.replace("\"", "\"\"") + "\"*"
        }
    }
}
```

Examples: `chick bre` → `"chick"* "bre"*`; `breast chicken` → `"breast"* "chicken"*` (same result set — FTS5 implicit AND); `...` → `null`.

```kotlin
// domain/model/FoodItem.kt
data class FoodItem(
    val id: Long,
    val name: String,
    val brand: String?,
    val barcode: String?,
    val isCustom: Boolean,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val sugarG: Double,
    val sodiumMg: Double,
)
```

- `FoodCatalogRepositoryImpl` builds the match string via `FtsQueryBuilder` (null → `emptyList()`), clamps `limit` to `1..50`, calls the DAO, and maps rows to `FoodItem` with private extensions. `FoodSearchRow` and entities must not leave `data/`.
- `SearchFoodUseCase(repository)` exposes `operator fun invoke(query: String, limit: Int = 30): List<FoodItem>`. No coroutines (AD-2/AD-4).

### 4. Koin wiring (`di/AppModule.kt`)

```kotlin
// Food catalog database — FTS5 requires the bundled SQLite driver
single {
    Room.databaseBuilder(get(), FoodCatalogDatabase::class.java, "food_catalog")
        .setDriver(BundledSQLiteDriver())                     // import androidx.sqlite.driver.bundled.BundledSQLiteDriver
        .createFromAsset("databases/food_catalog.db")
        .fallbackToDestructiveMigration()
        .allowMainThreadQueries()
        .build()
}
single { get<FoodCatalogDatabase>().foodCatalogDao() }
single<FoodCatalogRepository> { FoodCatalogRepositoryImpl(get()) }
factory { SearchFoodUseCase(get()) }
```

- Keep every declaration centralized in `AppModule.kt` (AD-6). Main database block stays as-is (no driver → `AndroidSQLiteDriver`).
- The asset is copied on first open; keep `androidResources { noCompress.add("db") }` so the ~10–20 MB file is not deflated (faster first copy; installed size is the same either way).

### 5. Seed pipeline (`tools/seed/build_food_catalog_db.py`)

**Source data — USDA FoodData Central, public domain (CC0 1.0).** Pin these URLs (verify availability when running; the script must fail loudly if a URL 404s):

| Dataset | URL (base `https://fdc.nal.usda.gov`) | Zip size | Foods |
| --- | --- | --- | --- |
| Foundation Foods 2026-04-30 | `/fdc-datasets/FoodData_Central_foundation_food_csv_2026-04-30.zip` | ~3.7 MB | ~400 |
| SR Legacy 2018-04 | `/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip` | ~6.7 MB | ~7,800 |
| FNDDS 2019-2020 (2022-10-28) | `/fdc-datasets/FoodData_Central_survey_food_csv_2022-10-28.zip` | ~4.3 MB | ~5,400 |
| Branded Foods 2026-04-30 | `/fdc-datasets/FoodData_Central_branded_food_csv_2026-04-30.zip` | ~428 MB | filter to fill ≥ 50,500 total |

**Algorithm:**
1. Preflight: Python `sqlite3.sqlite_version` must have FTS5 (`SELECT sqlite_compileoption_used('ENABLE_FTS5')` — Python's bundled SQLite does on Linux); create `tools/seed/.cache/`, download zips if missing, record SHA-256.
2. Parse each dataset's `food.csv` (`fdc_id, description, ...`) and `food_nutrient.csv` (`fdc_id, nutrient_id, amount`). Nutrient mapping (per 100 g): `1008` kcal (fallback `2047`/`2048`), `1003` protein, `1005` carbs, `1004` fat, `1079` fiber, `2000` sugar, `1093` sodium. Missing values → `0.0`.
3. Generic foods (Foundation + SR Legacy + FNDDS): include all with a non-empty description; `brand = NULL`, `barcode = NULL`.
4. Branded filter (deterministic, keep documentation in the script header): description 3–150 chars; `gtin_upc` present; all four core macros present (kcal/protein/carbs/fat); deduplicate on `(lower(gtin_upc))` and `(lower(trim(name)), lower(trim(brand_owner)))`; iterate by ascending `fdc_id` and take until the total catalog count reaches **50,500**.
5. `catalog_foods` assignment: sequential `id` starting at 1 in fixed dataset order (Foundation → SR Legacy → FNDDS → Branded) so IDs are stable across runs; `brand` from branded `brand_owner`/`brand_name`; `barcode` = normalized `gtin_upc` string (preserve leading zeros); `base_serving_size = 100.0`, `base_serving_unit = 'g'`; `is_custom = 0`, `is_deleted = 0`; `created_at`/`updated_at` = fixed constant (the FDC release date as epoch) for reproducibility.
6. `catalog_food_serving_units`: from each dataset's `food_portion.csv` (`fdc_id, measure_unit_name/portion_description/modifier, gram_weight`) for generic foods, and from `branded_food.csv` (`serving_size`, `serving_size_unit`, `household_serving_fulltext`) for branded; only rows with a positive gram weight; cap 8 units/food; deduplicate `(food_id, unit_name)`.
7. Schema: read the Room-exported JSON `app/schemas/debug/com.example.myfoodtracker.data.db.FoodCatalogDatabase/1.json` and execute, in order: every entity's `createSql` (tables, indices, the `CREATE VIRTUAL TABLE ... USING fts5(...)`), the FTS entity's `setupQueries` (sync triggers), then the database-level setup (`room_master_table` create + identity-hash insert). If the Room 3 schema-bundle format differs from Room 2 expectations, adapt the reader — the build step is the source of truth. Fallback recipe if the JSON proves brittle: open an empty `FoodCatalogDatabase` on an emulator (no `createFromAsset`), `adb pull` the created file, and fill it with this script (schema comes from Room itself).
8. Insert rows with `executemany` batching (triggers populate the FTS index automatically). Then `PRAGMA user_version = 1`; `INSERT INTO catalog_foods_fts(catalog_foods_fts) VALUES('rebuild')` as a safety net; `PRAGMA journal_mode = DELETE;` `VACUUM;` `PRAGMA integrity_check;`.
9. Write to `app/src/main/assets/databases/food_catalog.db` (create dirs). Print counts (`catalog_foods`, nutrients, serving units), FTS sanity query result, file size, and duration. Expected DB size: roughly 10–25 MB; commit it.
10. Never commit `tools/seed/.cache/`; commit the script, README, and the generated `.db`.

**Asset/schema mechanics recap:** with `createFromAsset`, Room copies only the main DB file (no `-wal`/`-shm`); the script sets `journal_mode = DELETE` so the file is self-contained. Room validates the pre-packaged schema on first open (or trusts the embedded identity hash).

### 6. Testing standards (existing conventions you MUST follow)

- JUnit 4 only (`org.junit.Test`, `Assert.*`), hand-written fakes — **no** MockK/Mockito/Robolectric (none are in the dependency catalog; do not add them for this story). `InstantTaskExecutorRule` only applies to LiveData tests (not needed here).
- When adding methods to a repository/DAO interface, update every fake implementation (lesson from Stories 2.4/2.5). New fakes live next to their tests.
- DAO integration tests belong in `androidTest` (project rule). `FoodCatalogFtsTest` must use the real `BundledSQLiteDriver`; JVM unit tests must not touch Android/Room runtimes.
- End-to-end card: `./gradlew testDebugUnitTest :app:assembleDebug`. Environment preflight per `AGENTS.md`: `echo "JAVA_HOME=$JAVA_HOME ANDROID_HOME=$ANDROID_HOME"` (expect Java 21 Linux, `ANDROID_HOME=$HOME/Android/Sdk`). Instrumented run: start the `medium_phone` emulator (see `create-device` skill; project-context documents the Windows detached launch), then `./gradlew connectedDebugAndroidTest`.
- Latency test must warm up ≥ 10 queries before timing, run ≥ 100 iterations over 2–3 query shapes, assert `average < 50ms` and log max (avoid flaky single-run assertions).

### 7. Architecture compliance guardrails

- `domain/` gains only pure Kotlin (`FoodItem`, `FoodCatalogRepository`, `SearchFoodUseCase`) — no `androidx.room3` imports (AD-1).
- Entities and `FoodSearchRow` never cross `data/`; map with private extensions (AD-3).
- Synchronous DAO calls only; no `suspend`, `Flow`, `runBlocking` in app code (AD-2 as amended for Room 3 Android blocking DAOs).
- New dependencies are registered centrally in `di/AppModule.kt`: `single` DB/DAO/Repository, `factory` UseCase (AD-6). Use `by viewModel()` only when Story 3.2 adds the ViewModel.
- Child entities declare explicit CASCADE FKs + FK indices (AD-7). FTS5 sync is Room-managed via `@Fts5(contentEntity)` triggers (AD-8, evolved).
- No UI in this story: no layouts, colors, strings, dimens, navigation changes. Story 2.6's resource discipline applies whenever 3.2 adds UI.

### 8. Git intelligence / previous story learnings

- Branch `develop`; implementation commits follow `Implement Story X.Y. <short desc>`; merge into `main` via PR. Commit the story file, `sprint-status.yaml`, `epics.md`, and tests together with code.
- **CRLF trap:** the working tree shows ~158 "modified" files that are line-ending noise. Always check `git diff --ignore-cr-at-eol --name-only` before staging; never `git add -A`.
- Recent-story lessons: use-case boundary enforcement and non-null DI were review fixes in 3.4; tests that "seed the fake into the state they assert" were flagged — write real assertions (3.4). Koin constructor changes break every call site/test (2.4). Prefer `@Transaction` DAO helpers for multi-table writes (1.1 P-2 precedent; open debts 2.4-D1/2.5-D1).
- `AGENTS.md` WSL rules apply to all Gradle calls; `local.properties` SDK warning is harmless.

### Project Structure Notes

- New files (all under `app/src/main/java/com/example/myfoodtracker/` unless noted):
  - `data/entity/CatalogFoodEntity.kt`, `CatalogFoodNutrientEntity.kt`, `CatalogFoodServingUnitEntity.kt`, `CatalogFoodFtsEntity.kt`
  - `data/db/FoodCatalogDatabase.kt`, `data/db/FoodSearchRow.kt`
  - `data/dao/FoodCatalogDao.kt`, `data/dao/FtsQueryBuilder.kt`
  - `data/repository/FoodCatalogRepositoryImpl.kt`
  - `domain/model/FoodItem.kt`, `domain/repository/FoodCatalogRepository.kt`, `domain/usecase/SearchFoodUseCase.kt`
  - `app/src/main/assets/databases/food_catalog.db` (generated, committed)
  - `app/schemas/debug/com.example.myfoodtracker.data.db.FoodCatalogDatabase/1.json` (generated, committed)
  - `tools/seed/build_food_catalog_db.py`, `tools/seed/README.md`
  - Tests: `app/src/test/.../data/dao/FtsQueryBuilderTest.kt`, `app/src/test/.../data/repository/FoodCatalogRepositoryImplTest.kt`, `app/src/test/.../domain/usecase/SearchFoodUseCaseTest.kt`, `app/src/androidTest/.../data/FoodCatalogFtsTest.kt`
- Modified: `app/build.gradle.kts`, `gradle/libs.versions.toml`, `.gitignore`, all Room-import files (Task 1 list), `di/AppModule.kt`, `_bmad-output/project-context.md`, `ARCHITECTURE-SPINE.md`, `epics.md`, optional `deferred-work.md`.
- Variances vs `ARCHITECTURE-SPINE.md` structural seed (stale, from before implementation reality): package is `com.example.myfoodtracker` (not `sergeevgk`); the catalog lives in `data/entity/` + `data/db/` (following actual conventions) rather than `data/entity/FoodEntity.kt` (name already taken by the meal-line entity); no `presentation/ui/search/` in this story (Story 3.2).
- `.agents/project_structure.md` does not exist (deferred 3.4-D1) — do not fail the story over it.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 3.1] — story statement, ACs, ARCH-8/9/10.
- [Source: _bmad-output/planning-artifacts/architecture/.../ARCHITECTURE-SPINE.md#AD-2, #AD-8, #Stack, #Structural Seed] — synchronous access rule, FTS engine rule, pinned stack, intended file layout.
- [Source: _bmad-output/planning-artifacts/prds/.../prd.md#FR-3, #FR-4, #NFR-2] — prefix/out-of-order search, ranking intent, <50ms target.
- [Source: _bmad-output/planning-artifacts/prds/.../addendum.md#1.1, #2] — catalog table spec, FTS5 DDL/trigger reference (now superseded by Room `@Fts5`).
- [Source: _bmad-output/planning-artifacts/ux-designs/.../EXPERIENCE.md#Search Autocomplete List] — behavior Story 3.2 will surface.
- [Source: _bmad-output/project-context.md] — layering, DI, testing, sync-execution and resource rules.
- [Source: Room 3.0 release notes https://developer.android.com/jetpack/androidx/releases/room3#3.0.3] — `@Fts5`, blocking-DAO Android exception, coroutines requirement, new coordinates.
- [Source: Room KMP guide https://developer.android.com/kotlin/multiplatform/room] — `BundledSQLiteDriver`, prepackaged DBs Android-only, driver setup.
- [Source: androidx `Fts5.kt` KDoc, `room3-common:3.0.3`] — annotation options (`tokenizer`, `contentEntity`, `prefix`, `detail`), BundledSQLiteDriver FTS5 warning.
- [Source: AOSP `external/sqlite/dist/Android.bp` android-15.0.0_r1/android-16.0.0_r1] — FTS5 not compiled into platform SQLite (`ENABLE_FTS3/4` only, `SQLITE_OMIT_LOAD_EXTENSION`).
- [Source: `androidx.sqlite:sqlite-bundled-android:2.7.1` AAR] — native lib contains `ENABLE_FTS5`, `fts5Bm25Function`, `fts5SnippetFunction`, `fts5HighlightFunction`.
- [Source: USDA FDC data downloads https://fdc.nal.usda.gov/download-datasets] — dataset files, sizes, CC0 1.0 license.
- [Source: `_bmad-output/implementation-artifacts/3-4-remember-user-session-on-device.md`] — review lessons; [Source: `deferred-work.md`] — open debts 2.4-D1/2.5-D1/3.4-D1.

## Dev Agent Record

### Agent Model Used

Muse Spark 1.3 Contributor (opencode-go) via bmad-dev-story workflow, 2026-10-03.

### Debug Log References

- Room 3 KSP failure on first build: `@Relation(parentColumn/entityColumn)` singular form no longer exists in `androidx.room3` — verified via `javap`-equivalent class inspection of `room3-common-jvm-3.0.3.jar` (`parentColumns`/`entityColumns` arrays + explicit `entity`). Fixed `MealWithFoods` and `UserWithGoal` (added `entity = X::class`). KSP green after fix.
- Room 3 schema JSON omits FTS sync triggers (they live in generated `FoodCatalogDatabase_Impl.createAllTables`); seed script mirrors the 4 exact trigger statements and applies `setupQueries` (room_master_table + identity hash `0407e73aa8c8219f3fca19ebe9dc8429`).
- Room 3 plugin schema export path is `app/schemas/<db-fqcn>/1.json` (no `debug/` segment); seed script reads that path.
- Branded `food.csv`/`branded_food.csv` are row-aligned (verified over first 20k rows) but not fdc-sorted; seed uses lockstep streaming + sort of cheap-filter candidates by fdc_id.
- Nutrient progress-log milestone bug (repeated prints) fixed before final run.
- On-device test notice: Per project instructions, agents do NOT run the Android emulator due to environment difficulties. Instrumented on-device testing (`FoodCatalogFtsTest`) requires manual execution on an emulator or device.

### Completion Notes List

- Task 1: Room 3.0.3 migration complete; `./gradlew testDebugUnitTest :app:assembleDebug` green with all 121 pre-existing tests passing (blocking DAOs + `allowMainThreadQueries()` retained; main DB keeps default driver).
- Task 2: catalog DB/DAO/repository/use-case + Koin wiring complete; `bm25()` query accepted by the Room 3 verifier (no `@SkipQueryVerification` needed); schema JSON exported and checked in.
- Task 3: seed run produced 50,500 foods / 50,500 nutrient rows / 109,315 serving units, 16.1 MB, `integrity_check = ok`, `user_version = 1`, `journal_mode = DELETE`; APK contains `assets/databases/food_catalog.db` uncompressed.
- Task 4: 25 new JVM tests (146/146 green); instrumented `FoodCatalogFtsTest` written and `:app:assembleDebugAndroidTest` green; on-device test execution marked for manual verification.
- Task 5: project-context, ARCHITECTURE-SPINE (AD-2 amendment, AD-8 restatement, stack table), epics ARCH-8/9/10, deferred-work 3.1-F1/F2 updated.
- Variances recorded: schema export path; spine `sergeevgk` package + structural-seed layout stale (actual `com.example.myfoodtracker`, catalog under `data/entity/` + `data/db/`); `.agents/project_structure.md` still missing (3.4-D1).

### File List

- `gradle/libs.versions.toml` (modified: room3/sqlite versions, libraries, plugin alias)
- `app/build.gradle.kts` (modified: room3 plugin + deps, schema dir, noCompress db, androidTest test:core)
- `app/src/main/java/com/example/myfoodtracker/data/db/MealWithFoods.kt` (modified: Room 3 `@Relation`)
- `app/src/main/java/com/example/myfoodtracker/data/dao/UserProfileDao.kt` (modified: Room 3 `@Relation` + room3 imports)
- `app/src/main/java/com/example/myfoodtracker/data/db/AppDatabase.kt` (modified: room3 imports)
- `app/src/main/java/com/example/myfoodtracker/data/db/MealEntryEntity.kt` (modified: room3 imports)
- `app/src/main/java/com/example/myfoodtracker/data/db/FoodEntity.kt` (modified: room3 imports)
- `app/src/main/java/com/example/myfoodtracker/data/db/WaterLogEntity.kt` (modified: room3 imports)
- `app/src/main/java/com/example/myfoodtracker/data/db/MealDao.kt` (modified: room3 imports)
- `app/src/main/java/com/example/myfoodtracker/data/db/WaterLogDao.kt` (modified: room3 imports)
- `app/src/main/java/com/example/myfoodtracker/data/entity/UserProfileEntity.kt` (modified: room3 imports)
- `app/src/main/java/com/example/myfoodtracker/data/entity/UserDailyGoalEntity.kt` (modified: room3 imports)
- `app/src/main/java/com/example/myfoodtracker/di/AppModule.kt` (modified: room3 import + catalog DB/DAO/repository/use-case wiring)
- `app/src/main/java/com/example/myfoodtracker/data/entity/CatalogFoodEntity.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/data/entity/CatalogFoodNutrientEntity.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/data/entity/CatalogFoodServingUnitEntity.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/data/entity/CatalogFoodFtsEntity.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/data/db/FoodCatalogDatabase.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/data/db/FoodSearchRow.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/data/dao/FoodCatalogDao.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/data/dao/FtsQueryBuilder.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/domain/model/FoodItem.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/domain/repository/FoodCatalogRepository.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/data/repository/FoodCatalogRepositoryImpl.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/SearchFoodUseCase.kt` (new)
- `app/schemas/com.example.myfoodtracker.data.db.FoodCatalogDatabase/1.json` (new, generated, checked in)
- `app/src/main/assets/databases/food_catalog.db` (new, generated 16.1 MB, committed)
- `tools/seed/build_food_catalog_db.py` (new)
- `tools/seed/README.md` (new)
- `.gitignore` (modified: ignore `tools/seed/.cache/`)
- `app/src/test/java/com/example/myfoodtracker/data/dao/FtsQueryBuilderTest.kt` (new)
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/SearchFoodUseCaseTest.kt` (new)
- `app/src/test/java/com/example/myfoodtracker/data/repository/FoodCatalogRepositoryImplTest.kt` (new)
- `app/src/androidTest/java/com/example/myfoodtracker/data/FoodCatalogFtsTest.kt` (new)
- `_bmad-output/project-context.md` (modified)
- `_bmad-output/planning-artifacts/architecture/architecture-MyFoodTracker-2026-09-06/ARCHITECTURE-SPINE.md` (modified)
- `_bmad-output/planning-artifacts/epics.md` (modified)
- `_bmad-output/implementation-artifacts/deferred-work.md` (modified: 3.1-F1/F2)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (modified: 3-1 → in-progress)
