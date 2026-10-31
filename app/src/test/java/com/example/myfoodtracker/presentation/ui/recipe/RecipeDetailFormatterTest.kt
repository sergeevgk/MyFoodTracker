package com.example.myfoodtracker.presentation.ui.recipe

import com.example.myfoodtracker.domain.model.Recipe
import com.example.myfoodtracker.domain.model.RecipeIngredient
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class RecipeDetailFormatterTest {

    private val locale = Locale.US

    @Test
    fun formatQuantity_wholeNumber_formatsWithoutDecimals() {
        assertEquals("150", RecipeDetailFormatter.formatQuantity(150.0, locale))
        assertEquals("0", RecipeDetailFormatter.formatQuantity(0.0, locale))
    }

    @Test
    fun formatQuantity_nearWholeNumber_roundsToWholeWithoutTrailingZero() {
        assertEquals("150", RecipeDetailFormatter.formatQuantity(150.04, locale))
        assertEquals("150", RecipeDetailFormatter.formatQuantity(149.96, locale))
        assertEquals("0", RecipeDetailFormatter.formatQuantity(0.04, locale))
    }

    @Test
    fun formatQuantity_fractionalNumber_formatsWithOneDecimal() {
        assertEquals("150.5", RecipeDetailFormatter.formatQuantity(150.5, locale))
        assertEquals("0.2", RecipeDetailFormatter.formatQuantity(0.24, locale))
    }

    @Test
    fun formatIngredientLine_formatsNameQuantityAndMacros() {
        val ingredient = RecipeIngredient(
            foodName = "Chicken Breast",
            quantityGrams = 150.0,
            unit = "g",
            calories = 180.0,
            proteinG = 34.5,
            carbsG = 0.8,
            fatG = 3.0,
            fiberG = 1.5
        )

        val formatted = RecipeDetailFormatter.formatIngredientLine(ingredient, locale)
        assertEquals(
            "Chicken Breast \u00B7 150 g \u00B7 180 kcal (P 34.5g, C 0.8g, F 3.0g)",
            formatted
        )
    }

    @Test
    fun formatTotalsSummary_singleIngredient() {
        val recipe = Recipe(
            id = "r1",
            name = "Boiled Egg",
            ingredients = listOf(
                RecipeIngredient(
                    foodName = "Egg",
                    quantityGrams = 50.0,
                    calories = 78.0,
                    proteinG = 6.3,
                    carbsG = 0.6,
                    fatG = 5.3,
                    fiberG = 0.0
                )
            )
        )

        val summary = RecipeDetailFormatter.formatTotalsSummary(recipe, locale)
        assertEquals("1 ingredient \u00B7 78 kcal \u00B7 P 6g \u00B7 C 1g \u00B7 F 5g", summary)
    }

    @Test
    fun formatTotalsSummary_withResourceTemplate_usesProvidedTemplate() {
        val recipe = Recipe(
            id = "r1",
            name = "Boiled Egg",
            ingredients = listOf(
                RecipeIngredient(
                    foodName = "Egg",
                    quantityGrams = 50.0,
                    calories = 78.0,
                    proteinG = 6.3,
                    carbsG = 0.6,
                    fatG = 5.3,
                    fiberG = 0.0
                )
            )
        )
        val resourceTemplate = "%1\$d ingredients \u00B7 %2\$.0f kcal \u00B7 P %3\$.0fg \u00B7 C %4\$.0fg \u00B7 F %5\$.0fg"

        val summary = RecipeDetailFormatter.formatTotalsSummary(recipe, locale, resourceTemplate)
        assertEquals("1 ingredients \u00B7 78 kcal \u00B7 P 6g \u00B7 C 1g \u00B7 F 5g", summary)
    }

    @Test
    fun formatTotalsSummary_multipleIngredients() {
        val recipe = Recipe(
            id = "r2",
            name = "Chicken Bowl",
            ingredients = listOf(
                RecipeIngredient(
                    foodName = "Chicken Breast",
                    quantityGrams = 150.0,
                    calories = 180.0,
                    proteinG = 34.5,
                    carbsG = 0.8,
                    fatG = 3.0,
                    fiberG = 0.0
                ),
                RecipeIngredient(
                    foodName = "Rice",
                    quantityGrams = 200.0,
                    calories = 260.0,
                    proteinG = 5.5,
                    carbsG = 57.2,
                    fatG = 0.5,
                    fiberG = 1.0
                )
            )
        )

        val summary = RecipeDetailFormatter.formatTotalsSummary(recipe, locale)
        assertEquals("2 ingredients \u00B7 440 kcal \u00B7 P 40g \u00B7 C 58g \u00B7 F 4g", summary)
    }

    @Test
    fun formatTotalsSummary_emptyIngredients() {
        val recipe = Recipe(
            id = "r3",
            name = "Empty Recipe",
            ingredients = emptyList()
        )

        val summary = RecipeDetailFormatter.formatTotalsSummary(recipe, locale)
        assertEquals("0 ingredients \u00B7 0 kcal \u00B7 P 0g \u00B7 C 0g \u00B7 F 0g", summary)
    }
}
