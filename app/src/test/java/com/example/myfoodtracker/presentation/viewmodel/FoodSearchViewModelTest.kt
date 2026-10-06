package com.example.myfoodtracker.presentation.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.myfoodtracker.domain.model.FoodItem
import com.example.myfoodtracker.domain.repository.FoodCatalogRepository
import com.example.myfoodtracker.domain.repository.MealRepository
import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.usecase.CreateCustomFoodUseCase
import com.example.myfoodtracker.domain.usecase.SearchFoodUseCase
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class FoodSearchViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var fakeCatalogRepository: FakeFoodCatalogRepository
    private lateinit var fakeMealRepository: FakeMealRepository
    private lateinit var viewModel: FoodSearchViewModel

    @Before
    fun setUp() {
        fakeCatalogRepository = FakeFoodCatalogRepository()
        fakeMealRepository = FakeMealRepository()
        viewModel = FoodSearchViewModel(
            SearchFoodUseCase(fakeCatalogRepository, fakeMealRepository),
            fakeMealRepository,
            CreateCustomFoodUseCase(fakeCatalogRepository)
        )
    }

    @Test
    fun search_validQuery_propagatesResults() {
        fakeCatalogRepository.results = listOf(
            foodItem(1L, "Chicken Breast"),
            foodItem(2L, "Chicken Broth")
        )

        viewModel.search("chick")

        val results = viewModel.results.value
        assertNotNull(results)
        assertEquals(2, results!!.size)
        assertEquals("Chicken Breast", results[0].name)
        assertEquals("chick", fakeCatalogRepository.lastQuery)
    }

    @Test
    fun search_blankQuery_emitsRecentFoods() {
        fakeMealRepository.storedEntries = listOf(
            MealEntry(
                id = "m1",
                title = "BREAKFAST",
                date = "2026-10-04",
                time = "08:00",
                foods = listOf(
                    com.example.myfoodtracker.domain.model.Food(
                        name = "Oatmeal",
                        weight = 50.0,
                        calories = 190.0,
                        carbs = 34.0,
                        fat = 3.0,
                        protein = 6.0,
                        fiber = 4.0
                    )
                )
            )
        )

        viewModel.search("   ")

        val results = viewModel.results.value
        assertNotNull(results)
        assertEquals(1, results!!.size)
        assertEquals("Oatmeal", results[0].name)
        // 50g -> 100g factor is 2.0x
        assertEquals(380.0, results[0].calories, 0.001)
        assertEquals(12.0, results[0].proteinG, 0.001)
    }

    @Test
    fun recentFoods_sortsHistoricalMealsNewestFirstAndDeduplicates() {
        fakeMealRepository.storedEntries = listOf(
            MealEntry(
                id = "m1",
                title = "BREAKFAST",
                date = "2026-10-01",
                time = "08:00",
                foods = listOf(
                    com.example.myfoodtracker.domain.model.Food(
                        name = "Banana",
                        weight = 100.0,
                        calories = 89.0,
                        carbs = 23.0,
                        fat = 0.3,
                        protein = 1.1,
                        fiber = 2.6
                    )
                )
            ),
            MealEntry(
                id = "m2",
                title = "LUNCH",
                date = "2026-10-03",
                time = "13:00",
                foods = listOf(
                    com.example.myfoodtracker.domain.model.Food(
                        name = "Chicken Breast",
                        weight = 150.0,
                        calories = 180.0,
                        carbs = 0.0,
                        fat = 3.0,
                        protein = 34.5,
                        fiber = 0.0
                    ),
                    com.example.myfoodtracker.domain.model.Food(
                        name = "Banana",
                        weight = 120.0,
                        calories = 106.8,
                        carbs = 27.6,
                        fat = 0.36,
                        protein = 1.32,
                        fiber = 3.12
                    )
                )
            ),
            MealEntry(
                id = "m3",
                title = "DINNER",
                date = "2026-10-03",
                time = "19:00",
                foods = listOf(
                    com.example.myfoodtracker.domain.model.Food(
                        name = "Zero Weight Food",
                        weight = 0.0,
                        calories = 50.0,
                        carbs = 0.0,
                        fat = 0.0,
                        protein = 0.0,
                        fiber = 0.0
                    )
                )
            )
        )

        val recent = viewModel.recentFoods(limit = 10)

        // Newest is 2026-10-03. Zero weight is excluded. Banana is deduplicated.
        assertEquals(2, recent.size)
        assertEquals("Chicken Breast", recent[0].name)
        assertEquals("Banana", recent[1].name)
    }

    @Test
    fun loadInitial_populatesResultsWithRecentFoods() {
        fakeMealRepository.storedEntries = listOf(
            MealEntry(
                id = "m1",
                title = "LUNCH",
                date = "2026-10-04",
                time = "12:00",
                foods = listOf(
                    com.example.myfoodtracker.domain.model.Food(
                        name = "Apple",
                        weight = 100.0,
                        calories = 52.0,
                        carbs = 14.0,
                        fat = 0.2,
                        protein = 0.3,
                        fiber = 2.4
                    )
                )
            )
        )

        viewModel.loadInitial()

        val results = viewModel.results.value
        assertNotNull(results)
        assertEquals(1, results!!.size)
        assertEquals("Apple", results[0].name)
    }

    @Test
    fun search_defaultLimit_isThirty() {
        viewModel.search("apple")

        assertEquals(30, fakeCatalogRepository.lastLimit)
    }

    @Test
    fun search_noResults_emitsEmptyWithoutError() {
        fakeCatalogRepository.results = emptyList()

        viewModel.search("zzz-no-such-food")

        assertTrue(viewModel.results.value!!.isEmpty())
        assertNull(viewModel.error.value)
    }

    @Test
    fun createCustomFood_validInput_returnsCreatedItemAndClearsError() {
        val created = viewModel.createCustomFood(
            name = "  MyProtein Whey  ",
            brand = "MyProtein",
            baseServingSize = 30.0,
            baseServingUnit = "g",
            calories = 120.0,
            proteinG = 24.0,
            carbsG = 3.0,
            fatG = 1.0
        )

        assertNotNull(created)
        assertEquals("MyProtein Whey", created!!.name)
        assertTrue(created.isCustom)
        assertNull(viewModel.error.value)
    }

    @Test
    fun createCustomFood_blankName_returnsNullAndSurfacesError() {
        val created = viewModel.createCustomFood(
            name = "   ",
            brand = null,
            baseServingSize = 100.0,
            baseServingUnit = "g",
            calories = 100.0,
            proteinG = 10.0,
            carbsG = 5.0,
            fatG = 2.0
        )

        assertNull(created)
        assertEquals("Enter a name", viewModel.error.value)
    }

    @Test
    fun createCustomFood_negativeMacro_returnsNullAndSurfacesError() {
        val created = viewModel.createCustomFood(
            name = "Oats",
            brand = null,
            baseServingSize = 100.0,
            baseServingUnit = "g",
            calories = -5.0,
            proteinG = 10.0,
            carbsG = 5.0,
            fatG = 2.0
        )

        assertNull(created)
        assertNotNull(viewModel.error.value)
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

    private class FakeFoodCatalogRepository : FoodCatalogRepository {
        var lastQuery: String? = null
        var lastLimit: Int? = null
        var results: List<FoodItem> = emptyList()

        override fun search(query: String, limit: Int): List<FoodItem> {
            lastQuery = query
            lastLimit = limit
            if (query.isBlank()) return emptyList()
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
        ): FoodItem {
            val trimmedName = name.trim()
            require(trimmedName.isNotBlank()) { "Enter a name" }
            require(baseServingSize.isFinite() && baseServingSize > 0) { "Enter a serving size greater than 0" }
            require(calories >= 0 && proteinG >= 0 && carbsG >= 0 && fatG >= 0) { "Enter 0 or more" }
            return FoodItem(
                id = 99L,
                name = trimmedName,
                brand = brand?.trim()?.ifBlank { null },
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
                baseServingUnit = baseServingUnit.trim().lowercase().ifBlank { "g" }
            )
        }
    }

    private class FakeMealRepository : MealRepository {
        var storedEntries: List<MealEntry> = emptyList()

        override fun getMealEntries(): List<MealEntry> = storedEntries
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
}
