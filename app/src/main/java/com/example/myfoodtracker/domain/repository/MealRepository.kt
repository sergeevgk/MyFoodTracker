package com.example.myfoodtracker.domain.repository

import com.example.myfoodtracker.domain.model.MealEntry

interface MealRepository {
    fun getMealEntries(): List<MealEntry>
    fun getMealEntriesByDate(date: String): List<MealEntry>
    fun addMealEntry(): List<MealEntry>
    fun updateMealEntry(id: String, newTitle: String): List<MealEntry>
    fun deleteMealEntry(id: String): List<MealEntry>
    fun logQuickAdd(
        name: String,
        calories: Double,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
        mealSlot: String,
        date: String
    ): List<MealEntry>
    fun logFoodEntry(
        foodName: String,
        quantityGrams: Double,
        calories: Double,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
        fiberG: Double,
        mealSlot: String,
        date: String
    ): List<MealEntry>
    fun restoreMealEntry(entry: MealEntry): List<MealEntry>
}
