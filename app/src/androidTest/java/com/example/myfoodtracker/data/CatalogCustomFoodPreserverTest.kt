package com.example.myfoodtracker.data

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.example.myfoodtracker.data.db.CatalogCustomFoodPreserver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Raw-driver round-trip for [CatalogCustomFoodPreserver] (story 6.3, AC #2) —
 * no Room, no Android framework: the helper is plain SQL over
 * `androidx.sqlite.SQLiteConnection`, so `BundledSQLiteDriver().open(":memory:")`
 * is enough to exercise it.
 *
 * The schema below is the **version 1** catalog shape copied verbatim from
 * `app/schemas/com.example.myfoodtracker.data.db.FoodCatalogDatabase/1.json`
 * (tables + indices + the `room_fts_content_sync_*` triggers); the rebuild
 * lambda drops and recreates the identical DDL, exactly like a future
 * `Migration(1, 2)` would do with the *next* exported schema.
 *
 * Key invariants asserted:
 * - custom rows survive **with their primary-key ids kept in place**
 *   (`recipe_ingredients.catalog_food_id` stays valid — no remap needed);
 * - nutrient/unit children re-attach by the surviving `food_id`, no orphans;
 * - everything NOT custom (the seeded ~50k rows on a real device) is gone
 *   after the rebuild — seed refresh stays the future migration's job;
 * - the three `_catalog_custom_*` temp tables are dropped;
 * - the FTS index is only ever written THROUGH the `room_fts_content_sync_*`
 *   triggers — the re-inserted custom food is searchable again.
 */
class CatalogCustomFoodPreserverTest {

    private lateinit var connection: SQLiteConnection

    @Before
    fun setUp() {
        connection = BundledSQLiteDriver().open(":memory:")
    }

    @After
    fun tearDown() {
        connection.close()
    }

    @Test
    fun rebuild_customRowsKeptWithIds_childrenReattached() {
        createV1CatalogSchema()
        // Fail loudly on any FK violations through the whole preservation walk.
        connection.execSQL("PRAGMA foreign_keys = 1")

        // Two seeded catalog rows (the ~50k rest of the asset on a device) and
        // one user-created custom food with a nutrient row and two units.
        connection.execSQL(
            "INSERT INTO catalog_foods (id, name, brand, barcode, base_serving_size, base_serving_unit, is_custom, is_deleted, created_at, updated_at) " +
                "VALUES (1, 'Apple', NULL, NULL, 100.0, 'g', 0, 0, 0, 0)"
        )
        connection.execSQL(
            "INSERT INTO catalog_food_nutrients (food_id, calories, protein_g, carbs_g, fat_g, fiber_g, sugar_g, sodium_mg) " +
                "VALUES (1, 52.0, 0.3, 14.0, 0.2, 2.4, 10.0, 1.0)"
        )
        connection.execSQL(
            "INSERT INTO catalog_foods (id, name, brand, barcode, base_serving_size, base_serving_unit, is_custom, is_deleted, created_at, updated_at) " +
                "VALUES (2, 'Banana', NULL, NULL, 100.0, 'g', 0, 0, 0, 0)"
        )
        connection.execSQL(
            "INSERT INTO catalog_food_nutrients (food_id, calories, protein_g, carbs_g, fat_g, fiber_g, sugar_g, sodium_mg) " +
                "VALUES (2, 89.0, 1.1, 22.8, 0.3, 2.6, 12.2, 1.0)"
        )
        connection.execSQL(
            "INSERT INTO catalog_foods (id, name, brand, barcode, base_serving_size, base_serving_unit, is_custom, is_deleted, created_at, updated_at) " +
                "VALUES (3001, 'My Whey', 'MyProtein', NULL, 30.0, 'g', 1, 0, 1000, 1000)"
        )
        connection.execSQL(
            "INSERT INTO catalog_food_nutrients (food_id, calories, protein_g, carbs_g, fat_g, fiber_g, sugar_g, sodium_mg) " +
                "VALUES (3001, 400.0, 80.0, 8.0, 5.0, 1.0, 3.0, 300.0)"
        )
        connection.execSQL(
            "INSERT INTO catalog_food_serving_units (id, food_id, unit_name, grams_per_unit) " +
                "VALUES (9001, 3001, 'scoop', 30.0)"
        )
        connection.execSQL(
            "INSERT INTO catalog_food_serving_units (id, food_id, unit_name, grams_per_unit) " +
                "VALUES (9002, 3001, 'glas', 250.0)"
        )

        CatalogCustomFoodPreserver.preserveThenRestore(connection) {
            // The future migration's REBUILD step: drop the old schema and
            // recreate the DDL (same v1 DDL here — no version bump exists yet).
            dropV1CatalogSchema()
            createV1CatalogSchema()
        }

        // Custom foods keep their primary-key ids — in place, no remap needed.
        assertEquals(
            1L,
            scalar("SELECT COUNT(*) FROM catalog_foods WHERE id = 3001 AND name = 'My Whey'")
        )
        val nutrientCount = scalar(
            "SELECT COUNT(*) FROM catalog_food_nutrients WHERE food_id = 3001"
        )
        assertEquals(1L, nutrientCount)
        val unitCount = scalar(
            "SELECT COUNT(*) FROM catalog_food_serving_units WHERE food_id = 3001"
        )
        assertEquals(2L, unitCount)
        // Child rows survive with their ids AND values intact.
        assertEquals(
            1L,
            scalar(
                "SELECT COUNT(*) FROM catalog_food_serving_units " +
                    "WHERE id = 9001 AND unit_name = 'scoop' AND grams_per_unit = 30.0"
            )
        )
        assertEquals(
            1L,
            scalar(
                "SELECT COUNT(*) FROM catalog_food_serving_units " +
                    "WHERE id = 9002 AND unit_name = 'glas' AND grams_per_unit = 250.0"
            )
        )

        // No seeded rows survive: the rebuild is destructive for the seed and
        // the preservation helper is only a guarantee for custom data.
        assertEquals(1L, scalar("SELECT COUNT(*) FROM catalog_foods"))
        assertEquals(1L, scalar("SELECT COUNT(*) FROM catalog_food_nutrients"))
        assertEquals(2L, scalar("SELECT COUNT(*) FROM catalog_food_serving_units"))

        // The three temp tables were dropped (they would also die with the
        // connection, but the helper cleans up by contract).
        assertEquals(
            0L,
            scalar(
                "SELECT COUNT(*) FROM sqlite_temp_master WHERE name IN " +
                    "('_catalog_custom_snapshot', '_catalog_custom_nutrients', '_catalog_custom_serving_units')"
            )
        )

        // The FTS index was rebuilt THROUGH the room_fts_content_sync_* AFTER
        // INSERT trigger — the preserved custom food is searchable again.
        assertEquals(
            3001L,
            scalar("SELECT rowid FROM catalog_foods_fts WHERE catalog_foods_fts MATCH 'whey'")
        )
    }

    // --- v1 schema (verbatim from the exported schema 1.json) --------------------------

    /** Creates the version-1 catalog tables, indices, FTS table and sync triggers. */
    private fun createV1CatalogSchema() {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `catalog_foods` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`brand` TEXT, " +
                "`barcode` TEXT, " +
                "`base_serving_size` REAL NOT NULL, " +
                "`base_serving_unit` TEXT NOT NULL, " +
                "`is_custom` INTEGER NOT NULL, " +
                "`is_deleted` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "`updated_at` INTEGER NOT NULL)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_catalog_foods_name` ON `catalog_foods` (`name`)"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_catalog_foods_barcode` ON `catalog_foods` (`barcode`)"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `catalog_food_nutrients` (" +
                "`food_id` INTEGER NOT NULL, " +
                "`calories` REAL NOT NULL, " +
                "`protein_g` REAL NOT NULL, " +
                "`carbs_g` REAL NOT NULL, " +
                "`fat_g` REAL NOT NULL, " +
                "`fiber_g` REAL NOT NULL, " +
                "`sugar_g` REAL NOT NULL, " +
                "`sodium_mg` REAL NOT NULL, " +
                "PRIMARY KEY(`food_id`), " +
                "FOREIGN KEY(`food_id`) REFERENCES `catalog_foods`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_catalog_food_nutrients_food_id` " +
                "ON `catalog_food_nutrients` (`food_id`)"
        )
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `catalog_food_serving_units` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`food_id` INTEGER NOT NULL, " +
                "`unit_name` TEXT NOT NULL, " +
                "`grams_per_unit` REAL NOT NULL, " +
                "FOREIGN KEY(`food_id`) REFERENCES `catalog_foods`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_catalog_food_serving_units_food_id` " +
                "ON `catalog_food_serving_units` (`food_id`)"
        )
        connection.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `catalog_foods_fts` USING FTS5(" +
                "`name`, `brand`, tokenize=`unicode61`, content=`catalog_foods`)"
        )
        connection.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_catalog_foods_fts_BEFORE_UPDATE " +
                "BEFORE UPDATE ON `catalog_foods` BEGIN DELETE FROM `catalog_foods_fts` " +
                "WHERE `rowid`=OLD.`rowid`; END"
        )
        connection.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_catalog_foods_fts_BEFORE_DELETE " +
                "BEFORE DELETE ON `catalog_foods` BEGIN DELETE FROM `catalog_foods_fts` " +
                "WHERE `rowid`=OLD.`rowid`; END"
        )
        connection.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_catalog_foods_fts_AFTER_UPDATE " +
                "AFTER UPDATE ON `catalog_foods` BEGIN INSERT INTO `catalog_foods_fts`" +
                "(`rowid`, `name`, `brand`) VALUES (NEW.`rowid`, NEW.`name`, NEW.`brand`); END"
        )
        connection.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_catalog_foods_fts_AFTER_INSERT " +
                "AFTER INSERT ON `catalog_foods` BEGIN INSERT INTO `catalog_foods_fts`" +
                "(`rowid`, `name`, `brand`) VALUES (NEW.`rowid`, NEW.`name`, NEW.`brand`); END"
        )
    }

    /** Drops the version-1 schema in the safe order a future migration uses. */
    private fun dropV1CatalogSchema() {
        // Triggers live on `catalog_foods` — dropping the table would remove
        // them implicitly, but the migration should own its teardown: drop the
        // FTS machinery first, then children, then the parent.
        connection.execSQL(
            "DROP TRIGGER IF EXISTS room_fts_content_sync_catalog_foods_fts_AFTER_INSERT"
        )
        connection.execSQL(
            "DROP TRIGGER IF EXISTS room_fts_content_sync_catalog_foods_fts_AFTER_UPDATE"
        )
        connection.execSQL(
            "DROP TRIGGER IF EXISTS room_fts_content_sync_catalog_foods_fts_BEFORE_UPDATE"
        )
        connection.execSQL(
            "DROP TRIGGER IF EXISTS room_fts_content_sync_catalog_foods_fts_BEFORE_DELETE"
        )
        connection.execSQL("DROP TABLE IF EXISTS catalog_foods_fts")
        connection.execSQL("DROP TABLE IF EXISTS catalog_food_nutrients")
        connection.execSQL("DROP TABLE IF EXISTS catalog_food_serving_units")
        connection.execSQL("DROP TABLE IF EXISTS catalog_foods")
    }

    /** First column of the first row as a long (COUNT / rowid style scalar). */
    private fun scalar(sql: String): Long {
        val statement = connection.prepare(sql)
        try {
            return if (statement.step()) statement.getLong(0) else -1L
        } finally {
            statement.close()
        }
    }
}
