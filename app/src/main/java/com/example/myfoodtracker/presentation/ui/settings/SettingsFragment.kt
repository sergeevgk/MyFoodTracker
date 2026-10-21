package com.example.myfoodtracker.presentation.ui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.myfoodtracker.R
import com.example.myfoodtracker.data.export.LogExportFileWriter
import com.example.myfoodtracker.databinding.FragmentSettingsBinding
import com.example.myfoodtracker.domain.model.ExportFormat
import com.example.myfoodtracker.domain.model.ExportPayload
import com.example.myfoodtracker.presentation.ui.settings.model.DateRange
import com.example.myfoodtracker.presentation.ui.settings.model.ExportState
import com.example.myfoodtracker.presentation.viewmodel.SettingsViewModel
import com.google.android.material.datepicker.MaterialDatePicker
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
 * The screen hosts the log export: a format toggle, a date-range picker and the
 * two export destinations (Android Sharesheet and the system document picker).
 *
 * The Fragment — not the ViewModel — performs the file IO and launches the
 * Intents, because both need a `Context` (project rule).
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SettingsViewModel by viewModel()
    private val fileWriter: LogExportFileWriter by inject()

    /** Which export destination the user asked for. */
    private var pendingAction: ExportAction? = null

    /** The payload waiting to be written into the user-picked document. */
    private var pendingSavePayload: ExportPayload? = null

    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val payload = pendingSavePayload
            pendingSavePayload = null
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
        pendingSavePayload = payload
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
            pendingSavePayload = null
            showError(getString(R.string.export_error_failed))
        }
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
        super.onDestroyView()
        _binding = null
    }

    private enum class ExportAction { SHARE, SAVE }

    companion object {
        private const val LOG_TAG = "SettingsFragment"
        private const val TAG_DATE_RANGE_PICKER = "EXPORT_DATE_RANGE"
    }
}
