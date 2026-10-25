package com.example.myfoodtracker.data.repository

import com.example.myfoodtracker.data.db.RecipeDao
import com.example.myfoodtracker.data.db.RecipeEntity
import com.example.myfoodtracker.data.db.RecipeIngredientEntity
import com.example.myfoodtracker.data.db.RecipeWithIngredients
import com.example.myfoodtracker.domain.model.NewRecipeIngredient
import com.example.myfoodtracker.domain.repository.SessionRepository
import com.example.myfoodtracker.domain.model.UserProfile
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RecipeRepositoryImplTest {

    private lateinit var fakeDao: FakeRecipeDao
    private lateinit var fakeSession: FakeSessionRepository
    private lateinit var repository: RecipeRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeRecipeDao()
        fakeSession = FakeSessionRepository()
        repository = RecipeRepositoryImpl(fakeDao, fakeSession)
    }

    @Test
    fun saveRecipe_persistsRecipeAndIngredients_scopedToProfile() {
        val saved = repository.saveRecipe(
            "Chicken Bowl",
            listOf(
                NewRecipeIngredient(
                    catalogFoodId = 7L, foodName = "Chicken Breast",
                    quantityGrams = 150.0, unit = "g",
                    calories = 180.0, proteinG = 34.5,
                    carbsG = 0.75, fatG = 3.0, fiberG = 0.0
                )
            )
        )

        assertEquals(1, saved.size)
        assertEquals("Chicken Bowl", saved[0].name)
        assertEquals("profile-1", fakeDao.lastRecipe!!.profileId)
        assertEquals(1, fakeDao.insertedIngredients.size)
        assertEquals("Chicken Breast", fakeDao.insertedIngredients[0].foodName)
        assertEquals(150.0, fakeDao.insertedIngredients[0].quantityGrams, 0.001)
        // Snapshot mapped back to domain with Double precision.
        assertEquals(180.0, saved[0].totalCalories, 0.0001)
        assertEquals(34.5, saved[0].totalProteinG, 0.0001)
    }

    @Test
    fun saveRecipe_writesThroughTransactionalCompositeMethod() {
        repository.saveRecipe(
            "Chicken Bowl",
            listOf(
                NewRecipeIngredient(
                    catalogFoodId = 7L, foodName = "Chicken Breast",
                    quantityGrams = 150.0, unit = "g",
                    calories = 180.0, proteinG = 34.5,
                    carbsG = 0.75, fatG = 3.0, fiberG = 0.0
                )
            )
        )

        // The recipe+ingredient pair must go through the atomic @Transaction DAO method.
        assertEquals(1, fakeDao.transactionalInsertCalls)
        assertEquals(0, fakeDao.directInsertRecipeCalls)
    }

    @Test
    fun getRecipes_noActiveProfile_returnsEmpty_neverTouchesDao() {
        fakeSession.profileId = null

        assertTrue(repository.getRecipes().isEmpty())
        assertFalse(fakeDao.getByProfileCalled)
    }

    @Test
    fun getRecipes_mapsEntitiesToDomain_withoutLeakingEntities() {
        fakeDao.recipes.add(
            RecipeEntity(
                id = "r1", profileId = "profile-1", name = "Bowl",
                createdAt = 1000L, updatedAt = 2000L
            )
        )
        fakeDao.ingredientsByRecipe["r1"] = mutableListOf(
            RecipeIngredientEntity(
                recipeId = "r1", catalogFoodId = 7L, foodName = "Rice",
                quantityGrams = 200.0, unit = "g",
                calories = 260.0, protein = 5.0, carbs = 56.0, fat = 0.6, fiber = 1.0
            )
        )

        val recipes = repository.getRecipes()

        assertEquals(1, recipes.size)
        assertEquals("Bowl", recipes[0].name)
        assertEquals(1, recipes[0].ingredientCount)
        assertEquals(260.0, recipes[0].totalCalories, 0.0001)
    }

    @Test
    fun deleteRecipe_scopesByProfile_andReturnsRemaining() {
        fakeDao.recipes.add(
            RecipeEntity(
                id = "r1", profileId = "profile-1", name = "Bowl",
                createdAt = 1000L, updatedAt = 2000L
            )
        )

        val remaining = repository.deleteRecipe("r1")

        assertTrue(remaining.isEmpty())
        assertEquals("r1", fakeDao.lastDeletedRecipeId)
        assertEquals("profile-1", fakeDao.lastDeletedRecipeProfile)
    }

    class FakeRecipeDao : RecipeDao {
        val recipes = mutableListOf<RecipeEntity>()
        val ingredientsByRecipe = mutableMapOf<String, MutableList<RecipeIngredientEntity>>()
        var lastRecipe: RecipeEntity? = null
        var insertedIngredients = listOf<RecipeIngredientEntity>()
        var getByProfileCalled = false
        var lastDeletedRecipeId: String? = null
        var lastDeletedRecipeProfile: String? = null
        var transactionalInsertCalls = 0
        var directInsertRecipeCalls = 0

        override fun insertRecipeWithIngredients(
            recipe: RecipeEntity,
            ingredients: List<RecipeIngredientEntity>
        ) {
            // Record the composite entry point, then apply the same state changes the
            // primitives would (without touching the direct-call counter).
            transactionalInsertCalls++
            lastRecipe = recipe
            recipes.removeAll { it.id == recipe.id }
            recipes.add(recipe)
            insertedIngredients = ingredients
            ingredients.forEach {
                ingredientsByRecipe.getOrPut(it.recipeId) { mutableListOf() }.add(it)
            }
        }

        override fun insertRecipe(recipe: RecipeEntity) {
            directInsertRecipeCalls++
            lastRecipe = recipe
            recipes.removeAll { it.id == recipe.id }
            recipes.add(recipe)
        }

        override fun insertIngredients(ingredients: List<RecipeIngredientEntity>) {
            insertedIngredients = ingredients
            ingredients.forEach {
                ingredientsByRecipe.getOrPut(it.recipeId) { mutableListOf() }.add(it)
            }
        }

        override fun getRecipesByProfileId(profileId: String): List<RecipeEntity> {
            getByProfileCalled = true
            return recipes.filter { it.profileId == profileId }
        }

        override fun getIngredientsByRecipeId(recipeId: String): List<RecipeIngredientEntity> {
            return ingredientsByRecipe[recipeId]?.toList() ?: emptyList()
        }

        override fun getRecipesWithIngredientsByProfileId(profileId: String): List<RecipeWithIngredients> {
            getByProfileCalled = true
            return recipes.filter { it.profileId == profileId }.map { recipe ->
                RecipeWithIngredients(
                    recipe = recipe,
                    ingredients = getIngredientsByRecipeId(recipe.id)
                )
            }
        }

        override fun updateRecipeName(id: String, profileId: String, name: String, updatedAt: Long) {
            val index = recipes.indexOfFirst { it.id == id && it.profileId == profileId }
            if (index != -1) recipes[index] = recipes[index].copy(name = name, updatedAt = updatedAt)
        }

        override fun deleteRecipe(id: String, profileId: String) {
            lastDeletedRecipeId = id
            lastDeletedRecipeProfile = profileId
            recipes.removeAll { it.id == id && it.profileId == profileId }
            ingredientsByRecipe.remove(id)
        }

        override fun deleteIngredient(ingredientId: Long, profileId: String) {
            ingredientsByRecipe.values.forEach { list ->
                list.removeAll { it.id == ingredientId }
            }
        }
    }

    class FakeSessionRepository : SessionRepository {
        var profileId: String? = "profile-1"

        override fun getActiveProfile(): UserProfile? = null
        override fun getActiveProfileId(): String? = profileId
        override fun setActiveProfile(profile: UserProfile, rememberDevice: Boolean) = Unit
        override fun getRememberedProfileId(): String? = null
        override fun forgetRememberedProfile() = Unit
        override fun clearSession() = Unit
        override fun isLoggedIn(): Boolean = profileId != null
    }
}
