package com.example.myfoodtracker.presentation.ui.settings.model

import com.example.myfoodtracker.domain.model.BackupErrorReason

/**
 * The outcome of a database backup run.
 *
 * [Ready] is informational: it carries the deterministic file name so the
 * document-picker callback can recover `cacheDir/backups/<fileName>` without a
 * `File` living in a Fragment field. Plain `object` is used instead of
 * `data object`, matching `ExportState`.
 */
sealed class BackupState {
    object Idle : BackupState()

    object Preparing : BackupState()

    data class Ready(val fileName: String) : BackupState()

    data class Saved(val fileName: String) : BackupState()

    data class Error(val reason: BackupErrorReason) : BackupState()
}
