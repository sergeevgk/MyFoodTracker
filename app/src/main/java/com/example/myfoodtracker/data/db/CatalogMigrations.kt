package com.example.myfoodtracker.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Every catalog-database `Migration` the builder is wired with.
 *
 * Register for Room by adding the migration to [ALL]; `di/AppModule.kt` spreads
 * this array into `addMigrations(*ALL)`. Because the destructive
 * `fallbackToDestructiveMigration()` path was removed from the catalog builder
 * (story 6.3 / deferred-work item 3.1-F1), **a version bump without a
 * registered `Migration` here fails fast at the first database open** instead
 * of silently re-copying the bundled asset and wiping every `is_custom = 1`
 * row plus the ~50k seeded rows.
 *
 * EMPTY today: `FoodCatalogDatabase` is at version 1, so no migration exists
 * yet and `addMigrations(...)` is behaviorally inert — that is the intended
 * guard (fail fast later, never wipe).
 *
 * Recipe for the future bump (see [CatalogCustomFoodPreserver]):
 *
 * ```
 * object CatalogMigrations {
 *     val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
 *
 *     // Story 3.1 exported the reference schema to
 *     // app/schemas/com.example.myfoodtracker.data.db.FoodCatalogDatabase/<n>.json
 *     // Copy the DROP/CREATE statements (tables + indices + the two
 *     // room_fts_content_sync_* triggers) verbatim from the NEXT exported
 *     // schema JSON — never hand-write them and never write the FTS table
 *     // directly.
 *     private val MIGRATION_1_2 = object : Migration(1, 2) {
 *         override suspend fun migrate(connection: SQLiteConnection) {
 *             CatalogCustomFoodPreserver.preserveThenRestore(connection) {
 *                 // 1) DROP TABLE IF EXISTS the old tables (child tables first:
 *                 //    the FTS sync triggers, `catalog_foods_fts`,
 *                 //    `catalog_food_nutrients`, `catalog_food_serving_units`,
 *                 //    `catalog_foods`).
 *                 // 2) CREATE the new-shape tables/indices/FTS table + the two
 *                 //    `room_fts_content_sync_*` triggers taken verbatim from
 *                 //    the exported <2>.json (create `catalog_foods` FIRST —
 *                 //    the children hold foreign keys to it).
 *             }
 *         }
 *     }
 * }
 * ```
 */
object CatalogMigrations {

    /** Wired into `addMigrations(...)` by `di/AppModule.kt`; empty at version 1. */
    val ALL: Array<Migration> = arrayOf()
}

