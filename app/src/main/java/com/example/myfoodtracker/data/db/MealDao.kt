package com.example.myfoodtracker.data.db

import androidx.room3.*

@Dao
interface MealDao {
    @Transaction
    @Query("SELECT * FROM meals WHERE profile_id = :profileId AND date = :date")
    fun getMealsWithFoodsByProfileIdAndDate(profileId: String, date: String): List<MealWithFoods>

    @Transaction
    @Query("SELECT * FROM meals WHERE profile_id = :profileId")
    fun getMealsWithFoodsByProfileId(profileId: String): List<MealWithFoods>

    @Transaction
    @Query("SELECT * FROM meals")
    fun getAllMealsWithFoods(): List<MealWithFoods>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMeal(meal: MealEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFoods(foods: List<FoodEntity>)

    /**
     * Atomic parent+child write: both rows commit together or neither does.
     * Delegates to the REPLACE-annotated primitives, so the same id re-insert
     * semantics (restore path) are inherited unchanged.
     */
    @Transaction
    fun insertMealWithFoods(meal: MealEntryEntity, foods: List<FoodEntity>) {
        insertMeal(meal)
        insertFoods(foods)
    }

    @Query("UPDATE meals SET title = :title WHERE id = :id AND profile_id = :profileId")
    fun updateMealTitle(id: String, profileId: String, title: String)

    @Query("DELETE FROM meals WHERE id = :id AND profile_id = :profileId")
    fun deleteMeal(id: String, profileId: String)
}

