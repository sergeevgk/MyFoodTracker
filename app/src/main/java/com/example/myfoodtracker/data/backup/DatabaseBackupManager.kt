package com.example.myfoodtracker.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.example.myfoodtracker.data.dao.FoodCatalogDao
import com.example.myfoodtracker.domain.model.BackupFacts
import com.example.myfoodtracker.domain.model.BackupValidation
import com.example.myfoodtracker.domain.model.CustomFoodBackup
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase.Companion.CUSTOM_FOODS_TABLE
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase.Companion.EXPECTED_USER_VERSION
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase.Companion.FORMAT_VERSION
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase.Companion.MAIN_DB_NAME
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase.Companion.META_KEY_APP_VERSION
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase.Companion.META_KEY_CREATED_AT
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase.Companion.META_KEY_CUSTOM_FOODS_COUNT
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase.Companion.META_KEY_FORMAT_VERSION
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase.Companion.META_KEY_IDENTITY_HASH
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase.Companion.META_KEY_MAIN_DB_NAME
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase.Companion.META_KEY_MAIN_USER_VERSION
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase.Companion.META_TABLE
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase.Companion.REQUIRED_TABLES
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Owns every file and SQLite operation of the database backup/restore flow.
 *
 * Lives in the data layer because it needs a [Context] (AD-1). It deliberately
 * works on the **file** of the main database with the platform
 * `android.database.sqlite.SQLiteDatabase` — never with Room (Room 3 exposes no
 * raw-SQL API) and never on `food_catalog` (that one needs FTS5 and is therefore
 * only reachable through [FoodCatalogDao]).
 *
 * Every public method throws on failure; the Fragment catches and maps the
 * exception to `BackupErrorReason.IO_ERROR`.
 */
