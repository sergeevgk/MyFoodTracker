package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.Food
import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.model.NewRecipeIngredient
import com.example.myfoodtracker.domain.model.Recipe
import com.example.myfoodtracker.domain.model.RecipeIngredient
import com.example.myfoodtracker.domain.repository.MealRepository
import com.example.myfoodtracker.domain.repository.RecipeRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class LogRecipeUseCaseTest {

    private lateinit var fakeRecipeRepository: FakeRecipeRepository
    private lateinit var fakeMealRepository: RecordingMealRepository
    private lateinit var useCase: LogRecipeUseCase

    private val chickenIngredient = RecipeIngredient(
        id = 1L,
        catalogFoodId = 7L,
        foodName = "Chicken Breast",
        quantityGrams = 150.0,
        unit = "g",
        calories = 180.0,
        proteinG = 34.5,
        carbsG = 0.75,
        fatG = 3.0,
        fiberG = 1.5
    )

    private val riceIngredient = RecipeIngredient(
        id = 2L,
        catalogFoodId = null,
        foodName = "Rice",
        quantityGrams = 200.0,
        unit = "g",
        calories = 260.0,
        proteinG = 5.2,
        carbsG = 57.0,
        fatG = 0.4,
        fiberG = 0.6
    )

    @Before
    fun setUp() {
        fakeRecipeRepository = FakeRecipeRepository()
        fakeMealRepository = RecordingMealRepository()
        useCase = LogRecipeUseCase(fakeRecipeRepository, fakeMealRepository)
        fakeRecipeRepository.stored.add(
            Recipe(id = "r1", name = "Chicken Bowl", ingredients = listOf(chickenIngredient, riceIngredient))
        )
    }

    @Test
    fun invoke_writesSnapshotVerbatim_notRescaled() {
        val count = useCase("r1", "LUNCH", "2026-10-04")

        assertEquals(2, count)
        val call = fakeMealRepository.calls.first()
        assertEquals("Chicken Breast", call.foodName)
        assertEquals(150.0, call.quantityGrams, 0.0001)
        // 180 kcal stays 180 - it is NOT multiplied by 150/100 again.
        assertEquals(180.0, call.calories, 0.0001)
        assertEquals(34.5, call.proteinG, 0.0001)
        assertEquals(0.75, call.carbsG, 0.0001)
        assertEquals(3.0, call.fatG, 0.0001)
        assertEquals(1.5, call.fiberG, 0.0001)
    }

    @Test
    fun invoke_twoIngredients_oneCallEachSameSlotAndDate() {
        useCase("r1", "DINNER", "2026-10-04")

        assertEquals(2, fakeMealRepository.calls.size)
        assertEquals(listOf("Chicken Breast", "Rice"), fakeMealRepository.calls.map { it.foodName })
        assertTrue(fakeMealRepository.calls.all { it.mealSlot == "DINNER" })
        assertTrue(fakeMealRepository.calls.all { it.date == "2026-10-04" })
    }

    @Test
    fun invoke_historicalDate_isForwardedVerbatim() {
        useCase("r1", "BREAKFAST", "2026-10-04")

        assertTrue(fakeMealRepository.calls.all { it.date == "2026-10-04" })
        assertEquals(2, fakeMealRepository.entriesByDate["2026-10-04"]?.size)
    }

    @Test
    fun invoke_paddedLowercaseSlot_isNormalized() {
        useCase("r1", " lunch ", "2026-10-04")

        assertTrue(fakeMealRepository.calls.all { it.mealSlot == "LUNCH" })
    }

    @Test
    fun invoke_twice_appendsAnotherFullSet() {
        useCase("r1", "LUNCH", "2026-10-04")
        useCase("r1", "LUNCH", "2026-10-04")

        assertEquals(4, fakeMealRepository.calls.size)
        assertEquals(4, fakeMealRepository.entriesByDate["2026-10-04"]?.size)
    }

    @Test
    fun invoke_unknownRecipeId_throwsAndDoesNotWrite() {
        assertThrowsIllegalArgument { useCase("missing", "LUNCH", "2026-10-04") }

        assertTrue(fakeMealRepository.calls.isEmpty())
    }

    @Test
    fun invoke_noActiveProfile_returnsNullAndDoesNotWrite() {
        fakeRecipeRepository.returnNull = true

        assertThrowsIllegalArgument { useCase("r1", "LUNCH", "2026-10-04") }

        assertTrue(fakeMealRepository.calls.isEmpty())
    }

    @Test
    fun invoke_emptyIngredients_throwsAndDoesNotWrite() {
        fakeRecipeRepository.stored.clear()
        fakeRecipeRepository.stored.add(Recipe(id = "r1", name = "Empty"))

        assertThrowsIllegalArgument { useCase("r1", "LUNCH", "2026-10-04") }

        assertTrue(fakeMealRepository.calls.isEmpty())
    }

    @Test
    fun invoke_invalidSlot_throwsAndDoesNotWrite() {
        assertThrowsIllegalArgument { useCase("r1", "BRUNCH", "2026-10-04") }
        assertThrowsIllegalArgument { useCase("r1", "", "2026-10-04") }

        assertTrue(fakeMealRepository.calls.isEmpty())
    }

    @Test
    fun invoke_invalidDate_throwsAndDoesNotWrite() {
        assertThrowsIllegalArgument { useCase("r1", "LUNCH", "") }
        assertThrowsIllegalArgument { useCase("r1", "LUNCH", "today") }
        assertThrowsIllegalArgument { useCase("r1", "LUNCH", "2026/10/04") }
        assertThrowsIllegalArgument { useCase("r1", "LUNCH", "2026-10-4") }

        assertTrue(fakeMealRepository.calls.isEmpty())
    }

    @Test
    fun invoke_secondIngredientZeroGrams_throwsBeforeAnyWrite() {
        fakeRecipeRepository.stored.clear()
        fakeRecipeRepository.stored.add(
            Recipe(
                id = "r1",
                name = "Bowl",
                ingredients = listOf(
                    chickenIngredient,
                    riceIngredient.copy(quantityGrams = 0.0)
                )
            )
        )

        assertThrowsIllegalArgument { useCase("r1", "LUNCH", "2026-10-04") }

        // Ingredient 1 was NOT inserted - validate-then-write guard.
        assertTrue(fakeMealRepository.calls.isEmpty())
    }

    @Test
    fun invoke_secondIngredientNegativeCalories_throwsBeforeAnyWrite() {
        fakeRecipeRepository.stored.clear()
        fakeRecipeRepository.stored.add(
            Recipe(
                id = "r1",
                name = "Bowl",
                ingredients = listOf(chickenIngredient, riceIngredient.copy(calories = -1.0))
            )
        )

        assertThrowsIllegalArgument { useCase("r1", "LUNCH", "2026-10-04") }

        assertTrue(fakeMealRepository.calls.isEmpty())
    }

    @Test
    fun invoke_secondIngredientBlankName_throwsBeforeAnyWrite() {
        fakeRecipeRepository.stored.clear()
        fakeRecipeRepository.stored.add(
            Recipe(
                id = "r1",
                name = "Bowl",
                ingredients = listOf(chickenIngredient, riceIngredient.copy(foodName = "   "))
            )
        )

        assertThrowsIllegalArgument { useCase("r1", "LUNCH", "2026-10-04") }

        assertTrue(fakeMealRepository.calls.isEmpty())
    }

    @Test
    fun invoke_catalogFoodIdZero_recentFood_isIgnoredAndNotFatal() {
        fakeRecipeRepository.stored.clear()
        fakeRecipeRepository.stored.add(
            Recipe(
                id = "r1",
                name = "Bowl",
                ingredients = listOf(chickenIngredient.copy(catalogFoodId = 0L))
            )
        )

        val count = useCase("r1", "LUNCH", "2026-10-04")

        assertEquals(1, count)
        assertEquals("Chicken Breast", fakeMealRepository.calls.single().foodName)
    }

    @Test
    fun invoke_dailyTotalsEqualSnapshotSum() {
        useCase("r1", "LUNCH", "2026-10-04")

        val stored = fakeMealRepository.entriesByDate["2026-10-04"]!!.flatMap { it.foods }
        assertEquals(180.0 + 260.0, stored.sumOf { it.calories }, 0.0001)
        assertEquals(34.5 + 5.2, stored.sumOf { it.protein }, 0.0001)
        assertEquals(1.5 + 0.6, stored.sumOf { it.fiber }, 0.0001)
    }

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "expected a mapped message, got: ${e.message}",
                e.message in setOf(
                    "Missing log date",
                    "Invalid meal slot",
                    "Recipe not found",
                    "Add at least one ingredient",
                    "Enter a name",
                    "Enter a quantity greater than 0",
                    "Enter 0 or more"
                )
            )
            return
        }
        throw AssertionError("expected IllegalArgumentException")
    }

    private data class LogCall(
        val foodName: String,
        val quantityGrams: Double,
        val calories: Double,
        val proteinG: Double,
        val carbsG: Double,
        val fatG: Double,
        val fiberG: Double,
        val mealSlot: String,
        val date: String
    )

    private class FakeRecipeRepository : RecipeRepository {
        var returnNull = false
        val stored = mutableListOf<Recipe>()

        override fun saveRecipe(name: String, ingredients: List<NewRecipeIngredient>): List<Recipe> =
            stored.toList()

        override fun getRecipes(): List<Recipe> = stored.toList()

        override fun getRecipeById(recipeId: String): Recipe? =
            if (returnNull) null else stored.firstOrNull { it.id == recipeId }

        override fun deleteRecipe(recipeId: String): List<Recipe> {
            stored.removeAll { it.id == recipeId }
            return stored.toList()
        }

        override fun deleteIngredient(ingredientId: Long): Recipe? = null
    }

    private class RecordingMealRepository : MealRepository {
        val calls = mutableListOf<LogCall>()
        val entriesByDate = mutableMapOf<String, MutableList<MealEntry>>()

        override fun getMealEntries(): List<MealEntry> = entriesByDate.values.flatten()

        override fun getMealEntriesByDate(date: String): List<MealEntry> =
            entriesByDate[date]?.toList() ?: emptyList()

        override fun addMealEntry(): List<MealEntry> =
            throw UnsupportedOperationException("addMealEntry must not be used for recipe logging")

        override fun updateMealEntry(id: String, newTitle: String): List<MealEntry> =
            throw UnsupportedOperationException("updateMealEntry must not be used for recipe logging")

        override fun deleteMealEntry(id: String): List<MealEntry> =
            throw UnsupportedOperationException("deleteMealEntry must not be used for recipe logging")

        override fun restoreMealEntry(entry: MealEntry): List<MealEntry> =
            throw UnsupportedOperationException("restoreMealEntry must not be used for recipe logging")

        override fun logQuickAdd(
            name: String,
            calories: Double,
            proteinG: Double,
            carbsG: Double,
            fatG: Double,
            mealSlot: String,
            date: String
        ): List<MealEntry> =
            throw UnsupportedOperationException("logQuickAdd must not be used for recipe logging")

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
            calls.add(
                LogCall(foodName, quantityGrams, calories, proteinG, carbsG, fatG, fiberG, mealSlot, date)
            )
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