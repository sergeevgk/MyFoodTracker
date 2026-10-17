package com.example.myfoodtracker.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.myfoodtracker.domain.model.NewRecipeIngredient
import com.example.myfoodtracker.domain.model.Recipe
import com.example.myfoodtracker.domain.usecase.DeleteRecipeUseCase
import com.example.myfoodtracker.domain.usecase.GetRecipesUseCase
import com.example.myfoodtracker.domain.usecase.SaveRecipeUseCase

class RecipeViewModel(
    private val saveRecipeUseCase: SaveRecipeUseCase,
    private val getRecipesUseCase: GetRecipesUseCase,
    private val deleteRecipeUseCase: DeleteRecipeUseCase
) : ViewModel() {

    private val _recipes = MutableLiveData<List<Recipe>>(emptyList())
    val recipes: LiveData<List<Recipe>> = _recipes

    private val _saveError = MutableLiveData<String?>(null)
    val saveError: LiveData<String?> = _saveError

    init {
        refresh()
    }

    fun refresh() {
        _recipes.value = getRecipesUseCase()
    }

    fun saveRecipe(name: String, ingredients: List<NewRecipeIngredient>): Boolean {
        return try {
            _recipes.value = saveRecipeUseCase(name, ingredients)
            _saveError.value = null
            true
        } catch (e: IllegalArgumentException) {
            _saveError.value = e.message
            false
        }
    }

    fun clearSaveError() {
        _saveError.value = null
    }

    fun deleteRecipe(recipeId: String) {
        _recipes.value = deleteRecipeUseCase(recipeId)
    }
}
