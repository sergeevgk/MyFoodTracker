package com.example.myfoodtracker.presentation.ui.settings.model

import com.example.myfoodtracker.domain.model.BackupErrorReason

/**
 * The outcome of a database restore run.
 *
 * [ConfirmRequired] and [Staged] are **dialog states**: they stay in the
 * ViewModel until the user acts (confirm / cancel), so a rotation re-shows the
 * prompt instead of stranding the user with a staged restore and no way to
 * decide. They must never be cleared from inside an observer.
 */
sealed class RestoreState {
    object Idle : RestoreState()

    object Validating : RestoreState()

    data class ConfirmRequired(val candidateName: String) : RestoreState()

    data class Invalid(val reason: BackupErrorReason) : RestoreState()

    object Staged : RestoreState()

    data class Error(val reason: BackupErrorReason) : RestoreState()
}
