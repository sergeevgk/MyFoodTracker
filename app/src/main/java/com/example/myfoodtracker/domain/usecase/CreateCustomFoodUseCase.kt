package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.FoodItem
import com.example.myfoodtracker.domain.repository.FoodCatalogRepository

class CreateCustomFoodUseCase(private val repository: FoodCatalogRepository) {
    operator fun invoke(
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
    ): FoodItem {
        val trimmedName = name.trim()
        require(trimmedName.isNotBlank()) { "Enter a name" }
        require(trimmedName.length <= 100) { "Name must be 100 characters or fewer" }
        val trimmedBrand = brand?.trim()?.ifBlank { null }
        require(trimmedBrand == null || trimmedBrand.length <= 100) { "Brand must be 100 characters or fewer" }
        val unit = baseServingUnit.trim().lowercase().ifBlank { "g" }
        require(unit in listOf("g", "ml", "servings")) { "Unsupported serving unit" }
        require(baseServingSize.isFinite() && baseServingSize > 0 && baseServingSize <= 100_000.0) {
            "Enter a serving size greater than 0"
        }
        require(
            calories.isFinite() && proteinG.isFinite() && carbsG.isFinite() && fatG.isFinite() &&
                fiberG.isFinite() && sugarG.isFinite() && sodiumMg.isFinite()
        ) { "Enter 0 or more" }
        require(
            calories >= 0 && proteinG >= 0 && carbsG >= 0 && fatG >= 0 &&
                fiberG >= 0 && sugarG >= 0 && sodiumMg >= 0
        ) { "Enter 0 or more" }
        require(
            calories <= 100_000.0 && proteinG <= 10_000.0 && carbsG <= 10_000.0 && fatG <= 10_000.0 &&
                fiberG <= 10_000.0 && sugarG <= 10_000.0 && sodiumMg <= 100_000.0
        ) { "Enter 0 or more" }
        return repository.createCustomFood(
            trimmedName,
            trimmedBrand,
            baseServingSize,
            unit,
            calories,
            proteinG,
            carbsG,
            fatG,
            fiberG,
            sugarG,
            sodiumMg
        )
    }
}
