package com.example.myfoodtracker.presentation.ui.settings.model

/**
 * The active profile shown in the Settings header.
 * Pure JVM — no Android imports.
 */
data class SettingsUiState(
    val username: String,
    val profileId: String
)
