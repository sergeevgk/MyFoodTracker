package com.example.myfoodtracker.domain.model

/**
 * The verdict of [com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase]
 * on a restore candidate.
 *
 * Plain `object` is used instead of `data object`, matching the project
 * convention (see `ExportState`). Pure Kotlin/JVM (AD-1).
 */
sealed class BackupValidation {
    object Valid : BackupValidation()

    data class Invalid(val reason: BackupErrorReason) : BackupValidation()
}
