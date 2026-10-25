package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.Recipe
import com.example.myfoodtracker.domain.repository.RecipeRepository

class GetRecipesUseCase(private val repository: RecipeRepository) {
    operator fun invoke(): List<Recipe> = repository.getRecipes()
}
