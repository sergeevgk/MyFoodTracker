package com.example.myfoodtracker.domain.model

data class DailySummary(
    val totalCalories: Double = 0.0,
    val totalProteinG: Double = 0.0,
    val totalCarbsG: Double = 0.0,
    val totalFatG: Double = 0.0
) {
    companion object {
        fun summarize(mealEntries: List<MealEntry>): DailySummary {
            var calories = 0.0
            var protein = 0.0
            var carbs = 0.0
            var fat = 0.0
            for (meal in mealEntries) {
                for (food in meal.foods) {
                    calories += food.calories
                    protein += food.protein
                    carbs += food.carbs
                    fat += food.fat
                }
            }
            return DailySummary(
                totalCalories = calories,
                totalProteinG = protein,
                totalCarbsG = carbs,
                totalFatG = fat
            )
        }
    }
}
