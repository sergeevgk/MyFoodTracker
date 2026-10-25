package com.example.myfoodtracker.domain.repository

import com.example.myfoodtracker.domain.model.NewRecipeIngredient
import com.example.myfoodtracker.domain.model.Recipe

interface RecipeRepository {
    fun saveRecipe(name: String, ingredients: List<NewRecipeIngredient>): List<Recipe>
    fun getRecipes(): List<Recipe>
    fun getRecipeById(recipeId: String): Recipe?
    fun deleteRecipe(recipeId: String): List<Recipe>
    fun deleteIngredient(ingredientId: Long): Recipe?
}
