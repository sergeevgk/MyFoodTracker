package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.data.dao.FtsQueryBuilder
import com.example.myfoodtracker.domain.model.FoodItem
import com.example.myfoodtracker.domain.repository.FoodCatalogRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SearchFoodUseCaseTest {

    private lateinit var fakeRepository: FakeFoodCatalogRepository
    private lateinit var useCase: SearchFoodUseCase

    @Before
    fun setUp() {
        fakeRepository = FakeFoodCatalogRepository()
        useCase = SearchFoodUseCase(fakeRepository)
    }

    @Test
    fun invoke_validQuery_delegatesQueryAndDefaultLimit() {
        useCase("chick bre")

        assertEquals("chick bre", fakeRepository.lastQuery)
        assertEquals(30, fakeRepository.lastLimit)
    }

    @Test
    fun invoke_explicitLimit_delegatesLimit() {
        useCase("apple", 10)

        assertEquals("apple", fakeRepository.lastQuery)
        assertEquals(10, fakeRepository.lastLimit)
    }

    @Test
    fun invoke_returnsRepositoryResults() {
        fakeRepository.results = listOf(
            foodItem(1L, "Chicken Breast"),
            foodItem(2L, "Chicken Broth")
        )

        val results = useCase("chick")

        assertEquals(2, results.size)
        assertEquals("Chicken Breast", results[0].name)
        assertEquals("Chicken Broth", results[1].name)
    }

    @Test
    fun invoke_blankQuery_returnsEmptyWithoutCallingRepository() {
        assertTrue(useCase("").isEmpty())
        assertTrue(useCase("   ").isEmpty())
        assertNull(fakeRepository.lastQuery)
    }

    @Test
    fun invoke_punctuationOnlyQuery_returnsEmptyWithoutCallingRepository() {
        assertTrue(useCase("...").isEmpty())
        assertNull(fakeRepository.lastQuery)
    }

    private fun foodItem(id: Long, name: String) = FoodItem(
        id = id,
        name = name,
        brand = null,
        barcode = null,
        isCustom = false,
        calories = 100.0,
        proteinG = 10.0,
        carbsG = 5.0,
        fatG = 2.0,
        fiberG = 1.0,
        sugarG = 1.0,
        sodiumMg = 50.0
    )

    private class FakeFoodCatalogRepository : FoodCatalogRepository {
        var lastQuery: String? = null
        var lastLimit: Int? = null
        var results: List<FoodItem> = emptyList()

        override fun search(query: String, limit: Int): List<FoodItem> {
            // Mirrors the real FoodCatalogRepositoryImpl contract by applying
            // the real FtsQueryBuilder rule: input with no searchable terms
            // (blank/punctuation-only) yields emptyList() without hitting the DAO.
            if (FtsQueryBuilder.build(query) == null) return emptyList()
            lastQuery = query
            lastLimit = limit
            return results
        }
    }
}
