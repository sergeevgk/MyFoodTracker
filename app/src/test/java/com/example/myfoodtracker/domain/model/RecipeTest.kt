package com.example.myfoodtracker.domain.model

import org.junit.Assert.*
import org.junit.Test

class RecipeTest {

    private val chicken = RecipeIngredient(
        foodName = "Chicken Breast",
        quantityGrams = 150.0,
        unit = "g",
        calories = 180.0,
        proteinG = 34.5,
        carbsG = 0.75,
        fatG = 3.0,
        fiberG = 0.0
    )
    private val rice = RecipeIngredient(
        foodName = "Rice",
        quantityGrams = 200.0,
        unit = "g",
        calories = 260.0,
        proteinG = 5.0,
        carbsG = 56.0,
        fatG = 0.6,
        fiberG = 1.0
    )

    @Test
    fun totals_sumAcrossIngredients_withDoublePrecision() {
        val recipe = Recipe(id = "r1", name = "Bowl", ingredients = listOf(chicken, rice))

        assertEquals(2, recipe.ingredientCount)
        assertEquals(440.0, recipe.totalCalories, 0.0001)
        assertEquals(39.5, recipe.totalProteinG, 0.0001)
        assertEquals(56.75, recipe.totalCarbsG, 0.0001)
        assertEquals(3.6, recipe.totalFatG, 0.0001)
        assertEquals(1.0, recipe.totalFiberG, 0.0001)
    }

    @Test
    fun totals_emptyIngredients_sumToZero() {
        val recipe = Recipe(id = "r1", name = "Empty")

        assertEquals(0, recipe.ingredientCount)
        assertEquals(0.0, recipe.totalCalories, 0.0)
        assertEquals(0.0, recipe.totalProteinG, 0.0)
    }

    @Test
    fun fromFoodItem_grams150_scalesBy1Point5() {
        val food = FoodItem(
            id = 7L, name = "Chicken Breast", brand = null, barcode = null,
            isCustom = false, calories = 120.0, proteinG = 23.0, carbsG = 0.5,
            fatG = 2.0, fiberG = 0.0, sugarG = 0.0, sodiumMg = 70.0
        )

        val draft = NewRecipeIngredient.fromFoodItem(food, 150.0, ServingUnit.G)

        assertEquals("Chicken Breast", draft.foodName)
        assertEquals(150.0, draft.quantityGrams, 0.001)
        assertEquals("g", draft.unit)
        assertEquals(180.0, draft.calories, 0.001)
        assertEquals(34.5, draft.proteinG, 0.001)
        assertEquals(7L, draft.catalogFoodId)
    }

    @Test
    fun fromFoodItem_servings_multipliesByBaseServingSize() {
        val food = FoodItem(
            id = 9L, name = "Oats", brand = null, barcode = null,
            isCustom = false, calories = 348.0, proteinG = 13.0, carbsG = 60.0,
            fatG = 7.0, fiberG = 10.0, sugarG = 1.0, sodiumMg = 5.0,
            baseServingSize = 40.0
        )

        val draft = NewRecipeIngredient.fromFoodItem(food, 2.0, ServingUnit.SERVINGS)

        // 2 servings x 40 g = 80 g -> factor 0.8
        assertEquals(80.0, draft.quantityGrams, 0.001)
        assertEquals("g", draft.unit)
        assertEquals(278.4, draft.calories, 0.001)
    }

    @Test
    fun fromFoodItem_fractionalQuantity_scalesPrecisely() {
        val food = FoodItem(
            id = 7L, name = "Chicken Breast", brand = null, barcode = null,
            isCustom = false, calories = 120.0, proteinG = 23.0, carbsG = 0.5,
            fatG = 2.0, fiberG = 0.0, sugarG = 0.0, sodiumMg = 70.0
        )

        val draft = NewRecipeIngredient.fromFoodItem(food, 12.5, ServingUnit.G)

        assertEquals(15.0, draft.calories, 0.001)
        assertEquals(2.875, draft.proteinG, 0.0001)
    }
}
