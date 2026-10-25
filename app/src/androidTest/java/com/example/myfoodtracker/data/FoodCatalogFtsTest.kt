package com.example.myfoodtracker.data

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.example.myfoodtracker.data.dao.FtsQueryBuilder
import com.example.myfoodtracker.data.db.FoodCatalogDatabase
import com.example.myfoodtracker.data.db.RestoredCustomFood
import com.example.myfoodtracker.data.entity.CatalogFoodEntity
import com.example.myfoodtracker.data.entity.CatalogFoodNutrientEntity
import com.example.myfoodtracker.data.entity.CatalogFoodServingUnitEntity
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FoodCatalogFtsTest {

    private lateinit var db: FoodCatalogDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Start from a clean copy so createFromAsset re-copies the bundled asset.
        context.getDatabasePath("food_catalog").delete()
        db = Room.databaseBuilder(context, FoodCatalogDatabase::class.java, "food_catalog")
            .setDriver(BundledSQLiteDriver())
            .createFromAsset("databases/food_catalog.db")
            .fallbackToDestructiveMigration()
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getDatabasePath("food_catalog").delete()
        context.getDatabasePath("food_catalog-wal").delete()
        context.getDatabasePath("food_catalog-shm").delete()
    }

    @Test
    fun assetCopy_containsAtLeast50000Foods() {
        assertTrue(
            "expected >= 50000 foods, got ${db.foodCatalogDao().countFoods()}",
            db.foodCatalogDao().countFoods() >= 50000
        )
    }

    @Test
    fun prefixSearch_returnsMatchingFoods() {
        val match = FtsQueryBuilder.build("chick bre")!!
        val results = db.foodCatalogDao().search(match, 30)

        assertTrue("expected prefix matches for 'chick bre'", results.isNotEmpty())
        assertTrue(results.all { it.name.isNotBlank() })
    }

    @Test
    fun outOfOrderTerms_returnSameFoods() {
        val forward = db.foodCatalogDao().search(FtsQueryBuilder.build("chicken breast")!!, 50)
        val reversed = db.foodCatalogDao().search(FtsQueryBuilder.build("breast chicken")!!, 50)

        assertTrue(forward.isNotEmpty())
        assertEquals(forward.map { it.id }, reversed.map { it.id })
    }

    @Test
    fun prefixSearch_averageLatencyUnder50ms() {
        val dao = db.foodCatalogDao()
        val queries = listOf("chick bre", "apple", "milk choc")
            .map { FtsQueryBuilder.build(it)!! }
        // Warm-up before timing.
        repeat(10) { dao.search(queries[it % queries.size], 30) }

        var totalNs = 0L
        var maxNs = 0L
        val iterations = 120
        repeat(iterations) { i ->
            val start = System.nanoTime()
            dao.search(queries[i % queries.size], 30)
            val elapsed = System.nanoTime() - start
            totalNs += elapsed
            if (elapsed > maxNs) maxNs = elapsed
        }
        val avgMs = totalNs / 1_000_000.0 / iterations
        println("FoodCatalogFtsTest latency: avg=${"%.2f".format(avgMs)}ms max=${"%.2f".format(maxNs / 1_000_000.0)}ms")
        assertTrue("average query latency ${avgMs}ms exceeds 50ms", avgMs < 50.0)
    }

    @Test
    fun insertAndDelete_syncFtsIndexViaTriggers() {
        // In-memory instance isolates the trigger test from the asset copy.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val mem = Room.inMemoryDatabaseBuilder(context, FoodCatalogDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .allowMainThreadQueries()
            .build()
        try {
            val dao = mem.foodCatalogDao()
            val id = dao.insertFoodWithNutrients(
                CatalogFoodEntity(
                    name = "Zzx Trigger Probe Food",
                    brand = "Probe Brand",
                    barcode = null,
                    isCustom = 1
                ),
                CatalogFoodNutrientEntity(foodId = 0, calories = 10.0)
            )
            val match = FtsQueryBuilder.build("zzx probe")!!
            assertTrue(dao.search(match, 10).any { it.id == id })

            dao.deleteFoodById(id)
            assertTrue(dao.search(match, 10).none { it.id == id })
        } finally {
            mem.close()
        }
    }

    /**
     * The AC #5 round-trip proof point (story 6.3): a wipe & restore through
     * `replaceCustomFoods` brings back the foods, their nutrient children AND
     * the serving-unit rows of format v2 — all pointing at the **new** food ids.
     * Compiled per policy; executed on device manually (no-emulator policy).
     */
    @Test
    fun replaceCustomFoods_roundTripsFoodsNutrientsAndServingUnits() {
        val dao = db.foodCatalogDao()
        val restored = listOf(
            RestoredCustomFood(
                food = CatalogFoodEntity(
                    name = "Zzx Restore Probe Food",
                    brand = "Probe Brand",
                    barcode = null,
                    isCustom = 1
                ),
                nutrient = CatalogFoodNutrientEntity(
                    foodId = 0,
                    calories = 400.0,
                    proteinG = 80.0,
                    carbsG = 8.0,
                    fatG = 5.0,
                    fiberG = 1.0,
                    sugarG = 3.0,
                    sodiumMg = 300.0
                ),
                sourceId = 3001L,
                servingUnits = listOf(
                    CatalogFoodServingUnitEntity(
                        foodId = 0, unitName = "scoop", gramsPerUnit = 30.0
                    ),
                    CatalogFoodServingUnitEntity(
                        foodId = 0, unitName = "glas", gramsPerUnit = 250.0
                    )
                )
            )
        )

        val mapping = dao.replaceCustomFoods(restored)

        assertEquals(1, mapping.size)
        assertEquals(3001L, mapping[0].first)
        val newId = mapping[0].second

        val foods = dao.getCustomFoods()
        assertEquals(1, foods.size)
        assertEquals(newId, foods[0].id)
        assertEquals("Zzx Restore Probe Food", foods[0].name)

        val nutrients = dao.getCustomFoodNutrients()
        assertEquals(1, nutrients.size)
        assertEquals(newId, nutrients[0].foodId)
        assertEquals(400.0, nutrients[0].calories, 0.001)

        val units = dao.getCustomFoodServingUnits()
        assertEquals(2, units.size)
        assertTrue("all units re-attached to the new food id", units.all { it.foodId == newId })
        assertEquals(
            setOf("scoop" to 30.0, "glas" to 250.0),
            units.map { it.unitName to it.gramsPerUnit }.toSet()
        )

        // Cleanup: the probe rows must not linger in the asset-backed database.
        dao.deleteFoodById(newId)
    }
}
