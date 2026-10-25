package com.example.myfoodtracker.di

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.example.myfoodtracker.data.db.AppDatabase
import com.example.myfoodtracker.data.db.CatalogMigrations
import com.example.myfoodtracker.data.db.FoodCatalogDatabase
import com.example.myfoodtracker.data.backup.DatabaseBackupManager
import com.example.myfoodtracker.data.repository.FoodCatalogRepositoryImpl
import com.example.myfoodtracker.data.repository.MealRepositoryImpl
import com.example.myfoodtracker.data.repository.SessionRepositoryImpl
import com.example.myfoodtracker.data.repository.UserRepositoryImpl
import com.example.myfoodtracker.data.export.LogExportFileWriter
import com.example.myfoodtracker.domain.repository.FoodCatalogRepository
import com.example.myfoodtracker.data.repository.RecipeRepositoryImpl
import com.example.myfoodtracker.domain.repository.MealRepository
import com.example.myfoodtracker.domain.repository.RecipeRepository
import com.example.myfoodtracker.domain.repository.SessionRepository
import com.example.myfoodtracker.domain.repository.UserRepository
import com.example.myfoodtracker.domain.repository.WaterRepository
import com.example.myfoodtracker.data.repository.WaterRepositoryImpl
import com.example.myfoodtracker.domain.usecase.AddMealEntryUseCase
import com.example.myfoodtracker.domain.usecase.AuthenticateUserUseCase
import com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase
import com.example.myfoodtracker.domain.usecase.CreateProfileUseCase
import com.example.myfoodtracker.domain.usecase.DeleteMealEntryUseCase
import com.example.myfoodtracker.domain.usecase.ExportLogsUseCase
import com.example.myfoodtracker.domain.usecase.GetMealEntriesByDateUseCase
import com.example.myfoodtracker.domain.usecase.GetMealEntriesUseCase
import com.example.myfoodtracker.domain.usecase.GetWaterTotalUseCase
import com.example.myfoodtracker.domain.usecase.LogFoodEntryUseCase
import com.example.myfoodtracker.domain.usecase.LogRecipeUseCase
import com.example.myfoodtracker.domain.usecase.LogQuickAddUseCase
import com.example.myfoodtracker.domain.usecase.LogWaterUseCase
import com.example.myfoodtracker.domain.usecase.LogoutUseCase
import com.example.myfoodtracker.domain.usecase.CreateCustomFoodUseCase
import com.example.myfoodtracker.domain.usecase.SearchFoodUseCase
import com.example.myfoodtracker.domain.usecase.RestoreMealEntryUseCase
import com.example.myfoodtracker.domain.usecase.DeleteRecipeUseCase
import com.example.myfoodtracker.domain.usecase.GetRecipesUseCase
import com.example.myfoodtracker.domain.usecase.RestoreRememberedSessionUseCase
import com.example.myfoodtracker.domain.usecase.SaveRecipeUseCase
import com.example.myfoodtracker.domain.usecase.UpdateMealEntryUseCase
import com.example.myfoodtracker.presentation.MealViewModel
import com.example.myfoodtracker.presentation.viewmodel.DashboardViewModel
import com.example.myfoodtracker.presentation.viewmodel.FoodSearchViewModel
import com.example.myfoodtracker.presentation.viewmodel.PasscodeAuthViewModel
import com.example.myfoodtracker.presentation.viewmodel.ProfileSetupViewModel
import com.example.myfoodtracker.presentation.viewmodel.RecipeViewModel
import com.example.myfoodtracker.presentation.viewmodel.SettingsViewModel
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
    single { get<AppDatabase>().recipeDao() }
    single { get<AppDatabase>().userProfileDao() }
    single { get<AppDatabase>().waterLogDao() }

    // Food catalog database — FTS5 requires the bundled SQLite driver
    //
    // Catalog schema is seeded from a bundled asset and extended by user data
    // (`is_custom = 1` rows in `catalog_foods`). A destructive fallback would
    // re-copy or blank the asset on a version bump and kill those custom foods.
    // A future version bump MUST ship an explicit `Migration` in
    // `CatalogMigrations.ALL` — `CatalogCustomFoodPreserver` does the
    // preservation work there (story 6.3 / deferred-work 3.1-F1).
    //
    // The main database builder above KEEPS `fallbackToDestructiveMigration()`
    // (project-context rule; do not "clean it up" here).
    single {
        Room.databaseBuilder(get(), FoodCatalogDatabase::class.java, "food_catalog")
            .setDriver(BundledSQLiteDriver())
            .createFromAsset("databases/food_catalog.db")
            .addMigrations(*CatalogMigrations.ALL)
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
    single<RecipeRepository> { RecipeRepositoryImpl(get(), get()) }
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
    factory { SaveRecipeUseCase(get()) }
    factory { GetRecipesUseCase(get()) }
    factory { DeleteRecipeUseCase(get()) }
    factory { LogFoodEntryUseCase(get()) }
    factory { RestoreMealEntryUseCase(get()) }
    factory { LogRecipeUseCase(get(), get()) }
    factory { ExportLogsUseCase(get()) }
    factory { BackupDatabaseUseCase() }

    // Infrastructure
    single { LogExportFileWriter(androidContext()) }
    single { DatabaseBackupManager(androidContext(), get(), get()) }

    // ViewModels
    viewModel { MealViewModel(get(), get(), get(), get()) }
    viewModel { ProfileSetupViewModel(get()) }
    viewModel { PasscodeAuthViewModel(get(), get(), get()) }
    viewModel { DashboardViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { RecipeViewModel(get(), get(), get(), get()) }
    viewModel { FoodSearchViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
}
