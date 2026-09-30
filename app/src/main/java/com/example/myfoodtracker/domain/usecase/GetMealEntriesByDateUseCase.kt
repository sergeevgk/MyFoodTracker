package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.repository.MealRepository

class GetMealEntriesByDateUseCase(private val repository: MealRepository) {
    operator fun invoke(date: String): List<MealEntry> = repository.getMealEntriesByDate(date)
}
