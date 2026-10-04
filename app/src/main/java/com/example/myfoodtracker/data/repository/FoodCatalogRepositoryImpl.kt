package com.example.myfoodtracker.data.repository

import com.example.myfoodtracker.data.dao.FoodCatalogDao
import com.example.myfoodtracker.data.dao.FtsQueryBuilder
import com.example.myfoodtracker.data.db.FoodSearchRow
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
