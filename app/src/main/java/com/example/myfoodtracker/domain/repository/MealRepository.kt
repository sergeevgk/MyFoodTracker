package com.example.myfoodtracker.domain.repository

import com.example.myfoodtracker.domain.model.MealEntry

interface MealRepository {
    fun getMealEntries(): List<MealEntry>
    fun addMealEntry(): List<MealEntry>
    fun updateMealEntry(id: String, newTitle: String): List<MealEntry>
    fun deleteMealEntry(id: String): List<MealEntry>
}
