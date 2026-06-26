package com.example.myfoodtracker.data.repository

import com.example.myfoodtracker.data.db.FoodEntity
import com.example.myfoodtracker.data.db.MealDao
import com.example.myfoodtracker.data.db.MealEntryEntity
import com.example.myfoodtracker.data.db.MealWithFoods
import com.example.myfoodtracker.domain.model.Food
import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.repository.MealRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class MealRepositoryImpl(private val mealDao: MealDao) : MealRepository {

    override fun getMealEntries(): List<MealEntry> {
        return mealDao.getAllMealsWithFoods().map { it.toDomain() }
    }

    override fun addMealEntry(): List<MealEntry> {
        val currentDate = LocalDate.now().toString()
        val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val meal = MealEntryEntity(
            id = UUID.randomUUID().toString(),
            title = "",
            date = currentDate,
            time = currentTime
        )
        mealDao.insertMeal(meal)
        return getMealEntries()
    }

    override fun updateMealEntry(id: String, newTitle: String): List<MealEntry> {
        mealDao.updateMealTitle(id, newTitle)
        return getMealEntries()
    }

    override fun deleteMealEntry(id: String): List<MealEntry> {
        mealDao.deleteMeal(id)
        return getMealEntries()
    }

    // Mapping Helpers
    private fun MealWithFoods.toDomain(): MealEntry {
        return MealEntry(
            id = meal.id,
            title = meal.title,
            date = meal.date,
            time = meal.time,
            foods = foods.map { it.toDomain() }
        )
    }

    private fun FoodEntity.toDomain(): Food {
        return Food(
            name = name,
            weight = weight,
            calories = calories,
            carbs = carbs,
            fat = fat,
            protein = protein,
            fiber = fiber
        )
    }
}
