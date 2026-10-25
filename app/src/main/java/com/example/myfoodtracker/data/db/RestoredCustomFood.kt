package com.example.myfoodtracker.data.db

import com.example.myfoodtracker.data.entity.CatalogFoodEntity
import com.example.myfoodtracker.data.entity.CatalogFoodNutrientEntity
import com.example.myfoodtracker.data.entity.CatalogFoodServingUnitEntity

/**
 * Pure data row (FoodSearchRow precedent — flat transport, data layer only): a
 * custom food being re-inserted into the catalog during a restore, together
 * with the **backup-side** catalog id (`source_catalog_id`) its
 * `recipe_ingredients` rows reference. The restore path builds the old→new
 * remap from it. `sourceId` is `null` when a legacy pre-6.2 backup carries no
 * ids — the link then degrades to the dangling sweep.
 *
 * `servingUnits` carries the food's `catalog_food_serving_units` rows since
 * format v2 (story 6.3) — empty for a v1 file; inserted inside the same
 * `replaceCustomFoods` transaction as the food itself.
 */
data class RestoredCustomFood(
    val food: CatalogFoodEntity,
    val nutrient: CatalogFoodNutrientEntity,
    val sourceId: Long?,
    val servingUnits: List<CatalogFoodServingUnitEntity> = emptyList()
)
