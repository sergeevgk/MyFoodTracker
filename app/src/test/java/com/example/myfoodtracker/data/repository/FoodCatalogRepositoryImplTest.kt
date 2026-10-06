package com.example.myfoodtracker.data.repository

import com.example.myfoodtracker.data.dao.FoodCatalogDao
import com.example.myfoodtracker.data.db.FoodSearchRow
import com.example.myfoodtracker.data.entity.CatalogFoodEntity
import com.example.myfoodtracker.data.entity.CatalogFoodNutrientEntity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class FoodCatalogRepositoryImplTest {

    private lateinit var fakeDao: FakeFoodCatalogDao
    private lateinit var repository: FoodCatalogRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeFoodCatalogDao()
        repository = FoodCatalogRepositoryImpl(fakeDao)
    }

    @Test
    fun search_twoTerms_buildsPerTermPrefixMatch() {
        repository.search("chick bre")

        assertEquals("\"chick\"* \"bre\"*", fakeDao.lastMatchQuery)
    }

    @Test
    fun search_outOfOrderTerms_buildsMatchInGivenOrder() {
        repository.search("breast chicken")

        assertEquals("\"breast\"* \"chicken\"*", fakeDao.lastMatchQuery)
    }

    @Test
    fun search_embeddedQuote_escapesByDoubling() {
        repository.search("a\"b")

        assertEquals("\"a\"\"b\"*", fakeDao.lastMatchQuery)
    }

    @Test
    fun search_blankQuery_returnsEmptyWithoutCallingDao() {
        assertTrue(repository.search("").isEmpty())
        assertTrue(repository.search("   ").isEmpty())
        assertFalse(fakeDao.searchCalled)
    }

    @Test
    fun search_punctuationOnlyQuery_returnsEmptyWithoutCallingDao() {
        assertTrue(repository.search("... ***").isEmpty())
        assertFalse(fakeDao.searchCalled)
    }

    @Test
    fun search_limitZero_clampsToOne() {
        repository.search("apple", 0)

        assertEquals(1, fakeDao.lastLimit)
    }

    @Test
    fun search_limitAboveFifty_clampsToFifty() {
        repository.search("apple", 500)

        assertEquals(50, fakeDao.lastLimit)
    }

    @Test
    fun search_limitInRange_passesThrough() {
        repository.search("apple", 10)

        assertEquals(10, fakeDao.lastLimit)
    }

    @Test
    fun search_mapsRowToDomainModel() {
        fakeDao.rows = listOf(
            FoodSearchRow(
                id = 7L,
                name = "Chicken Breast",
                brand = "Brand X",
                barcode = "0123456789",
                isCustom = 1,
                calories = 120.0,
                proteinG = 23.0,
                carbsG = 0.5,
                fatG = 2.0,
                fiberG = 0.0,
                sugarG = 0.0,
                sodiumMg = 70.0
            )
        )

        val results = repository.search("chicken")

        assertEquals(1, results.size)
        val item = results[0]
        assertEquals(7L, item.id)
        assertEquals("Chicken Breast", item.name)
        assertEquals("Brand X", item.brand)
        assertEquals("0123456789", item.barcode)
        assertTrue(item.isCustom)
        assertEquals(120.0, item.calories, 0.001)
        assertEquals(23.0, item.proteinG, 0.001)
        assertEquals(0.5, item.carbsG, 0.001)
        assertEquals(2.0, item.fatG, 0.001)
        assertEquals(0.0, item.fiberG, 0.001)
        assertEquals(0.0, item.sugarG, 0.001)
        assertEquals(70.0, item.sodiumMg, 0.001)
    }

    @Test
    fun search_mapsBaseServingFieldsToDomainModel() {
        fakeDao.rows = listOf(
            FoodSearchRow(
                id = 7L, name = "Chicken Breast", brand = "Brand X", barcode = null,
                isCustom = 0, calories = 120.0, proteinG = 23.0, carbsG = 0.5,
                fatG = 2.0, fiberG = 0.0, sugarG = 0.0, sodiumMg = 70.0,
                baseServingSize = 150.0, baseServingUnit = "g"
            )
        )

        val results = repository.search("chicken")

        assertEquals(1, results.size)
        assertEquals(150.0, results[0].baseServingSize, 0.001)
        assertEquals("g", results[0].baseServingUnit)
    }

    @Test
    fun search_rowWithoutServingFields_defaultsTo100g() {
        fakeDao.rows = listOf(
            FoodSearchRow(
                id = 1L, name = "Apple", brand = null, barcode = null,
                isCustom = 0, calories = 52.0, proteinG = 0.3, carbsG = 14.0,
                fatG = 0.2, fiberG = 2.4, sugarG = 10.0, sodiumMg = 1.0
            )
        )

        val results = repository.search("apple")

        assertEquals(100.0, results[0].baseServingSize, 0.001)
        assertEquals("g", results[0].baseServingUnit)
    }

    @Test
    fun search_nonCustomRow_mapsIsCustomFalse() {
        fakeDao.rows = listOf(
            FoodSearchRow(
                id = 1L, name = "Apple", brand = null, barcode = null,
                isCustom = 0, calories = 52.0, proteinG = 0.3, carbsG = 14.0,
                fatG = 0.2, fiberG = 2.4, sugarG = 10.0, sodiumMg = 1.0
            )
        )

        val results = repository.search("apple")

        assertEquals(1, results.size)
        assertFalse(results[0].isCustom)
        assertNull(results[0].brand)
        assertNull(results[0].barcode)
    }

    @Test
    fun createCustomFood_persistsCustomFlagViaInsertTransaction() {
        val item = repository.createCustomFood(
            name = "MyProtein Whey",
            brand = "MyProtein",
            baseServingSize = 30.0,
            baseServingUnit = "g",
            calories = 120.0,
            proteinG = 24.0,
            carbsG = 3.0,
            fatG = 1.0
        )

        assertEquals(77L, item.id)
        assertTrue(item.isCustom)
        assertEquals("MyProtein Whey", item.name)
        assertEquals("MyProtein", item.brand)
        assertEquals(1, fakeDao.lastInsertedFood?.isCustom)
        assertEquals("MyProtein Whey", fakeDao.lastInsertedFood?.name)
        assertNull(fakeDao.lastInsertedFood?.barcode)
        assertEquals(0, fakeDao.lastInsertedFood?.isDeleted)
    }

    @Test
    fun createCustomFood_linksNutrientsToGeneratedId() {
        repository.createCustomFood(
            name = "Oats",
            brand = null,
            baseServingSize = 100.0,
            baseServingUnit = "g",
            calories = 350.0,
            proteinG = 10.0,
            carbsG = 60.0,
            fatG = 5.0,
            fiberG = 8.0,
            sugarG = 1.0,
            sodiumMg = 5.0
        )

        assertEquals(77L, fakeDao.lastInsertedNutrients?.foodId)
        assertEquals(350.0, fakeDao.lastInsertedNutrients!!.calories, 0.001)
        assertEquals(8.0, fakeDao.lastInsertedNutrients!!.fiberG, 0.001)
    }

    /** Hand-written fake mirroring FoodCatalogDao behavior for search/count. */
    private class FakeFoodCatalogDao : FoodCatalogDao {
        var rows: List<FoodSearchRow> = emptyList()
        var searchCalled = false
        var lastMatchQuery: String? = null
        var lastLimit: Int? = null
        var lastInsertedFood: CatalogFoodEntity? = null
        var lastInsertedNutrients: CatalogFoodNutrientEntity? = null

        override fun search(matchQuery: String, limit: Int): List<FoodSearchRow> {
            searchCalled = true
            lastMatchQuery = matchQuery
            lastLimit = limit
            return rows.take(limit)
        }

        override fun countFoods(): Int = rows.size

        override fun insertFood(food: CatalogFoodEntity): Long {
            lastInsertedFood = food
            return 77L
        }

        override fun insertNutrients(nutrients: CatalogFoodNutrientEntity) {
            lastInsertedNutrients = nutrients
        }

        override fun insertFoodWithNutrients(
            food: CatalogFoodEntity,
            nutrients: CatalogFoodNutrientEntity
        ): Long {
            val id = insertFood(food)
            insertNutrients(nutrients.copy(foodId = id))
            return id
        }

        override fun deleteFoodById(foodId: Long) = Unit
    }
}
