package com.example.myfoodtracker.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.myfoodtracker.domain.model.NewRecipeIngredient
import com.example.myfoodtracker.domain.usecase.SaveRecipeUseCase

/**
 * Backs the recipe builder screen. Holds the in-progress draft (recipe name
 * plus chosen ingredients) so it survives a configuration change — the
 * Fragment owns no draft state (story 8.2, AC #1).
 *
 * Saving reuses the factory-registered [SaveRecipeUseCase]; the
 * try/catch-`IllegalArgumentException` → error-`LiveData` contract mirrors
 * [RecipeViewModel.saveRecipe]. The ViewModel stays Context-free and
 * synchronous (no coroutines).
 */
class RecipeBuilderViewModel(
    private val saveRecipeUseCase: SaveRecipeUseCase
) : ViewModel() {

    private val _drafts = MutableLiveData<List<NewRecipeIngredient>>(emptyList())
    val drafts: LiveData<List<NewRecipeIngredient>> = _drafts

    private val _name = MutableLiveData("")
    val name: LiveData<String> = _name

    private val _saveError = MutableLiveData<String?>(null)
    val saveError: LiveData<String?> = _saveError

    fun addIngredient(ingredient: NewRecipeIngredient) {
        _drafts.value = _drafts.value.orEmpty() + ingredient
    }

    fun removeIngredient(position: Int) {
        val current = _drafts.value.orEmpty()
        if (position !in current.indices) return
        _drafts.value = current.toMutableList().apply { removeAt(position) }
    }

    fun setName(name: String) {
        _name.value = name
    }

    fun clearSaveError() {
        _saveError.value = null
    }

    /**
     * Saves the draft held in [drafts] under the caller-supplied [name].
     * Returns `true` on success; on failure returns `false` and exposes the
     * use case's `IllegalArgumentException` message through [saveError].
     */
    fun saveRecipe(name: String): Boolean {
        return try {
            saveRecipeUseCase(name, _drafts.value.orEmpty())
            _saveError.value = null
            true
        } catch (e: IllegalArgumentException) {
            _saveError.value = e.message
            false
        }
    }
}
