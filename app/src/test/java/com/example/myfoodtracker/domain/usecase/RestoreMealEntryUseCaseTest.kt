package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.Food
import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.repository.MealRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RestoreMealEntryUseCaseTest {

    private lateinit var fakeMealRepository: FakeMealRepository
    private lateinit var useCase: RestoreMealEntryUseCase

    @Before
    fun setUp() {
        fakeMealRepository = FakeMealRepository()
        useCase = RestoreMealEntryUseCase(fakeMealRepository)
    }

    @Test
    fun invoke_delegatesToRepositoryAndRestoresExactEntry() {
        val entry = MealEntry(
            id = "meal-restore-1",
            title = "LUNCH",
            date = "2026-09-30",
            time = "13:00",
            foods = listOf(
                Food(
                    name = "Office lunch",
                    weight = 0.0,
                    calories = 500.0,
                    carbs = 45.0,
                    fat = 15.0,
                    protein = 20.0,
                    fiber = 0.0
                )
            )
        )

        val result = useCase(entry)

        assertEquals(entry, fakeMealRepository.lastRestored)
        assertTrue(result.any { it.id == "meal-restore-1" })
        val restored = result.first { it.id == "meal-restore-1" }
        assertEquals("LUNCH", restored.title)
        assertEquals("2026-09-30", restored.date)
        assertEquals("13:00", restored.time)
        assertEquals(1, restored.foods.size)
        assertEquals("Office lunch", restored.foods[0].name)
        assertEquals(500.0, restored.foods[0].calories, 0.001)
    }

    @Test
    fun invoke_restoreOverSameId_replacesWithoutDuplicate() {
        val entry = MealEntry(
            id = "meal-dup",
            title = "SNACK",
            date = "2026-09-30",
            time = "16:00",
            foods = emptyList()
        )
        fakeMealRepository.stored["meal-dup"] = entry.copy(title = "STALE")

        useCase(entry)
        useCase(entry)

        val result = fakeMealRepository.getMealEntriesByDate("2026-09-30")
        assertEquals(1, result.count { it.id == "meal-dup" })
        assertEquals("SNACK", result.first { it.id == "meal-dup" }.title)
    }

    private class FakeMealRepository : MealRepository {
        val stored = mutableMapOf<String, MealEntry>()
        var lastRestored: MealEntry? = null

        override fun getMealEntries(): List<MealEntry> = stored.values.toList()

        override fun getMealEntriesByDate(date: String): List<MealEntry> =
            stored.values.filter { it.date == date }

        override fun addMealEntry(): List<MealEntry> = emptyList()
        override fun updateMealEntry(id: String, newTitle: String): List<MealEntry> = emptyList()
        override fun deleteMealEntry(id: String): List<MealEntry> = emptyList()

        override fun logQuickAdd(
            name: String,
            calories: Double,
            proteinG: Double,
            carbsG: Double,
            fatG: Double,
            mealSlot: String,
            date: String
        ): List<MealEntry> = emptyList()

        override fun restoreMealEntry(entry: MealEntry): List<MealEntry> {
            lastRestored = entry
            stored[entry.id] = entry
            return getMealEntriesByDate(entry.date)
        }
    }
}
