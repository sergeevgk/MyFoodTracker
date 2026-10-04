package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.repository.MealRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class LogQuickAddUseCaseTest {

    private lateinit var fakeMealRepository: FakeMealRepository
    private lateinit var useCase: LogQuickAddUseCase

    @Before
    fun setUp() {
        fakeMealRepository = FakeMealRepository()
        useCase = LogQuickAddUseCase(fakeMealRepository)
    }

    @Test
    fun invoke_delegatesNameMacrosSlotAndDateToRepository() {
        val entries = useCase("Office lunch", 500.0, 20.0, 45.0, 15.0, "LUNCH", "2026-09-30")

        assertEquals(1, entries.size)
        assertEquals("LUNCH", entries[0].title)
        assertEquals("2026-09-30", entries[0].date)
        assertEquals(1, entries[0].foods.size)
        assertEquals("Office lunch", entries[0].foods[0].name)
        assertEquals(500.0, entries[0].foods[0].calories, 0.001)
        assertEquals(20.0, entries[0].foods[0].protein, 0.001)
        assertEquals(45.0, entries[0].foods[0].carbs, 0.001)
        assertEquals(15.0, entries[0].foods[0].fat, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_withBlankName_throws() {
        useCase("   ", 500.0, 20.0, 45.0, 15.0, "LUNCH", "2026-09-30")
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_withNegativeCalories_throws() {
        useCase("Snack", -10.0, 5.0, 5.0, 5.0, "SNACK", "2026-09-30")
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_withNegativeProtein_throws() {
        useCase("Snack", 100.0, -1.0, 5.0, 5.0, "SNACK", "2026-09-30")
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_withNegativeCarbs_throws() {
        useCase("Snack", 100.0, 5.0, -1.0, 5.0, "SNACK", "2026-09-30")
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_withNegativeFat_throws() {
        useCase("Snack", 100.0, 5.0, 5.0, -1.0, "SNACK", "2026-09-30")
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_withUnknownSlot_throws() {
        useCase("Snack", 100.0, 5.0, 5.0, 5.0, "BRUNCH", "2026-09-30")
    }

    @Test
    fun invoke_withLowercaseSlot_acceptsAndNormalizes() {
        val entries = useCase("Toast", 130.0, 4.0, 25.0, 2.0, "breakfast", "2026-09-30")

        assertEquals(1, entries.size)
        assertEquals("BREAKFAST", entries[0].title)
    }

    @Test
    fun invoke_withSlotContainingWhitespace_acceptsAndNormalizes() {
        val entries = useCase("Toast", 130.0, 4.0, 25.0, 2.0, "  breakfast  ", "2026-09-30")

        assertEquals(1, entries.size)
        assertEquals("BREAKFAST", entries[0].title)
    }

    @Test
    fun invoke_differentDates_isolated() {
        useCase("Lunch", 500.0, 20.0, 45.0, 15.0, "LUNCH", "2026-09-30")
        useCase("Dinner", 700.0, 30.0, 60.0, 20.0, "DINNER", "2026-09-29")

        assertEquals(1, fakeMealRepository.entriesByDate["2026-09-30"]?.size)
        assertEquals(1, fakeMealRepository.entriesByDate["2026-09-29"]?.size)
        assertEquals("LUNCH", fakeMealRepository.entriesByDate["2026-09-30"]?.first()?.title)
        assertEquals("DINNER", fakeMealRepository.entriesByDate["2026-09-29"]?.first()?.title)
    }

    @Test
    fun invoke_withZeroMacros_accepted() {
        val entries = useCase("Water", 0.0, 0.0, 0.0, 0.0, "SNACK", "2026-09-30")

        assertEquals(1, entries.size)
        assertEquals(0.0, entries[0].foods[0].calories, 0.001)
    }

    private class FakeMealRepository : MealRepository {
        val entriesByDate = mutableMapOf<String, MutableList<MealEntry>>()

        override fun getMealEntries(): List<MealEntry> = entriesByDate.values.flatten()

        override fun getMealEntriesByDate(date: String): List<MealEntry> {
            return entriesByDate[date]?.toList() ?: emptyList()
        }

        override fun addMealEntry(): List<MealEntry> = emptyList()
        override fun updateMealEntry(id: String, newTitle: String): List<MealEntry> = emptyList()
        override fun deleteMealEntry(id: String): List<MealEntry> = emptyList()
        override fun restoreMealEntry(entry: MealEntry): List<MealEntry> = emptyList()

        override fun logQuickAdd(
            name: String,
            calories: Double,
            proteinG: Double,
            carbsG: Double,
            fatG: Double,
            mealSlot: String,
            date: String
        ): List<MealEntry> {
            val entry = MealEntry(
                id = UUID.randomUUID().toString(),
                title = mealSlot,
                date = date,
                time = "12:00",
                foods = listOf(
                    com.example.myfoodtracker.domain.model.Food(
                        name = name,
                        weight = 0.0,
                        calories = calories,
                        carbs = carbsG,
                        fat = fatG,
                        protein = proteinG,
                        fiber = 0.0
                    )
                )
            )
            entriesByDate.getOrPut(date) { mutableListOf() }.add(entry)
            return getMealEntriesByDate(date)
        }

        override fun logFoodEntry(
            foodName: String,
            quantityGrams: Double,
            calories: Double,
            proteinG: Double,
            carbsG: Double,
            fatG: Double,
            fiberG: Double,
            mealSlot: String,
            date: String
        ): List<MealEntry> = emptyList()
    }
}
