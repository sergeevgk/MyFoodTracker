package com.example.myfoodtracker.domain.repository

import com.example.myfoodtracker.domain.model.FoodEntry

interface FoodRepository {
    fun getFoodEntries(): List<FoodEntry>
    fun addFoodEntry(): List<FoodEntry>
    fun updateFoodEntry(id: String, newText: String): List<FoodEntry>
    fun deleteFoodEntry(id: String): List<FoodEntry>
}
