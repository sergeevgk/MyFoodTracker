package com.example.myfoodtracker.data.repository

import com.example.myfoodtracker.data.dao.FoodCatalogDao
import com.example.myfoodtracker.data.dao.FtsQueryBuilder
import com.example.myfoodtracker.data.db.FoodSearchRow
import com.example.myfoodtracker.data.entity.CatalogFoodEntity
import com.example.myfoodtracker.data.entity.CatalogFoodNutrientEntity
import com.example.myfoodtracker.domain.model.FoodItem
import com.example.myfoodtracker.domain.repository.FoodCatalogRepository

class FoodCatalogRepositoryImpl(
    private val foodCatalogDao: FoodCatalogDao
) : FoodCatalogRepository {

    override fun search(query: String, limit: Int): List<FoodItem> {
        val matchQuery = FtsQueryBuilder.build(query) ?: return emptyList()
        val clampedLimit = limit.coerceIn(1, 50)
        return foodCatalogDao.search(matchQuery, clampedLimit).map { it.toDomain() }
    }

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
        // Writes go through catalog_foods only; Room's room_fts_content_sync_*
        // triggers index the row in catalog_foods_fts immediately.
        val now = System.currentTimeMillis()
        val id = foodCatalogDao.insertFoodWithNutrients(
            CatalogFoodEntity(
                name = name,
                brand = brand,
                barcode = null,
                baseServingSize = baseServingSize,
                baseServingUnit = baseServingUnit,
                isCustom = 1,
                isDeleted = 0,
                createdAt = now,
                updatedAt = now
            ),
            CatalogFoodNutrientEntity(
                foodId = 0,
                calories = calories,
                proteinG = proteinG,
                carbsG = carbsG,
                fatG = fatG,
                fiberG = fiberG,
                sugarG = sugarG,
                sodiumMg = sodiumMg
            )
        )
        return FoodItem(
            id = id,
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

    private fun FoodSearchRow.toDomain(): FoodItem {
        return FoodItem(
            id = id,
            name = name,
            brand = brand,
            barcode = barcode,
            isCustom = isCustom != 0,
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
