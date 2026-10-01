package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.repository.MealRepository

class LogQuickAddUseCase(private val repository: MealRepository) {
    operator fun invoke(
        name: String,
        calories: Double,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
        mealSlot: String,
        date: String
    ): List<MealEntry> {
        require(name.isNotBlank()) { "Enter a name" }
        require(calories.isFinite() && proteinG.isFinite() && carbsG.isFinite() && fatG.isFinite()) { "Enter 0 or more" }
        require(calories >= 0 && proteinG >= 0 && carbsG >= 0 && fatG >= 0) { "Enter 0 or more" }
        val slot = mealSlot.trim().uppercase()
        require(slot in setOf("BREAKFAST", "LUNCH", "DINNER", "SNACK")) { "Invalid meal slot" }
        return repository.logQuickAdd(
            name.trim(),
            calories,
            proteinG,
            carbsG,
            fatG,
            slot,
            date
        )
    }
}
