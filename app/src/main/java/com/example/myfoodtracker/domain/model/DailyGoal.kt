package com.example.myfoodtracker.domain.model

data class DailyGoal(
    val profileId: String = "",
    val targetCalories: Double? = null,
    val targetProteinG: Double? = null,
    val targetCarbsG: Double? = null,
    val targetFatG: Double? = null,
    val targetWaterMl: Int? = null
) {
    val calorieTarget: Double? get() = targetCalories
    val proteinTargetGrams: Double? get() = targetProteinG
    val carbTargetGrams: Double? get() = targetCarbsG
    val fatTargetGrams: Double? get() = targetFatG
    val waterTargetMl: Int? get() = targetWaterMl
}
