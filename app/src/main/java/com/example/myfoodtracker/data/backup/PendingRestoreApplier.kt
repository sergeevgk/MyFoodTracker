package com.example.myfoodtracker.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import com.example.myfoodtracker.data.dao.FoodCatalogDao
import com.example.myfoodtracker.data.db.RecipeDao
import com.example.myfoodtracker.data.db.RestoredCustomFood
import com.example.myfoodtracker.data.entity.CatalogFoodEntity
import com.example.myfoodtracker.data.entity.CatalogFoodNutrientEntity
import com.example.myfoodtracker.data.entity.CatalogFoodServingUnitEntity
import com.example.myfoodtracker.data.local.SharedPrefsSessionStorage
import com.example.myfoodtracker.domain.model.CustomFoodBackup
import com.example.myfoodtracker.domain.model.ServingUnitBackup
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase.Companion.CUSTOM_FOODS_TABLE
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase.Companion.MAIN_DB_NAME
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase.Companion.META_TABLE
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase.Companion.SERVING_UNITS_TABLE
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Applies a staged database restore at cold start.
 *
 * [applyPendingDatabaseSwap] **must run before Koin starts**, i.e. before Room
 * has opened the main database. That is why this class is constructed directly
 * by `MainApplication` and is deliberately not a Koin binding (AD-6 exception):
 * a Koin `single` cannot be resolved before Koin exists.
 *
 * Two databases are involved, and they are treated very differently:
 * - `meals_database` is swapped as a file (platform SQLite only);
 * - the restored custom foods are written into `food_catalog` through the Room
 *   DAO **after** Koin started, because that file needs FTS5 (AD-8).
 */
