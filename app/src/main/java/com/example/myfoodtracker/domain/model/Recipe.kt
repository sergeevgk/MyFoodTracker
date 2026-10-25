package com.example.myfoodtracker.domain.model

data class RecipeIngredient(
    val id: Long = 0,
    val catalogFoodId: Long? = null,
    val foodName: String,
    val quantityGrams: Double,
    val unit: String = "g",
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double
)

data class Recipe(
    val id: String,
    val name: String,
    val ingredients: List<RecipeIngredient> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val ingredientCount: Int get() = ingredients.size
    val totalCalories: Double get() = ingredients.sumOf { it.calories }
    val totalProteinG: Double get() = ingredients.sumOf { it.proteinG }
    val totalCarbsG: Double get() = ingredients.sumOf { it.carbsG }
    val totalFatG: Double get() = ingredients.sumOf { it.fatG }
    val totalFiberG: Double get() = ingredients.sumOf { it.fiberG }
}

data class NewRecipeIngredient(
    val catalogFoodId: Long? = null,
    val foodName: String,
    val quantityGrams: Double,
    val unit: String = "g",
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double
) {
    companion object {
        fun fromFoodItem(food: FoodItem, quantity: Double, unit: ServingUnit): NewRecipeIngredient {
            val grams = when (unit) {
                ServingUnit.G -> quantity
                ServingUnit.ML -> quantity
                ServingUnit.SERVINGS -> quantity * food.baseServingSize
            }
            val factor = grams / 100.0
            val unitLabel = when (unit) {
                ServingUnit.G -> "g"
                ServingUnit.ML -> "ml"
                ServingUnit.SERVINGS -> food.baseServingUnit.ifBlank { "g" }
            }
            return NewRecipeIngredient(
                catalogFoodId = food.id,
                foodName = food.name,
                quantityGrams = grams,
                unit = unitLabel,
                calories = food.calories * factor,
                proteinG = food.proteinG * factor,
                carbsG = food.carbsG * factor,
                fatG = food.fatG * factor,
                fiberG = food.fiberG * factor
            )
        }
    }
}
