package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.FoodItem
import com.example.myfoodtracker.domain.repository.FoodCatalogRepository
import com.example.myfoodtracker.domain.repository.MealRepository

class SearchFoodUseCase(
    private val repository: FoodCatalogRepository,
    private val mealRepository: MealRepository
) {
    operator fun invoke(query: String, limit: Int = 30): List<FoodItem> {
        if (query.isBlank()) return emptyList()
        val results = repository.search(query, limit)
        if (results.isEmpty()) return results
        // FR-4 relevance boost: frequently logged custom items outrank pre-seeded
        // items. Seeded-only lists keep the DAO bm25()/name order untouched.
        // Stable sort preserves that order within equal (custom, frequency)
        // buckets. Meal names are free text with no catalog FK, so frequency is
        // counted by normalized name (accepted MVP behavior).
        if (results.none { it.isCustom }) return results
        val frequency = mealRepository.getMealEntries()
            .flatMap { it.foods }
            .filter { it.name.isNotBlank() }
            .groupingBy { it.name.trim().lowercase() }
            .eachCount()
        return results.sortedWith(
            compareByDescending<FoodItem> { it.isCustom }
                .thenByDescending { frequency[it.name.trim().lowercase()] ?: 0 }
        )
    }
}