class PendingRestoreApplier(
    private val context: Context
) {

    /**
     * Replaces the live database with the staged one and returns the custom
     * foods carried by the backup.
     *
     * Returns an empty list when nothing is staged or when the staged file is
     * unusable — the app then starts with its current database instead of
     * crashing.
     */
    fun applyPendingDatabaseSwap(): List<CustomFoodBackup>? {
        val stagedFile = File(stagingDirectory(), MAIN_DB_NAME)
        if (!stagedFile.exists()) return null

        return try {
            val customFoods = prepareStagedFile(stagedFile)
            if (customFoods == null) {
                Log.e(LOG_TAG, "The staged restore is unusable and will be discarded")
                discardStagedRestore()
                null
            } else {
                replaceLiveDatabase(stagedFile)
                clearRememberedSession()
                discardStagedRestore()
                customFoods
            }
        } catch (e: Exception) {
            // A failing restore must never crash the app on startup. The live
            // database is left as it was (or replaced only when the copy had
            // already completed) and the staged file is dropped so it cannot be
            // applied twice.
            Log.e(LOG_TAG, "Could not apply the staged database restore", e)
            discardStagedRestore()
            null
        }
    }

    /**
     * Inserts the restored custom foods into the catalog and remaps the
     * restored `recipe_ingredients` catalog links. Called **after** Koin
     * started, and the remap is consumed **before this method returns** —
     * i.e. before any UI exists to read recipes (resolves 5.2-D1).
     *
     * The device's previous custom foods are removed first (a restore replaces
     * state, it does not merge). New ids are allocated by SQLite — the backed-up
     * ids could collide with the seeded catalog of a different asset version.
     * `replaceCustomFoods` returns the carried old→new id mapping (built from
     * the backup's `source_catalog_id`; empty for a legacy pre-6.2 backup),
     * which [RecipeIngredientLinkRemapper] applies: mapped links are rewritten
     * to the new ids; links that no longer resolve in the rebuilt catalog are
     * nulled explicitly — never left dangling.
     *
     * Writes go to `catalog_foods`, so Room's FTS sync triggers index them.
     */
    fun applyCustomFoods(
        foodCatalogDao: FoodCatalogDao,
        recipeDao: RecipeDao,
        foods: List<CustomFoodBackup>
    ) {
        // Units of a food that carries no source_catalog_id have no owner on the
        // restore side (the id is the only link back to the backup) — dropped,
        // counted once (5.2 invariant: a restore never crashes on startup).
        val droppedUnitCount = foods.sumOf { food ->
            if (food.sourceCatalogId == null) food.servingUnits.size else 0
        }
        if (droppedUnitCount > 0) {
            Log.w(
                LOG_TAG,
                "Dropping $droppedUnitCount serving-unit rows: their food carries no source_catalog_id"
            )
        }
        val restored = foods.map { food ->
            val units = if (food.sourceCatalogId != null) {
                food.servingUnits.map { unit ->
                    CatalogFoodServingUnitEntity(
                        foodId = 0,
                        unitName = unit.unitName,
                        gramsPerUnit = unit.gramsPerUnit
                    )
                }
            } else {
                emptyList()
            }
            RestoredCustomFood(
                food = CatalogFoodEntity(
                    name = food.name,
                    brand = food.brand,
                    barcode = food.barcode,
                    baseServingSize = food.baseServingSize,
                    baseServingUnit = food.baseServingUnit,
                    isCustom = 1,
                    isDeleted = food.isDeleted,
                    createdAt = food.createdAt,
                    updatedAt = food.updatedAt
                ),
                nutrient = CatalogFoodNutrientEntity(
                    foodId = 0,
                    calories = food.calories,
                    proteinG = food.proteinG,
                    carbsG = food.carbsG,
                    fatG = food.fatG,
                    fiberG = food.fiberG,
                    sugarG = food.sugarG,
                    sodiumMg = food.sodiumMg
                ),
                sourceId = food.sourceCatalogId,
                servingUnits = units
            )
        }
        val mapping = foodCatalogDao.replaceCustomFoods(restored)
        RecipeIngredientLinkRemapper(recipeDao, foodCatalogDao).remapAfterRestore(mapping)
    }

    // --- Internals --------------------------------------------------------------------

    /**
     * Reads the carried custom foods and strips the three backup-only tables
     * from the staged copy, so the file that replaces the live database has
     * exactly the live schema.
     *
     * Returns `null` when the staged file cannot be opened or fails its own
     * integrity check.
     */
    private fun prepareStagedFile(stagedFile: File): List<CustomFoodBackup>? {
        val database = try {
            SQLiteDatabase.openDatabase(stagedFile.path, null, SQLiteDatabase.OPEN_READWRITE)
        } catch (e: SQLiteException) {
            Log.e(LOG_TAG, "The staged restore does not open as SQLite", e)
            return null
        }
        try {
            // The staged copy still carries the WAL flag in its header; writes
            // must land in the main file, otherwise they would go to a sidecar
            // that never travels.
            val journalMode = database.rawQuery("PRAGMA journal_mode = DELETE", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            if (!journalMode.equals("delete", ignoreCase = true)) {
                Log.e(LOG_TAG, "Failed to switch staged database to rollback journal: journal_mode was $journalMode")
                return null
            }
            if (!isIntegrityOk(database)) return null

            val customFoods = readCustomFoods(database)
            // Read before the drop — the v2 serving-unit table disappears with
            // the other backup-only tables below (story 6.3).
            val servingUnits = readCustomServingUnits(database)
            val customFoodsWithUnits = if (servingUnits.isEmpty()) {
                customFoods
            } else {
                val validFoodIds = customFoods.mapNotNull { it.sourceCatalogId }.toSet()
                val droppedOrphanCount = servingUnits.count { it.sourceCatalogId !in validFoodIds }
                if (droppedOrphanCount > 0) {
                    Log.w(
                        LOG_TAG,
                        "Dropping $droppedOrphanCount serving-unit rows: no matching source_catalog_id in custom foods"
                    )
                }
                val unitsByFoodId = servingUnits.groupBy { it.sourceCatalogId }
                customFoods.map { food ->
                    val foodUnits = food.sourceCatalogId?.let { unitsByFoodId[it] }.orEmpty()
                    if (foodUnits.isEmpty()) {
                        food
                    } else {
                        food.copy(
                            servingUnits = foodUnits.map {
                                ServingUnitBackup(it.unitName, it.gramsPerUnit)
                            }
                        )
                    }
                }
            }

            database.beginTransaction()
            try {
                database.execSQL("DROP TABLE IF EXISTS $META_TABLE")
                database.execSQL("DROP TABLE IF EXISTS $CUSTOM_FOODS_TABLE")
                // Backup-only since format v2; `IF EXISTS` keeps every pre-6.3
                // (v1) staged file working verbatim.
                database.execSQL("DROP TABLE IF EXISTS $SERVING_UNITS_TABLE")
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
            return customFoodsWithUnits
        } catch (e: SQLiteException) {
            Log.e(LOG_TAG, "Could not prepare the staged restore", e)
            return null
        } finally {
            database.close()
            File(stagedFile.path + "-wal").delete()
            File(stagedFile.path + "-shm").delete()
        }
    }

    /**
     * Two-tier column read (story 6.2).
     *
     * The backup written by this app version carries the `source_catalog_id`
     * column — the backup-side catalog id the restored `recipe_ingredients`
     * rows reference, needed to build the restore-time remap. A **pre-6.2
     * backup** has no such column: the first query throws `SQLiteException`
     * and the fallback re-reads the legacy 15-column list with
     * `sourceCatalogId = null` (the remap then relies on the dangling sweep).
     * A missing id **column** must therefore degrade to `null` ids — only the
     * *table itself* being absent falls through to an empty list (outer catch).
     */
    private fun readCustomFoods(database: SQLiteDatabase): List<CustomFoodBackup> =
        try {
            readCustomFoods(database, withSourceId = true)
        } catch (_: Exception) {
            readCustomFoods(database, withSourceId = false)
        }

    private fun readCustomFoods(
        database: SQLiteDatabase,
        withSourceId: Boolean
    ): List<CustomFoodBackup> =
        try {
            val sql = "SELECT name, brand, barcode, base_serving_size, base_serving_unit, " +
                "is_deleted, calories, protein_g, carbs_g, fat_g, fiber_g, sugar_g, " +
                "sodium_mg, created_at, updated_at" +
                if (withSourceId) ", source_catalog_id FROM $CUSTOM_FOODS_TABLE" else " FROM $CUSTOM_FOODS_TABLE"
            database.rawQuery(sql, null).use { cursor ->
                val foods = mutableListOf<CustomFoodBackup>()
                while (cursor.moveToNext()) {
                    foods.add(
                        CustomFoodBackup(
                            name = cursor.getString(0),
                            brand = if (cursor.isNull(1)) null else cursor.getString(1),
                            barcode = if (cursor.isNull(2)) null else cursor.getString(2),
                            baseServingSize = cursor.getDouble(3),
                            baseServingUnit = cursor.getString(4),
                            isDeleted = cursor.getInt(5),
                            calories = cursor.getDouble(6),
                            proteinG = cursor.getDouble(7),
                            carbsG = cursor.getDouble(8),
                            fatG = cursor.getDouble(9),
                            fiberG = cursor.getDouble(10),
                            sugarG = cursor.getDouble(11),
                            sodiumMg = cursor.getDouble(12),
                            createdAt = cursor.getLong(13),
                            updatedAt = cursor.getLong(14),
                            sourceCatalogId = if (withSourceId && !cursor.isNull(15)) cursor.getLong(15) else null
                        )
                    )
                }
                foods
            }
        } catch (e: Exception) {
            if (withSourceId) throw e
            Log.w(LOG_TAG, "The staged backup carries no readable custom foods", e)
            emptyList()
        }

    // --- Serving-unit read (format v2, story 6.3) ----------------------------------------

    /**
     * Data-layer-only projection row for one `myfoodtracker_custom_serving_units`
     * line of a v2 backup. `sourceCatalogId` links back to the carried custom
     * food; restore re-allocates the catalog unit id.
     */
    private data class ServingUnitBackupFoodKey(
        val sourceCatalogId: Long,
        val unitName: String,
        val gramsPerUnit: Double
    )

    /**
     * Reads the serving units of the carried custom foods. **A missing table is
     * the v1 case and must degrade silently** (unlike `readCustomFoods`'s
     * missing *column* re-throwing one tier): there is no second tier here —
     * the table is simply absent in every pre-6.3 file.
     */
    private fun readCustomServingUnits(database: SQLiteDatabase): List<ServingUnitBackupFoodKey> =
        try {
            database.rawQuery(
                "SELECT source_catalog_id, unit_name, grams_per_unit FROM $SERVING_UNITS_TABLE",
                null
            ).use { cursor ->
                val units = mutableListOf<ServingUnitBackupFoodKey>()
                while (cursor.moveToNext()) {
                    units.add(
                        ServingUnitBackupFoodKey(
                            sourceCatalogId = cursor.getLong(0),
                            unitName = cursor.getString(1),
                            gramsPerUnit = cursor.getDouble(2)
                        )
                    )
                }
                units
            }
        } catch (e: SQLiteException) {
            Log.w(LOG_TAG, "The staged backup carries no serving-unit table (v1 file)", e)
            emptyList()
        }

    private fun isIntegrityOk(database: SQLiteDatabase): Boolean =
        try {
            database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
            }
        } catch (e: SQLiteException) {
            false
        }

    /**
     * Removes the old database **with its sidecars** before writing the restored
     * file. Replaying stale WAL frames against a new file would corrupt it.
     */
    private fun replaceLiveDatabase(stagedFile: File) {
        val liveFile = context.getDatabasePath(MAIN_DB_NAME)
        val directory = liveFile.parentFile
        if (directory != null && !directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create the database directory")
        }
        val tmpFile = File(directory, "$MAIN_DB_NAME.restore_tmp")
        if (tmpFile.exists()) tmpFile.delete()
        try {
            FileInputStream(stagedFile).use { input ->
                FileOutputStream(tmpFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                    output.fd.sync()
                }
            }
            context.deleteDatabase(MAIN_DB_NAME)
            if (!tmpFile.renameTo(liveFile)) {
                throw IOException("Could not move restored database into place")
            }
        } catch (e: Exception) {
            tmpFile.delete()
            throw e
        }
    }

    /**
     * The restored profile set may differ from this device's, so the stale
     * remembered id is dropped and the user logs in with the restored passcode.
     * `commit()` (not `apply()`) because the process is killed right after.
     */
    private fun clearRememberedSession() {
        context.getSharedPreferences(SharedPrefsSessionStorage.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun discardStagedRestore() {
        val stagingDirectory = stagingDirectory()
        if (stagingDirectory.exists() && !stagingDirectory.deleteRecursively()) {
            Log.w(LOG_TAG, "Could not delete the restore staging directory $stagingDirectory")
        }
    }

    private fun stagingDirectory(): File = File(context.filesDir, STAGING_DIRECTORY_NAME)

    companion object {
        /** App-private staging location; must survive a process kill, not a cache wipe. */
        const val STAGING_DIRECTORY_NAME = "pending_restore"


        private const val LOG_TAG = "PendingRestoreApplier"
    }
}
