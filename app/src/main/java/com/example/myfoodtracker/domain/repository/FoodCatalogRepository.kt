package com.example.myfoodtracker.domain.repository

import com.example.myfoodtracker.domain.model.FoodItem

interface FoodCatalogRepository {
    fun search(query: String, limit: Int = 30): List<FoodItem>

    fun createCustomFood(
        name: String,
        brand: String?,
        baseServingSize: Double,
        baseServingUnit: String,
        calories: Double,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
        fiberG: Double = 0.0,
        sugarG: Double = 0.0,
        sodiumMg: Double = 0.0
    ): FoodItem
}
