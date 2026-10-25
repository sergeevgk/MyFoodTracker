package com.example.myfoodtracker

import android.app.Application
import android.util.Log
import com.example.myfoodtracker.data.backup.PendingRestoreApplier
import com.example.myfoodtracker.data.dao.FoodCatalogDao
import com.example.myfoodtracker.di.appModule
import com.example.myfoodtracker.domain.model.CustomFoodBackup
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // A staged restore has to be applied BEFORE Koin starts, i.e. before
        // Room opens `meals_database`. The applier is therefore constructed
        // directly — it is intentionally not a Koin binding (AD-6 exception).
        val restoreApplier = PendingRestoreApplier(this)
        val restoredCustomFoods = applyPendingRestore(restoreApplier)

        val koinApplication = startKoin {
            androidLogger()
            androidContext(this@MainApplication)
            modules(appModule)
        }

        // The restored custom foods belong to the catalog database, which needs
        // FTS5 and is therefore only reachable through the Room DAO. The DAO is
        // resolved only when a restore actually occurred (even if carrying 0 custom
        // foods, so pre-existing custom foods are replaced per AC #7), so a normal cold
        // start never opens (and on the first run never copies) the catalog.
        if (restoredCustomFoods != null) {
            try {
                restoreApplier.applyCustomFoods(
                    koinApplication.koin.get<FoodCatalogDao>(),
                    restoredCustomFoods
                )
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Could not restore the backed-up custom foods", e)
            }
        }
    }

    /** A failing restore application must never crash the app on startup. */
    private fun applyPendingRestore(restoreApplier: PendingRestoreApplier): List<CustomFoodBackup>? =
        try {
            restoreApplier.applyPendingDatabaseSwap()
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Could not apply the pending database restore", e)
            null
        }

    companion object {
        private const val LOG_TAG = "MainApplication"
    }
}
