package com.example.myfoodtracker.data.backup

import com.example.myfoodtracker.data.dao.FoodCatalogDao
import com.example.myfoodtracker.data.db.RecipeDao

/**
 * Makes the restored `recipe_ingredients` catalog links survive the fresh id
 * allocation of a restore (resolves deferred item **5.2-D1**). Remap first —
 * the authoritative old→new mapping carried by the backup — then a dangling
 * sweep against the rebuilt catalog: any link the mapping could not confirm
 * and that no longer exists in the rebuilt catalog is nulled explicitly
 * (`catalog_food_id = NULL`, **never left dangling**). A legacy pre-6.2 backup
 * carries no ids at all, so its links rely entirely on the sweep (graceful
 * degradation, not a merge attempt).
 *
 * Chain-safety (AC #4): affected rows are captured **by primary key** before
 * any write — mapping entry A→B can therefore never re-catch rows that a later
 * entry B→C has just written, regardless of id collisions between the old and
 * re-allocated id spaces.
 *
 * Pure orchestration over the DAOs only — no `Context`, so JVM-testable with
 * fakes. Stateless and consumed immediately by [PendingRestoreApplier]; not a
 * Koin binding (same inline-construction exception as the applier itself).
 */
class RecipeIngredientLinkRemapper(
    private val recipeDao: RecipeDao,
    private val catalogDao: FoodCatalogDao
) {

    fun remapAfterRestore(mapping: List<Pair<Long, Long>>) {
        // Capture target rows BEFORE any write so mapping entry A→B can never
        // re-catch rows that a later entry B→C has just written (chain-safety).
        val links = recipeDao.getIngredientsWithCatalogLinks()
        if (links.isEmpty()) return

        val mappingByOld = mapping.associate { it }
        val remaps = linkedMapOf<Long, MutableList<Long>>() // newId → ingredient row ids
        val danglingRowsByFoodId = linkedMapOf<Long, MutableList<Long>>() // food id → ingredient row ids
        links.forEach { link ->
            val newFoodId = mappingByOld[link.catalogFoodId]
            when {
                newFoodId == null ->
                    // Unmapped: either a legacy/id-less backup reference or a
                    // valid seeded reference — decide via the rebuilt catalog.
                    // Rows are collected by food id so the existence probe can
                    // decide whole groups, but clearing happens by ROW id.
                    danglingRowsByFoodId
                        .getOrPut(link.catalogFoodId) { mutableListOf() }
                        .add(link.id)
                newFoodId != link.catalogFoodId ->
                    remaps.getOrPut(newFoodId) { mutableListOf() }.add(link.id)
                // else: same-id re-allocation (same device + same asset ⇒ the
                // allocation repeats) — the row already points at the correct,
                // freshly re-inserted food; decided without any write, and the
                // dangling sweep must not fire for it.
            }
        }

        if (danglingRowsByFoodId.isNotEmpty()) {
            // Bound-variable cap (SQLite 999 per statement): probe the distinct
            // food ids in batches — never `SELECT id FROM catalog_foods`
            // wholesale (~10k seed rows).
            val existing = danglingRowsByFoodId.keys.chunked(EXISTENCE_PROBE_BATCH)
                .flatMap { catalogDao.getExistingFoodIds(it) }
                .toSet()
            // Only the ROWS pointing at missing foods are cleared — by primary
            // key (AC #2: the link is nulled, the row's snapshots stay intact).
            val clearedIngredientIds = danglingRowsByFoodId
                .filterKeys { foodId -> foodId !in existing }
                .values
                .flatten()
            if (remaps.isNotEmpty() || clearedIngredientIds.isNotEmpty()) {
                recipeDao.remapAndClearCatalogFoodLinks(remaps, clearedIngredientIds)
            }
        } else if (remaps.isNotEmpty()) {
            recipeDao.remapAndClearCatalogFoodLinks(remaps, emptyList())
        }
    }

    private companion object {
        const val EXISTENCE_PROBE_BATCH = 500
    }
}
