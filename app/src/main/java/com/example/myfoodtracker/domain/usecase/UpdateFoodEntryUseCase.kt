package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.FoodEntry
import com.example.myfoodtracker.domain.repository.FoodRepository

class UpdateFoodEntryUseCase(private val repository: FoodRepository) {
    operator fun invoke(id: String, newText: String): List<FoodEntry> =
        repository.updateFoodEntry(id, newText)
}
