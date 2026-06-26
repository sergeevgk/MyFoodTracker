package com.example.myfoodtracker.di

import androidx.room.Room
import com.example.myfoodtracker.data.db.AppDatabase
import com.example.myfoodtracker.data.repository.MealRepositoryImpl
import com.example.myfoodtracker.domain.repository.MealRepository
import com.example.myfoodtracker.domain.usecase.AddMealEntryUseCase
import com.example.myfoodtracker.domain.usecase.DeleteMealEntryUseCase
import com.example.myfoodtracker.domain.usecase.GetMealEntriesUseCase
import com.example.myfoodtracker.domain.usecase.UpdateMealEntryUseCase
import com.example.myfoodtracker.presentation.MealViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Database
    single {
        Room.databaseBuilder(
            get(),
            AppDatabase::class.java,
            "meals_database"
        )
        .allowMainThreadQueries()
        .build()
    }
    single { get<AppDatabase>().mealDao() }

    // Repository
    single<MealRepository> { MealRepositoryImpl(get()) }

    // Use Cases
    factory { GetMealEntriesUseCase(get()) }
    factory { AddMealEntryUseCase(get()) }
    factory { UpdateMealEntryUseCase(get()) }
    factory { DeleteMealEntryUseCase(get()) }

    // ViewModel
    viewModel { MealViewModel(get(), get(), get(), get()) }
}


