package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.Recipe
import com.example.myfoodtracker.domain.repository.RecipeRepository

class DeleteRecipeUseCase(private val repository: RecipeRepository) {
    operator fun invoke(recipeId: String): List<Recipe> = repository.deleteRecipe(recipeId)
}
