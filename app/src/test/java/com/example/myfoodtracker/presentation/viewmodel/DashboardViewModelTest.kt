package com.example.myfoodtracker.presentation.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.myfoodtracker.domain.model.DailyGoal
import com.example.myfoodtracker.domain.model.Food
import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.MealRepository
import com.example.myfoodtracker.domain.repository.SessionRepository
import com.example.myfoodtracker.domain.usecase.GetMealEntriesByDateUseCase
import com.example.myfoodtracker.domain.usecase.LogoutUseCase
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
    private lateinit var logoutUseCase: LogoutUseCase
    private lateinit var getMealEntriesByDateUseCase: GetMealEntriesByDateUseCase
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
        fakeMealRepository = FakeMealRepository()
        logoutUseCase = LogoutUseCase(fakeSessionRepository)
        getMealEntriesByDateUseCase = GetMealEntriesByDateUseCase(fakeMealRepository)

        viewModel = DashboardViewModel(
            sessionRepository = fakeSessionRepository,
            logoutUseCase = logoutUseCase,
            getMealEntriesByDateUseCase = getMealEntriesByDateUseCase
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

    private class FakeSessionRepository : SessionRepository {
        private val current = AtomicReference<UserProfile?>(null)

        override fun getActiveProfile(): UserProfile? = current.get()
        override fun getActiveProfileId(): String? = current.get()?.id
        override fun setActiveProfile(profile: UserProfile) { current.set(profile) }
        override fun clearSession() { current.set(null) }
        override fun isLoggedIn(): Boolean = current.get() != null
    }

    private class FakeMealRepository : MealRepository {
        val entriesByDate = mutableMapOf<String, List<MealEntry>>()

        override fun getMealEntries(): List<MealEntry> = emptyList()

        override fun getMealEntriesByDate(date: String): List<MealEntry> {
            return entriesByDate[date] ?: emptyList()
        }

        override fun addMealEntry(): List<MealEntry> = emptyList()
        override fun updateMealEntry(id: String, newTitle: String): List<MealEntry> = emptyList()
        override fun deleteMealEntry(id: String): List<MealEntry> = emptyList()
    }
}
