package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.NewRecipeIngredient
import com.example.myfoodtracker.domain.model.Recipe
import com.example.myfoodtracker.domain.repository.RecipeRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SaveRecipeUseCaseTest {

    private lateinit var fakeRepository: FakeRecipeRepository
    private lateinit var useCase: SaveRecipeUseCase

    private fun draft(
        name: String = "Chicken Breast",
        grams: Double = 150.0
    ) = NewRecipeIngredient(
        catalogFoodId = 7L,
        foodName = name,
        quantityGrams = grams,
        unit = "g",
        calories = 180.0,
        proteinG = 34.5,
        carbsG = 0.75,
        fatG = 3.0,
        fiberG = 0.0
    )

    @Before
    fun setUp() {
        fakeRepository = FakeRecipeRepository()
        useCase = SaveRecipeUseCase(fakeRepository)
    }

    @Test
    fun invoke_validRecipe_delegatesTrimmedName() {
        val saved = useCase("  Chicken Bowl  ", listOf(draft()))

        assertEquals(1, saved.size)
        assertEquals("Chicken Bowl", saved[0].name)
        assertEquals("Chicken Bowl", fakeRepository.lastSavedName)
        assertEquals(1, fakeRepository.lastSavedIngredients!!.size)
    }

    @Test
    fun invoke_noActiveProfile_returnsEmpty() {
        fakeRepository.hasActiveProfile = false

        val saved = useCase("Bowl", listOf(draft()))

        assertTrue(saved.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_blankName_throws() {
        useCase("   ", listOf(draft()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_emptyIngredients_throws() {
        useCase("Bowl", emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_zeroQuantity_throws() {
        useCase("Bowl", listOf(draft(grams = 0.0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_negativeQuantity_throws() {
        useCase("Bowl", listOf(draft(grams = -50.0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_nanQuantity_throws() {
        useCase("Bowl", listOf(draft(grams = Double.NaN)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_infiniteQuantity_throws() {
        useCase("Bowl", listOf(draft(grams = Double.POSITIVE_INFINITY)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_blankIngredientName_throws() {
        useCase("Bowl", listOf(draft(name = "  ")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_nonFiniteCalories_throws() {
        useCase("Bowl", listOf(draft().copy(calories = Double.NaN)))
    }

    class FakeRecipeRepository : RecipeRepository {
        var hasActiveProfile = true
        var lastSavedName: String? = null
        var lastSavedIngredients: List<NewRecipeIngredient>? = null
        private val stored = mutableListOf<Recipe>()

        override fun saveRecipe(name: String, ingredients: List<NewRecipeIngredient>): List<Recipe> {
            if (!hasActiveProfile) return emptyList()
            lastSavedName = name
            lastSavedIngredients = ingredients
            val recipe = Recipe(
                id = "recipe-${stored.size + 1}",
                name = name,
                ingredients = ingredients.mapIndexed { index, d ->
                    com.example.myfoodtracker.domain.model.RecipeIngredient(
                        id = index + 1L,
                        catalogFoodId = d.catalogFoodId,
                        foodName = d.foodName,
                        quantityGrams = d.quantityGrams,
                        unit = d.unit,
                        calories = d.calories,
                        proteinG = d.proteinG,
                        carbsG = d.carbsG,
                        fatG = d.fatG,
                        fiberG = d.fiberG
                    )
                }
            )
            stored.add(recipe)
            return stored.toList()
        }

        override fun getRecipes(): List<Recipe> = stored.toList()

        override fun getRecipeById(recipeId: String): Recipe? =
            stored.firstOrNull { it.id == recipeId }

        override fun deleteRecipe(recipeId: String): List<Recipe> {
            stored.removeAll { it.id == recipeId }
            return stored.toList()
        }

        override fun deleteIngredient(ingredientId: Long): Recipe? = null
    }
}
