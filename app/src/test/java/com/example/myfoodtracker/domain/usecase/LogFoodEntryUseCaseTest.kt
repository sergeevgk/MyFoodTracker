package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.Food
import com.example.myfoodtracker.domain.model.FoodItem
import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.model.ServingUnit
import com.example.myfoodtracker.domain.repository.MealRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class LogFoodEntryUseCaseTest {

    private lateinit var fakeMealRepository: FakeMealRepository
    private lateinit var useCase: LogFoodEntryUseCase

    private val chicken = FoodItem(
        id = 7L,
        name = "Chicken Breast",
        brand = "Brand X",
        barcode = null,
        isCustom = false,
        calories = 120.0,
        proteinG = 23.0,
        carbsG = 0.5,
        fatG = 2.0,
        fiberG = 0.0,
        sugarG = 0.0,
        sodiumMg = 70.0,
        baseServingSize = 100.0,
        baseServingUnit = "g"
    )

    @Before
    fun setUp() {
        fakeMealRepository = FakeMealRepository()
        useCase = LogFoodEntryUseCase(fakeMealRepository)
    }

    @Test
    fun invoke_grams150_scalesBy1Point5() {
        val entries = useCase(chicken, 150.0, ServingUnit.G, "BREAKFAST", "2026-10-04")

        assertEquals(1, entries.size)
        assertEquals("BREAKFAST", entries[0].title)
        assertEquals("2026-10-04", entries[0].date)
        val food = entries[0].foods.single()
        assertEquals("Chicken Breast", food.name)
        assertEquals(150.0, food.weight, 0.001)
        assertEquals(180.0, food.calories, 0.001)
        assertEquals(34.5, food.protein, 0.001)
        assertEquals(0.75, food.carbs, 0.001)
        assertEquals(3.0, food.fat, 0.001)
        // Delegation captured the same scaled values.
        assertEquals(150.0, fakeMealRepository.lastQuantityGrams!!, 0.001)
        assertEquals(180.0, fakeMealRepository.lastCalories!!, 0.001)
        assertEquals("2026-10-04", fakeMealRepository.lastDate)
    }

    @Test
    fun invoke_ml_mapsOneToOneToGrams() {
        val entries = useCase(chicken, 200.0, ServingUnit.ML, "LUNCH", "2026-10-04")

        assertEquals(200.0, entries[0].foods.single().weight, 0.001)
        assertEquals(240.0, entries[0].foods.single().calories, 0.001)
        assertEquals(46.0, entries[0].foods.single().protein, 0.001)
    }

    @Test
    fun invoke_servings_multipliesByBaseServingSize() {
        val bigServing = chicken.copy(baseServingSize = 150.0)
        val entries = useCase(bigServing, 2.0, ServingUnit.SERVINGS, "DINNER", "2026-10-04")

        // 2 servings x 150 g = 300 g -> factor 3.0
        assertEquals(300.0, entries[0].foods.single().weight, 0.001)
        assertEquals(360.0, entries[0].foods.single().calories, 0.001)
        assertEquals(69.0, entries[0].foods.single().protein, 0.001)
    }

    @Test
    fun invoke_fractionalQuantity_scalesPrecisely() {
        val entries = useCase(chicken, 12.5, ServingUnit.G, "SNACK", "2026-10-04")

        assertEquals(15.0, entries[0].foods.single().calories, 0.001)
        assertEquals(2.875, entries[0].foods.single().protein, 0.0001)
    }

    @Test
    fun invoke_largeQuantity_noOverflow() {
        val entries = useCase(chicken, 5000.0, ServingUnit.G, "LUNCH", "2026-10-04")

        assertEquals(6000.0, entries[0].foods.single().calories, 0.001)
        assertTrue(entries[0].foods.single().calories.isFinite())
    }

    @Test
    fun invoke_fiber_passesRealValueNotZero() {
        val fibrous = chicken.copy(fiberG = 2.5)
        val entries = useCase(fibrous, 200.0, ServingUnit.G, "LUNCH", "2026-10-04")

        assertEquals(5.0, entries[0].foods.single().fiber, 0.001)
        assertEquals(5.0, fakeMealRepository.lastFiberG!!, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_zeroQuantity_throws() {
        useCase(chicken, 0.0, ServingUnit.G, "LUNCH", "2026-10-04")
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_negativeQuantity_throws() {
        useCase(chicken, -50.0, ServingUnit.G, "LUNCH", "2026-10-04")
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_nanQuantity_throws() {
        useCase(chicken, Double.NaN, ServingUnit.G, "LUNCH", "2026-10-04")
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_infiniteQuantity_throws() {
        useCase(chicken, Double.POSITIVE_INFINITY, ServingUnit.G, "LUNCH", "2026-10-04")
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_blankFoodName_throws() {
        useCase(chicken.copy(name = "   "), 100.0, ServingUnit.G, "LUNCH", "2026-10-04")
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_unknownSlot_throws() {
        useCase(chicken, 100.0, ServingUnit.G, "BRUNCH", "2026-10-04")
    }

    @Test
    fun invoke_lowercaseSlot_acceptsAndNormalizes() {
        val entries = useCase(chicken, 100.0, ServingUnit.G, "breakfast", "2026-10-04")

        assertEquals("BREAKFAST", entries[0].title)
        assertEquals("BREAKFAST", fakeMealRepository.lastMealSlot)
    }

    @Test
    fun invoke_datePassedThrough_notToday() {
        useCase(chicken, 100.0, ServingUnit.G, "DINNER", "2026-09-01")

        assertEquals("2026-09-01", fakeMealRepository.lastDate)
        assertEquals(1, fakeMealRepository.entriesByDate["2026-09-01"]?.size)
        assertTrue((fakeMealRepository.entriesByDate["2026-10-04"] ?: emptyList()).isEmpty())
    }

    private class FakeMealRepository : MealRepository {
        val entriesByDate = mutableMapOf<String, MutableList<MealEntry>>()
        var lastQuantityGrams: Double? = null
        var lastCalories: Double? = null
        var lastFiberG: Double? = null
        var lastMealSlot: String? = null
        var lastDate: String? = null

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
        ): List<MealEntry> {
            lastQuantityGrams = quantityGrams
            lastCalories = calories
            lastFiberG = fiberG
            lastMealSlot = mealSlot
            lastDate = date
            val entry = MealEntry(
                id = UUID.randomUUID().toString(),
                title = mealSlot,
                date = date,
                time = "12:00",
                foods = listOf(
                    Food(
                        name = foodName,
                        weight = quantityGrams,
                        calories = calories,
                        carbs = carbsG,
                        fat = fatG,
                        protein = proteinG,
                        fiber = fiberG
                    )
                )
            )
            entriesByDate.getOrPut(date) { mutableListOf() }.add(entry)
            return getMealEntriesByDate(date)
        }
    }
}
