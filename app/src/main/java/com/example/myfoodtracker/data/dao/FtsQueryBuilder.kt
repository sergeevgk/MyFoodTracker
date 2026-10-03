package com.example.myfoodtracker.data.dao

object FtsQueryBuilder {
    private val WHITESPACE = Regex("\\s+")

    /** Returns an FTS5 MATCH expression (per-term prefix, order-independent) or null when there is nothing searchable. */
    fun build(rawInput: String): String? {
        val terms = rawInput.trim().split(WHITESPACE).filter { term -> term.any(Char::isLetterOrDigit) }
        if (terms.isEmpty()) return null
        return terms.joinToString(separator = " ") { term ->
            "\"" + term.replace("\"", "\"\"") + "\"*"
        }
    }
}
