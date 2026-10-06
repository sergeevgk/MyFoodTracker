package com.example.myfoodtracker.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.myfoodtracker.domain.model.FoodItem
import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.repository.MealRepository
import com.example.myfoodtracker.domain.usecase.CreateCustomFoodUseCase
import com.example.myfoodtracker.domain.usecase.SearchFoodUseCase

class FoodSearchViewModel(
    private val searchFoodUseCase: SearchFoodUseCase,
    private val mealRepository: MealRepository,
    private val createCustomFoodUseCase: CreateCustomFoodUseCase
) : ViewModel() {

    private val _results = MutableLiveData<List<FoodItem>>(emptyList())
    val results: LiveData<List<FoodItem>> = _results

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    fun search(query: String, activeDate: String = "") {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _results.value = recentFoods(activeDate)
        } else {
            _results.value = searchFoodUseCase(trimmed)
        }
        _error.value = null
    }

    fun loadInitial(activeDate: String = "") {
        _results.value = recentFoods(activeDate)
    }

    fun createCustomFood(
        name: String,
        brand: String?,
        baseServingSize: Double,
        baseServingUnit: String,
        calories: Double,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
        fiberG: Double = 0.0,
        sugarG: Double = 0.0,
        sodiumMg: Double = 0.0
    ): FoodItem? {
        return try {
            val created = createCustomFoodUseCase(
                name, brand, baseServingSize, baseServingUnit,
                calories, proteinG, carbsG, fatG, fiberG, sugarG, sodiumMg
            )
            _error.value = null
            created
        } catch (e: IllegalArgumentException) {
            _error.value = e.message
            null
        }
    }

    fun recentFoods(date: String = "", limit: Int = 10): List<FoodItem> {
        val entries = mealRepository.getMealEntries().ifEmpty {
            if (date.isNotBlank()) mealRepository.getMealEntriesByDate(date) else emptyList()
        }
        return entries
            .sortedWith(compareByDescending<MealEntry> { it.date }.thenByDescending { it.time })
            .flatMap { entry -> entry.foods }
            .filter { it.weight > 0 }
            .distinctBy { it.name.trim().lowercase() }
            .take(limit.coerceIn(1, 10))
            .map { food ->
                val factor = if (food.weight > 0) 100.0 / food.weight else 1.0
                FoodItem(
                    id = 0L,
                    name = food.name,
                    brand = null,
                    barcode = null,
                    isCustom = false,
                    calories = food.calories * factor,
                    proteinG = food.protein * factor,
                    carbsG = food.carbs * factor,
                    fatG = food.fat * factor,
                    fiberG = food.fiber * factor,
                    sugarG = 0.0,
                    sodiumMg = 0.0
                )
            }
    }
}
