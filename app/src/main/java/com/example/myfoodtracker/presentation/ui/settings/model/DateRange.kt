package com.example.myfoodtracker.presentation.ui.settings.model

import java.time.LocalDate

/**
 * The inclusive date range the export covers.
 * Pure JVM — no Android imports.
 */
data class DateRange(
    val from: LocalDate,
    val to: LocalDate
)
