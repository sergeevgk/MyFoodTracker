package com.example.myfoodtracker.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.myfoodtracker.domain.model.DailySummary
import com.example.myfoodtracker.domain.model.FoodItem
import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.model.ServingUnit
import com.example.myfoodtracker.domain.repository.SessionRepository
import com.example.myfoodtracker.domain.usecase.DeleteMealEntryUseCase
import com.example.myfoodtracker.domain.usecase.GetMealEntriesByDateUseCase
import com.example.myfoodtracker.domain.usecase.GetWaterTotalUseCase
import com.example.myfoodtracker.domain.usecase.LogFoodEntryUseCase
import com.example.myfoodtracker.domain.usecase.LogQuickAddUseCase
import com.example.myfoodtracker.domain.usecase.LogWaterUseCase
import com.example.myfoodtracker.domain.usecase.LogoutUseCase
import com.example.myfoodtracker.domain.usecase.RestoreMealEntryUseCase
import com.example.myfoodtracker.presentation.ui.dashboard.model.DashboardUiState
import com.example.myfoodtracker.presentation.ui.dashboard.model.DayItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

class DashboardViewModel(
    private val sessionRepository: SessionRepository,
    private val logoutUseCase: LogoutUseCase,
    private val getMealEntriesByDateUseCase: GetMealEntriesByDateUseCase,
    private val getWaterTotalUseCase: GetWaterTotalUseCase,
    private val logWaterUseCase: LogWaterUseCase,
    private val logQuickAddUseCase: LogQuickAddUseCase,
    private val logFoodEntryUseCase: LogFoodEntryUseCase,
    private val deleteMealEntryUseCase: DeleteMealEntryUseCase,
    private val restoreMealEntryUseCase: RestoreMealEntryUseCase
) : ViewModel() {

    private var pendingDeleted: MealEntry? = null
    private var pendingDeletedIndex: Int = -1

    private val _uiState = MutableLiveData<DashboardUiState>()
    val uiState: LiveData<DashboardUiState> = _uiState

    private val headerDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.getDefault())
    private val dayNameFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    private val dayNumberFormatter = DateTimeFormatter.ofPattern("d", Locale.getDefault())

    init {
        initDashboard()
    }

    fun initDashboard() {
        val today = LocalDate.now()
        selectDate(today)
    }

    fun selectDate(date: LocalDate) {
        val profile = sessionRepository.getActiveProfile()
        val username = profile?.username ?: ""
        val profileId = profile?.id ?: ""

        val weekDays = buildWeekDays(date)
        val mealEntries = getMealEntriesByDateUseCase(date.toString())
        val summary = DailySummary.summarize(mealEntries)
        val goal = profile?.dailyGoal
        val waterTotal = getWaterTotalUseCase(date.toString())

        _uiState.value = DashboardUiState(
            activeDate = date,
            formattedDateHeader = date.format(headerDateFormatter),
            weekDays = weekDays,
            mealEntries = mealEntries,
            username = username,
            profileId = profileId,
            dailySummary = summary,
            dailyGoal = goal,
            waterTotalMl = waterTotal
        )
    }

    fun previousWeek() {
        val currentDate = _uiState.value?.activeDate ?: LocalDate.now()
        selectDate(currentDate.minusWeeks(1))
    }

    fun nextWeek() {
        val currentDate = _uiState.value?.activeDate ?: LocalDate.now()
        selectDate(currentDate.plusWeeks(1))
    }

    fun jumpToToday() {
        selectDate(LocalDate.now())
    }

    fun logWaterPlus250() {
        val date = _uiState.value?.activeDate ?: LocalDate.now()
        val total = logWaterUseCase(250, date.toString())
        _uiState.value = _uiState.value?.copy(waterTotalMl = total)
    }

    fun logQuickAdd(
        name: String,
        calories: Double,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
        mealSlot: String
    ) {
        val date = _uiState.value?.activeDate ?: LocalDate.now()
        try {
            logQuickAddUseCase(name, calories, proteinG, carbsG, fatG, mealSlot, date.toString())
        } catch (e: IllegalArgumentException) {
            return
        }
        selectDate(date)
    }

    fun logFoodEntry(
        food: FoodItem,
        quantity: Double,
        unit: ServingUnit,
        mealSlot: String
    ) {
        val date = _uiState.value?.activeDate ?: LocalDate.now()
        try {
            logFoodEntryUseCase(food, quantity, unit, mealSlot, date.toString())
        } catch (e: IllegalArgumentException) {
            return
        }
        selectDate(date)
    }

    fun logout() {
        logoutUseCase()
    }

    fun deleteMealEntry(entryId: String) {
        val date = _uiState.value?.activeDate ?: LocalDate.now()
        val entries = _uiState.value?.mealEntries ?: return
        val index = entries.indexOfFirst { it.id == entryId }
        // Stale-id safe no-op (already deleted or never existed).
        if (index == -1) return
        pendingDeleted = entries[index]
        pendingDeletedIndex = index
        // Ignore the all-meals return; selectDate reloads the date-filtered list + summary.
        deleteMealEntryUseCase(entryId)
        selectDate(date)
    }

    fun restoreLastDeleted() {
        val pending = pendingDeleted ?: return
        restoreMealEntryUseCase(pending)
        pendingDeleted = null
        pendingDeletedIndex = -1
        // The repo write already targeted pending.date; refresh the currently viewed
        // date so the visible list stays coherent even if the user navigated away
        // while the Snackbar was showing.
        selectDate(_uiState.value?.activeDate ?: LocalDate.parse(pending.date))
    }

    fun clearPendingDelete() {
        pendingDeleted = null
        pendingDeletedIndex = -1
    }

    private fun buildWeekDays(selectedDate: LocalDate): List<DayItem> {
        val today = LocalDate.now()
        val monday = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        return (0L..6L).map { offset ->
            val dayDate = monday.plusDays(offset)
            DayItem(
                date = dayDate,
                dayName = dayDate.format(dayNameFormatter),
                dayNumber = dayDate.format(dayNumberFormatter),
                isSelected = (dayDate == selectedDate),
                isToday = (dayDate == today)
            )
        }
    }
}
