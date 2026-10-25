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

    // --- Restore-time catalog link remap (story 6.2) -----------------------------------

    /**
     * Catalog links of every restored ingredient. `recipe_ingredients` has no
     * FK to the catalog — the id is a soft cross-database reference by design,
     * which is exactly why it can go stale after a restore (5.2-D1).
     */
    @Query("SELECT id, catalog_food_id FROM recipe_ingredients WHERE catalog_food_id IS NOT NULL")
    fun getIngredientsWithCatalogLinks(): List<RecipeIngredientLinkRow>

    @Query("UPDATE recipe_ingredients SET catalog_food_id = :newFoodId WHERE id IN (:ingredientIds)")
    fun updateCatalogFoodLink(ingredientIds: List<Long>, newFoodId: Long)

    @Query("UPDATE recipe_ingredients SET catalog_food_id = NULL WHERE id IN (:ingredientIds)")
    fun clearCatalogFoodLink(ingredientIds: List<Long>)

    /**
     * Applies the restore-time remap in one transaction. Writes are keyed on
     * `recipe_ingredients.id` (**primary key**, captured by
     * [getIngredientsWithCatalogLinks] before any write) — chain-safe: entry
     * A→B can never re-catch rows a later entry B→C has just written. Batches
     * stay within SQLite's 999 bound-variable cap.
     */
    @Transaction
    fun remapAndClearCatalogFoodLinks(remaps: Map<Long, List<Long>>, clearedIngredientIds: List<Long>) {
        remaps.forEach { (newFoodId, ingredientIds) ->
            ingredientIds.chunked(BOUND_VARIABLE_BATCH).forEach { batch ->
                updateCatalogFoodLink(batch, newFoodId)
            }
        }
        clearedIngredientIds.chunked(BOUND_VARIABLE_BATCH).forEach { batch ->
            clearCatalogFoodLink(batch)
        }
    }

    companion object {
        /** SQLite's default limit is 999 bound variables per statement; stay well below. */
        const val BOUND_VARIABLE_BATCH = 500
    }
}
