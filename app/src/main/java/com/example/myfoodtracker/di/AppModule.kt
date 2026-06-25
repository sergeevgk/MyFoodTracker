package com.example.myfoodtracker.di

import com.example.myfoodtracker.data.repository.FoodRepositoryImpl
import com.example.myfoodtracker.domain.repository.FoodRepository
import com.example.myfoodtracker.domain.usecase.AddFoodEntryUseCase
import com.example.myfoodtracker.domain.usecase.DeleteFoodEntryUseCase
import com.example.myfoodtracker.domain.usecase.GetFoodEntriesUseCase
import com.example.myfoodtracker.domain.usecase.UpdateFoodEntryUseCase
import com.example.myfoodtracker.presentation.FoodViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Repository
    single<FoodRepository> { FoodRepositoryImpl(get()) }

    // Use Cases
    factory { GetFoodEntriesUseCase(get()) }
    factory { AddFoodEntryUseCase(get()) }
    factory { UpdateFoodEntryUseCase(get()) }
    factory { DeleteFoodEntryUseCase(get()) }

    // ViewModel
    viewModel { FoodViewModel(get(), get(), get(), get()) }
}
