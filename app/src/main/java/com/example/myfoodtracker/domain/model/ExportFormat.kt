package com.example.myfoodtracker.domain.model

/**
 * The two export formats supported by the log export feature.
 *
 * Pure Kotlin — no Android imports (AD-1).
 */
enum class ExportFormat(
    val fileExtension: String,
    val mimeType: String
) {
    CSV("csv", "text/csv"),
    TXT("txt", "text/plain")
}
