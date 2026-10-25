package com.example.myfoodtracker.presentation.ui.settings.model

import com.example.myfoodtracker.domain.model.ExportPayload

/**
 * The outcome of an export run.
 *
 * The payload is produced by the domain layer; the Fragment performs the file IO
 * and the Intents. Plain `object` is used instead of `data object` for safety.
 */
sealed class ExportState {
    object Idle : ExportState()

    data class Ready(val payload: ExportPayload) : ExportState()

    object Empty : ExportState()

    data class Error(val message: String) : ExportState()
}
