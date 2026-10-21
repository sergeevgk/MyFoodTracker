package com.example.myfoodtracker.presentation.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.myfoodtracker.domain.model.DailyGoal
import com.example.myfoodtracker.domain.model.ExportFormat
import com.example.myfoodtracker.domain.model.Food
import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.MealRepository
import com.example.myfoodtracker.domain.repository.SessionRepository
import com.example.myfoodtracker.domain.usecase.ExportLogsUseCase
import com.example.myfoodtracker.presentation.ui.settings.model.ExportState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    private lateinit var exportLogsUseCase: ExportLogsUseCase
    private lateinit var viewModel: SettingsViewModel

    private val testUser = UserProfile(
        id = "user-1",
        username = "Georgii",
        passcodeHash = "hash",
        dailyGoal = DailyGoal()
    )

    private val from = LocalDate.of(2026, 10, 1)
    private val to = LocalDate.of(2026, 10, 21)

    @Before
    fun setUp() {
        fakeSessionRepository = FakeSessionRepository()
        fakeSessionRepository.setActiveProfile(testUser)
        fakeMealRepository = FakeMealRepository()
        exportLogsUseCase = ExportLogsUseCase(fakeMealRepository)
        viewModel = SettingsViewModel(
            exportLogsUseCase = exportLogsUseCase,
            sessionRepository = fakeSessionRepository
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

    // --- Helpers -------------------------------------------------------------------

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

        override fun addMealEntry(): List<MealEntry> =
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
