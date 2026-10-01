package com.example.myfoodtracker.di

import androidx.room.Room
import com.example.myfoodtracker.data.db.AppDatabase
import com.example.myfoodtracker.data.repository.MealRepositoryImpl
import com.example.myfoodtracker.data.repository.SessionRepositoryImpl
import com.example.myfoodtracker.data.repository.UserRepositoryImpl
import com.example.myfoodtracker.domain.repository.MealRepository
import com.example.myfoodtracker.domain.repository.SessionRepository
import com.example.myfoodtracker.domain.repository.UserRepository
import com.example.myfoodtracker.domain.repository.WaterRepository
import com.example.myfoodtracker.data.repository.WaterRepositoryImpl
import com.example.myfoodtracker.domain.usecase.AddMealEntryUseCase
import com.example.myfoodtracker.domain.usecase.AuthenticateUserUseCase
import com.example.myfoodtracker.domain.usecase.CreateProfileUseCase
import com.example.myfoodtracker.domain.usecase.DeleteMealEntryUseCase
import com.example.myfoodtracker.domain.usecase.GetMealEntriesByDateUseCase
import com.example.myfoodtracker.domain.usecase.GetMealEntriesUseCase
import com.example.myfoodtracker.domain.usecase.GetWaterTotalUseCase
import com.example.myfoodtracker.domain.usecase.LogQuickAddUseCase
import com.example.myfoodtracker.domain.usecase.LogWaterUseCase
import com.example.myfoodtracker.domain.usecase.LogoutUseCase
import com.example.myfoodtracker.domain.usecase.UpdateMealEntryUseCase
import com.example.myfoodtracker.presentation.MealViewModel
import com.example.myfoodtracker.presentation.viewmodel.DashboardViewModel
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
    single { get<AppDatabase>().waterLogDao() }

    // Repositories
    single<SessionRepository> { SessionRepositoryImpl() }
    single<MealRepository> { MealRepositoryImpl(get(), get()) }
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<WaterRepository> { WaterRepositoryImpl(get(), get()) }

    // Use Cases
    factory { GetMealEntriesUseCase(get()) }
    factory { GetMealEntriesByDateUseCase(get()) }
    factory { AddMealEntryUseCase(get()) }
    factory { UpdateMealEntryUseCase(get()) }
    factory { DeleteMealEntryUseCase(get()) }
    factory { CreateProfileUseCase(get()) }
    factory { AuthenticateUserUseCase(get(), get()) }
    factory { LogoutUseCase(get()) }
    factory { LogWaterUseCase(get()) }
    factory { GetWaterTotalUseCase(get()) }
    factory { LogQuickAddUseCase(get()) }

    // ViewModels
    viewModel { MealViewModel(get(), get(), get(), get()) }
    viewModel { ProfileSetupViewModel(get()) }
    viewModel { PasscodeAuthViewModel(get(), get()) }
    viewModel { DashboardViewModel(get(), get(), get(), get(), get(), get()) }
}
