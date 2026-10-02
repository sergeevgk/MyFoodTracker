package com.example.myfoodtracker.data.repository

import com.example.myfoodtracker.data.db.FoodEntity
import com.example.myfoodtracker.data.db.MealDao
import com.example.myfoodtracker.data.db.MealEntryEntity
import com.example.myfoodtracker.data.db.MealWithFoods
import com.example.myfoodtracker.domain.model.Food
import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.repository.MealRepository
import com.example.myfoodtracker.domain.repository.SessionRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class MealRepositoryImpl(
    private val mealDao: MealDao,
    private val sessionRepository: SessionRepository
) : MealRepository {

    override fun getMealEntries(): List<MealEntry> {
        val profileId = sessionRepository.getActiveProfileId() ?: return emptyList()
        return mealDao.getMealsWithFoodsByProfileId(profileId).map { it.toDomain() }
    }

    override fun getMealEntriesByDate(date: String): List<MealEntry> {
        val profileId = sessionRepository.getActiveProfileId() ?: return emptyList()
        return mealDao.getMealsWithFoodsByProfileIdAndDate(profileId, date).map { it.toDomain() }
    }

    override fun addMealEntry(): List<MealEntry> {
        val profileId = sessionRepository.getActiveProfileId() ?: return emptyList()
        val currentDate = LocalDate.now().toString()
        val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val meal = MealEntryEntity(
            id = UUID.randomUUID().toString(),
            profileId = profileId,
            title = "",
            date = currentDate,
            time = currentTime
        )
        mealDao.insertMeal(meal)
        return getMealEntries()
    }

    override fun updateMealEntry(id: String, newTitle: String): List<MealEntry> {
        val profileId = sessionRepository.getActiveProfileId() ?: return emptyList()
        mealDao.updateMealTitle(id, profileId, newTitle)
        return getMealEntries()
    }

    override fun deleteMealEntry(id: String): List<MealEntry> {
        val profileId = sessionRepository.getActiveProfileId() ?: return emptyList()
        mealDao.deleteMeal(id, profileId)
        return getMealEntries()
    }

    override fun logQuickAdd(
        name: String,
        calories: Double,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
        mealSlot: String,
        date: String
    ): List<MealEntry> {
        val profileId = sessionRepository.getActiveProfileId() ?: return getMealEntriesByDate(date)
        val slot = mealSlot.trim().uppercase()
        require(slot in setOf("BREAKFAST", "LUNCH", "DINNER", "SNACK")) { "Invalid meal slot" }
        val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val meal = MealEntryEntity(
            id = UUID.randomUUID().toString(),
            profileId = profileId,
            title = slot,
            date = date,
            time = currentTime
        )
        mealDao.insertMeal(meal)
        val food = FoodEntity(
            mealId = meal.id,
            name = name.trim(),
            weight = 0.0,
            calories = calories,
            carbs = carbsG,
            fat = fatG,
            protein = proteinG,
            fiber = 0.0
        )
        mealDao.insertFoods(listOf(food))
        return getMealEntriesByDate(date)
    }

    override fun restoreMealEntry(entry: MealEntry): List<MealEntry> {
        val profileId = sessionRepository.getActiveProfileId() ?: return emptyList()
        // Parent first (FK order), same id so REPLACE re-inserts the deleted row exactly.
        val meal = MealEntryEntity(
            id = entry.id,
            profileId = profileId,
            title = entry.title,
            date = entry.date,
            time = entry.time
        )
        mealDao.insertMeal(meal)
        val foods = entry.foods.map {
            FoodEntity(
                mealId = entry.id,
                name = it.name,
                weight = it.weight,
                calories = it.calories,
                carbs = it.carbs,
                fat = it.fat,
                protein = it.protein,
                fiber = it.fiber
            )
        }
        mealDao.insertFoods(foods)
        return getMealEntriesByDate(entry.date)
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
