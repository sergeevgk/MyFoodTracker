package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.BackupErrorReason
import com.example.myfoodtracker.domain.model.BackupFacts
import com.example.myfoodtracker.domain.model.BackupValidation
import java.time.LocalDate

/**
 * Names the backup artifact and decides whether a restore candidate may be
 * applied.
 *
 * This is the only place that implements the validation decision table: the data
 * layer collects a [BackupFacts] snapshot, this use case decides. The checks run
 * in a fixed order and the **first** failure wins, so the user always sees the
 * most specific reason (a text file is "not a backup", not a version problem).
 *
 * Pure Kotlin/JVM: no Android imports, no `suspend`, no coroutines (AD-1, AD-2).
 * Registered in Koin as a `factory` (AD-4).
 */
class BackupDatabaseUseCase {

    /** `myfoodtracker_backup_<yyyy-MM-dd>.db` — `LocalDate.toString()` is ISO. */
    fun backupFileName(date: LocalDate): String =
        "$BACKUP_FILE_PREFIX$date$BACKUP_FILE_SUFFIX"

    operator fun invoke(facts: BackupFacts): BackupValidation {
        // 1. It must open as SQLite and carry the backup meta table.
        if (!facts.fileReadable || !facts.metaTablePresent) {
            return BackupValidation.Invalid(BackupErrorReason.NOT_A_DATABASE)
        }
        // 2. The SQLite file must not be damaged.
        if (!facts.integrityOk) {
            return BackupValidation.Invalid(BackupErrorReason.INTEGRITY_CHECK_FAILED)
        }
        // 3. Only format version 1 is understood.
        if (facts.formatVersion != FORMAT_VERSION) {
            return BackupValidation.Invalid(BackupErrorReason.UNSUPPORTED_BACKUP_VERSION)
        }
        // 4. The file must be self-consistent: declared version == real version.
        val declaredUserVersion = facts.declaredUserVersion
        if (declaredUserVersion == null || declaredUserVersion != facts.actualUserVersion) {
            return BackupValidation.Invalid(BackupErrorReason.INCOMPLETE_BACKUP)
        }
        // 5. It must match the schema version the running app compiles against;
        //    otherwise `fallbackToDestructiveMigration()` would wipe the data.
        if (facts.actualUserVersion != facts.expectedUserVersion) {
            return BackupValidation.Invalid(BackupErrorReason.SCHEMA_VERSION_MISMATCH)
        }
        // 6. Every application table must be there.
        if (facts.missingTables.isNotEmpty()) {
            return BackupValidation.Invalid(BackupErrorReason.MISSING_TABLES)
        }
        // 7. The identity hash must match the live database, otherwise Room
        //    throws "Room cannot verify the data integrity" on the next open.
        //    A device without a database yet has nothing to compare against.
        val liveIdentityHash = facts.liveIdentityHash
        if (liveIdentityHash != null && facts.candidateIdentityHash != liveIdentityHash) {
            return BackupValidation.Invalid(BackupErrorReason.SCHEMA_MISMATCH)
        }
        // 8. The declared custom-food count must match the carried rows.
        val declaredCustomFoodCount = facts.declaredCustomFoodCount
        if (declaredCustomFoodCount == null ||
            declaredCustomFoodCount != facts.actualCustomFoodCount
        ) {
            return BackupValidation.Invalid(BackupErrorReason.INCOMPLETE_BACKUP)
        }
        return BackupValidation.Valid
    }

    companion object {
        // --- File format ---------------------------------------------------------

        /** The single source of truth for the backup file format. */
        const val META_TABLE = "myfoodtracker_backup_meta"
        const val CUSTOM_FOODS_TABLE = "myfoodtracker_custom_foods"
        const val FORMAT_VERSION = 1

        const val META_KEY_FORMAT_VERSION = "format_version"
        const val META_KEY_APP_VERSION = "app_version"
        const val META_KEY_CREATED_AT = "created_at"
        const val META_KEY_MAIN_USER_VERSION = "main_user_version"
        const val META_KEY_IDENTITY_HASH = "identity_hash"
        const val META_KEY_CUSTOM_FOODS_COUNT = "custom_foods_count"
        const val META_KEY_MAIN_DB_NAME = "main_db_name"

        /** The live Room database this backup carries. */
        const val MAIN_DB_NAME = "meals_database"

        /** `AppDatabase.version` — a restore must match it exactly (AC #5). */
        const val EXPECTED_USER_VERSION = 4

        /** The application tables a complete backup must contain. */
        val REQUIRED_TABLES = listOf(
            "meals",
            "foods",
            "users",
            "user_daily_goals",
            "water_logs",
            "recipes",
            "recipe_ingredients"
        )

        private const val BACKUP_FILE_PREFIX = "myfoodtracker_backup_"
        private const val BACKUP_FILE_SUFFIX = ".db"
    }
}
