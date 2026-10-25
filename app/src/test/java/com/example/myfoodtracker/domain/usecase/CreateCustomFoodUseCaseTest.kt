package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.FoodItem
import com.example.myfoodtracker.domain.repository.FoodCatalogRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CreateCustomFoodUseCaseTest {

    private lateinit var fakeRepository: FakeFoodCatalogRepository
    private lateinit var useCase: CreateCustomFoodUseCase

    @Before
    fun setUp() {
        fakeRepository = FakeFoodCatalogRepository()
        useCase = CreateCustomFoodUseCase(fakeRepository)
    }

    @Test
    fun invoke_validInput_persistsCustomWithTrimmedNameAndBrand() {
        val item = useCase(
            name = "  MyProtein Whey  ",
            brand = "  MyProtein  ",
            baseServingSize = 30.0,
            baseServingUnit = "g",
            calories = 120.0,
            proteinG = 24.0,
            carbsG = 3.0,
            fatG = 1.0
        )

        assertEquals("MyProtein Whey", fakeRepository.lastName)
        assertEquals("MyProtein", fakeRepository.lastBrand)
        assertTrue(item.isCustom)
        assertEquals(99L, item.id)
        assertEquals("MyProtein Whey", item.name)
    }

    @Test
    fun invoke_blankBrand_preservesNull() {
        useCase(
            name = "Oats",
            brand = "   ",
            baseServingSize = 100.0,
            baseServingUnit = "g",
            calories = 350.0,
            proteinG = 10.0,
            carbsG = 60.0,
            fatG = 5.0
        )

        assertNull(fakeRepository.lastBrand)
    }

    @Test
    fun invoke_blankServingUnit_defaultsToGrams() {
        useCase(
            name = "Oats",
            brand = null,
            baseServingSize = 100.0,
            baseServingUnit = "  ",
            calories = 350.0,
            proteinG = 10.0,
            carbsG = 60.0,
            fatG = 5.0
        )

        assertEquals("g", fakeRepository.lastServingUnit)
    }

    @Test
    fun invoke_optionalMacros_defaultToZero() {
        val item = useCase(
            name = "Oats",
            brand = null,
            baseServingSize = 100.0,
            baseServingUnit = "g",
            calories = 350.0,
            proteinG = 10.0,
            carbsG = 60.0,
            fatG = 5.0
        )

        assertEquals(0.0, item.fiberG, 0.001)
        assertEquals(0.0, item.sugarG, 0.001)
        assertEquals(0.0, item.sodiumMg, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_blankName_throws() {
        useCase(
            name = "   ",
            brand = null,
            baseServingSize = 100.0,
            baseServingUnit = "g",
            calories = 100.0,
            proteinG = 10.0,
            carbsG = 5.0,
            fatG = 2.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_zeroServingSize_throws() {
        useCase(
            name = "Oats",
            brand = null,
            baseServingSize = 0.0,
            baseServingUnit = "g",
            calories = 100.0,
            proteinG = 10.0,
            carbsG = 5.0,
            fatG = 2.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_negativeServingSize_throws() {
        useCase(
            name = "Oats",
            brand = null,
            baseServingSize = -30.0,
            baseServingUnit = "g",
            calories = 100.0,
            proteinG = 10.0,
            carbsG = 5.0,
            fatG = 2.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_nanServingSize_throws() {
        useCase(
            name = "Oats",
            brand = null,
            baseServingSize = Double.NaN,
            baseServingUnit = "g",
            calories = 100.0,
            proteinG = 10.0,
            carbsG = 5.0,
            fatG = 2.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_negativeMacro_throws() {
        useCase(
            name = "Oats",
            brand = null,
            baseServingSize = 100.0,
            baseServingUnit = "g",
            calories = -10.0,
            proteinG = 10.0,
            carbsG = 5.0,
            fatG = 2.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_nanMacro_throws() {
        useCase(
            name = "Oats",
            brand = null,
            baseServingSize = 100.0,
            baseServingUnit = "g",
            calories = 100.0,
            proteinG = Double.NaN,
            carbsG = 5.0,
            fatG = 2.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_infiniteMacro_throws() {
        useCase(
            name = "Oats",
            brand = null,
            baseServingSize = 100.0,
            baseServingUnit = "g",
            calories = 100.0,
            proteinG = 10.0,
            carbsG = Double.POSITIVE_INFINITY,
            fatG = 2.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_nameTooLong_throws() {
        useCase(
            name = "A".repeat(101),
            brand = null,
            baseServingSize = 100.0,
            baseServingUnit = "g",
            calories = 100.0,
            proteinG = 10.0,
            carbsG = 20.0,
            fatG = 2.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_brandTooLong_throws() {
        useCase(
            name = "Oats",
            brand = "B".repeat(101),
            baseServingSize = 100.0,
            baseServingUnit = "g",
            calories = 100.0,
            proteinG = 10.0,
            carbsG = 20.0,
            fatG = 2.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_unsupportedUnit_throws() {
        useCase(
            name = "Oats",
            brand = null,
            baseServingSize = 100.0,
            baseServingUnit = "gallons",
            calories = 100.0,
            proteinG = 10.0,
            carbsG = 20.0,
            fatG = 2.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_servingSizeExceedsMax_throws() {
        useCase(
            name = "Oats",
            brand = null,
            baseServingSize = 200_000.0,
            baseServingUnit = "g",
            calories = 100.0,
            proteinG = 10.0,
            carbsG = 20.0,
            fatG = 2.0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invoke_macroExceedsMax_throws() {
        useCase(
            name = "Oats",
            brand = null,
            baseServingSize = 100.0,
            baseServingUnit = "g",
            calories = 150_000.0,
            proteinG = 10.0,
            carbsG = 20.0,
            fatG = 2.0
        )
    }

    private class FakeFoodCatalogRepository : FoodCatalogRepository {
        var lastName: String? = null
        var lastBrand: String? = null
        var lastServingUnit: String? = null

        override fun search(query: String, limit: Int): List<FoodItem> = emptyList()

        override fun createCustomFood(
            name: String,
            brand: String?,
            baseServingSize: Double,
            baseServingUnit: String,
            calories: Double,
            proteinG: Double,
            carbsG: Double,
            fatG: Double,
            fiberG: Double,
            sugarG: Double,
            sodiumMg: Double
        ): FoodItem {
            lastName = name
            lastBrand = brand
            lastServingUnit = baseServingUnit
            return FoodItem(
                id = 99L,
                name = name,
                brand = brand,
                barcode = null,
                isCustom = true,
                calories = calories,
                proteinG = proteinG,
                carbsG = carbsG,
                fatG = fatG,
                fiberG = fiberG,
                sugarG = sugarG,
                sodiumMg = sodiumMg,
                baseServingSize = baseServingSize,
                baseServingUnit = baseServingUnit
            )
        }
    }
}
