package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.NewRecipeIngredient
import com.example.myfoodtracker.domain.model.Recipe
import com.example.myfoodtracker.domain.repository.RecipeRepository

class SaveRecipeUseCase(private val repository: RecipeRepository) {
    operator fun invoke(name: String, ingredients: List<NewRecipeIngredient>): List<Recipe> {
        require(name.isNotBlank()) { "Enter a recipe name" }
        require(ingredients.isNotEmpty()) { "Add at least one ingredient" }
        ingredients.forEach {
            require(it.foodName.isNotBlank()) { "Enter a name" }
            require(it.quantityGrams.isFinite() && it.quantityGrams > 0) {
                "Enter a quantity greater than 0"
            }
            require(
                it.calories.isFinite() && it.proteinG.isFinite() &&
                    it.carbsG.isFinite() && it.fatG.isFinite() && it.fiberG.isFinite()
            ) { "Enter 0 or more" }
            require(
                it.calories >= 0 && it.proteinG >= 0 && it.carbsG >= 0 &&
                    it.fatG >= 0 && it.fiberG >= 0
            ) { "Enter 0 or more" }
        }
        return repository.saveRecipe(name.trim(), ingredients)
    }
}
