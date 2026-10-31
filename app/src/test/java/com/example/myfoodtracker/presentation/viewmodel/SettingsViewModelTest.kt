package com.example.myfoodtracker.presentation.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.myfoodtracker.domain.model.BackupErrorReason
import com.example.myfoodtracker.domain.model.DailyGoal
import com.example.myfoodtracker.domain.model.ExportFormat
import com.example.myfoodtracker.domain.model.ExportPayload
import com.example.myfoodtracker.domain.model.Food
import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.MealRepository
import com.example.myfoodtracker.domain.repository.SessionRepository
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase
import com.example.myfoodtracker.domain.model.WaterLog
import com.example.myfoodtracker.domain.repository.WaterRepository
import com.example.myfoodtracker.domain.usecase.ExportLogsUseCase
import com.example.myfoodtracker.domain.usecase.LogoutUseCase
import com.example.myfoodtracker.presentation.ui.settings.model.BackupState
import com.example.myfoodtracker.presentation.ui.settings.model.ExportState
import com.example.myfoodtracker.presentation.ui.settings.model.RestoreState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference

class SettingsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var fakeSessionRepository: FakeSessionRepository
    private lateinit var fakeMealRepository: FakeMealRepository
    private lateinit var fakeWaterRepository: FakeWaterRepository
    private lateinit var exportLogsUseCase: ExportLogsUseCase
    private lateinit var backupDatabaseUseCase: BackupDatabaseUseCase
    private lateinit var logoutUseCase: LogoutUseCase
    private lateinit var viewModel: SettingsViewModel

    private val testUser = UserProfile(
        id = "user-1",
        username = "Georgii",
        dailyGoal = DailyGoal()
    )

    private val from = LocalDate.of(2026, 10, 1)
    private val to = LocalDate.of(2026, 10, 21)

    @Before
    fun setUp() {
        fakeSessionRepository = FakeSessionRepository()
        fakeSessionRepository.setActiveProfile(testUser, rememberDevice = true)
        fakeMealRepository = FakeMealRepository()
        fakeWaterRepository = FakeWaterRepository()
        exportLogsUseCase = ExportLogsUseCase(fakeMealRepository, fakeWaterRepository)
        backupDatabaseUseCase = BackupDatabaseUseCase()
        logoutUseCase = LogoutUseCase(fakeSessionRepository)
        viewModel = SettingsViewModel(
            exportLogsUseCase = exportLogsUseCase,
            sessionRepository = fakeSessionRepository,
            backupDatabaseUseCase = backupDatabaseUseCase,
            logoutUseCase = logoutUseCase
        )
    }

    // --- Initial state -------------------------------------------------------------

    @Test
    fun init_exposesActiveProfileInUiState() {
        val state = viewModel.uiState.value

        assertNotNull(state)
        assertEquals("Georgii", state!!.username)
        assertEquals("user-1", state.profileId)
    }

    @Test
    fun init_defaultsToCsvFormat() {
        assertEquals(ExportFormat.CSV, viewModel.format.value)
    }

    @Test
    fun init_defaultsToFirstDayOfMonthUntilToday() {
        val range = viewModel.dateRange.value

        assertNotNull(range)
        val today = LocalDate.now()
        assertEquals(today.withDayOfMonth(1), range!!.from)
        assertEquals(today, range.to)
    }

    // --- Format and range ----------------------------------------------------------

    @Test
    fun setFormat_txt_updatesFormat() {
        viewModel.setFormat(ExportFormat.TXT)

        assertEquals(ExportFormat.TXT, viewModel.format.value)
    }

    @Test
    fun setDateRange_carriesTheExactDates() {
        viewModel.setDateRange(from, to)

        val range = viewModel.dateRange.value
        assertNotNull(range)
        assertEquals(from, range!!.from)
        assertEquals(to, range.to)
    }

    @Test
    fun setDateRange_survivesRecreation() {
        viewModel.setDateRange(from, to)

        // A new ViewModel instance would re-read the default range, so the
        // selected range has to live in the ViewModel, not in the Fragment.
        val range = viewModel.dateRange.value
        assertEquals(from, range!!.from)
        assertEquals(to, range.to)
    }

    // --- Export --------------------------------------------------------------------

    @Test
    fun export_validRange_returnsReadyWithPayload() {
        fakeMealRepository.entries = listOf(
            meal("m1", "2026-10-04", "08:00")
        )
        viewModel.setDateRange(from, to)

        viewModel.export()

        val state = viewModel.exportState.value
        assertTrue(state is ExportState.Ready)
        val payload = (state as ExportState.Ready).payload
        assertEquals(1, payload.rowCount)
        assertEquals(ExportFormat.CSV, payload.format)
        assertEquals("myfoodtracker_logs_2026-10-01_2026-10-21.csv", payload.fileName)
    }

    @Test
    fun export_forwardsTheSelectedFormat() {
        fakeMealRepository.entries = listOf(meal("m1", "2026-10-04", "08:00"))
        viewModel.setDateRange(from, to)
        viewModel.setFormat(ExportFormat.TXT)

        viewModel.export()

        val state = viewModel.exportState.value
        assertTrue(state is ExportState.Ready)
        val payload = (state as ExportState.Ready).payload
        assertEquals(ExportFormat.TXT, payload.format)
        assertEquals("myfoodtracker_logs_2026-10-01_2026-10-21.txt", payload.fileName)
    }

    @Test
    fun export_zeroRows_returnsEmpty() {
        fakeMealRepository.entries = emptyList()
        viewModel.setDateRange(from, to)

        viewModel.export()

        assertEquals(ExportState.Empty, viewModel.exportState.value)
    }

    @Test
    fun export_noActiveProfile_returnsErrorAndDoesNotUseTheRepository() {
        fakeSessionRepository.clearSession()
        viewModel.setDateRange(from, to)

        viewModel.export()

        val state = viewModel.exportState.value
        assertTrue(state is ExportState.Error)
        assertEquals(SettingsViewModel.ERROR_NO_ACTIVE_PROFILE, (state as ExportState.Error).message)
        assertEquals(0, fakeMealRepository.getMealEntriesCallCount)
    }

    @Test
    fun export_invalidRange_returnsError() {
        viewModel.setDateRange(to, from)

        viewModel.export()

        val state = viewModel.exportState.value
        assertTrue(state is ExportState.Error)
        assertEquals(SettingsViewModel.ERROR_INVALID_DATE_RANGE, (state as ExportState.Error).message)
    }

    @Test
    fun export_repositoryThrowsUnexpectedException_returnsErrorInsteadOfCrashing() {
        fakeMealRepository.failureToThrow = IllegalStateException("Disk I/O error")
        viewModel.setDateRange(from, to)

        viewModel.export()

        val state = viewModel.exportState.value
        assertTrue(state is ExportState.Error)
        assertEquals(SettingsViewModel.ERROR_EXPORT_FAILED, (state as ExportState.Error).message)
    }

    @Test
    fun export_usesTheRangeFromTheCaller_notLocalDateNow() {
        fakeMealRepository.entries = listOf(meal("m1", "2026-10-04", "08:00"))
        viewModel.setDateRange(from, to)

        viewModel.export()

        val state = viewModel.exportState.value
        assertTrue(state is ExportState.Ready)
        // The file name carries the caller-supplied range.
        assertEquals("2026-10-01_2026-10-21", (state as ExportState.Ready).payload.fileName.removePrefix("myfoodtracker_logs_").removeSuffix(".csv"))
    }

    // --- State reset ---------------------------------------------------------------

    @Test
    fun clearExportState_resetsToIdle() {
        fakeMealRepository.entries = listOf(meal("m1", "2026-10-04", "08:00"))
        viewModel.setDateRange(from, to)
        viewModel.export()
        assertTrue(viewModel.exportState.value is ExportState.Ready)

        viewModel.clearExportState()

        assertEquals(ExportState.Idle, viewModel.exportState.value)
    }

    @Test
    fun exportState_observerThatClearsAfterHandling_terminates() {
        // LiveData re-dispatches when a value is set from inside an observer.
        // The Fragment clears the state after handling an event but never while
        // the state is Idle — this test proves the pattern terminates instead of
        // looping forever.
        fakeMealRepository.entries = listOf(meal("m1", "2026-10-04", "08:00"))
        viewModel.setDateRange(from, to)

        var notifications = 0
        viewModel.exportState.observeForever { state ->
            notifications++
            assertTrue("Observer ran more than 10 times — dispatch loop", notifications <= 10)
            if (state !is ExportState.Idle) {
                viewModel.clearExportState()
            }
        }

        viewModel.export()

        // One Idle delivery on registration, one Ready delivery, one
        // re-dispatch of Idle — then it settles.
        assertEquals(3, notifications)
        assertEquals(ExportState.Idle, viewModel.exportState.value)
    }

    // --- Backup state ---------------------------------------------------------------

    @Test
    fun backup_initialStateIsIdle() {
        assertEquals(BackupState.Idle, viewModel.backupState.value)
    }

    @Test
    fun backupFileName_usesTheDeviceLocalDateAndIsoFormat() {
        assertEquals(
            "myfoodtracker_backup_2026-10-21.db",
            viewModel.backupFileName(LocalDate.of(2026, 10, 21))
        )
    }

    @Test
    fun backupFileName_defaultsToToday() {
        assertEquals(
            "myfoodtracker_backup_${LocalDate.now()}.db",
            viewModel.backupFileName()
        )
    }

    @Test
    fun onBackupPreparing_movesToPreparing() {
        viewModel.onBackupPreparing()

        assertEquals(BackupState.Preparing, viewModel.backupState.value)
    }

    @Test
    fun onBackupReady_carriesTheDeterministicFileName() {
        viewModel.onBackupReady("myfoodtracker_backup_2026-10-21.db")

        assertEquals(
            BackupState.Ready("myfoodtracker_backup_2026-10-21.db"),
            viewModel.backupState.value
        )
    }

    @Test
    fun onBackupSaved_carriesTheFileName() {
        viewModel.onBackupSaved("myfoodtracker_backup_2026-10-21.db")

        assertEquals(
            BackupState.Saved("myfoodtracker_backup_2026-10-21.db"),
            viewModel.backupState.value
        )
    }

    @Test
    fun onBackupFailed_carriesTheReason() {
        viewModel.onBackupFailed(BackupErrorReason.IO_ERROR)

        assertEquals(
            BackupState.Error(BackupErrorReason.IO_ERROR),
            viewModel.backupState.value
        )
    }

    @Test
    fun cancelBackup_resetsToIdle() {
        viewModel.onBackupReady("myfoodtracker_backup_2026-10-21.db")

        viewModel.cancelBackup()

        assertEquals(BackupState.Idle, viewModel.backupState.value)
    }

    // --- Restore state --------------------------------------------------------------

    @Test
    fun restore_initialStateIsIdle() {
        assertEquals(RestoreState.Idle, viewModel.restoreState.value)
    }

    @Test
    fun onRestoreValidating_movesToValidating() {
        viewModel.onRestoreValidating()

        assertEquals(RestoreState.Validating, viewModel.restoreState.value)
    }

    @Test
    fun onRestoreCandidateReady_movesToConfirmRequiredWithTheDisplayName() {
        viewModel.onRestoreCandidateReady("myfoodtracker_backup_2026-10-21.db")

        assertEquals(
            RestoreState.ConfirmRequired("myfoodtracker_backup_2026-10-21.db"),
            viewModel.restoreState.value
        )
    }

    @Test
    fun onRestoreInvalid_carriesTheReason() {
        viewModel.onRestoreInvalid(BackupErrorReason.NOT_A_DATABASE)

        assertEquals(
            RestoreState.Invalid(BackupErrorReason.NOT_A_DATABASE),
            viewModel.restoreState.value
        )
    }

    @Test
    fun onRestoreStaged_movesToStaged() {
        viewModel.onRestoreStaged()

        assertEquals(RestoreState.Staged, viewModel.restoreState.value)
    }

    @Test
    fun onRestoreFailed_carriesTheReason() {
        viewModel.onRestoreFailed(BackupErrorReason.IO_ERROR)

        assertEquals(
            RestoreState.Error(BackupErrorReason.IO_ERROR),
            viewModel.restoreState.value
        )
    }

    @Test
    fun confirmRestore_advancesConfirmRequiredToStaged() {
        viewModel.onRestoreCandidateReady("backup.db")

        viewModel.confirmRestore()

        assertEquals(RestoreState.Staged, viewModel.restoreState.value)
    }

    @Test
    fun confirmRestore_outsideConfirmRequired_isIgnored() {
        viewModel.onRestoreInvalid(BackupErrorReason.MISSING_TABLES)

        viewModel.confirmRestore()

        assertEquals(
            RestoreState.Invalid(BackupErrorReason.MISSING_TABLES),
            viewModel.restoreState.value
        )
    }

    @Test
    fun cancelRestore_resetsToIdle() {
        viewModel.onRestoreCandidateReady("backup.db")

        viewModel.cancelRestore()

        assertEquals(RestoreState.Idle, viewModel.restoreState.value)
    }

    @Test
    fun restoreDialogStates_areNotClearedByObservation() {
        // Regression guard for the "dialog lost on rotation" disaster: unlike the
        // export state, ConfirmRequired and Staged are only cleared by an explicit
        // user action, so re-attaching the observer re-shows the prompt.
        viewModel.onRestoreCandidateReady("backup.db")

        var observed: RestoreState? = null
        val observer = androidx.lifecycle.Observer<RestoreState> { observed = it }
        viewModel.restoreState.observeForever(observer)

        assertEquals(RestoreState.ConfirmRequired("backup.db"), observed)
        assertEquals(
            RestoreState.ConfirmRequired("backup.db"),
            viewModel.restoreState.value
        )

        viewModel.confirmRestore()

        assertEquals(RestoreState.Staged, observed)
        assertEquals(RestoreState.Staged, viewModel.restoreState.value)

        viewModel.restoreState.removeObserver(observer)
    }

    // --- Pending save payload carrier (story 8.2, AC #2) ----------------------------

    @Test
    fun pendingSavePayload_neverSet_consumeReturnsNull() {
        assertNull(viewModel.consumePendingSavePayload())
    }

    @Test
    fun pendingSavePayload_setThenConsume_returnsThePayloadAndClearsIt() {
        viewModel.setPendingSavePayload(payload)

        val consumed = viewModel.consumePendingSavePayload()

        assertEquals(payload, consumed)
    }

    @Test
    fun pendingSavePayload_secondConsume_returnsNull() {
        viewModel.setPendingSavePayload(payload)
        viewModel.consumePendingSavePayload()

        assertNull(viewModel.consumePendingSavePayload())
    }

    @Test
    fun pendingSavePayload_clearedPayload_survivesAsCleared() {
        // Rotation case: the payload is set before the picker is foregrounded
        // and consumed once when the activity result returns (or the user
        // cancels); must not be re-delivered afterwards.
        viewModel.setPendingSavePayload(payload)
        viewModel.consumePendingSavePayload()
        viewModel.setPendingSavePayload(payload)
        viewModel.consumePendingSavePayload()

        assertNull(viewModel.consumePendingSavePayload())
    }

    // --- Helpers -------------------------------------------------------------------

    private val payload = ExportPayload(
        fileName = "myfoodtracker_logs_2026-10-01_2026-10-21.csv",
        content = "col\nrow\n",
        rowCount = 1,
        format = ExportFormat.CSV
    )

    private fun meal(id: String, date: String, time: String) = MealEntry(
        id = id,
        title = "BREAKFAST",
        date = date,
        time = time,
        foods = listOf(
            Food(
                name = "Apple",
                weight = 150.0,
                calories = 80.0,
                carbs = 20.0,
                fat = 0.5,
                protein = 1.0,
                fiber = 0.0
            )
        )
    )

    private class FakeSessionRepository : SessionRepository {
        private val current = AtomicReference<UserProfile?>(null)
        private var rememberedId: String? = null

        override fun getActiveProfile(): UserProfile? = current.get()
        override fun getActiveProfileId(): String? = current.get()?.id
        override fun setActiveProfile(profile: UserProfile, rememberDevice: Boolean) {
            current.set(profile)
            rememberedId = if (rememberDevice) profile.id else null
        }

        override fun getRememberedProfileId(): String? = rememberedId
        override fun forgetRememberedProfile() {
            rememberedId = null
        }

        override fun clearSession() {
            current.set(null)
            rememberedId = null
        }

        override fun isLoggedIn(): Boolean = current.get() != null
    }


    // --- Logout & Water Export (Story 8.4) ------------------------------------------

    @Test
    fun logout_clearsActiveSessionAndRememberedProfile() {
        assertTrue(fakeSessionRepository.isLoggedIn())
        assertEquals(testUser, fakeSessionRepository.getActiveProfile())
        assertEquals(testUser.id, fakeSessionRepository.getRememberedProfileId())

        viewModel.logout()

        org.junit.Assert.assertFalse(fakeSessionRepository.isLoggedIn())
        assertNull(fakeSessionRepository.getActiveProfile())
        assertNull(fakeSessionRepository.getRememberedProfileId())
    }

    @Test
    fun export_withWaterLogsOnly_emitsReadyState() {
        fakeMealRepository.entries = emptyList()
        fakeWaterRepository.logs = listOf(
            WaterLog(
                id = "w1",
                profileId = testUser.id,
                date = "2026-10-04",
                amountMl = 250
            )
        )

        viewModel.export()

        val state = viewModel.exportState.value
        assertTrue(state is ExportState.Ready)
        val ready = state as ExportState.Ready
        assertEquals(1, ready.payload.rowCount)
        assertTrue(ready.payload.content.contains("Water,250"))
    }

    private class FakeWaterRepository : WaterRepository {
        var logs: List<WaterLog> = emptyList()

        override fun getAllWaterLogs(): List<WaterLog> = logs

        override fun getWaterLogs(date: String): List<WaterLog> =
            throw UnsupportedOperationException("Not used by the export path")

        override fun getWaterTotalMl(date: String): Int =
            throw UnsupportedOperationException("Not used by the export path")

        override fun logWater(amountMl: Int, date: String): Int =
            throw UnsupportedOperationException("Not used by the export path")
    }

    private class FakeMealRepository : MealRepository {
        var entries: List<MealEntry> = emptyList()
        var getMealEntriesCallCount: Int = 0
        var failureToThrow: RuntimeException? = null

        override fun getMealEntries(): List<MealEntry> {
            getMealEntriesCallCount++
            failureToThrow?.let { throw it }
            return entries
        }

        override fun getMealEntriesByDate(date: String): List<MealEntry> =
            throw UnsupportedOperationException("Not used by the export path")

        override fun addMealEntry(date: String): List<MealEntry> =
            throw UnsupportedOperationException("Not used by the export path")

        override fun updateMealEntry(id: String, newTitle: String): List<MealEntry> =
            throw UnsupportedOperationException("Not used by the export path")

        override fun deleteMealEntry(id: String): List<MealEntry> =
            throw UnsupportedOperationException("Not used by the export path")

        override fun logQuickAdd(
            name: String,
            calories: Double,
            proteinG: Double,
            carbsG: Double,
            fatG: Double,
            mealSlot: String,
            date: String
        ): List<MealEntry> = throw UnsupportedOperationException("Not used by the export path")

        override fun logFoodEntry(
            foodName: String,
            quantityGrams: Double,
            calories: Double,
            proteinG: Double,
            carbsG: Double,
            fatG: Double,
            fiberG: Double,
            mealSlot: String,
            date: String
        ): List<MealEntry> = throw UnsupportedOperationException("Not used by the export path")

        override fun restoreMealEntry(entry: MealEntry): List<MealEntry> =
            throw UnsupportedOperationException("Not used by the export path")
    }
}
