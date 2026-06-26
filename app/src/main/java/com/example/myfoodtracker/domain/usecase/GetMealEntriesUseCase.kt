package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.repository.MealRepository

class GetMealEntriesUseCase(private val repository: MealRepository) {
    operator fun invoke(): List<MealEntry> = repository.getMealEntries()
}