class DatabaseBackupManager(
    private val context: Context,
    private val foodCatalogDao: FoodCatalogDao,
    private val backupUseCase: BackupDatabaseUseCase
) {

    // --- Backup ---------------------------------------------------------------------

    /**
     * Materialises the backup in `cacheDir/backups/<fileName>` and returns it.
     *
     * The path is deterministic, so the caller can recover the file after a
     * configuration change without keeping a [File] in a Fragment field.
     */
    fun createBackupFile(fileName: String): File {
        val liveFile = context.getDatabasePath(MAIN_DB_NAME)
        if (!liveFile.exists()) {
            throw IOException("The live database does not exist yet")
        }
        checkpointWal(liveFile)

        val destination = cachedBackupFile(fileName)
        val directory = destination.parentFile
        if (directory != null && !directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create the backup directory")
        }

        try {
            liveFile.copyTo(destination, overwrite = true)
            appendBackupTables(destination)
        } catch (e: Exception) {
            deleteSidecars(destination)
            destination.delete()
            throw e
        }
        return destination
    }

    /** Streams the cached backup into the user-picked `content://` document. */
    fun writeBackupTo(uri: Uri, fileName: String) {
        val cached = cachedBackupFile(fileName)
        if (!cached.exists()) {
            throw IOException("The prepared backup is missing")
        }
        var documentCreated = false
        try {
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Could not open the selected document for writing")
            documentCreated = true
            output.use { stream ->
                FileInputStream(cached).use { input -> input.copyTo(stream) }
                stream.flush()
            }
        } catch (e: Exception) {
            // AC #10: do not leave a half-written backup in the user's chosen
            // location. Deleting the created document is best effort — some
            // providers refuse it, and that must not mask the original error.
            if (documentCreated) deleteUserDocument(uri)
            throw e
        }
    }

    fun deleteCachedBackup(fileName: String) {
        val cached = cachedBackupFile(fileName)
        if (cached.exists() && !cached.delete()) {
            Log.w(LOG_TAG, "Could not delete the cached backup $cached")
        }
    }

    // --- Restore --------------------------------------------------------------------

    /**
     * Copies the picked document into the app cache and validates it.
     *
     * A candidate that fails validation is deleted immediately — only a
     * [BackupValidation.Valid] candidate stays on disk for [stageRestore].
     */
    fun validateCandidate(uri: Uri): BackupValidation {
        val candidate = cachedCandidateFile()
        deleteCachedCandidate()
        copyUriToFile(uri, candidate)
        val validation = backupUseCase(collectFacts(candidate))
        if (validation !is BackupValidation.Valid) {
            deleteCachedCandidate()
        }
        return validation
    }

    /** The validated candidate currently sitting in the cache. */
    fun cachedCandidateFile(): File = File(restoreDirectory(), CANDIDATE_FILE_NAME)

    fun deleteCachedCandidate() {
        val candidate = cachedCandidateFile()
        if (candidate.exists() && !candidate.delete()) {
            Log.w(LOG_TAG, "Could not delete the cached restore candidate $candidate")
        }
    }

    /**
     * Durably stages the validated candidate in `filesDir/pending_restore/`.
     *
     * The file is written to a temporary name and renamed, so a failure leaves
     * no partially staged restore behind. `fd.sync()` makes the write survive a
     * process kill right after the user confirms the restart.
     */
    fun stageRestore(candidate: File): File {
        if (!candidate.exists()) {
            throw IOException("The validated restore candidate is missing")
        }
        val stagingDirectory = stagingDirectory()
        if (!stagingDirectory.exists() && !stagingDirectory.mkdirs()) {
            throw IOException("Could not create the restore staging directory")
        }
        val stagedFile = File(stagingDirectory, MAIN_DB_NAME)
        val temporaryFile = File(stagingDirectory, "$MAIN_DB_NAME.tmp")
        if (temporaryFile.exists()) temporaryFile.delete()

        try {
            FileInputStream(candidate).use { input ->
                FileOutputStream(temporaryFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                    output.fd.sync()
                }
            }
            if (stagedFile.exists() && !stagedFile.delete()) {
                throw IOException("Could not replace the previously staged restore")
            }
            if (!temporaryFile.renameTo(stagedFile)) {
                throw IOException("Could not stage the restored database")
            }
        } catch (e: Exception) {
            temporaryFile.delete()
            throw e
        }
        return stagedFile
    }

    /** Aborts a staged restore; called when the user cancels a dialog. */
    fun discardStagedRestore() {
        val stagingDirectory = stagingDirectory()
        if (stagingDirectory.exists() && !stagingDirectory.deleteRecursively()) {
            Log.w(LOG_TAG, "Could not delete the restore staging directory $stagingDirectory")
        }
    }

    // --- Backup creation internals ---------------------------------------------------

    /**
     * WAL checkpoint before the byte copy.
     *
     * Room 3 opens the main database in WAL mode, so committed rows may live only
     * in `meals_database-wal` until a checkpoint. Copying without checkpointing
     * produces a backup that silently misses recent writes.
     */
    private fun checkpointWal(liveFile: File) {
        val database = SQLiteDatabase.openDatabase(
            liveFile.path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        try {
            val journalMode = querySingleString(database, "PRAGMA journal_mode")
            if (journalMode == null || !journalMode.equals("wal", ignoreCase = true)) {
                return
            }
            if (checkpoint(database)) return
            if (checkpoint(database)) return
            throw IOException("The database WAL is busy and cannot be checkpointed")
        } finally {
            database.close()
        }
    }

    /** `PRAGMA wal_checkpoint(TRUNCATE)` returns `(busy, log, checkpointed)`. */
    private fun checkpoint(database: SQLiteDatabase): Boolean =
        database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
            cursor.moveToFirst() && cursor.getInt(0) == 0
        }

    /**
     * Appends the two backup-only tables to the copied database.
     *
     * The copy keeps the WAL flag of the live database in its header, so it must
     * be switched to a rollback journal first — otherwise the writes land in a
     * `<copy>-wal` sidecar that never travels with the file.
     */
    private fun appendBackupTables(copyFile: File) {
        val customFoods = readCustomFoods()
        val database = SQLiteDatabase.openDatabase(
            copyFile.path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        try {
            switchToRollbackJournal(database)
            database.execSQL(CREATE_META_TABLE_SQL)
            database.execSQL(CREATE_CUSTOM_FOODS_SQL)

            val userVersion = readUserVersion(database)
            val identityHash = readIdentityHash(database)

            database.beginTransaction()
            try {
                writeMeta(database, userVersion, identityHash, customFoods.size)
                customFoods.forEach { insertCustomFood(database, it) }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        } finally {
            database.close()
        }
        deleteSidecars(copyFile)
    }

    private fun switchToRollbackJournal(database: SQLiteDatabase) {
        // The result is "delete" once the conversion succeeded; the statement
        // must run outside a transaction.
        val journalMode = database.rawQuery("PRAGMA journal_mode = DELETE", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        if (!journalMode.equals("delete", ignoreCase = true)) {
            throw SQLiteException("Failed to switch database to rollback journal: journal_mode was $journalMode")
        }
    }

    private fun writeMeta(
        database: SQLiteDatabase,
        userVersion: Int,
        identityHash: String?,
        customFoodCount: Int
    ) {
        val values = linkedMapOf(
            META_KEY_FORMAT_VERSION to FORMAT_VERSION.toString(),
            META_KEY_APP_VERSION to appVersionName(),
            META_KEY_CREATED_AT to System.currentTimeMillis().toString(),
            META_KEY_MAIN_USER_VERSION to userVersion.toString(),
            META_KEY_CUSTOM_FOODS_COUNT to customFoodCount.toString(),
            META_KEY_MAIN_DB_NAME to MAIN_DB_NAME
        )
        identityHash?.let { values[META_KEY_IDENTITY_HASH] = it }

        values.forEach { (key, value) ->
            database.execSQL(
                "INSERT OR REPLACE INTO $META_TABLE (key, value) VALUES (?, ?)",
                arrayOf(key, value)
            )
        }
    }

    /**
     * Reads the user's own foods from the catalog through the Room DAO. The
     * catalog file itself is never opened with the platform SQLite — it needs
     * FTS5 (AD-8).
     */
    private fun readCustomFoods(): List<CustomFoodBackup> {
        val foods = foodCatalogDao.getCustomFoods()
        if (foods.isEmpty()) return emptyList()
        val nutrients = foodCatalogDao.getCustomFoodNutrients().associateBy { it.foodId }
        return foods.map { food ->
            val nutrient = nutrients[food.id]
            CustomFoodBackup(
                name = food.name,
                brand = food.brand,
                barcode = food.barcode,
                baseServingSize = food.baseServingSize,
                baseServingUnit = food.baseServingUnit,
                isDeleted = food.isDeleted,
                calories = nutrient?.calories ?: 0.0,
                proteinG = nutrient?.proteinG ?: 0.0,
                carbsG = nutrient?.carbsG ?: 0.0,
                fatG = nutrient?.fatG ?: 0.0,
                fiberG = nutrient?.fiberG ?: 0.0,
                sugarG = nutrient?.sugarG ?: 0.0,
                sodiumMg = nutrient?.sodiumMg ?: 0.0,
                createdAt = food.createdAt,
                updatedAt = food.updatedAt
            )
        }
    }

    private fun insertCustomFood(database: SQLiteDatabase, food: CustomFoodBackup) {
        database.execSQL(
            "INSERT INTO $CUSTOM_FOODS_TABLE (" +
                "name, brand, barcode, base_serving_size, base_serving_unit, is_deleted, " +
                "calories, protein_g, carbs_g, fat_g, fiber_g, sugar_g, sodium_mg, " +
                "created_at, updated_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>(
                food.name,
                food.brand,
                food.barcode,
                food.baseServingSize,
                food.baseServingUnit,
                food.isDeleted,
                food.calories,
                food.proteinG,
                food.carbsG,
                food.fatG,
                food.fiberG,
                food.sugarG,
                food.sodiumMg,
                food.createdAt,
                food.updatedAt
            )
        )
    }

    // --- Restore validation internals --------------------------------------------------

    private fun copyUriToFile(uri: Uri, destination: File) {
        val directory = destination.parentFile
        if (directory != null && !directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create the restore cache directory")
        }
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open the selected file")
            input.use { source ->
                FileOutputStream(destination).use { output ->
                    source.copyTo(output)
                    output.flush()
                    output.fd.sync()
                }
            }
        } catch (e: Exception) {
            destination.delete()
            throw e
        }
    }

    /**
     * Collects the snapshot the use case decides on. An unreadable table or
     * pragma degrades into a *fact* (`null`/`false`), never into an exception.
     */
    private fun collectFacts(candidate: File): BackupFacts {
        val liveIdentityHash = readLiveIdentityHash()
        val database = try {
            SQLiteDatabase.openDatabase(candidate.path, null, SQLiteDatabase.OPEN_READONLY)
        } catch (e: SQLiteException) {
            Log.w(LOG_TAG, "The selected file does not open as SQLite", e)
            return notADatabaseFacts(liveIdentityHash, fileReadable = false)
        }
        try {
            val meta = readMeta(database)
                ?: return notADatabaseFacts(liveIdentityHash, fileReadable = true)
            return BackupFacts(
                fileReadable = true,
                metaTablePresent = true,
                integrityOk = isIntegrityOk(database),
                formatVersion = meta[META_KEY_FORMAT_VERSION]?.toIntOrNull(),
                declaredUserVersion = meta[META_KEY_MAIN_USER_VERSION]?.toIntOrNull(),
                actualUserVersion = readUserVersion(database),
                expectedUserVersion = EXPECTED_USER_VERSION,
                missingTables = REQUIRED_TABLES.filterNot { tableExists(database, it) },
                candidateIdentityHash = readIdentityHash(database),
                liveIdentityHash = liveIdentityHash,
                declaredCustomFoodCount = meta[META_KEY_CUSTOM_FOODS_COUNT]?.toIntOrNull(),
                actualCustomFoodCount = readCustomFoodCount(database)
            )
        } catch (e: SQLiteException) {
            Log.w(LOG_TAG, "Could not read the restore candidate facts", e)
            return notADatabaseFacts(liveIdentityHash, fileReadable = true)
        } finally {
            database.close()
        }
    }

    private fun notADatabaseFacts(
        liveIdentityHash: String?,
        fileReadable: Boolean
    ): BackupFacts = BackupFacts(
        fileReadable = fileReadable,
        metaTablePresent = false,
        integrityOk = false,
        formatVersion = null,
        declaredUserVersion = null,
        actualUserVersion = null,
        expectedUserVersion = EXPECTED_USER_VERSION,
        missingTables = emptyList(),
        candidateIdentityHash = null,
        liveIdentityHash = liveIdentityHash,
        declaredCustomFoodCount = null,
        actualCustomFoodCount = 0
    )

    /** `null` when the meta table is absent — never an exception. */
    private fun readMeta(database: SQLiteDatabase): Map<String, String>? =
        try {
            database.rawQuery("SELECT key, value FROM $META_TABLE", null).use { cursor ->
                val values = mutableMapOf<String, String>()
                while (cursor.moveToNext()) {
                    values[cursor.getString(0)] = cursor.getString(1)
                }
                values
            }
        } catch (e: SQLiteException) {
            null
        }

    private fun isIntegrityOk(database: SQLiteDatabase): Boolean =
        try {
            database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
            }
        } catch (e: SQLiteException) {
            false
        }

    private fun tableExists(database: SQLiteDatabase, table: String): Boolean =
        database.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
            arrayOf(table)
        ).use { cursor -> cursor.moveToFirst() }

    private fun readCustomFoodCount(database: SQLiteDatabase): Int =
        try {
            database.rawQuery("SELECT COUNT(*) FROM $CUSTOM_FOODS_TABLE", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        } catch (e: SQLiteException) {
            0
        }

    private fun readUserVersion(database: SQLiteDatabase): Int =
        database.rawQuery("PRAGMA user_version", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    /**
     * Reads the live database's Room identity hash at runtime (never hard-coded).
     * `null` when the live database does not exist yet — the validator then skips
     * the identity comparison.
     */
    private fun readLiveIdentityHash(): String? {
        val liveFile = context.getDatabasePath(MAIN_DB_NAME)
        if (!liveFile.exists()) return null
        return try {
            val database = SQLiteDatabase.openDatabase(
                liveFile.path,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            try {
                readIdentityHash(database)
            } finally {
                database.close()
            }
        } catch (e: SQLiteException) {
            Log.w(LOG_TAG, "Could not read the live database identity hash", e)
            null
        }
    }

    private fun readIdentityHash(database: SQLiteDatabase): String? =
        try {
            database.rawQuery(
                "SELECT identity_hash FROM room_master_table WHERE id = 42",
                null
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (e: SQLiteException) {
            null
        }

    private fun querySingleString(database: SQLiteDatabase, sql: String): String? =
        database.rawQuery(sql, null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun appVersionName(): String =
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                ?: FALLBACK_APP_VERSION
        } catch (e: Exception) {
            FALLBACK_APP_VERSION
        }

    private fun deleteSidecars(databaseFile: File) {
        File(databaseFile.path + "-wal").delete()
        File(databaseFile.path + "-shm").delete()
    }

    private fun deleteUserDocument(uri: Uri) {
        try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Could not delete the partially written document $uri", e)
        }
    }

    private fun backupsDirectory(): File = File(context.cacheDir, BACKUPS_DIR)

    private fun restoreDirectory(): File = File(context.cacheDir, RESTORE_DIR)

    private fun stagingDirectory(): File =
        File(context.filesDir, PendingRestoreApplier.STAGING_DIRECTORY_NAME)

    private fun cachedBackupFile(fileName: String): File = File(backupsDirectory(), fileName)

    companion object {
        private const val LOG_TAG = "DatabaseBackupManager"

        private const val BACKUPS_DIR = "backups"
        private const val RESTORE_DIR = "restore"
        private const val CANDIDATE_FILE_NAME = "candidate.db"
        private const val FALLBACK_APP_VERSION = "1.0"

        private val CREATE_META_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS $META_TABLE (
                key   TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
        """.trimIndent()

        private val CREATE_CUSTOM_FOODS_SQL = """
            CREATE TABLE IF NOT EXISTS $CUSTOM_FOODS_TABLE (
                name              TEXT    NOT NULL,
                brand             TEXT,
                barcode           TEXT,
                base_serving_size REAL    NOT NULL,
                base_serving_unit TEXT    NOT NULL,
                is_deleted        INTEGER NOT NULL,
                calories          REAL    NOT NULL,
                protein_g         REAL    NOT NULL,
                carbs_g           REAL    NOT NULL,
                fat_g             REAL    NOT NULL,
                fiber_g           REAL    NOT NULL,
                sugar_g           REAL    NOT NULL,
                sodium_mg         REAL    NOT NULL,
                created_at        INTEGER NOT NULL,
                updated_at        INTEGER NOT NULL
            )
        """.trimIndent()
    }
}
