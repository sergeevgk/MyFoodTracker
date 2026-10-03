package com.example.myfoodtracker.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.example.myfoodtracker.data.db.FoodSearchRow
import com.example.myfoodtracker.data.entity.CatalogFoodEntity
import com.example.myfoodtracker.data.entity.CatalogFoodNutrientEntity

@Dao
interface FoodCatalogDao {
    @Query(
        """
        SELECT f.id, f.name, f.brand, f.barcode, f.is_custom,
               COALESCE(n.calories, 0.0) AS calories,
               COALESCE(n.protein_g, 0.0) AS protein_g,
               COALESCE(n.carbs_g, 0.0) AS carbs_g,
               COALESCE(n.fat_g, 0.0) AS fat_g,
               COALESCE(n.fiber_g, 0.0) AS fiber_g,
               COALESCE(n.sugar_g, 0.0) AS sugar_g,
               COALESCE(n.sodium_mg, 0.0) AS sodium_mg
        FROM catalog_foods_fts
        JOIN catalog_foods AS f ON f.id = catalog_foods_fts.rowid
        LEFT JOIN catalog_food_nutrients AS n ON n.food_id = f.id
        WHERE catalog_foods_fts MATCH :matchQuery
        ORDER BY bm25(catalog_foods_fts) ASC, f.name ASC
        LIMIT :limit
        """
    )
    fun search(matchQuery: String, limit: Int): List<FoodSearchRow>

    @Query("SELECT COUNT(*) FROM catalog_foods")
    fun countFoods(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFood(food: CatalogFoodEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertNutrients(nutrients: CatalogFoodNutrientEntity)

    @Query("DELETE FROM catalog_foods WHERE id = :foodId")
    fun deleteFoodById(foodId: Long)

    @Transaction
    fun insertFoodWithNutrients(food: CatalogFoodEntity, nutrients: CatalogFoodNutrientEntity): Long {
        val id = insertFood(food)
        insertNutrients(nutrients.copy(foodId = id))
        return id
    }
}
