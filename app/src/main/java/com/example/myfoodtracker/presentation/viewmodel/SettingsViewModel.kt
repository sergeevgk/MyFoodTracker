package com.example.myfoodtracker.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.myfoodtracker.domain.model.BackupErrorReason
import com.example.myfoodtracker.domain.model.ExportFormat
import com.example.myfoodtracker.domain.model.ExportPayload
import com.example.myfoodtracker.domain.repository.SessionRepository
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase
import com.example.myfoodtracker.domain.usecase.ExportLogsUseCase
import com.example.myfoodtracker.domain.usecase.LogoutUseCase
import com.example.myfoodtracker.presentation.ui.settings.model.BackupState
import com.example.myfoodtracker.presentation.ui.settings.model.DateRange
import com.example.myfoodtracker.presentation.ui.settings.model.ExportState
import com.example.myfoodtracker.presentation.ui.settings.model.RestoreState
import com.example.myfoodtracker.presentation.ui.settings.model.SettingsUiState
import java.time.LocalDate

/**
 * Backs the Settings & Data Management screen.
 *
 * The ViewModel holds no `Context`, no `File` and no `Uri` (project rule): it
 * returns the export payload and drives the backup/restore state machine, while
 * the Fragment performs the file IO and the Intents. The selected date range and
 * the pending restore dialogs live here, so they survive a rotation.
 */
