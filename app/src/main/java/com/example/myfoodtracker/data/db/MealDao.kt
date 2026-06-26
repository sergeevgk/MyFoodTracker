package com.example.myfoodtracker.data.db

import androidx.room.*

@Dao
interface MealDao {
    @Transaction
    @Query("SELECT * FROM meals")
    fun getAllMealsWithFoods(): List<MealWithFoods>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMeal(meal: MealEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFoods(foods: List<FoodEntity>)

    @Query("UPDATE meals SET title = :title WHERE id = :id")
    fun updateMealTitle(id: String, title: String)

    @Query("DELETE FROM meals WHERE id = :id")
    fun deleteMeal(id: String)
}
