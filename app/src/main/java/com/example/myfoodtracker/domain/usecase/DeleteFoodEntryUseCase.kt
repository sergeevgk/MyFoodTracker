package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.FoodEntry
import com.example.myfoodtracker.domain.repository.FoodRepository

class DeleteFoodEntryUseCase(private val repository: FoodRepository) {
    operator fun invoke(id: String): List<FoodEntry> = repository.deleteFoodEntry(id)
}
