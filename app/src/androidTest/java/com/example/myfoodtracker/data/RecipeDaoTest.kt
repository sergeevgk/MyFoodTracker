package com.example.myfoodtracker.data

import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import com.example.myfoodtracker.data.db.AppDatabase
import com.example.myfoodtracker.data.db.RecipeEntity
import com.example.myfoodtracker.data.db.RecipeIngredientEntity
import com.example.myfoodtracker.data.entity.UserProfileEntity
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RecipeDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.userProfileDao().insertUser(
            UserProfileEntity(
                id = "profile-1",
                username = "tester",
                passcodeHash = "hash",
                createdAt = 1000L
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndQuery_roundTripsRecipeWithIngredients() {
        val dao = db.recipeDao()
        dao.insertRecipe(
            RecipeEntity(
                id = "r1", profileId = "profile-1", name = "Bowl",
                createdAt = 1000L, updatedAt = 2000L
            )
        )
        dao.insertIngredients(
            listOf(
                RecipeIngredientEntity(
                    recipeId = "r1", catalogFoodId = 7L, foodName = "Rice",
                    quantityGrams = 200.0, unit = "g",
                    calories = 260.0, protein = 5.0, carbs = 56.0, fat = 0.6, fiber = 1.0
                )
            )
        )

        val loaded = dao.getRecipesWithIngredientsByProfileId("profile-1")

        assertEquals(1, loaded.size)
        assertEquals("Bowl", loaded[0].recipe.name)
        assertEquals(1, loaded[0].ingredients.size)
        assertEquals("Rice", loaded[0].ingredients[0].foodName)
        assertEquals(200.0, loaded[0].ingredients[0].quantityGrams, 0.001)
    }

    @Test
    fun query_filtersByProfileId() {
        val dao = db.recipeDao()
        dao.insertRecipe(
            RecipeEntity(
                id = "r1", profileId = "profile-1", name = "Bowl",
                createdAt = 1000L, updatedAt = 2000L
            )
        )

        assertTrue(dao.getRecipesWithIngredientsByProfileId("other-profile").isEmpty())
        assertEquals(1, dao.getRecipesWithIngredientsByProfileId("profile-1").size)
    }

    @Test
    fun deleteRecipe_cascadesToIngredients() {
        val dao = db.recipeDao()
        dao.insertRecipe(
            RecipeEntity(
                id = "r1", profileId = "profile-1", name = "Bowl",
                createdAt = 1000L, updatedAt = 2000L
            )
        )
        dao.insertIngredients(
            listOf(
                RecipeIngredientEntity(
                    recipeId = "r1", catalogFoodId = null, foodName = "Rice",
                    quantityGrams = 200.0, unit = "g",
                    calories = 260.0, protein = 5.0, carbs = 56.0, fat = 0.6, fiber = 1.0
                )
            )
        )

        dao.deleteRecipe("r1", "profile-1")

        assertTrue(dao.getRecipesWithIngredientsByProfileId("profile-1").isEmpty())
        assertTrue(dao.getIngredientsByRecipeId("r1").isEmpty())
    }
}
