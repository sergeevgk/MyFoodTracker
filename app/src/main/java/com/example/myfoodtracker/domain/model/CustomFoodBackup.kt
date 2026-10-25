package com.example.myfoodtracker.domain.model

/**
 * A user-created food as carried by a backup file.
 *
 * `sourceCatalogId` carries the backup-side catalog primary key: restored rows
 * are inserted with a **freshly allocated** id (so they can never collide with
 * the seeded catalog of a different asset version), and this old id is what the
 * restored `recipe_ingredients.rows` reference — the restore path builds its
 * old→new remap from it. Nullable-defaulted so backups written before story 6.2
 * (no ids carried) still read: the link then degrades to the dangling sweep.
 * `isDeleted` mirrors `catalog_foods.is_deleted`, so a soft-deleted custom food
 * stays soft-deleted after a restore.
 *
 * Pure Kotlin/JVM (AD-1).
 */
data class CustomFoodBackup(
    val name: String,
    val brand: String?,
    val barcode: String?,
    val baseServingSize: Double,
    val baseServingUnit: String,
    val isDeleted: Int,
    val calories: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val sugarG: Double,
    val sodiumMg: Double,
    val createdAt: Long,
    val updatedAt: Long,
    val sourceCatalogId: Long? = null
)