class SettingsViewModel(
    private val exportLogsUseCase: ExportLogsUseCase,
    private val sessionRepository: SessionRepository,
    private val backupDatabaseUseCase: BackupDatabaseUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    private val _uiState = MutableLiveData<SettingsUiState>()
    val uiState: LiveData<SettingsUiState> = _uiState

    private val _exportState = MutableLiveData<ExportState>(ExportState.Idle)
    val exportState: LiveData<ExportState> = _exportState

    private val _backupState = MutableLiveData<BackupState>(BackupState.Idle)
    val backupState: LiveData<BackupState> = _backupState

    private val _restoreState = MutableLiveData<RestoreState>(RestoreState.Idle)
    val restoreState: LiveData<RestoreState> = _restoreState

    private val _format = MutableLiveData(ExportFormat.CSV)
    val format: LiveData<ExportFormat> = _format

    private val _dateRange = MutableLiveData(defaultDateRange())
    val dateRange: LiveData<DateRange> = _dateRange

    init {
        refreshProfile()
    }

    /** Re-reads the active profile into the header state. */
    fun refreshProfile() {
        val profile = sessionRepository.getActiveProfile()
        _uiState.value = SettingsUiState(
            username = profile?.username.orEmpty(),
            profileId = profile?.id.orEmpty()
        )
    }

    fun logout() {
        logoutUseCase()
    }

    fun setFormat(format: ExportFormat) {
        _format.value = format
    }

    fun setDateRange(from: LocalDate, to: LocalDate) {
        _dateRange.value = DateRange(from, to)
    }

    /**
     * Runs the export for the currently selected format and range.
     *
     * The range always comes from the caller — the ViewModel never calls
     * [LocalDate.now] for the export range itself.
     */
    fun export() {
        val range = _dateRange.value ?: defaultDateRange()
        val activeProfile = sessionRepository.getActiveProfile()
        if (activeProfile == null) {
            _exportState.value = ExportState.Error(ERROR_NO_ACTIVE_PROFILE)
            return
        }
        if (range.from.isAfter(range.to)) {
            _exportState.value = ExportState.Error(ERROR_INVALID_DATE_RANGE)
            return
        }
        val selectedFormat = _format.value ?: ExportFormat.CSV
        val payload = try {
            exportLogsUseCase(selectedFormat, range.from, range.to)
        } catch (e: IllegalArgumentException) {
            logExportException(e, selectedFormat, range, activeProfile.id)
            _exportState.value = ExportState.Error(e.message ?: ERROR_INVALID_DATE_RANGE)
            return
        } catch (e: Exception) {
            logExportException(e, selectedFormat, range, activeProfile.id)
            _exportState.value = ExportState.Error(ERROR_EXPORT_FAILED)
            return
        }
        _exportState.value = if (payload.rowCount == 0) {
            ExportState.Empty
        } else {
            ExportState.Ready(payload)
        }
    }

    /** Resets the export state so a rotation does not re-trigger the Intent. */
    fun clearExportState() {
        _exportState.value = ExportState.Idle
    }

    // --- Pending "Save to file…" payload (story 8.2, AC #2) ---------------------------

    /**
     * The payload waiting to be written into the user-picked document, kept
     * in the ViewModel (not in a Fragment field) so it survives the rotation
     * that may happen while the system document picker is foregrounded.
     * Plain field on purpose: no `Uri`, no `File`, no LiveData re-dispatch.
     */
    private var pendingSavePayload: ExportPayload? = null

    /** Arms the carrier before the document picker is launched. */
    fun setPendingSavePayload(payload: ExportPayload) {
        pendingSavePayload = payload
    }

    /**
     * Returns and clears the pending payload — a single-use carrier, so a
     * second consume (or a consume that was never armed) returns `null`.
     */
    fun consumePendingSavePayload(): ExportPayload? {
        val payload = pendingSavePayload
        pendingSavePayload = null
        return payload
    }

    // --- Backup ----------------------------------------------------------------------

    /** `myfoodtracker_backup_<yyyy-MM-dd>.db`, device-local date by default. */
    fun backupFileName(date: LocalDate = LocalDate.now()): String =
        backupDatabaseUseCase.backupFileName(date)

    fun onBackupPreparing() {
        _backupState.value = BackupState.Preparing
    }

    fun onBackupReady(fileName: String) {
        _backupState.value = BackupState.Ready(fileName)
    }

    fun onBackupSaved(fileName: String) {
        _backupState.value = BackupState.Saved(fileName)
    }

    fun onBackupFailed(reason: BackupErrorReason) {
        _backupState.value = BackupState.Error(reason)
    }

    fun cancelBackup() {
        _backupState.value = BackupState.Idle
    }

    // --- Restore ---------------------------------------------------------------------

    fun onRestoreValidating() {
        _restoreState.value = RestoreState.Validating
    }

    fun onRestoreCandidateReady(candidateName: String) {
        _restoreState.value = RestoreState.ConfirmRequired(candidateName)
    }

    fun onRestoreInvalid(reason: BackupErrorReason) {
        _restoreState.value = RestoreState.Invalid(reason)
    }

    /**
     * Advances the state machine to the committed restore.
     *
     * Unlike the export state, [RestoreState.ConfirmRequired] and
     * [RestoreState.Staged] are **not** cleared after being observed: the
     * Fragment re-shows the dialogs from the retained state after a rotation.
     */
    fun onRestoreStaged() {
        _restoreState.value = RestoreState.Staged
    }

    fun onRestoreFailed(reason: BackupErrorReason) {
        _restoreState.value = RestoreState.Error(reason)
    }

    /** The user accepted the overwrite warning. */
    fun confirmRestore() {
        if (_restoreState.value is RestoreState.ConfirmRequired) {
            _restoreState.value = RestoreState.Staged
        }
    }

    /** The user cancelled either dialog: nothing is pending any more. */
    fun cancelRestore() {
        _restoreState.value = RestoreState.Idle
    }

    private fun logExportException(
        exception: Exception,
        format: ExportFormat,
        range: DateRange,
        profileId: String
    ) {
        val detail = "Export failed [profileId=$profileId, format=$format, " +
            "from=${range.from}, to=${range.to}]: ${exception::class.java.name}: ${exception.message}"
        try {
            Log.e(LOG_TAG, detail, exception)
        } catch (_: RuntimeException) {
            // Host JVM unit tests do not mock android.util.Log; fall back to stderr.
            System.err.println("$LOG_TAG: $detail")
            exception.printStackTrace(System.err)
        }
    }

    companion object {
        private const val LOG_TAG = "SettingsViewModel"
        const val ERROR_NO_ACTIVE_PROFILE = "No active profile"
        const val ERROR_INVALID_DATE_RANGE = "Invalid date range"
        const val ERROR_EXPORT_FAILED = "Export failed"

        /** First day of the current month → today, both inclusive. */
        fun defaultDateRange(today: LocalDate = LocalDate.now()): DateRange =
            DateRange(
                from = today.withDayOfMonth(1),
                to = today
            )
    }
}