/**
 * The preservation path a future catalog migration runs instead of ripping the
 * user's data out (story 6.3 / deferred-work item 3.1-F1).
 *
 * The catalog schema is special-case material: it is seeded from a bundled
 * asset AND extended by the user's own foods (`is_custom = 1` rows in
 * `catalog_foods`, with their `catalog_food_nutrients` /
 * `catalog_food_serving_units` children). A destructive rebuild must therefore
 * take a **snapshot of the custom rows → let [rebuild] recreate the (new)
 * schema → restore the snapshot rows with their primary-key ids kept in place**:
 *
 * 1. `CREATE TEMP TABLE _catalog_custom_snapshot AS SELECT * FROM catalog_foods
 *    WHERE is_custom = 1`
 * 2. `CREATE TEMP TABLE _catalog_custom_nutrients AS SELECT n.* FROM
 *    catalog_food_nutrients AS n JOIN _catalog_custom_snapshot AS s ON
 *    s.id = n.food_id`
 * 3. `CREATE TEMP TABLE _catalog_custom_serving_units AS SELECT u.* FROM
 *    catalog_food_serving_units AS u JOIN _catalog_custom_snapshot AS s ON
 *    s.id = u.food_id`
 * 4. `rebuild()` — drops and recreates all four catalog tables from the NEW
 *    exported schema DDL, including `catalog_foods_fts` and the two
 *    `room_fts_content_sync_*` triggers (they are part of the exported schema,
 *    never hand-written; the FTS index is only ever written THROUGH them)
 * 5. `INSERT INTO catalog_foods SELECT * FROM _catalog_custom_snapshot` — the
 *    copied `id` column carries SQLite's explicit-value rule for
 *    `INTEGER PRIMARY KEY AUTOINCREMENT`, so **custom foods keep their ids**
 *    and `recipe_ingredients.catalog_food_id` links stay valid without any
 *    remap (`sqlite_sequence` is advanced accordingly). Only the backup
    * restore path re-allocates ids (story 6.2) — never an app update.
 * 6. `INSERT INTO catalog_food_nutrients SELECT * FROM
 *    _catalog_custom_nutrients`; same for serving units — children re-attach
 *    by the surviving `food_id`, so no orphaned child rows remain.
 * 7. the three temp tables are dropped in `finally` (statement 3-4-style
 *    cleanup; they would also die with the connection, but never rely on that).
 *
 * **.intended call shape inside a Migration:**
 *
 * ```
 * object : Migration(N, N + 1) {
 *     override suspend fun migrate(connection: SQLiteConnection) {
 *         CatalogCustomFoodPreserver.preserveThenRestore(connection) {
 *             // new-schema DDL copied from the exported schema JSON
 *         }
 *     }
 * }
 * ```
 *
 * The `suspend` on `Migration.migrate` is Room's framework-mandated KMP
 * signature (as is the `SQLiteConnection` parameter) — not a project-level
 * decision; the no-suspend / no-coroutines rule (AD-2) applies to project
 * code, which stays synchronous. Runs as raw SQL over the driver
 * (`BundledSQLiteDriver`), so it is testable without Room and independent of
 * any specific target-version schema.
 *
 * **Known limitation (document here, revisit only when a bump actually needs
 * it):** a future bump that ALSO ships a new seed asset does not refresh the
 * seed for existing installs — `createFromAsset` copies only on first create,
 * and this rebuild runs code-side without touching the asset. Seed refresh +
 * custom preservation combined would become an export→asset-reimport design.
 */
object CatalogCustomFoodPreserver {

    /**
     * Snapshot → rebuild → re-insert. All three phases belong inside the same
     * migration call (`Migration.migrate` already runs in Room's transaction).
     */
    fun preserveThenRestore(
        connection: SQLiteConnection,
        rebuild: () -> Unit
    ) {
        // Defensively drop any stale temp tables from an interrupted run.
        connection.execSQL("DROP TABLE IF EXISTS _catalog_custom_snapshot")
        connection.execSQL("DROP TABLE IF EXISTS _catalog_custom_nutrients")
        connection.execSQL("DROP TABLE IF EXISTS _catalog_custom_serving_units")

        // Phases 1–3: snapshot the custom foods and their child tables.
        connection.execSQL(
            """
            CREATE TEMP TABLE _catalog_custom_snapshot AS
            SELECT * FROM catalog_foods WHERE is_custom = 1
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE TEMP TABLE _catalog_custom_nutrients AS
            SELECT n.* FROM catalog_food_nutrients AS n
            JOIN _catalog_custom_snapshot AS s ON s.id = n.food_id
            """.trimIndent()
        )
        connection.execSQL(
            """
            CREATE TEMP TABLE _catalog_custom_serving_units AS
            SELECT u.* FROM catalog_food_serving_units AS u
            JOIN _catalog_custom_snapshot AS s ON s.id = u.food_id
            """.trimIndent()
        )

        try {
            // Phase 4: the migration's own DROP/CREATE DDL (new schema).
            rebuild()

            // Phase 5: ids copied explicitly → custom foods keep their ids.
            connection.execSQL(
                "INSERT INTO catalog_foods SELECT * FROM _catalog_custom_snapshot"
            )
            // Phase 6: children re-attach by the surviving food_id.
            connection.execSQL(
                "INSERT INTO catalog_food_nutrients SELECT * FROM _catalog_custom_nutrients"
            )
            connection.execSQL(
                "INSERT INTO catalog_food_serving_units SELECT * FROM _catalog_custom_serving_units"
            )
        } finally {
            // Phase 7: never leak temp tables (best-effort; DROP here is
            // cleanup — they also die with the connection).
            connection.execSQL("DROP TABLE IF EXISTS _catalog_custom_snapshot")
            connection.execSQL("DROP TABLE IF EXISTS _catalog_custom_nutrients")
            connection.execSQL("DROP TABLE IF EXISTS _catalog_custom_serving_units")
        }
    }
}
