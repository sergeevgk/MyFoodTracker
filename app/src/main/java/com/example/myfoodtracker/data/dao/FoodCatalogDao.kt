package com.example.myfoodtracker.data.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import com.example.myfoodtracker.data.db.FoodSearchRow
import com.example.myfoodtracker.data.db.RestoredCustomFood
import com.example.myfoodtracker.data.entity.CatalogFoodEntity
import com.example.myfoodtracker.data.entity.CatalogFoodNutrientEntity
import com.example.myfoodtracker.data.entity.CatalogFoodServingUnitEntity

@Dao
interface FoodCatalogDao {
    @Query(
        """
        SELECT f.id, f.name, f.brand, f.barcode, f.is_custom,
               f.base_serving_size, f.base_serving_unit,
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

    /**
     * Rows the user created themselves. These are the only catalog rows a
     * database backup carries; the seeded catalog is reinstallable from the
     * bundled asset.
     */
    @Query("SELECT * FROM catalog_foods WHERE is_custom = 1")
    fun getCustomFoods(): List<CatalogFoodEntity>

    @Query(
        "SELECT * FROM catalog_food_nutrients " +
            "WHERE food_id IN (SELECT id FROM catalog_foods WHERE is_custom = 1)"
    )
    fun getCustomFoodNutrients(): List<CatalogFoodNutrientEntity>

    /** Serving-unit rows of the user's own foods — carried by backup v2 (story 6.3). */
    @Query(
        "SELECT * FROM catalog_food_serving_units " +
            "WHERE food_id IN (SELECT id FROM catalog_foods WHERE is_custom = 1)"
    )
    fun getCustomFoodServingUnits(): List<CatalogFoodServingUnitEntity>

    /** Plain insert — fresh autoGenerate ids; children die with the food by the FK CASCADE. */
    @Insert
    fun insertServingUnits(units: List<CatalogFoodServingUnitEntity>)

    /**
     * Replaces the device's custom foods during a restore. The nutrient children
     * are removed by the `ON DELETE CASCADE` foreign key — never by hand.
     */
    @Query("DELETE FROM catalog_foods WHERE is_custom = 1")
    fun deleteCustomFoods()

    @Transaction
    fun insertFoodWithNutrients(food: CatalogFoodEntity, nutrients: CatalogFoodNutrientEntity): Long {
        val id = insertFood(food)
        insertNutrients(nutrients.copy(foodId = id))
        return id
    }

    /**
     * Atomically replaces the user's custom foods during a restore. Deletion of
     * old foods and insertion of all restored foods occur within a single
     * transaction. Returns the **old→new id mapping**: a re-insert
     * allocates a fresh id, and the applier needs to remap the restored
     * `recipe_ingredients` references (story 6.2) before the first recipe read.
     *
     * Since format v2 (story 6.3) each restored food's serving units are
     * inserted in the same transaction (fresh unit ids; children die with the
     * food by the FK CASCADE).
     */
    @Transaction
    fun replaceCustomFoods(foods: List<RestoredCustomFood>): List<Pair<Long, Long>> {
        deleteCustomFoods()
        val mapping = mutableListOf<Pair<Long, Long>>()
        foods.forEach { item ->
            val id = insertFood(item.food)
            insertNutrients(item.nutrient.copy(foodId = id))
            if (item.servingUnits.isNotEmpty()) {
                insertServingUnits(item.servingUnits.map { it.copy(foodId = id) })
            }
            item.sourceId?.let { mapping.add(it to id) }
        }
        return mapping
    }

    /**
     * Existence probe for the restore-time dangling sweep: used with the
     * (small) candidate id list — never load the whole ~10k-row seed id space.
     */
    @Query("SELECT id FROM catalog_foods WHERE id IN (:ids)")
    fun getExistingFoodIds(ids: List<Long>): List<Long>
}
