package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.FoodItem
import com.example.myfoodtracker.domain.repository.FoodCatalogRepository

class SearchFoodUseCase(private val repository: FoodCatalogRepository) {
    operator fun invoke(query: String, limit: Int = 30): List<FoodItem> =
        repository.search(query, limit)
}
