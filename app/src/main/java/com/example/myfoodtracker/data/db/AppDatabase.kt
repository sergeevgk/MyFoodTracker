package com.example.myfoodtracker.data.db

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.example.myfoodtracker.data.dao.UserProfileDao
import com.example.myfoodtracker.data.entity.UserDailyGoalEntity
import com.example.myfoodtracker.data.entity.UserProfileEntity

@Database(
    entities = [
        MealEntryEntity::class,
        FoodEntity::class,
        UserProfileEntity::class,
        UserDailyGoalEntity::class,
        WaterLogEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun waterLogDao(): WaterLogDao
    abstract fun recipeDao(): RecipeDao
}
