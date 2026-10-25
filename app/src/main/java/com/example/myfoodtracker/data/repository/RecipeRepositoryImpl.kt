package com.example.myfoodtracker.data.repository

import com.example.myfoodtracker.data.db.RecipeDao
import com.example.myfoodtracker.data.db.RecipeEntity
import com.example.myfoodtracker.data.db.RecipeIngredientEntity
import com.example.myfoodtracker.data.db.RecipeWithIngredients
import com.example.myfoodtracker.domain.model.NewRecipeIngredient
import com.example.myfoodtracker.domain.model.Recipe
import com.example.myfoodtracker.domain.model.RecipeIngredient
import com.example.myfoodtracker.domain.repository.RecipeRepository
import com.example.myfoodtracker.domain.repository.SessionRepository
import java.util.UUID

class RecipeRepositoryImpl(
    private val recipeDao: RecipeDao,
    private val sessionRepository: SessionRepository
) : RecipeRepository {

    override fun saveRecipe(name: String, ingredients: List<NewRecipeIngredient>): List<Recipe> {
        val profileId = sessionRepository.getActiveProfileId() ?: return emptyList()
        val now = System.currentTimeMillis()
        val recipeId = UUID.randomUUID().toString()
        recipeDao.insertRecipe(
            RecipeEntity(
                id = recipeId,
                profileId = profileId,
                name = name,
                createdAt = now,
                updatedAt = now
            )
        )
        recipeDao.insertIngredients(
            ingredients.map {
                RecipeIngredientEntity(
                    recipeId = recipeId,
                    catalogFoodId = it.catalogFoodId,
                    foodName = it.foodName,
                    quantityGrams = it.quantityGrams,
                    unit = it.unit,
                    calories = it.calories,
                    protein = it.proteinG,
                    carbs = it.carbsG,
                    fat = it.fatG,
                    fiber = it.fiberG
                )
            }
        )
        return getRecipes()
    }

    override fun getRecipes(): List<Recipe> {
        val profileId = sessionRepository.getActiveProfileId() ?: return emptyList()
        return recipeDao.getRecipesWithIngredientsByProfileId(profileId).map { it.toDomain() }
    }

    override fun getRecipeById(recipeId: String): Recipe? {
        val profileId = sessionRepository.getActiveProfileId() ?: return null
        return recipeDao.getRecipesWithIngredientsByProfileId(profileId)
            .firstOrNull { it.recipe.id == recipeId }
            ?.toDomain()
    }

    override fun deleteRecipe(recipeId: String): List<Recipe> {
        val profileId = sessionRepository.getActiveProfileId() ?: return emptyList()
        recipeDao.deleteRecipe(recipeId, profileId)
        return getRecipes()
    }

    override fun deleteIngredient(ingredientId: Long): Recipe? {
        val profileId = sessionRepository.getActiveProfileId() ?: return null
        recipeDao.deleteIngredient(ingredientId, profileId)
        return null
    }

    private fun RecipeWithIngredients.toDomain(): Recipe {
        return Recipe(
            id = recipe.id,
            name = recipe.name,
            ingredients = ingredients.map { it.toDomain() },
            createdAt = recipe.createdAt,
            updatedAt = recipe.updatedAt
        )
    }

    private fun RecipeIngredientEntity.toDomain(): RecipeIngredient {
        return RecipeIngredient(
            id = id,
            catalogFoodId = catalogFoodId,
            foodName = foodName,
            quantityGrams = quantityGrams,
            unit = unit,
            calories = calories,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
            fiberG = fiber
        )
    }
}
