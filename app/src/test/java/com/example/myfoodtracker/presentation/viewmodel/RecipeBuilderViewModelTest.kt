package com.example.myfoodtracker.presentation.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.myfoodtracker.domain.model.NewRecipeIngredient
import com.example.myfoodtracker.domain.model.Recipe
import com.example.myfoodtracker.domain.repository.RecipeRepository
import com.example.myfoodtracker.domain.usecase.SaveRecipeUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for the builder-specific draft state ViewModel (story 8.2, AC #1):
 * the recipe draft (name + ingredients) must live in the ViewModel so it
 * survives a configuration change, while the save path reuses the
 * [SaveRecipeUseCase] contract of [RecipeViewModel] untouched.
 */
class RecipeBuilderViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var fakeRecipeRepository: RecordingRecipeRepository
    private lateinit var viewModel: RecipeBuilderViewModel

    private val chicken = NewRecipeIngredient(
        foodName = "Chicken Breast",
        quantityGrams = 150.0,
        calories = 180.0,
        proteinG = 34.5,
        carbsG = 0.75,
        fatG = 3.0,
        fiberG = 1.5
    )

    private val rice = NewRecipeIngredient(
        foodName = "Rice",
        quantityGrams = 100.0,
        calories = 130.0,
        proteinG = 2.7,
        carbsG = 28.0,
        fatG = 0.3,
        fiberG = 0.4
    )

    @Before
    fun setUp() {
        fakeRecipeRepository = RecordingRecipeRepository()
        viewModel = RecipeBuilderViewModel(
            saveRecipeUseCase = SaveRecipeUseCase(fakeRecipeRepository)
        )
    }

    // --- Initial state ---------------------------------------------------------------

    @Test
    fun init_startsEmpty() {
        assertEquals(emptyList<NewRecipeIngredient>(), viewModel.drafts.value)
        assertEquals("", viewModel.name.value)
        assertNull(viewModel.saveError.value)
    }

    // --- Draft mutations ---------------------------------------------------------------

    @Test
    fun addIngredient_appendsAndEmits() {
        viewModel.addIngredient(chicken)

        assertEquals(listOf(chicken), viewModel.drafts.value)

        viewModel.addIngredient(rice)

        assertEquals(listOf(chicken, rice), viewModel.drafts.value)
    }

    @Test
    fun setName_carriesTheValue() {
        viewModel.setName("Bowl")

        assertEquals("Bowl", viewModel.name.value)
    }

    // --- Removal -----------------------------------------------------------------------

    @Test
    fun removeIngredient_removesByPosition() {
        viewModel.addIngredient(chicken)
        viewModel.addIngredient(rice)

        viewModel.removeIngredient(0)

        assertEquals(listOf(rice), viewModel.drafts.value)
    }

    @Test
    fun removeIngredient_invalidIndex_isANoOp() {
        viewModel.addIngredient(chicken)

        viewModel.removeIngredient(1)
        viewModel.removeIngredient(-1)

        assertEquals(listOf(chicken), viewModel.drafts.value)
    }

    @Test
    fun removeIngredient_onEmptyDrafts_isANoOp() {
        viewModel.removeIngredient(0)

        assertEquals(emptyList<NewRecipeIngredient>(), viewModel.drafts.value)
    }

    // --- Save ------------------------------------------------------------------------

    @Test
    fun saveRecipe_success_returnsTrueClearsErrorAndForwardsNameAndDrafts() {
        viewModel.addIngredient(chicken)
        viewModel.addIngredient(rice)

        val success = viewModel.saveRecipe("Chicken Bowl")

        assertTrue(success)
        assertNull(viewModel.saveError.value)
        assertEquals("Chicken Bowl", fakeRecipeRepository.savedName)
        assertEquals(listOf(chicken, rice), fakeRecipeRepository.savedIngredients)
    }

    @Test
    fun saveRecipe_blankName_returnsFalseAndSetsError() {
        viewModel.addIngredient(chicken)

        val success = viewModel.saveRecipe("   ")

        assertEquals(false, success)
        assertEquals("Enter a recipe name", viewModel.saveError.value)
        assertNull(fakeRecipeRepository.savedName)
    }

    @Test
    fun saveRecipe_emptyDrafts_returnsFalseAndSetsError() {
        val success = viewModel.saveRecipe("Chicken Bowl")

        assertEquals(false, success)
        assertEquals("Add at least one ingredient", viewModel.saveError.value)
    }

    @Test
    fun saveRecipe_successAfterFailure_clearsPreviousError() {
        viewModel.saveRecipe("   ")
        assertEquals("Enter a recipe name", viewModel.saveError.value)

        viewModel.addIngredient(chicken)
        val success = viewModel.saveRecipe("Chicken Bowl")

        assertTrue(success)
        assertNull(viewModel.saveError.value)
    }

    @Test
    fun clearSaveError_resetsSaveErrorToNull() {
        viewModel.saveRecipe("   ")
        assertEquals("Enter a recipe name", viewModel.saveError.value)

        viewModel.clearSaveError()

        assertNull(viewModel.saveError.value)
    }

    // --- Fakes -----------------------------------------------------------------------

    /** Records the saveRecipe call so the use-case forwarding can be asserted. */
    private class RecordingRecipeRepository : RecipeRepository {
        var savedName: String? = null
        var savedIngredients: List<NewRecipeIngredient>? = null

        override fun saveRecipe(name: String, ingredients: List<NewRecipeIngredient>): List<Recipe> {
            savedName = name
            savedIngredients = ingredients
            return emptyList()
        }

        override fun getRecipes(): List<Recipe> = emptyList()
        override fun getRecipeById(recipeId: String): Recipe? = null
        override fun deleteRecipe(recipeId: String): List<Recipe> = emptyList()
        override fun deleteIngredient(ingredientId: Long): Recipe? = null
    }
}
