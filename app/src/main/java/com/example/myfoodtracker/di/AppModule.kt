package com.example.myfoodtracker.di

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.example.myfoodtracker.data.db.AppDatabase
import com.example.myfoodtracker.data.db.FoodCatalogDatabase
import com.example.myfoodtracker.data.repository.FoodCatalogRepositoryImpl
import com.example.myfoodtracker.data.repository.MealRepositoryImpl
import com.example.myfoodtracker.data.repository.SessionRepositoryImpl
import com.example.myfoodtracker.data.repository.UserRepositoryImpl
import com.example.myfoodtracker.domain.repository.FoodCatalogRepository
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
import com.example.myfoodtracker.domain.usecase.LogFoodEntryUseCase
import com.example.myfoodtracker.domain.usecase.LogQuickAddUseCase
import com.example.myfoodtracker.domain.usecase.LogWaterUseCase
import com.example.myfoodtracker.domain.usecase.LogoutUseCase
import com.example.myfoodtracker.domain.usecase.CreateCustomFoodUseCase
import com.example.myfoodtracker.domain.usecase.SearchFoodUseCase
import com.example.myfoodtracker.domain.usecase.RestoreMealEntryUseCase
import com.example.myfoodtracker.domain.usecase.RestoreRememberedSessionUseCase
import com.example.myfoodtracker.domain.usecase.UpdateMealEntryUseCase
import com.example.myfoodtracker.presentation.MealViewModel
import com.example.myfoodtracker.presentation.viewmodel.DashboardViewModel
import com.example.myfoodtracker.presentation.viewmodel.FoodSearchViewModel
import com.example.myfoodtracker.presentation.viewmodel.PasscodeAuthViewModel
import com.example.myfoodtracker.presentation.viewmodel.ProfileSetupViewModel
import android.content.Context
import com.example.myfoodtracker.data.local.SharedPrefsSessionStorage
import com.example.myfoodtracker.domain.repository.SessionStorage
import org.koin.android.ext.koin.androidContext
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

    // Food catalog database — FTS5 requires the bundled SQLite driver
    single {
        Room.databaseBuilder(get(), FoodCatalogDatabase::class.java, "food_catalog")
            .setDriver(BundledSQLiteDriver())
            .createFromAsset("databases/food_catalog.db")
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()
    }
    single { get<FoodCatalogDatabase>().foodCatalogDao() }
    single<FoodCatalogRepository> { FoodCatalogRepositoryImpl(get()) }
    factory { SearchFoodUseCase(get(), get()) }
    factory { CreateCustomFoodUseCase(get()) }

    // Session Preferences & Storage
    single {
        androidContext().getSharedPreferences(
            SharedPrefsSessionStorage.PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }
    single<SessionStorage> { SharedPrefsSessionStorage(get()) }

    // Repositories
    single<SessionRepository> { SessionRepositoryImpl(get()) }
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
    factory { RestoreRememberedSessionUseCase(get()) }
    factory { LogWaterUseCase(get()) }
    factory { GetWaterTotalUseCase(get()) }
    factory { LogQuickAddUseCase(get()) }
    factory { LogFoodEntryUseCase(get()) }
    factory { RestoreMealEntryUseCase(get()) }

    // ViewModels
    viewModel { MealViewModel(get(), get(), get(), get()) }
    viewModel { ProfileSetupViewModel(get()) }
    viewModel { PasscodeAuthViewModel(get(), get(), get()) }
    viewModel { DashboardViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { FoodSearchViewModel(get(), get(), get()) }
}
