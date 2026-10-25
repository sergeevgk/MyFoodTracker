package com.example.myfoodtracker.domain.model

/**
 * A user-created food as carried by a backup file.
 *
 * The backed-up catalog primary key is deliberately not kept: restored rows are
 * inserted with a freshly allocated id so they can never collide with the seeded
 * catalog of a different asset version. `isDeleted` mirrors
 * `catalog_foods.is_deleted`, so a soft-deleted custom food stays soft-deleted
 * after a restore.
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
    val updatedAt: Long
)
