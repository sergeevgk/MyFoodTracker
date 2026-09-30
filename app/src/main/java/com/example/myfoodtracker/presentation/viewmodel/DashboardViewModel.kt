package com.example.myfoodtracker.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.myfoodtracker.domain.model.DailySummary
import com.example.myfoodtracker.domain.repository.SessionRepository
import com.example.myfoodtracker.domain.usecase.GetMealEntriesByDateUseCase
import com.example.myfoodtracker.domain.usecase.GetWaterTotalUseCase
import com.example.myfoodtracker.domain.usecase.LogWaterUseCase
import com.example.myfoodtracker.domain.usecase.LogoutUseCase
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
    private val logWaterUseCase: LogWaterUseCase
) : ViewModel() {

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

    fun logout() {
        logoutUseCase()
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
