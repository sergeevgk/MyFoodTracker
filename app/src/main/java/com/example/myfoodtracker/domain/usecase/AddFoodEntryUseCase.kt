package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.FoodEntry
import com.example.myfoodtracker.domain.repository.FoodRepository

class AddFoodEntryUseCase(private val repository: FoodRepository) {
    operator fun invoke(): List<FoodEntry> = repository.addFoodEntry()
}
