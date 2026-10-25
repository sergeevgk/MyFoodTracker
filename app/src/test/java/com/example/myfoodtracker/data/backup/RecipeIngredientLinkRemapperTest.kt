package com.example.myfoodtracker.data.backup

import com.example.myfoodtracker.data.dao.FoodCatalogDao
import com.example.myfoodtracker.data.db.FoodSearchRow
import com.example.myfoodtracker.data.db.RecipeDao
import com.example.myfoodtracker.data.db.RecipeIngredientLinkRow
import com.example.myfoodtracker.data.db.RecipeWithIngredients
import com.example.myfoodtracker.data.db.RecipeEntity
import com.example.myfoodtracker.data.db.RecipeIngredientEntity
import com.example.myfoodtracker.data.entity.CatalogFoodEntity
import com.example.myfoodtracker.data.entity.CatalogFoodNutrientEntity
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * JVM tests for the restore-time catalog-link remap (story 6.2, AC #1–#4).
 * Hand-written fakes, no MockK / Robolectric / Room — the remapper is pure
 * orchestration over the two DAOs. The fakes APPLY the DAO semantics so the
 * final row state can be asserted row-by-row (row-id asserted, not count).
 *
 * Red→green note: `RecipeIngredientLinkRemapper` is a new class — no pre-task
 * code existed for these tests to fail against, so the suite is a new-file
 * pure addition (justified per the story's test plan).
 */
class RecipeIngredientLinkRemapperTest {

    private lateinit var fakeRecipeDao: FakeRecipeDao
    private lateinit var fakeCatalogDao: FakeFoodCatalogDao
    private lateinit var remapper: RecipeIngredientLinkRemapper

    @Before
    fun setUp() {
        fakeRecipeDao = FakeRecipeDao()
        fakeCatalogDao = FakeFoodCatalogDao()
        remapper = RecipeIngredientLinkRemapper(fakeRecipeDao, fakeCatalogDao)
    }

    @Test
    fun remapAfterRestore_reallocatedFood_rewritesExistingRows() {
        fakeRecipeDao.links(1L to 3001L, 2L to 3002L)
        val mapping = listOf(3001L to 9001L, 3002L to 9002L)

        remapper.remapAfterRestore(mapping)

        // Exact final value per row id, not a count.
        assertEquals(9001L, fakeRecipeDao.linkOf(1L))
        assertEquals(9002L, fakeRecipeDao.linkOf(2L))
    }

    @Test
    fun remapAfterRestore_chainScenario_neverDoubleMaps() {
        // Row 1 referenced old custom food 3001, row 2 referenced 3050.
        // The re-allocated id for 3001 happens to be 3050 (different seed row
        // counts across catalog assets make this coincidental equality legal).
        fakeRecipeDao.links(1L to 3001L, 2L to 3050L)
        val mapping = listOf(3001L to 3050L, 3050L to 3060L)

        remapper.remapAfterRestore(mapping)

        // Snapshot capture yields {id1→3050, id2→3060}; a naive sequential
        // `WHERE catalog_food_id = :old` implementation would produce
        // {id1→3060, id2→3060} (AC #4 regression guard).
        assertEquals(3050L, fakeRecipeDao.linkOf(1L))
        assertEquals(3060L, fakeRecipeDao.linkOf(2L))
    }

    @Test
    fun remapAfterRestore_danglingReference_setToNull() {
        fakeRecipeDao.links(1L to 9999L)
        fakeCatalogDao.existingFoodIds = setOf() // 9999 no longer exists

        remapper.remapAfterRestore(mapping = emptyList())

        assertEquals(null, fakeRecipeDao.linkOrNull(1L))
    }

    @Test
    fun remapAfterRestore_validSeededReference_leftUntouched() {
        fakeRecipeDao.links(1L to 5L)
        fakeCatalogDao.existingFoodIds = setOf(5L) // seeded food exists in the rebuilt catalog

        remapper.remapAfterRestore(mapping = emptyList())

        assertEquals(5L, fakeRecipeDao.linkOf(1L))
        assertEquals(0, fakeRecipeDao.updateCalls)
        assertEquals(0, fakeRecipeDao.clearCalls)
        assertEquals(0, fakeRecipeDao.remapAndClearCalls)
    }

    @Test
    fun remapAfterRestore_legacyBackupWithoutIds_danglingNulledSeededKept() {
        // Empty mapping: a legacy pre-6.2 backup carries no ids, so every link
        // is decided only by the dangling sweep.
        fakeRecipeDao.links(1L to 9999L, 2L to 5L)
        fakeCatalogDao.existingFoodIds = setOf(5L)

        remapper.remapAfterRestore(mapping = emptyList())

        assertEquals(null, fakeRecipeDao.linkOrNull(1L))
        assertEquals(5L, fakeRecipeDao.linkOf(2L))
    }

    @Test
    fun remapAfterRestore_noLinkedIngredients_makesNoWrites() {
        // No links → early-out; zero DAO writes of any kind.
        remapper.remapAfterRestore(mapping = listOf(3001L to 9001L))

        assertEquals(0, fakeRecipeDao.updateCalls)
        assertEquals(0, fakeRecipeDao.clearCalls)
        assertEquals(0, fakeRecipeDao.remapAndClearCalls)
        assertEquals(0, fakeCatalogDao.getExistingFoodIdsCalls)
    }

    @Test
    fun remapAfterRestore_mappingMatchesAllocatedId_noOp() {
        // Same device + same catalog asset ⇒ the re-allocation repeats the
        // backed-up id. The row is decided without any write and the dangling
        // sweep must not fire for it.
        fakeRecipeDao.links(1L to 3001L)
        fakeCatalogDao.existingFoodIds = setOf(3001L, 9999L) // decoys must not be probed

        remapper.remapAfterRestore(mapping = listOf(3001L to 3001L))

        assertEquals(3001L, fakeRecipeDao.linkOf(1L))
        assertEquals(0, fakeRecipeDao.updateCalls)
        assertEquals(0, fakeRecipeDao.clearCalls)
        assertEquals(0, fakeRecipeDao.remapAndClearCalls)
        assertEquals(0, fakeCatalogDao.getExistingFoodIdsCalls)
    }

    @Test
    fun remapAfterRestore_mixedRemapDanglingAndSeeded_allDecidedInOnePass() {
        fakeRecipeDao.links(1L to 3001L, 2L to 9999L, 3L to 5L)
        fakeCatalogDao.existingFoodIds = setOf(5L)
        val mapping = listOf(3001L to 9001L)

        remapper.remapAfterRestore(mapping)

        assertEquals(9001L, fakeRecipeDao.linkOf(1L))
        assertEquals(null, fakeRecipeDao.linkOrNull(2L))
        assertEquals(5L, fakeRecipeDao.linkOf(3L))
    }

    // --- Fakes -------------------------------------------------------------------------

    /** RecipeDao fake that applies the remap primitives to link state. */
    class FakeRecipeDao : RecipeDao {
        /** Row id → current `catalog_food_id` (null once cleared). */
        val linksById = mutableMapOf<Long, Long?>()

        var updateCalls = 0
        var clearCalls = 0
        var remapAndClearCalls = 0

        fun links(vararg pairs: Pair<Long, Long>) {
            pairs.forEach { (id, foodId) -> linksById[id] = foodId }
        }

        fun linkOf(id: Long): Long = requireNotNull(linksById[id]) { "row $id was removed" }
        fun linkOrNull(id: Long): Long? = linksById[id]

        override fun getIngredientsWithCatalogLinks(): List<RecipeIngredientLinkRow> =
            linksById.entries
                .filter { it.value != null }
                .map { RecipeIngredientLinkRow(id = it.key, catalogFoodId = it.value!!) }

        override fun updateCatalogFoodLink(ingredientIds: List<Long>, newFoodId: Long) {
            updateCalls++
            ingredientIds.forEach { id ->
                require(linksById.containsKey(id)) { "update touched unknown row $id" }
                linksById[id] = newFoodId
            }
        }

        override fun clearCatalogFoodLink(ingredientIds: List<Long>) {
            clearCalls++
            ingredientIds.forEach { id ->
                require(linksById.containsKey(id)) { "clear touched unknown row $id" }
                linksById[id] = null
            }
        }

        override fun remapAndClearCatalogFoodLinks(
            remaps: Map<Long, List<Long>>,
            clearedIngredientIds: List<Long>
        ) {
            remapAndClearCalls++
            // Mirror the DAO's default body (batching is irrelevant at this size).
            remaps.forEach { (newFoodId, ids) -> updateCatalogFoodLink(ids, newFoodId) }
            if (clearedIngredientIds.isNotEmpty()) clearCatalogFoodLink(clearedIngredientIds)
        }

        // Unrelated RecipeDao members — the remapper never calls them.
        override fun insertRecipe(recipe: RecipeEntity) = TODO("not used by the remapper")
        override fun insertIngredients(ingredients: List<RecipeIngredientEntity>) =
            TODO("not used by the remapper")
        override fun getRecipesByProfileId(profileId: String): List<RecipeEntity> =
            TODO("not used by the remapper")
        override fun getIngredientsByRecipeId(recipeId: String): List<RecipeIngredientEntity> =
            TODO("not used by the remapper")
        override fun getRecipesWithIngredientsByProfileId(profileId: String): List<RecipeWithIngredients> =
            TODO("not used by the remapper")
        override fun updateRecipeName(id: String, profileId: String, name: String, updatedAt: Long) =
            TODO("not used by the remapper")
        override fun deleteRecipe(id: String, profileId: String) = TODO("not used by the remapper")
        override fun deleteIngredient(ingredientId: Long, profileId: String) =
            TODO("not used by the remapper")
    }

    /** FoodCatalogDao fake implementing only the existence probe the remapper calls. */
    class FakeFoodCatalogDao : FoodCatalogDao {
        var existingFoodIds: Set<Long> = emptySet()
        var getExistingFoodIdsCalls = 0

        override fun getExistingFoodIds(ids: List<Long>): List<Long> {
            getExistingFoodIdsCalls++
            return ids.filter { it in existingFoodIds }
        }

        override fun search(matchQuery: String, limit: Int): List<FoodSearchRow> =
            TODO("not used by the remapper")
        override fun countFoods(): Int = TODO("not used by the remapper")
        override fun insertFood(food: CatalogFoodEntity): Long = TODO("not used by the remapper")
        override fun insertNutrients(nutrients: CatalogFoodNutrientEntity) =
            TODO("not used by the remapper")
        override fun deleteFoodById(foodId: Long) = TODO("not used by the remapper")
        override fun getCustomFoods(): List<CatalogFoodEntity> = TODO("not used by the remapper")
        override fun getCustomFoodNutrients(): List<CatalogFoodNutrientEntity> =
            TODO("not used by the remapper")
        override fun deleteCustomFoods() = TODO("not used by the remapper")
    }
}
