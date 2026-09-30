package com.example.myfoodtracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DailySummaryTest {

    @Test
    fun summarize_emptyList_returnsZeros() {
        val result = DailySummary.summarize(emptyList())

        assertEquals(0.0, result.totalCalories, 0.001)
        assertEquals(0.0, result.totalProteinG, 0.001)
        assertEquals(0.0, result.totalCarbsG, 0.001)
        assertEquals(0.0, result.totalFatG, 0.001)
    }

    @Test
    fun summarize_singleMealWithFoods_sumsMacros() {
        val entries = listOf(
            MealEntry(
                id = "meal-1",
                title = "Breakfast",
                date = "2026-09-30",
                time = "08:00",
                foods = listOf(
                    Food(name = "Chicken Breast", weight = 150.0, calories = 250.0, carbs = 0.0, fat = 5.0, protein = 46.0, fiber = 0.0),
                    Food(name = "Rice", weight = 200.0, calories = 260.0, carbs = 56.0, fat = 1.0, protein = 5.0, fiber = 1.0)
                )
            )
        )

        val result = DailySummary.summarize(entries)

        assertEquals(510.0, result.totalCalories, 0.001)
        assertEquals(51.0, result.totalProteinG, 0.001)
        assertEquals(56.0, result.totalCarbsG, 0.001)
        assertEquals(6.0, result.totalFatG, 0.001)
    }

    @Test
    fun summarize_multipleMeals_sumsAcrossAllMeals() {
        val entries = listOf(
            MealEntry(
                id = "meal-1",
                title = "Breakfast",
                date = "2026-09-30",
                time = "08:00",
                foods = listOf(
                    Food(name = "Oats", weight = 100.0, calories = 389.0, carbs = 66.0, fat = 7.0, protein = 17.0, fiber = 10.0)
                )
            ),
            MealEntry(
                id = "meal-2",
                title = "Lunch",
                date = "2026-09-30",
                time = "13:00",
                foods = listOf(
                    Food(name = "Chicken", weight = 100.0, calories = 165.0, carbs = 0.0, fat = 3.6, protein = 31.0, fiber = 0.0),
                    Food(name = "Broccoli", weight = 100.0, calories = 34.0, carbs = 6.6, fat = 0.4, protein = 2.8, fiber = 2.6)
                )
            )
        )

        val result = DailySummary.summarize(entries)

        assertEquals(588.0, result.totalCalories, 0.001)
        assertEquals(50.8, result.totalProteinG, 0.001)
        assertEquals(72.6, result.totalCarbsG, 0.001)
        assertEquals(11.0, result.totalFatG, 0.001)
    }

    @Test
    fun summarize_mealWithNoFoods_treatedAsZero() {
        val entries = listOf(
            MealEntry(id = "meal-1", title = "Snack", date = "2026-09-30", time = "16:00", foods = emptyList())
        )

        val result = DailySummary.summarize(entries)

        assertEquals(0.0, result.totalCalories, 0.001)
        assertEquals(0.0, result.totalProteinG, 0.001)
        assertEquals(0.0, result.totalCarbsG, 0.001)
        assertEquals(0.0, result.totalFatG, 0.001)
    }
}
