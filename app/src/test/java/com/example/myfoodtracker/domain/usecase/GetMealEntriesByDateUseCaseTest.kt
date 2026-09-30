package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.repository.MealRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetMealEntriesByDateUseCaseTest {

    private lateinit var fakeMealRepository: FakeMealRepository
    private lateinit var useCase: GetMealEntriesByDateUseCase

    @Before
    fun setUp() {
        fakeMealRepository = FakeMealRepository()
        useCase = GetMealEntriesByDateUseCase(fakeMealRepository)
    }

    @Test
    fun invoke_delegatesToRepositoryWithDate() {
        val date = "2026-09-30"
        val expectedEntries = listOf(
            MealEntry(
                id = "meal-1",
                title = "Breakfast",
                date = date,
                time = "08:30",
                foods = emptyList()
            )
        )
        fakeMealRepository.entriesByDate[date] = expectedEntries

        val result = useCase(date)

        assertEquals(expectedEntries, result)
        assertEquals(date, fakeMealRepository.lastQueriedDate)
    }

    private class FakeMealRepository : MealRepository {
        val entriesByDate = mutableMapOf<String, List<MealEntry>>()
        var lastQueriedDate: String? = null

        override fun getMealEntries(): List<MealEntry> = emptyList()

        override fun getMealEntriesByDate(date: String): List<MealEntry> {
            lastQueriedDate = date
            return entriesByDate[date] ?: emptyList()
        }

        override fun addMealEntry(): List<MealEntry> = emptyList()
        override fun updateMealEntry(id: String, newTitle: String): List<MealEntry> = emptyList()
        override fun deleteMealEntry(id: String): List<MealEntry> = emptyList()
    }
}
