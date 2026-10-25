package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.NewRecipeIngredient
import com.example.myfoodtracker.domain.model.Recipe
import com.example.myfoodtracker.domain.model.RecipeIngredient
import com.example.myfoodtracker.domain.repository.RecipeRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RecipeQueryUseCasesTest {

    private lateinit var fakeRepository: FakeRecipeRepository
    private lateinit var getRecipes: GetRecipesUseCase
    private lateinit var deleteRecipe: DeleteRecipeUseCase

    @Before
    fun setUp() {
        fakeRepository = FakeRecipeRepository()
        getRecipes = GetRecipesUseCase(fakeRepository)
        deleteRecipe = DeleteRecipeUseCase(fakeRepository)
    }

    @Test
    fun getRecipes_returnsAllForActiveProfile() {
        fakeRepository.stored.addAll(
            listOf(
                Recipe(id = "r1", name = "Bowl"),
                Recipe(id = "r2", name = "Shake")
            )
        )

        val result = getRecipes()

        assertEquals(2, result.size)
        assertEquals("Bowl", result[0].name)
    }

    @Test
    fun getRecipes_noActiveProfile_returnsEmpty() {
        fakeRepository.hasActiveProfile = false
        fakeRepository.stored.add(Recipe(id = "r1", name = "Bowl"))

        assertTrue(getRecipes().isEmpty())
    }

    @Test
    fun deleteRecipe_removesAndReturnsRemaining() {
        fakeRepository.stored.addAll(
            listOf(
                Recipe(id = "r1", name = "Bowl"),
                Recipe(id = "r2", name = "Shake")
            )
        )

        val remaining = deleteRecipe("r1")

        assertEquals(1, remaining.size)
        assertEquals("r2", remaining[0].id)
    }

    @Test
    fun deleteRecipe_unknownId_returnsUnchanged() {
        fakeRepository.stored.add(Recipe(id = "r1", name = "Bowl"))

        val remaining = deleteRecipe("missing")

        assertEquals(1, remaining.size)
    }

    class FakeRecipeRepository : RecipeRepository {
        var hasActiveProfile = true
        val stored = mutableListOf<Recipe>()

        override fun saveRecipe(
            name: String,
            ingredients: List<NewRecipeIngredient>
        ): List<Recipe> = stored.toList()

        override fun getRecipes(): List<Recipe> =
            if (hasActiveProfile) stored.toList() else emptyList()

        override fun getRecipeById(recipeId: String): Recipe? =
            stored.firstOrNull { it.id == recipeId }

        override fun deleteRecipe(recipeId: String): List<Recipe> {
            stored.removeAll { it.id == recipeId }
            return stored.toList()
        }

        override fun deleteIngredient(ingredientId: Long): Recipe? = null
    }
}
