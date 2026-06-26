package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.repository.MealRepository

class UpdateMealEntryUseCase(private val repository: MealRepository) {
    operator fun invoke(id: String, newTitle: String): List<MealEntry> =
        repository.updateMealEntry(id, newTitle)
}
