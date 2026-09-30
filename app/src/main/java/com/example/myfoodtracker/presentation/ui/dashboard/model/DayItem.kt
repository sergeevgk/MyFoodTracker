package com.example.myfoodtracker.presentation.ui.dashboard.model

import java.time.LocalDate

data class DayItem(
    val date: LocalDate,
    val dayName: String,
    val dayNumber: String,
    val isSelected: Boolean,
    val isToday: Boolean
)
