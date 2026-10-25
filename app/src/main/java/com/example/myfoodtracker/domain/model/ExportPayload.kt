package com.example.myfoodtracker.domain.model

/**
 * The complete result of a log export.
 *
 * [content] is the *entire* file content, including the trailing newline.
 * The presentation layer never formats anything itself — it only writes
 * [content] to disk (or to a user-picked document URI).
 */
data class ExportPayload(
    val fileName: String,
    val content: String,
    val rowCount: Int,
    val format: ExportFormat
)
