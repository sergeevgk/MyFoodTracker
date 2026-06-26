package com.example.myfoodtracker.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.usecase.AddMealEntryUseCase
import com.example.myfoodtracker.domain.usecase.DeleteMealEntryUseCase
import com.example.myfoodtracker.domain.usecase.GetMealEntriesUseCase
import com.example.myfoodtracker.domain.usecase.UpdateMealEntryUseCase

class MealViewModel(
    private val getMealEntriesUseCase: GetMealEntriesUseCase,
    private val addMealEntryUseCase: AddMealEntryUseCase,
    private val updateMealEntryUseCase: UpdateMealEntryUseCase,
    private val deleteMealEntryUseCase: DeleteMealEntryUseCase
) : ViewModel() {

    private val _mealEntries = MutableLiveData<List<MealEntry>>()
    val mealEntries: LiveData<List<MealEntry>> = _mealEntries

    private val _newlyAddedPosition = MutableLiveData<Int?>()
    val newlyAddedPosition: LiveData<Int?> = _newlyAddedPosition

    fun loadMealEntries() {
        _mealEntries.value = getMealEntriesUseCase()
    }

    fun addMealEntry() {
        val currentSize = _mealEntries.value?.size ?: 0
        _mealEntries.value = addMealEntryUseCase()
        _newlyAddedPosition.value = currentSize
    }

    fun updateMealEntry(id: String, newTitle: String) {
        _mealEntries.value = updateMealEntryUseCase(id, newTitle)
    }

    fun deleteMealEntry(id: String) {
        _mealEntries.value = deleteMealEntryUseCase(id)
    }

    fun consumeNewlyAddedPosition() {
        _newlyAddedPosition.value = null
    }
}
