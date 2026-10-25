package com.example.myfoodtracker.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction

@Dao
interface RecipeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecipe(recipe: RecipeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertIngredients(ingredients: List<RecipeIngredientEntity>)

    /**
     * Atomic parent+child write: the recipe and its ingredients commit together
     * or neither does. Delegates to the REPLACE-annotated primitives.
     */
    @Transaction
    fun insertRecipeWithIngredients(recipe: RecipeEntity, ingredients: List<RecipeIngredientEntity>) {
        insertRecipe(recipe)
        insertIngredients(ingredients)
    }

    @Query("SELECT * FROM recipes WHERE profile_id = :profileId ORDER BY updated_at DESC")
    fun getRecipesByProfileId(profileId: String): List<RecipeEntity>

    @Query("SELECT * FROM recipe_ingredients WHERE recipe_id = :recipeId")
    fun getIngredientsByRecipeId(recipeId: String): List<RecipeIngredientEntity>

    @Transaction
    @Query("SELECT * FROM recipes WHERE profile_id = :profileId ORDER BY updated_at DESC")
    fun getRecipesWithIngredientsByProfileId(profileId: String): List<RecipeWithIngredients>

    @Query("UPDATE recipes SET name = :name, updated_at = :updatedAt WHERE id = :id AND profile_id = :profileId")
    fun updateRecipeName(id: String, profileId: String, name: String, updatedAt: Long)

    @Query("DELETE FROM recipes WHERE id = :id AND profile_id = :profileId")
    fun deleteRecipe(id: String, profileId: String)

    @Query("DELETE FROM recipe_ingredients WHERE id = :ingredientId AND recipe_id IN (SELECT id FROM recipes WHERE profile_id = :profileId)")
    fun deleteIngredient(ingredientId: Long, profileId: String)
}
