package com.example.myfoodtracker.presentation.ui.dashboard.model

import com.example.myfoodtracker.domain.model.DailyGoal
import com.example.myfoodtracker.domain.model.DailySummary
import com.example.myfoodtracker.domain.model.MealEntry
import java.time.LocalDate

data class DashboardUiState(
    val activeDate: LocalDate = LocalDate.now(),
    val formattedDateHeader: String = "",
    val weekDays: List<DayItem> = emptyList(),
    val mealEntries: List<MealEntry> = emptyList(),
    val username: String = "",
    val profileId: String = "",
    val dailySummary: DailySummary = DailySummary(),
    val dailyGoal: DailyGoal? = null
)
