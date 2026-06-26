package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.repository.MealRepository

class AddMealEntryUseCase(private val repository: MealRepository) {
    operator fun invoke(): List<MealEntry> = repository.addMealEntry()
}
