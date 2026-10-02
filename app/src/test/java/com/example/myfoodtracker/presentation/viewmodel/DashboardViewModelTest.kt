package com.example.myfoodtracker.presentation.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.myfoodtracker.domain.model.DailyGoal
import com.example.myfoodtracker.domain.model.Food
import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.model.WaterLog
import com.example.myfoodtracker.domain.repository.MealRepository
import com.example.myfoodtracker.domain.repository.SessionRepository
import com.example.myfoodtracker.domain.repository.WaterRepository
import com.example.myfoodtracker.domain.usecase.DeleteMealEntryUseCase
import com.example.myfoodtracker.domain.usecase.GetMealEntriesByDateUseCase
import com.example.myfoodtracker.domain.usecase.GetWaterTotalUseCase
import com.example.myfoodtracker.domain.usecase.LogQuickAddUseCase
import com.example.myfoodtracker.domain.usecase.LogWaterUseCase
import com.example.myfoodtracker.domain.usecase.LogoutUseCase
import com.example.myfoodtracker.domain.usecase.RestoreMealEntryUseCase
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicReference

class DashboardViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var fakeSessionRepository: FakeSessionRepository
    private lateinit var fakeMealRepository: FakeMealRepository
    private lateinit var fakeWaterRepository: FakeWaterRepository
    private lateinit var logoutUseCase: LogoutUseCase
    private lateinit var getMealEntriesByDateUseCase: GetMealEntriesByDateUseCase
    private lateinit var getWaterTotalUseCase: GetWaterTotalUseCase
    private lateinit var logWaterUseCase: LogWaterUseCase
    private lateinit var logQuickAddUseCase: LogQuickAddUseCase
    private lateinit var deleteMealEntryUseCase: DeleteMealEntryUseCase
    private lateinit var restoreMealEntryUseCase: RestoreMealEntryUseCase
    private lateinit var viewModel: DashboardViewModel

    private val testUser = UserProfile(
        id = "user-1",
        username = "Georgii",
        passcodeHash = "hash",
        dailyGoal = DailyGoal()
    )

    @Before
    fun setUp() {
        fakeSessionRepository = FakeSessionRepository()
        fakeSessionRepository.setActiveProfile(testUser)
        fakeMealRepository = FakeMealRepository(fakeSessionRepository)
        fakeWaterRepository = FakeWaterRepository(fakeSessionRepository)
        logoutUseCase = LogoutUseCase(fakeSessionRepository)
        getMealEntriesByDateUseCase = GetMealEntriesByDateUseCase(fakeMealRepository)
        getWaterTotalUseCase = GetWaterTotalUseCase(fakeWaterRepository)
        logWaterUseCase = LogWaterUseCase(fakeWaterRepository)
        logQuickAddUseCase = LogQuickAddUseCase(fakeMealRepository)
        deleteMealEntryUseCase = DeleteMealEntryUseCase(fakeMealRepository)
        restoreMealEntryUseCase = RestoreMealEntryUseCase(fakeMealRepository)

        viewModel = DashboardViewModel(
            sessionRepository = fakeSessionRepository,
            logoutUseCase = logoutUseCase,
            getMealEntriesByDateUseCase = getMealEntriesByDateUseCase,
            getWaterTotalUseCase = getWaterTotalUseCase,
            logWaterUseCase = logWaterUseCase,
            logQuickAddUseCase = logQuickAddUseCase,
            deleteMealEntryUseCase = deleteMealEntryUseCase,
            restoreMealEntryUseCase = restoreMealEntryUseCase
        )
    }

    @Test
    fun initDashboard_populatesTodayAndWeekDaysAndUserProfile() {
        viewModel.initDashboard()

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertEquals("Georgii", state!!.username)
        assertEquals("user-1", state.profileId)
        assertEquals(LocalDate.now(), state.activeDate)
        assertEquals(7, state.weekDays.size)

        // Verifies week starts on Monday and ends on Sunday
        assertEquals(DayOfWeek.MONDAY, state.weekDays.first().date.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, state.weekDays.last().date.dayOfWeek)

        // Verifies today is marked as selected
        val todayItem = state.weekDays.firstOrNull { it.isToday }
        assertNotNull(todayItem)
        assertTrue(todayItem!!.isSelected)
    }

    @Test
    fun selectDate_switchesActiveDateAndLoadsMealsForDate() {
        val targetDate = LocalDate.of(2026, 9, 15) // Tuesday
        val expectedMeal = MealEntry(
            id = "meal-101",
            title = "Lunch",
            date = "2026-09-15",
            time = "13:00",
            foods = emptyList()
        )
        fakeMealRepository.entriesByDate["2026-09-15"] = listOf(expectedMeal)

        viewModel.selectDate(targetDate)

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertEquals(targetDate, state!!.activeDate)
        assertEquals(1, state.mealEntries.size)
        assertEquals("meal-101", state.mealEntries[0].id)

        // The selected day in weekDays should be targetDate
        val selectedItem = state.weekDays.first { it.isSelected }
        assertEquals(targetDate, selectedItem.date)
    }

    @Test
    fun previousWeek_shiftsDateByMinus7Days() {
        val anchorDate = LocalDate.of(2026, 9, 20)
        viewModel.selectDate(anchorDate)

        viewModel.previousWeek()

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertEquals(LocalDate.of(2026, 9, 13), state!!.activeDate)
    }

    @Test
    fun nextWeek_shiftsDateByPlus7Days() {
        val anchorDate = LocalDate.of(2026, 9, 20)
        viewModel.selectDate(anchorDate)

        viewModel.nextWeek()

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertEquals(LocalDate.of(2026, 9, 27), state!!.activeDate)
    }

    @Test
    fun jumpToToday_resetsActiveDateToToday() {
        val pastDate = LocalDate.of(2025, 1, 1)
        viewModel.selectDate(pastDate)
        assertEquals(pastDate, viewModel.uiState.value?.activeDate)

        viewModel.jumpToToday()

        assertEquals(LocalDate.now(), viewModel.uiState.value?.activeDate)
    }

    @Test
    fun logout_clearsActiveSession() {
        assertTrue(fakeSessionRepository.isLoggedIn())

        viewModel.logout()

        assertFalse(fakeSessionRepository.isLoggedIn())
        assertNull(fakeSessionRepository.getActiveProfile())
    }

    @Test
    fun selectDate_withNoMeals_emitsEmptyMealEntries() {
        val targetDate = LocalDate.of(2026, 10, 1)
        viewModel.selectDate(targetDate)

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertTrue(state!!.mealEntries.isEmpty())
    }

    @Test
    fun selectDate_withNullProfile_emitsEmptyUserStrings() {
        fakeSessionRepository.clearSession()
        viewModel.selectDate(LocalDate.now())

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertEquals("", state!!.username)
        assertEquals("", state.profileId)
    }

    @Test
    fun selectDate_populatesDailySummaryTotalsFromFoods() {
        val targetDate = LocalDate.of(2026, 9, 15)
        fakeMealRepository.entriesByDate["2026-09-15"] = listOf(
            MealEntry(
                id = "meal-1",
                title = "Breakfast",
                date = "2026-09-15",
                time = "08:00",
                foods = listOf(
                    Food(name = "Eggs", weight = 100.0, calories = 155.0, carbs = 1.1, fat = 11.0, protein = 13.0, fiber = 0.0),
                    Food(name = "Toast", weight = 50.0, calories = 130.0, carbs = 25.0, fat = 2.0, protein = 4.0, fiber = 1.0)
                )
            ),
            MealEntry(
                id = "meal-2",
                title = "Lunch",
                date = "2026-09-15",
                time = "13:00",
                foods = listOf(
                    Food(name = "Chicken", weight = 100.0, calories = 165.0, carbs = 0.0, fat = 3.6, protein = 31.0, fiber = 0.0)
                )
            )
        )

        viewModel.selectDate(targetDate)

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertEquals(450.0, state!!.dailySummary.totalCalories, 0.001)
        assertEquals(48.0, state.dailySummary.totalProteinG, 0.001)
        assertEquals(26.1, state.dailySummary.totalCarbsG, 0.001)
        assertEquals(16.6, state.dailySummary.totalFatG, 0.001)
    }

    @Test
    fun selectDate_exposesDailyGoalFromActiveProfile() {
        val goal = DailyGoal(
            profileId = "user-1",
            targetCalories = 2000.0,
            targetProteinG = 120.0,
            targetCarbsG = null,
            targetFatG = 65.0,
            targetWaterMl = 2000
        )
        fakeSessionRepository.setActiveProfile(testUser.copy(dailyGoal = goal))

        viewModel.selectDate(LocalDate.of(2026, 9, 15))

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertEquals(2000.0, state!!.dailyGoal?.targetCalories ?: 0.0, 0.001)
        assertEquals(120.0, state.dailyGoal?.targetProteinG ?: 0.0, 0.001)
        assertNull(state.dailyGoal?.targetCarbsG)
        assertEquals(65.0, state.dailyGoal?.targetFatG ?: 0.0, 0.001)
    }

    @Test
    fun selectDate_withNoMeals_emitsZeroDailySummary() {
        val targetDate = LocalDate.of(2026, 10, 1)
        viewModel.selectDate(targetDate)

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertEquals(0.0, state!!.dailySummary.totalCalories, 0.001)
        assertEquals(0.0, state.dailySummary.totalProteinG, 0.001)
        assertEquals(0.0, state.dailySummary.totalCarbsG, 0.001)
        assertEquals(0.0, state.dailySummary.totalFatG, 0.001)
    }

    @Test
    fun selectDate_withNullProfile_emitsEmptySummaryAndNullGoal() {
        fakeSessionRepository.clearSession()
        viewModel.selectDate(LocalDate.now())

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertEquals(0.0, state!!.dailySummary.totalCalories, 0.001)
        assertNull(state.dailyGoal)
    }

    @Test
    fun selectDate_populatesWaterTotalMlFromStubbedWater() {
        val targetDate = LocalDate.of(2026, 9, 15)
        fakeWaterRepository.amountsByDate["user-1|2026-09-15"] = mutableListOf(250, 250)

        viewModel.selectDate(targetDate)

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertEquals(500, state!!.waterTotalMl)
    }

    @Test
    fun logWaterPlus250_inserts250ForActiveDateAndUpdatesState() {
        val targetDate = LocalDate.of(2026, 9, 15)
        viewModel.selectDate(targetDate)
        assertEquals(0, viewModel.uiState.value?.waterTotalMl)

        viewModel.logWaterPlus250()

        assertEquals(250, viewModel.uiState.value?.waterTotalMl)

        viewModel.logWaterPlus250()

        assertEquals(500, viewModel.uiState.value?.waterTotalMl)
    }

    @Test
    fun selectDate_reloadsWaterTotalPerDate_notStale() {
        fakeWaterRepository.amountsByDate["user-1|2026-09-15"] = mutableListOf(500)
        viewModel.selectDate(LocalDate.of(2026, 9, 15))
        assertEquals(500, viewModel.uiState.value?.waterTotalMl)

        viewModel.selectDate(LocalDate.of(2026, 9, 16))

        assertEquals(0, viewModel.uiState.value?.waterTotalMl)
    }

    @Test
    fun water_withNullProfile_emitsZeroAndNoCrash() {
        fakeSessionRepository.clearSession()
        viewModel.selectDate(LocalDate.now())

        assertEquals(0, viewModel.uiState.value?.waterTotalMl)

        viewModel.logWaterPlus250()

        assertEquals(0, viewModel.uiState.value?.waterTotalMl)
    }

    @Test
    fun logWater_withSkippedWaterTarget_stillLogsTotal() {
        fakeSessionRepository.setActiveProfile(testUser.copy(dailyGoal = DailyGoal(targetWaterMl = null)))
        viewModel.selectDate(LocalDate.of(2026, 9, 15))

        viewModel.logWaterPlus250()

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertEquals(250, state!!.waterTotalMl)
        assertNull(state.dailyGoal?.targetWaterMl)
    }

    @Test
    fun logQuickAdd_insertsForActiveDateAndUpdatesSummary() {
        val targetDate = LocalDate.of(2026, 9, 15)
        viewModel.selectDate(targetDate)
        assertEquals(0, viewModel.uiState.value?.mealEntries?.size)

        viewModel.logQuickAdd("Office lunch", 500.0, 20.0, 45.0, 15.0, "LUNCH")

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertEquals(1, state!!.mealEntries.size)
        assertEquals("LUNCH", state.mealEntries[0].title)
        assertEquals("2026-09-15", state.mealEntries[0].date)
        assertEquals(500.0, state.dailySummary.totalCalories, 0.001)
        assertEquals(20.0, state.dailySummary.totalProteinG, 0.001)
    }

    @Test
    fun logQuickAdd_onHistoricalDate_writesToSelectedDate() {
        val pastDate = LocalDate.of(2026, 9, 1)
        viewModel.selectDate(pastDate)

        viewModel.logQuickAdd("Old dinner", 700.0, 30.0, 60.0, 20.0, "DINNER")

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertEquals(pastDate, state!!.activeDate)
        assertEquals(1, state.mealEntries.size)
        assertEquals("2026-09-01", state.mealEntries[0].date)
        assertTrue((fakeMealRepository.entriesByDate[LocalDate.now().toString()] ?: emptyList()).isEmpty())
    }

    @Test
    fun logQuickAdd_withBlankName_stateUnchangedAndNoCrash() {
        val targetDate = LocalDate.of(2026, 9, 15)
        viewModel.selectDate(targetDate)

        viewModel.logQuickAdd("   ", 500.0, 20.0, 45.0, 15.0, "LUNCH")

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertTrue(state!!.mealEntries.isEmpty())
        assertEquals(0.0, state.dailySummary.totalCalories, 0.001)
    }

    @Test
    fun logQuickAdd_withNullProfile_noCrashAndMealsEmpty() {
        fakeSessionRepository.clearSession()
        viewModel.selectDate(LocalDate.now())

        viewModel.logQuickAdd("Snack", 100.0, 1.0, 1.0, 1.0, "SNACK")

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertTrue(state!!.mealEntries.isEmpty())
    }

    @Test
    fun deleteMealEntry_removesRowAndZeroesSummary() {
        val targetDate = LocalDate.of(2026, 9, 15)
        fakeMealRepository.entriesByDate["2026-09-15"] = listOf(
            MealEntry(
                id = "meal-del",
                title = "LUNCH",
                date = "2026-09-15",
                time = "13:00",
                foods = listOf(
                    Food(name = "Lunch", weight = 0.0, calories = 500.0, carbs = 45.0, fat = 15.0, protein = 20.0, fiber = 0.0)
                )
            )
        )
        viewModel.selectDate(targetDate)
        assertEquals(500.0, viewModel.uiState.value!!.dailySummary.totalCalories, 0.001)

        viewModel.deleteMealEntry("meal-del")

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertTrue(state!!.mealEntries.isEmpty())
        assertEquals(0.0, state.dailySummary.totalCalories, 0.001)
        assertEquals(0.0, state.dailySummary.totalProteinG, 0.001)
    }

    @Test
    fun restoreLastDeleted_bringsEntryAndSummaryBack() {
        val targetDate = LocalDate.of(2026, 9, 15)
        fakeMealRepository.entriesByDate["2026-09-15"] = listOf(
            MealEntry(
                id = "meal-undo",
                title = "DINNER",
                date = "2026-09-15",
                time = "19:00",
                foods = listOf(
                    Food(name = "Dinner", weight = 0.0, calories = 700.0, carbs = 60.0, fat = 20.0, protein = 30.0, fiber = 0.0)
                )
            )
        )
        viewModel.selectDate(targetDate)
        viewModel.deleteMealEntry("meal-undo")
        assertTrue(viewModel.uiState.value!!.mealEntries.isEmpty())

        viewModel.restoreLastDeleted()

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertEquals(1, state!!.mealEntries.size)
        assertEquals("meal-undo", state.mealEntries[0].id)
        assertEquals("DINNER", state.mealEntries[0].title)
        assertEquals(700.0, state.dailySummary.totalCalories, 0.001)
        assertEquals(30.0, state.dailySummary.totalProteinG, 0.001)
    }

    @Test
    fun deleteMealEntry_onHistoricalDate_leavesTodayUntouched() {
        val pastDate = LocalDate.of(2026, 9, 1)
        val todayStr = LocalDate.now().toString()
        fakeMealRepository.entriesByDate["2026-09-01"] = listOf(
            MealEntry(id = "meal-old", title = "DINNER", date = "2026-09-01", time = "19:00", foods = emptyList())
        )
        fakeMealRepository.entriesByDate[todayStr] = listOf(
            MealEntry(id = "meal-today", title = "LUNCH", date = todayStr, time = "12:00", foods = emptyList())
        )
        viewModel.selectDate(pastDate)

        viewModel.deleteMealEntry("meal-old")

        assertTrue(viewModel.uiState.value!!.mealEntries.isEmpty())
        assertEquals(1, fakeMealRepository.entriesByDate[todayStr]?.size)
    }

    @Test
    fun deleteMealEntry_withStaleId_noOpAndNoCrash() {
        val targetDate = LocalDate.of(2026, 9, 15)
        fakeMealRepository.entriesByDate["2026-09-15"] = listOf(
            MealEntry(id = "meal-keep", title = "LUNCH", date = "2026-09-15", time = "13:00", foods = emptyList())
        )
        viewModel.selectDate(targetDate)

        viewModel.deleteMealEntry("no-such-id")

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertEquals(1, state!!.mealEntries.size)
        assertEquals("meal-keep", state.mealEntries[0].id)
    }

    @Test
    fun deleteMealEntry_withNullProfile_noCrashAndMealsEmpty() {
        fakeSessionRepository.clearSession()
        viewModel.selectDate(LocalDate.now())

        viewModel.deleteMealEntry("any-id")
        viewModel.restoreLastDeleted()

        val state = viewModel.uiState.value
        assertNotNull(state)
        assertTrue(state!!.mealEntries.isEmpty())
    }

    private class FakeSessionRepository : SessionRepository {
        private val current = AtomicReference<UserProfile?>(null)

        override fun getActiveProfile(): UserProfile? = current.get()
        override fun getActiveProfileId(): String? = current.get()?.id
        override fun setActiveProfile(profile: UserProfile) { current.set(profile) }
        override fun clearSession() { current.set(null) }
        override fun isLoggedIn(): Boolean = current.get() != null
    }

    private class FakeMealRepository(
        private val sessionRepository: SessionRepository
    ) : MealRepository {
        val entriesByDate = mutableMapOf<String, List<MealEntry>>()

        override fun getMealEntries(): List<MealEntry> = emptyList()

        override fun getMealEntriesByDate(date: String): List<MealEntry> {
            return entriesByDate[date] ?: emptyList()
        }

        override fun addMealEntry(): List<MealEntry> = emptyList()
        override fun updateMealEntry(id: String, newTitle: String): List<MealEntry> = emptyList()
        override fun deleteMealEntry(id: String): List<MealEntry> {
            sessionRepository.getActiveProfileId() ?: return emptyList()
            entriesByDate.forEach { (date, entries) ->
                entriesByDate[date] = entries.filter { it.id != id }
            }
            return emptyList()
        }

        override fun restoreMealEntry(entry: MealEntry): List<MealEntry> {
            sessionRepository.getActiveProfileId() ?: return emptyList()
            val current = getMealEntriesByDate(entry.date).filter { it.id != entry.id }
            entriesByDate[entry.date] = current + entry
            return getMealEntriesByDate(entry.date)
        }

        override fun logQuickAdd(
            name: String,
            calories: Double,
            proteinG: Double,
            carbsG: Double,
            fatG: Double,
            mealSlot: String,
            date: String
        ): List<MealEntry> {
            sessionRepository.getActiveProfileId() ?: return getMealEntriesByDate(date)
            val entry = MealEntry(
                id = java.util.UUID.randomUUID().toString(),
                title = mealSlot,
                date = date,
                time = "12:00",
                foods = listOf(
                    Food(
                        name = name,
                        weight = 0.0,
                        calories = calories,
                        carbs = carbsG,
                        fat = fatG,
                        protein = proteinG,
                        fiber = 0.0
                    )
                )
            )
            entriesByDate[date] = getMealEntriesByDate(date) + entry
            return getMealEntriesByDate(date)
        }
    }

    private class FakeWaterRepository(
        private val sessionRepository: SessionRepository
    ) : WaterRepository {
        val amountsByDate = mutableMapOf<String, MutableList<Int>>()

        override fun getWaterLogs(date: String): List<WaterLog> {
            val profileId = sessionRepository.getActiveProfileId() ?: return emptyList()
            return (amountsByDate["$profileId|$date"] ?: emptyList()).mapIndexed { index, amount ->
                WaterLog(id = "water-$index", profileId = profileId, date = date, amountMl = amount)
            }
        }

        override fun getWaterTotalMl(date: String): Int {
            val profileId = sessionRepository.getActiveProfileId() ?: return 0
            return amountsByDate["$profileId|$date"]?.sum() ?: 0
        }

        override fun logWater(amountMl: Int, date: String): Int {
            val profileId = sessionRepository.getActiveProfileId() ?: return 0
            amountsByDate.getOrPut("$profileId|$date") { mutableListOf() }.add(amountMl)
            return getWaterTotalMl(date)
        }
    }
}
