package com.example.myfoodtracker.di

import androidx.room.Room
import com.example.myfoodtracker.data.db.AppDatabase
import com.example.myfoodtracker.data.repository.MealRepositoryImpl
import com.example.myfoodtracker.data.repository.SessionRepositoryImpl
import com.example.myfoodtracker.data.repository.UserRepositoryImpl
import com.example.myfoodtracker.domain.repository.MealRepository
import com.example.myfoodtracker.domain.repository.SessionRepository
import com.example.myfoodtracker.domain.repository.UserRepository
import com.example.myfoodtracker.domain.usecase.AddMealEntryUseCase
import com.example.myfoodtracker.domain.usecase.AuthenticateUserUseCase
import com.example.myfoodtracker.domain.usecase.CreateProfileUseCase
import com.example.myfoodtracker.domain.usecase.DeleteMealEntryUseCase
import com.example.myfoodtracker.domain.usecase.GetMealEntriesUseCase
import com.example.myfoodtracker.domain.usecase.LogoutUseCase
import com.example.myfoodtracker.domain.usecase.UpdateMealEntryUseCase
import com.example.myfoodtracker.presentation.MealViewModel
import com.example.myfoodtracker.presentation.viewmodel.PasscodeAuthViewModel
import com.example.myfoodtracker.presentation.viewmodel.ProfileSetupViewModel
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
        .fallbackToDestructiveMigration()
        .allowMainThreadQueries()
        .build()
    }
    single { get<AppDatabase>().mealDao() }
    single { get<AppDatabase>().userProfileDao() }

    // Repositories
    single<SessionRepository> { SessionRepositoryImpl() }
    single<MealRepository> { MealRepositoryImpl(get(), get()) }
    single<UserRepository> { UserRepositoryImpl(get()) }

    // Use Cases
    factory { GetMealEntriesUseCase(get()) }
    factory { AddMealEntryUseCase(get()) }
    factory { UpdateMealEntryUseCase(get()) }
    factory { DeleteMealEntryUseCase(get()) }
    factory { CreateProfileUseCase(get()) }
    factory { AuthenticateUserUseCase(get(), get()) }
    factory { LogoutUseCase(get()) }

    // ViewModels
    viewModel { MealViewModel(get(), get(), get(), get()) }
    viewModel { ProfileSetupViewModel(get()) }
    viewModel { PasscodeAuthViewModel(get(), get()) }
}
