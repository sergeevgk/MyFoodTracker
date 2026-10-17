package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.repository.MealRepository
import com.example.myfoodtracker.domain.repository.RecipeRepository

/**
 * Fans a saved recipe out into one intake row per constituent.
 *
 * Recipe ingredients already store absolute grams and already-scaled macros
 * (see [com.example.myfoodtracker.domain.model.NewRecipeIngredient.fromFoodItem]),
 * so the snapshots are forwarded to [MealRepository.logFoodEntry] unchanged.
 * No per-100g rescaling happens here.
 */
class LogRecipeUseCase(
    private val recipeRepository: RecipeRepository,
    private val mealRepository: MealRepository
) {

    operator fun invoke(recipeId: String, mealSlot: String, date: String): Int {
        val logDate = date.trim()
        require(DATE_PATTERN.matches(logDate)) { "Missing log date" }

        val slot = mealSlot.trim().uppercase()
        require(slot in VALID_SLOTS) { "Invalid meal slot" }

        val recipe = recipeRepository.getRecipeById(recipeId)
            ?: throw IllegalArgumentException("Recipe not found")

        val ingredients = recipe.ingredients
        require(ingredients.isNotEmpty()) { "Add at least one ingredient" }

        // Validate the whole list BEFORE the first write so a bad ingredient
        // can never leave a partial recipe on the dashboard.
        ingredients.forEach { ingredient ->
            require(ingredient.foodName.isNotBlank()) { "Enter a name" }
            require(ingredient.quantityGrams.isFinite() && ingredient.quantityGrams > 0.0) {
                "Enter a quantity greater than 0"
            }
            require(
                isValidMacro(ingredient.calories) &&
                    isValidMacro(ingredient.proteinG) &&
                    isValidMacro(ingredient.carbsG) &&
                    isValidMacro(ingredient.fatG) &&
                    isValidMacro(ingredient.fiberG)
            ) { "Enter 0 or more" }
        }

        ingredients.forEach { ingredient ->
            mealRepository.logFoodEntry(
                foodName = ingredient.foodName,
                quantityGrams = ingredient.quantityGrams,
                calories = ingredient.calories,
                proteinG = ingredient.proteinG,
                carbsG = ingredient.carbsG,
                fatG = ingredient.fatG,
                fiberG = ingredient.fiberG,
                mealSlot = slot,
                date = logDate
            )
        }

        return ingredients.size
    }

    private fun isValidMacro(value: Double): Boolean = value.isFinite() && value >= 0.0

    private companion object {
        val VALID_SLOTS = setOf("BREAKFAST", "LUNCH", "DINNER", "SNACK")
        val DATE_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")
    }
}