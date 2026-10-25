package com.example.myfoodtracker.domain.model

/**
 * Everything the data layer could observe about a restore candidate.
 *
 * The data layer only *collects* facts (SQLite is unreadable from the domain
 * layer); [com.example.myfoodtracker.domain.usecase.BackupDatabaseUseCase]
 * decides. A missing or unreadable value is represented as `null` instead of
 * throwing, so a corrupt file degrades into a rejection reason.
 *
 * Pure Kotlin/JVM (AD-1).
 */
data class BackupFacts(
    val fileReadable: Boolean,
    val metaTablePresent: Boolean,
    val integrityOk: Boolean,
    val formatVersion: Int?,
    val declaredUserVersion: Int?,
    val actualUserVersion: Int?,
    val expectedUserVersion: Int,
    val missingTables: List<String>,
    val candidateIdentityHash: String?,
    val liveIdentityHash: String?,
    val declaredCustomFoodCount: Int?,
    val actualCustomFoodCount: Int
)
