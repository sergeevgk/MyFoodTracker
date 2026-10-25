package com.example.myfoodtracker.domain.model

/**
 * Why a database backup or a restore candidate was rejected.
 *
 * The presentation layer maps every reason to a user-visible message; the data
 * layer reports [IO_ERROR] for file/URI failures. Pure Kotlin, no Android
 * imports (AD-1).
 */
enum class BackupErrorReason {
    NOT_A_DATABASE,
    INTEGRITY_CHECK_FAILED,
    UNSUPPORTED_BACKUP_VERSION,
    SCHEMA_VERSION_MISMATCH,
    SCHEMA_MISMATCH,
    MISSING_TABLES,
    INCOMPLETE_BACKUP,
    IO_ERROR
}
