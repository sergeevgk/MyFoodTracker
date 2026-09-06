package com.example.myfoodtracker.domain.model

data class DailyGoal(
    val id: Long = 0,
    val profileId: Long = 0,
    val calorieTarget: Int? = null,
    val proteinTargetGrams: Int? = null,
    val carbTargetGrams: Int? = null,
    val fatTargetGrams: Int? = null,
    val waterTargetMl: Int? = null
)
