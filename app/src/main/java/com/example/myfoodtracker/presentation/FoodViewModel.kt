package com.example.myfoodtracker.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.myfoodtracker.domain.model.FoodEntry
import com.example.myfoodtracker.domain.usecase.AddFoodEntryUseCase
import com.example.myfoodtracker.domain.usecase.DeleteFoodEntryUseCase
import com.example.myfoodtracker.domain.usecase.GetFoodEntriesUseCase
import com.example.myfoodtracker.domain.usecase.UpdateFoodEntryUseCase

class FoodViewModel(
    private val getFoodEntriesUseCase: GetFoodEntriesUseCase,
    private val addFoodEntryUseCase: AddFoodEntryUseCase,
    private val updateFoodEntryUseCase: UpdateFoodEntryUseCase,
    private val deleteFoodEntryUseCase: DeleteFoodEntryUseCase
) : ViewModel() {

    private val _foodEntries = MutableLiveData<List<FoodEntry>>()
    val foodEntries: LiveData<List<FoodEntry>> = _foodEntries

    private val _newlyAddedPosition = MutableLiveData<Int?>()
    val newlyAddedPosition: LiveData<Int?> = _newlyAddedPosition

    fun loadFoodEntries() {
        _foodEntries.value = getFoodEntriesUseCase()
    }

    fun addFoodEntry() {
        val currentSize = _foodEntries.value?.size ?: 0
        _foodEntries.value = addFoodEntryUseCase()
        _newlyAddedPosition.value = currentSize
    }

    fun updateFoodEntry(id: String, newText: String) {
        _foodEntries.value = updateFoodEntryUseCase(id, newText)
    }

    fun deleteFoodEntry(id: String) {
        _foodEntries.value = deleteFoodEntryUseCase(id)
    }

    fun consumeNewlyAddedPosition() {
        _newlyAddedPosition.value = null
    }
}
