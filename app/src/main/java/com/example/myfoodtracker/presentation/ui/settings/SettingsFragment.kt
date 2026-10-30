package com.example.myfoodtracker.presentation.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.myfoodtracker.R
import com.example.myfoodtracker.data.backup.DatabaseBackupManager
import com.example.myfoodtracker.data.export.LogExportFileWriter
import com.example.myfoodtracker.databinding.FragmentSettingsBinding
import com.example.myfoodtracker.domain.model.BackupErrorReason
import com.example.myfoodtracker.domain.model.BackupValidation
import com.example.myfoodtracker.domain.model.ExportFormat
import com.example.myfoodtracker.domain.model.ExportPayload
import com.example.myfoodtracker.presentation.ui.settings.model.BackupState
import com.example.myfoodtracker.presentation.ui.settings.model.DateRange
import com.example.myfoodtracker.presentation.ui.settings.model.ExportState
import com.example.myfoodtracker.presentation.ui.settings.model.RestoreState
import com.example.myfoodtracker.presentation.viewmodel.SettingsViewModel
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Settings & Data Management screen.
 *
 * The screen hosts the log export (format toggle, date-range picker, Sharesheet
 * and the system document picker) and, below it, the database backup/restore
 * section.
 *
 * The Fragment — not the ViewModel — performs the file IO and launches the
 * Intents, because both need a `Context` (project rule).
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModel()
    private val fileWriter: LogExportFileWriter by inject()
    private val backupManager: DatabaseBackupManager by inject()

    /** Which export destination the user asked for. */
    private var pendingAction: ExportAction? = null

    /**
     * The currently shown backup/restore dialog. Kept so a re-dispatch of the
     * retained restore state cannot stack a second dialog on top.
     */
    private var restoreDialog: AlertDialog? = null

    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val payload = viewModel.consumePendingSavePayload()
            if (result.resultCode != Activity.RESULT_OK) {
                // The user cancelled — nothing is written and no error is shown.
                return@registerForActivityResult
            }
            val uri = result.data?.data
            if (uri == null || payload == null) {
                showError(getString(R.string.export_error_save_failed))
                return@registerForActivityResult
            }
            try {
                fileWriter.writeToUri(uri, payload.content)
                showSuccess(payload)
            } catch (e: Exception) {
                Log.e(
                    LOG_TAG,
                    "Failed to write export to URI [fileName=${payload.fileName}, format=${payload.format}, rowCount=${payload.rowCount}, uri=$uri]",
                    e
                )
                showError(getString(R.string.export_error_save_failed))
            }
        }

    /**
     * The system create-document picker for a database backup. The launched
     * argument becomes `EXTRA_TITLE`, i.e. the pre-filled file name.
     */
    private val createBackupDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument(MIME_TYPE_SQLITE)) { uri ->
            handleBackupDestination(uri)
        }

    /** The system open-document picker for a restore candidate. */
    private val openBackupDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            // A cancelled picker writes nothing, changes nothing and shows no error.
            if (uri == null) return@registerForActivityResult
            validateAndAskToRestore(uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Re-attach the positive listener if the date-range picker survived a screen rotation.
        @Suppress("UNCHECKED_CAST")
        (parentFragmentManager.findFragmentByTag(TAG_DATE_RANGE_PICKER)
            as? MaterialDatePicker<androidx.core.util.Pair<Long, Long>>)?.let { existingPicker ->
            attachDateRangePickerListener(existingPicker)
        }

        binding.toggleFormat.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.setFormat(
                if (checkedId == R.id.btn_format_txt) ExportFormat.TXT else ExportFormat.CSV
            )
        }

        binding.btnDateRange.setOnClickListener { showDateRangePicker() }

        binding.btnExportShare.setOnClickListener {
            pendingAction = ExportAction.SHARE
            viewModel.export()
        }

        binding.btnExportSave.setOnClickListener {
            pendingAction = ExportAction.SAVE
            viewModel.export()
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            binding.tvSettingsUsername.text =
                getString(R.string.settings_signed_in_format, state.username)
            binding.tvSettingsProfileId.text =
                getString(R.string.active_profile_format, state.profileId)
        }

        viewModel.format.observe(viewLifecycleOwner) { format ->
            binding.btnExportShare.setText(
                if (format == ExportFormat.TXT) {
                    R.string.settings_export_share_txt
                } else {
                    R.string.settings_export_share
                }
            )
            val checkedId = if (format == ExportFormat.TXT) {
                R.id.btn_format_txt
            } else {
                R.id.btn_format_csv
            }
            if (binding.toggleFormat.checkedButtonId != checkedId) {
                binding.toggleFormat.check(checkedId)
            }
        }

        viewModel.dateRange.observe(viewLifecycleOwner) { range ->
            val label = formatRange(range)
            binding.btnDateRange.text = label
            binding.btnDateRange.contentDescription =
                getString(R.string.a11y_export_date_range, label)
        }

        viewModel.exportState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ExportState.Ready -> handleReady(state.payload)
                is ExportState.Empty -> showMessage(getString(R.string.export_empty))
                is ExportState.Error -> showMessage(mapErrorMessage(state.message))
                ExportState.Idle -> Unit
            }
            // Clear the state so a rotation does not re-trigger the Intent.
            // Guarded on `Idle`: LiveData re-dispatches when a value is set from
            // inside an observer, so clearing unconditionally would loop forever.
            if (state !is ExportState.Idle) {
                viewModel.clearExportState()
            }
        }

        binding.btnBackupDatabase.setOnClickListener { startBackup() }
        binding.btnRestoreDatabase.setOnClickListener { launchRestorePicker() }

        // Only the dialog states are rendered from the observer: ConfirmRequired
        // and Staged deliberately stay in the ViewModel so a rotation re-shows
        // the prompt instead of stranding the user with a staged restore.
        viewModel.restoreState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is RestoreState.ConfirmRequired -> showRestoreConfirmDialog(state.candidateName)
                RestoreState.Staged -> showRestartDialog()
                else -> dismissRestoreDialog()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The profile may have changed while the screen was in the background.
        viewModel.refreshProfile()
    }

    // --- Export --------------------------------------------------------------------

    private fun handleReady(payload: ExportPayload) {
        when (pendingAction) {
            ExportAction.SHARE -> shareExport(payload)
            ExportAction.SAVE -> startSaveToFile(payload)
            null -> Unit
        }
        pendingAction = null
    }

    /**
     * Writes the file into the app cache and hands it to the Android Sharesheet
     * as a `content://` URI. A `file://` URI would throw `FileUriExposedException`.
     */
    private fun shareExport(payload: ExportPayload) {
        if (!isAdded) return
        try {
            val file = fileWriter.writeToCache(payload.fileName, payload.content)
            val uri = fileWriter.contentUriFor(file)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = payload.format.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.export_share_subject))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, getString(R.string.export_share_title))
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(chooser)
            showSuccess(payload)
        } catch (e: Exception) {
            Log.e(
                LOG_TAG,
                "Failed to share export [fileName=${payload.fileName}, format=${payload.format}, rowCount=${payload.rowCount}]",
                e
            )
            showError(getString(R.string.export_error_failed))
        }
    }

    /**
     * Opens the system document picker. Nothing is written until the user has
     * confirmed a destination.
     */
    private fun startSaveToFile(payload: ExportPayload) {
        if (!isAdded) return
        // The pending payload lives in the ViewModel, not in a Fragment field,
        // so a rotation while the picker is foregrounded cannot lose it
        // (bug 5.1-F2 / story 8.2 AC #2).
        viewModel.setPendingSavePayload(payload)
        try {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = payload.format.mimeType
                putExtra(Intent.EXTRA_TITLE, payload.fileName)
            }
            createDocumentLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(
                LOG_TAG,
                "Failed to launch document picker [fileName=${payload.fileName}, format=${payload.format}]",
                e
            )
            // The picker never opened, so the result callback will not run:
            // disarm the carrier so a stale payload cannot be consumed later.
            viewModel.consumePendingSavePayload()
            showError(getString(R.string.export_error_failed))
        }
    }

    // --- Backup & restore ------------------------------------------------------------

    /**
     * Materialises the backup in the app cache and opens the create-document
     * picker. Nothing is written to the user's chosen location until the picker
     * returns a URI.
     */
    private fun startBackup() {
        if (!isAdded) return
        viewModel.onBackupPreparing()
        val fileName = viewModel.backupFileName()
        try {
            backupManager.createBackupFile(fileName)
            viewModel.onBackupReady(fileName)
            createBackupDocumentLauncher.launch(fileName)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to prepare the database backup [fileName=$fileName]", e)
            backupManager.deleteCachedBackup(fileName)
            viewModel.onBackupFailed(BackupErrorReason.IO_ERROR)
            showMessage(getString(R.string.backup_error_prepare))
        }
    }

    /**
     * Called when the create-document picker returns. `Ready(fileName)` — not a
     * Fragment field — is the source of the deterministic cache path, so a
     * rotation cannot lose the prepared backup.
     */
    private fun handleBackupDestination(uri: Uri?) {
        val fileName = (viewModel.backupState.value as? BackupState.Ready)?.fileName ?: return
        if (uri == null) {
            // The user cancelled: delete the cache artifact and show no error.
            backupManager.deleteCachedBackup(fileName)
            viewModel.cancelBackup()
            return
        }
        try {
            backupManager.writeBackupTo(uri, fileName)
            backupManager.deleteCachedBackup(fileName)
            viewModel.onBackupSaved(fileName)
            showBackupSuccess(fileName)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to write the database backup [fileName=$fileName, uri=$uri]", e)
            backupManager.deleteCachedBackup(fileName)
            viewModel.onBackupFailed(BackupErrorReason.IO_ERROR)
            showMessage(getString(R.string.backup_error_write))
        }
    }

    private fun launchRestorePicker() {
        if (!isAdded) return
        try {
            openBackupDocumentLauncher.launch(BACKUP_MIME_TYPES)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to launch the restore file picker", e)
            viewModel.onRestoreFailed(BackupErrorReason.IO_ERROR)
            showMessage(getString(R.string.restore_error_failed))
        }
    }

    /** Copies the picked file into the cache and validates it before anything is touched. */
    private fun validateAndAskToRestore(uri: Uri) {
        if (!isAdded) return
        viewModel.onRestoreValidating()
        val candidateName = resolveDisplayName(uri) ?: DEFAULT_CANDIDATE_NAME
        val validation = try {
            backupManager.validateCandidate(uri)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to read the selected backup [uri=$uri]", e)
            backupManager.deleteCachedCandidate()
            viewModel.onRestoreFailed(BackupErrorReason.IO_ERROR)
            showMessage(getString(R.string.backup_error_read))
            return
        }
        when (validation) {
            is BackupValidation.Valid -> viewModel.onRestoreCandidateReady(candidateName)
            is BackupValidation.Invalid -> {
                backupManager.deleteCachedCandidate()
                viewModel.onRestoreInvalid(validation.reason)
                showRestoreError(validation.reason)
            }
        }
    }

    private fun showRestoreConfirmDialog(candidateName: String) {
        if (!isAdded || restoreDialog?.isShowing == true) return
        // The exact overwrite wording is required; the picked file's display name
        // is appended so the user knows what is about to be restored.
        val message = getString(R.string.restore_confirm_message) + "\n\n" + candidateName
        restoreDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.restore_confirm_title)
            .setMessage(message)
            .setNegativeButton(R.string.action_cancel) { _, _ -> abortRestore() }
            .setPositiveButton(R.string.restore_confirm_positive) { _, _ ->
                viewModel.confirmRestore()
                stageRestore()
            }
            .setOnCancelListener { abortRestore() }
            .show()
    }

    /**
     * The commit point: the validated candidate is durably written to
     * `filesDir/pending_restore/` before the restart prompt is shown.
     */
    private fun stageRestore() {
        restoreDialog = null
        try {
            backupManager.stageRestore(backupManager.cachedCandidateFile())
            backupManager.deleteCachedCandidate()
            viewModel.onRestoreStaged()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to stage the database restore", e)
            backupManager.discardStagedRestore()
            viewModel.onRestoreFailed(BackupErrorReason.IO_ERROR)
            showMessage(getString(R.string.restore_error_failed))
        }
    }

    private fun showRestartDialog() {
        if (!isAdded || restoreDialog?.isShowing == true) return
        // AC #6: the prompt must make clear that the local data is replaced.
        val message = getString(R.string.restore_restart_message) + "\n\n" +
            getString(R.string.restore_restart_message_replace_notice)
        restoreDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.restore_restart_title)
            .setMessage(message)
            .setNegativeButton(R.string.action_cancel) { _, _ -> abortRestore() }
            .setPositiveButton(R.string.restore_restart_positive) { _, _ ->
                backupManager.deleteCachedCandidate()
                Process.killProcess(Process.myPid())
            }
            .setOnCancelListener { abortRestore() }
            .show()
    }

    /** Cancelling either dialog discards the staged restore and cached candidate. */
    private fun abortRestore() {
        viewModel.cancelRestore()
        backupManager.discardStagedRestore()
        backupManager.deleteCachedCandidate()
    }

    private fun dismissRestoreDialog() {
        restoreDialog?.dismiss()
        restoreDialog = null
    }

    /** Uses the picked file's display name; never shows a raw path. */
    private fun resolveDisplayName(uri: Uri): String? = try {
        requireContext().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
    } catch (e: Exception) {
        Log.w(LOG_TAG, "Could not read the display name of $uri", e)
        null
    }

    private fun showRestoreError(reason: BackupErrorReason) {
        if (!isAdded) return
        val message = getString(restoreErrorString(reason))
        binding.root.announceForAccessibility(
            getString(R.string.a11y_restore_invalid, message)
        )
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showBackupSuccess(fileName: String) {
        if (!isAdded) return
        binding.root.announceForAccessibility(
            getString(R.string.a11y_backup_success, fileName)
        )
        Snackbar.make(
            binding.root,
            getString(R.string.backup_success_format, fileName),
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun restoreErrorString(reason: BackupErrorReason): Int = when (reason) {
        BackupErrorReason.NOT_A_DATABASE -> R.string.restore_error_not_a_database
        BackupErrorReason.INTEGRITY_CHECK_FAILED -> R.string.restore_error_integrity
        BackupErrorReason.UNSUPPORTED_BACKUP_VERSION -> R.string.restore_error_version
        BackupErrorReason.SCHEMA_VERSION_MISMATCH -> R.string.restore_error_schema_version
        BackupErrorReason.SCHEMA_MISMATCH -> R.string.restore_error_schema
        BackupErrorReason.MISSING_TABLES -> R.string.restore_error_missing_tables
        BackupErrorReason.INCOMPLETE_BACKUP -> R.string.restore_error_incomplete
        BackupErrorReason.IO_ERROR -> R.string.restore_error_failed
    }

    // --- Date range ----------------------------------------------------------------

    private fun showDateRangePicker() {
        if (parentFragmentManager.findFragmentByTag(TAG_DATE_RANGE_PICKER) != null) return
        val range = viewModel.dateRange.value ?: return
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(getString(R.string.settings_date_range_label))
            .setSelection(
                androidx.core.util.Pair<Long, Long>(
                    range.from.toUtcMillis(),
                    range.to.toUtcMillis()
                )
            )
            .build()

        attachDateRangePickerListener(picker)
        picker.show(parentFragmentManager, TAG_DATE_RANGE_PICKER)
    }

    private fun attachDateRangePickerListener(
        picker: MaterialDatePicker<androidx.core.util.Pair<Long, Long>>
    ) {
        picker.addOnPositiveButtonClickListener { selection ->
            val startMillis = selection.first
            val endMillis = selection.second
            if (startMillis == null || endMillis == null) {
                return@addOnPositiveButtonClickListener
            }
            // Convert exactly like DashboardFragment.showDatePicker, so the
            // exported range never shifts by a day because of the device time zone.
            val from = Instant.ofEpochMilli(startMillis).atZone(ZoneId.of("UTC")).toLocalDate()
            val to = Instant.ofEpochMilli(endMillis).atZone(ZoneId.of("UTC")).toLocalDate()
            viewModel.setDateRange(from, to)
        }
    }

    private fun formatRange(range: DateRange): String {
        val startFormatter = if (range.from.year == range.to.year) {
            rangeStartFormatter
        } else {
            rangeEndFormatter
        }
        return getString(
            R.string.settings_date_range_format,
            range.from.format(startFormatter),
            range.to.format(rangeEndFormatter)
        )
    }

    // --- Feedback ------------------------------------------------------------------

    private fun showSuccess(payload: ExportPayload) {
        if (!isAdded) return
        val formatLabel = getString(
            if (payload.format == ExportFormat.TXT) {
                R.string.settings_format_txt
            } else {
                R.string.settings_format_csv
            }
        )
        val message = getString(
            if (payload.format == ExportFormat.TXT) {
                R.string.export_success_txt
            } else {
                R.string.export_success_csv
            },
            payload.rowCount
        )
        binding.root.announceForAccessibility(
            getString(R.string.a11y_export_success, payload.rowCount, formatLabel)
        )
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun showError(message: String) {
        showMessage(message)
    }

    private fun showMessage(message: String) {
        if (!isAdded) return
        binding.root.announceForAccessibility(message)
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    private fun mapErrorMessage(message: String): String = when (message) {
        SettingsViewModel.ERROR_NO_ACTIVE_PROFILE -> getString(R.string.export_error_profile)
        SettingsViewModel.ERROR_INVALID_DATE_RANGE -> getString(R.string.export_error_range)
        else -> getString(R.string.export_error_failed)
    }

    // --- Helpers -------------------------------------------------------------------

    private fun LocalDate.toUtcMillis(): Long =
        atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

    // Instance fields, not static: the default locale must be resolved when the
    // screen is created, not when the class is loaded.
    private val rangeStartFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    private val rangeEndFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

    override fun onDestroyView() {
        dismissRestoreDialog()
        super.onDestroyView()
        _binding = null
    }

    private enum class ExportAction { SHARE, SAVE }

    companion object {
        private const val LOG_TAG = "SettingsFragment"
        private const val TAG_DATE_RANGE_PICKER = "EXPORT_DATE_RANGE"
        private const val DEFAULT_CANDIDATE_NAME = "backup"
        private const val MIME_TYPE_SQLITE = "application/vnd.sqlite3"

        /**
         * Many providers report `.db` files as `application/octet-stream`, so the
         * filter accepts the SQLite types as well (Dev Notes: MIME type).
         */
        private val BACKUP_MIME_TYPES = arrayOf(
            "application/vnd.sqlite3",
            "application/x-sqlite3",
            "application/octet-stream"
        )
    }
}
