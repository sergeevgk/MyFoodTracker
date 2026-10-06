package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.data.dao.FtsQueryBuilder
import com.example.myfoodtracker.domain.model.FoodItem
import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.repository.FoodCatalogRepository
import com.example.myfoodtracker.domain.repository.MealRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SearchFoodUseCaseTest {

    private lateinit var fakeRepository: FakeFoodCatalogRepository
    private lateinit var fakeMealRepository: FakeMealRepository
    private lateinit var useCase: SearchFoodUseCase

    @Before
    fun setUp() {
        fakeRepository = FakeFoodCatalogRepository()
        fakeMealRepository = FakeMealRepository()
        useCase = SearchFoodUseCase(fakeRepository, fakeMealRepository)
    }

    @Test
    fun invoke_validQuery_delegatesQueryAndDefaultLimit() {
        useCase("chick bre")

        assertEquals("chick bre", fakeRepository.lastQuery)
        assertEquals(30, fakeRepository.lastLimit)
    }

    @Test
    fun invoke_explicitLimit_delegatesLimit() {
        useCase("apple", 10)

        assertEquals("apple", fakeRepository.lastQuery)
        assertEquals(10, fakeRepository.lastLimit)
    }

    @Test
    fun invoke_returnsRepositoryResults() {
        fakeRepository.results = listOf(
            foodItem(1L, "Chicken Breast"),
            foodItem(2L, "Chicken Broth")
        )

        val results = useCase("chick")

        assertEquals(2, results.size)
        assertEquals("Chicken Breast", results[0].name)
        assertEquals("Chicken Broth", results[1].name)
    }

    @Test
    fun invoke_blankQuery_returnsEmptyWithoutCallingRepository() {
        assertTrue(useCase("").isEmpty())
        assertTrue(useCase("   ").isEmpty())
        assertNull(fakeRepository.lastQuery)
    }

    @Test
    fun invoke_punctuationOnlyQuery_returnsEmptyWithoutCallingRepository() {
        assertTrue(useCase("...").isEmpty())
        assertNull(fakeRepository.lastQuery)
    }

    private fun foodItem(id: Long, name: String) = FoodItem(
        id = id,
        name = name,
        brand = null,
        barcode = null,
        isCustom = false,
        calories = 100.0,
        proteinG = 10.0,
        carbsG = 5.0,
        fatG = 2.0,
        fiberG = 1.0,
        sugarG = 1.0,
        sodiumMg = 50.0
    )

    private fun customFoodItem(id: Long, name: String) = foodItem(id, name).copy(isCustom = true)

    private fun loggedEntry(id: String, vararg names: String) = MealEntry(
        id = id,
        title = "BREAKFAST",
        date = "2026-10-05",
        time = "08:00",
        foods = names.map { mealName ->
            com.example.myfoodtracker.domain.model.Food(
                name = mealName,
                weight = 100.0,
                calories = 100.0,
                carbs = 5.0,
                fat = 2.0,
                protein = 10.0,
                fiber = 1.0
            )
        }
    )

    @Test
    fun invoke_customLoggedFiveTimes_outranksSeeded() {
        fakeRepository.results = listOf(
            foodItem(1L, "Whey Protein"),
            customFoodItem(2L, "Whey Protein Custom")
        )
        fakeMealRepository.storedEntries = listOf(
            loggedEntry("m1", "Whey Protein Custom"),
            loggedEntry("m2", "Whey Protein Custom"),
            loggedEntry("m3", "Whey Protein Custom"),
            loggedEntry("m4", "Whey Protein Custom"),
            loggedEntry("m5", "Whey Protein Custom")
        )

        val results = useCase("whey protein")

        assertEquals(2, results.size)
        assertEquals(2L, results[0].id)
        assertTrue(results[0].isCustom)
        assertEquals(1L, results[1].id)
    }

    @Test
    fun invoke_noHistory_preservesDaoOrder() {
        fakeRepository.results = listOf(
            foodItem(1L, "Chicken Breast"),
            foodItem(2L, "Chicken Broth")
        )

        val results = useCase("chick")

        assertEquals(2, results.size)
        assertEquals(1L, results[0].id)
        assertEquals(2L, results[1].id)
        assertFalse(fakeMealRepository.historyRead)
    }

    @Test
    fun invoke_moreFrequentCustom_outranksLessFrequentCustom() {
        fakeRepository.results = listOf(
            customFoodItem(1L, "Rare Mix"),
            customFoodItem(2L, "Daily Shake")
        )
        fakeMealRepository.storedEntries = listOf(
            loggedEntry("m1", "Rare Mix"),
            loggedEntry("m2", "Daily Shake"),
            loggedEntry("m3", "Daily Shake"),
            loggedEntry("m4", "Daily Shake")
        )

        val results = useCase("mix")

        assertEquals(2L, results[0].id)
        assertEquals(1L, results[1].id)
    }

    @Test
    fun invoke_seededLoggedOften_stillBelowCustom() {
        fakeRepository.results = listOf(
            foodItem(1L, "Oats"),
            customFoodItem(2L, "Oats")
        )
        fakeMealRepository.storedEntries = listOf(
            loggedEntry("m1", "Oats"),
            loggedEntry("m2", "Oats"),
            loggedEntry("m3", "Oats")
        )

        val results = useCase("oats")

        assertEquals(2L, results[0].id)
        assertTrue(results[0].isCustom)
    }

    @Test
    fun invoke_emptyMealHistory_customRanksAboveSeeded() {
        fakeRepository.results = listOf(
            foodItem(1L, "Apple Pie"),
            customFoodItem(2L, "Apple Pie Custom")
        )
        fakeMealRepository.storedEntries = emptyList()

        val results = useCase("apple")

        assertEquals(2, results.size)
        assertEquals(2L, results[0].id)
        assertTrue(results[0].isCustom)
        assertEquals(1L, results[1].id)
        assertFalse(results[1].isCustom)
    }

    @Test
    fun invoke_mealHistoryBlankNames_filteredOutWithoutCrashing() {
        fakeRepository.results = listOf(
            foodItem(1L, "Oats"),
            customFoodItem(2L, "Oats")
        )
        fakeMealRepository.storedEntries = listOf(
            loggedEntry("m1", "   "),
            loggedEntry("m2", "")
        )

        val results = useCase("oats")

        assertEquals(2L, results[0].id)
        assertTrue(results[0].isCustom)
    }

    @Test
    fun invoke_blankQuery_returnsEmptyWithoutReadingHistory() {
        assertTrue(useCase("   ").isEmpty())
        assertNull(fakeRepository.lastQuery)
        assertFalse(fakeMealRepository.historyRead)
    }

    private class FakeMealRepository : MealRepository {
        var storedEntries: List<MealEntry> = emptyList()
        var historyRead = false

        override fun getMealEntries(): List<MealEntry> {
            historyRead = true
            return storedEntries
        }
        override fun getMealEntriesByDate(date: String): List<MealEntry> =
            storedEntries.filter { it.date == date }
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
        ): List<MealEntry> = emptyList()

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

    private class FakeFoodCatalogRepository : FoodCatalogRepository {
        var lastQuery: String? = null
        var lastLimit: Int? = null
        var results: List<FoodItem> = emptyList()

        override fun search(query: String, limit: Int): List<FoodItem> {
            // Mirrors the real FoodCatalogRepositoryImpl contract by applying
            // the real FtsQueryBuilder rule: input with no searchable terms
            // (blank/punctuation-only) yields emptyList() without hitting the DAO.
            if (FtsQueryBuilder.build(query) == null) return emptyList()
            lastQuery = query
            lastLimit = limit
            return results
        }

        override fun createCustomFood(
            name: String,
            brand: String?,
            baseServingSize: Double,
            baseServingUnit: String,
            calories: Double,
            proteinG: Double,
            carbsG: Double,
            fatG: Double,
            fiberG: Double,
            sugarG: Double,
            sodiumMg: Double
        ): FoodItem = FoodItem(
            id = 99L,
            name = name,
            brand = brand,
            barcode = null,
            isCustom = true,
            calories = calories,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            fiberG = fiberG,
            sugarG = sugarG,
            sodiumMg = sodiumMg,
            baseServingSize = baseServingSize,
            baseServingUnit = baseServingUnit
        )
    }
}
