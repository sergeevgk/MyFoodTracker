package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.FoodItem
import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.model.ServingUnit
import com.example.myfoodtracker.domain.repository.MealRepository

class LogFoodEntryUseCase(private val repository: MealRepository) {
    operator fun invoke(
        food: FoodItem,
        quantity: Double,
        unit: ServingUnit,
        mealSlot: String,
        date: String
    ): List<MealEntry> {
        require(food.name.isNotBlank()) { "Enter a name" }
        require(quantity.isFinite() && quantity > 0) { "Enter a quantity greater than 0" }
        val slot = mealSlot.trim().uppercase()
        require(slot in setOf("BREAKFAST", "LUNCH", "DINNER", "SNACK")) { "Invalid meal slot" }
        val grams = when (unit) {
            ServingUnit.G -> quantity
            ServingUnit.ML -> quantity
            ServingUnit.SERVINGS -> quantity * food.baseServingSize
        }
        require(grams.isFinite() && grams > 0) { "Enter a quantity greater than 0" }
        val factor = grams / 100.0
        return repository.logFoodEntry(
            food.name,
            grams,
            food.calories * factor,
            food.proteinG * factor,
            food.carbsG * factor,
            food.fatG * factor,
            food.fiberG * factor,
            slot,
            date
        )
    }
}
