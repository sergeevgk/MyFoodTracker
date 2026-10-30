package com.example.myfoodtracker.presentation.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.model.NewRecipeIngredient
import com.example.myfoodtracker.domain.model.Recipe
import com.example.myfoodtracker.domain.model.RecipeIngredient
import com.example.myfoodtracker.domain.repository.MealRepository
import com.example.myfoodtracker.domain.repository.RecipeRepository
import com.example.myfoodtracker.domain.usecase.DeleteRecipeUseCase
import com.example.myfoodtracker.domain.usecase.GetRecipesUseCase
import com.example.myfoodtracker.domain.usecase.LogRecipeUseCase
import com.example.myfoodtracker.domain.usecase.SaveRecipeUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RecipeViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var fakeRecipeRepository: FakeRecipeRepository
    private lateinit var fakeMealRepository: RecordingMealRepository
    private lateinit var viewModel: RecipeViewModel

    private val bowl = Recipe(
        id = "r1",
        name = "Chicken Bowl",
        ingredients = listOf(
            RecipeIngredient(
                foodName = "Chicken Breast",
                quantityGrams = 150.0,
                calories = 180.0,
                proteinG = 34.5,
                carbsG = 0.75,
                fatG = 3.0,
                fiberG = 1.5
            )
        )
    )

    @Before
    fun setUp() {
        fakeRecipeRepository = FakeRecipeRepository()
        fakeMealRepository = RecordingMealRepository()
        fakeRecipeRepository.stored.add(bowl)
        viewModel = RecipeViewModel(
            saveRecipeUseCase = SaveRecipeUseCase(fakeRecipeRepository),
            getRecipesUseCase = GetRecipesUseCase(fakeRecipeRepository),
            deleteRecipeUseCase = DeleteRecipeUseCase(fakeRecipeRepository),
            logRecipeUseCase = LogRecipeUseCase(fakeRecipeRepository, fakeMealRepository)
        )
    }

    @Test
    fun logRecipe_forwardsIdSlotAndHistoricalDate_andReturnsTrue() {
        val success = viewModel.logRecipe("r1", "DINNER", "2026-10-04")

        assertTrue(success)
        assertEquals("r1", fakeRecipeRepository.requestedId)
        assertEquals("DINNER", fakeMealRepository.calls.single().mealSlot)
        assertEquals("2026-10-04", fakeMealRepository.calls.single().date)
        assertNull(viewModel.logError.value)
    }

    @Test
    fun logRecipe_success_doesNotRefreshRecipeList() {
        viewModel.refresh()
        val before = viewModel.recipes.value
        val callsBefore = fakeRecipeRepository.getRecipesCallCount

        viewModel.logRecipe("r1", "DINNER", "2026-10-04")

        assertEquals(before, viewModel.recipes.value)
        assertEquals(callsBefore, fakeRecipeRepository.getRecipesCallCount)
    }

    @Test
    fun logRecipe_useCaseThrows_returnsFalseAndExposesMessage() {
        val success = viewModel.logRecipe("missing", "DINNER", "2026-10-04")

        assertEquals(false, success)
        assertEquals("Recipe not found", viewModel.logError.value)
        assertTrue(fakeMealRepository.calls.isEmpty())
    }

    @Test
    fun logRecipe_invalidDate_returnsFalseAndDoesNotWrite() {
        val success = viewModel.logRecipe("r1", "DINNER", "today")

        assertEquals(false, success)
        assertEquals("Missing log date", viewModel.logError.value)
        assertTrue(fakeMealRepository.calls.isEmpty())
    }

    @Test
    fun logRecipe_successAfterFailure_clearsPreviousError() {
        viewModel.logRecipe("missing", "DINNER", "2026-10-04")
        assertEquals("Recipe not found", viewModel.logError.value)

        val success = viewModel.logRecipe("r1", "DINNER", "2026-10-04")

        assertTrue(success)
        assertNull(viewModel.logError.value)
    }

    private class FakeRecipeRepository : RecipeRepository {
        var requestedId: String? = null
        var getRecipesCallCount = 0
        val stored = mutableListOf<Recipe>()

        override fun saveRecipe(name: String, ingredients: List<NewRecipeIngredient>): List<Recipe> =
            stored.toList()

        override fun getRecipes(): List<Recipe> {
            getRecipesCallCount++
            return stored.toList()
        }

        override fun getRecipeById(recipeId: String): Recipe? {
            requestedId = recipeId
            return stored.firstOrNull { it.id == recipeId }
        }

        override fun deleteRecipe(recipeId: String): List<Recipe> {
            stored.removeAll { it.id == recipeId }
            return stored.toList()
        }

        override fun deleteIngredient(ingredientId: Long): Recipe? = null
    }

    private class RecordingMealRepository : MealRepository {
        val calls = mutableListOf<Call>()

        data class Call(
            val foodName: String,
            val quantityGrams: Double,
            val calories: Double,
            val mealSlot: String,
            val date: String
        )

        override fun getMealEntries(): List<MealEntry> = emptyList()
        override fun getMealEntriesByDate(date: String): List<MealEntry> = emptyList()
        override fun addMealEntry(date: String): List<MealEntry> = throw UnsupportedOperationException()
        override fun updateMealEntry(id: String, newTitle: String): List<MealEntry> =
            throw UnsupportedOperationException()

        override fun deleteMealEntry(id: String): List<MealEntry> = throw UnsupportedOperationException()
        override fun restoreMealEntry(entry: MealEntry): List<MealEntry> = throw UnsupportedOperationException()

        override fun logQuickAdd(
            name: String,
            calories: Double,
            proteinG: Double,
            carbsG: Double,
            fatG: Double,
            mealSlot: String,
            date: String
        ): List<MealEntry> = throw UnsupportedOperationException()

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
            calls.add(Call(foodName, quantityGrams, calories, mealSlot, date))
            return emptyList()
        }
    }
}