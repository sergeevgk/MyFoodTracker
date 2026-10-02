package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.repository.MealRepository

class RestoreMealEntryUseCase(private val repository: MealRepository) {
    operator fun invoke(entry: MealEntry): List<MealEntry> = repository.restoreMealEntry(entry)
}
