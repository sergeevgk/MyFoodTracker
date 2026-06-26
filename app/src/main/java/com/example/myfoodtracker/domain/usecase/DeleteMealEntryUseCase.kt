package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.repository.MealRepository

class DeleteMealEntryUseCase(private val repository: MealRepository) {
    operator fun invoke(id: String): List<MealEntry> = repository.deleteMealEntry(id)
}
