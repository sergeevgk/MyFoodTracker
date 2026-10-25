package com.example.myfoodtracker.domain.model

/**
 * One serving unit (`catalog_food_serving_units` row) of a user-created food,
 * as carried by backup format v2 (story 6.3, AC #3).
 *
 * Keyed by the backup-side catalog id (`CustomFoodBackup.sourceCatalogId`);
 * the restore path re-attaches the units through the re-insert transaction —
 * a v1 backup simply carries no unit rows. The auto-generated row id is never
 * carried: restore re-allocates it.
 *
 * Pure Kotlin/JVM (AD-1).
 */
data class ServingUnitBackup(
    val unitName: String,
    val gramsPerUnit: Double
)
