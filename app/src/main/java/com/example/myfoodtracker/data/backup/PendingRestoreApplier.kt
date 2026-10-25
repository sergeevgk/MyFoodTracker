package com.example.myfoodtracker.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import com.example.myfoodtracker.data.dao.FoodCatalogDao
import com.example.myfoodtracker.data.entity.CatalogFoodEntity
import com.example.myfoodtracker.data.entity.CatalogFoodNutrientEntity
import com.example.myfoodtracker.data.local.SharedPrefsSessionStorage
import com.example.myfoodtracker.domain.model.CustomFoodBackup
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase.Companion.CUSTOM_FOODS_TABLE
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase.Companion.MAIN_DB_NAME
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase.Companion.META_TABLE
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
     * Inserts the restored custom foods into the catalog. Called **after** Koin
     * started.
     *
     * The device's previous custom foods are removed first (a restore replaces
     * state, it does not merge). New ids are allocated by SQLite — the backed-up
     * ids could collide with the seeded catalog of a different asset version.
     * Writes go to `catalog_foods`, so Room's FTS sync triggers index them.
     */
    fun applyCustomFoods(foodCatalogDao: FoodCatalogDao, foods: List<CustomFoodBackup>) {
        val pairs = foods.map { food ->
            Pair(
                CatalogFoodEntity(
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
                CatalogFoodNutrientEntity(
                    foodId = 0,
                    calories = food.calories,
                    proteinG = food.proteinG,
                    carbsG = food.carbsG,
                    fatG = food.fatG,
                    fiberG = food.fiberG,
                    sugarG = food.sugarG,
                    sodiumMg = food.sodiumMg
                )
            )
        }
        foodCatalogDao.replaceCustomFoods(pairs)
    }

    // --- Internals --------------------------------------------------------------------

    /**
     * Reads the carried custom foods and strips the two backup-only tables from
     * the staged copy, so the file that replaces the live database has exactly
     * the live schema.
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

            database.beginTransaction()
            try {
                database.execSQL("DROP TABLE IF EXISTS $META_TABLE")
                database.execSQL("DROP TABLE IF EXISTS $CUSTOM_FOODS_TABLE")
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
            return customFoods
        } catch (e: SQLiteException) {
            Log.e(LOG_TAG, "Could not prepare the staged restore", e)
            return null
        } finally {
            database.close()
            File(stagedFile.path + "-wal").delete()
            File(stagedFile.path + "-shm").delete()
        }
    }

    private fun readCustomFoods(database: SQLiteDatabase): List<CustomFoodBackup> =
        try {
            database.rawQuery(
                "SELECT name, brand, barcode, base_serving_size, base_serving_unit, " +
                    "is_deleted, calories, protein_g, carbs_g, fat_g, fiber_g, sugar_g, " +
                    "sodium_mg, created_at, updated_at FROM $CUSTOM_FOODS_TABLE",
                null
            ).use { cursor ->
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
                            updatedAt = cursor.getLong(14)
                        )
                    )
                }
                foods
            }
        } catch (e: SQLiteException) {
            Log.w(LOG_TAG, "The staged backup carries no readable custom foods", e)
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
