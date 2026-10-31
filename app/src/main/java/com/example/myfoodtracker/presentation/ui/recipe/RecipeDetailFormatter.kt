package com.example.myfoodtracker.presentation.ui.recipe

import com.example.myfoodtracker.domain.model.Recipe
import com.example.myfoodtracker.domain.model.RecipeIngredient
import java.util.Locale
import kotlin.math.round

object RecipeDetailFormatter {

    const val DEFAULT_INGREDIENT_LINE_TEMPLATE =
        "%1\$s \u00B7 %2\$s %3\$s \u00B7 %4\$.0f kcal (P %5\$.1fg, C %6\$.1fg, F %7\$.1fg)"
    private const val DEFAULT_TOTALS_SINGULAR_TEMPLATE =
        "%1\$d ingredient \u00B7 %2\$.0f kcal \u00B7 P %3\$.0fg \u00B7 C %4\$.0fg \u00B7 F %5\$.0fg"
    private const val DEFAULT_TOTALS_PLURAL_TEMPLATE =
        "%1\$d ingredients \u00B7 %2\$.0f kcal \u00B7 P %3\$.0fg \u00B7 C %4\$.0fg \u00B7 F %5\$.0fg"

    fun formatQuantity(quantity: Double, locale: Locale = Locale.getDefault()): String {
        val rounded = round(quantity * 10.0) / 10.0
        return if (rounded % 1.0 == 0.0) {
            String.format(locale, "%.0f", rounded)
        } else {
            String.format(locale, "%.1f", rounded)
        }
    }

    /**
     * Formats one ingredient line for the detail breakdown:
     * e.g. "Chicken Breast · 150 g · 180 kcal (P 34.5g, C 0.8g, F 3.0g)"
     */
    fun formatIngredientLine(
        ingredient: RecipeIngredient,
        locale: Locale = Locale.getDefault(),
        template: String = DEFAULT_INGREDIENT_LINE_TEMPLATE
    ): String {
        val qty = formatQuantity(ingredient.quantityGrams, locale)
        return String.format(
            locale,
            template,
            ingredient.foodName,
            qty,
            ingredient.unit,
            ingredient.calories,
            ingredient.proteinG,
            ingredient.carbsG,
            ingredient.fatG
        )
    }

    /**
     * Formats the totals summary line:
     * e.g. "2 ingredients · 440 kcal · P 40g · C 58g · F 3g"
     */
    fun formatTotalsSummary(
        recipe: Recipe,
        locale: Locale = Locale.getDefault(),
        template: String? = null
    ): String {
        val count = recipe.ingredientCount
        val resolvedTemplate = template ?: if (count == 1) {
            DEFAULT_TOTALS_SINGULAR_TEMPLATE
        } else {
            DEFAULT_TOTALS_PLURAL_TEMPLATE
        }
        return String.format(
            locale,
            resolvedTemplate,
            count,
            recipe.totalCalories,
            recipe.totalProteinG,
            recipe.totalCarbsG,
            recipe.totalFatG
        )
    }
}
