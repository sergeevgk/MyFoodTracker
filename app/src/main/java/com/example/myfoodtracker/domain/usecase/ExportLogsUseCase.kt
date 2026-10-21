package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.ExportFormat
import com.example.myfoodtracker.domain.model.ExportPayload
import com.example.myfoodtracker.domain.model.Food
import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.repository.MealRepository
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Builds the export file content for a date range.
 *
 * Reads through the existing synchronous [MealRepository], which already scopes
 * the result to the active profile (NFR-5) and returns empty for a missing
 * session. No DAO query, no repository method and no entity column is added by
 * this story — the export is a read + filter over the existing meal list.
 *
 * Pure Kotlin/JVM: no Android imports, no coroutines (AD-1, AD-2).
 */
class ExportLogsUseCase(
    private val mealRepository: MealRepository
) {

    operator fun invoke(format: ExportFormat, from: LocalDate, to: LocalDate): ExportPayload {
        require(!from.isAfter(to)) { INVALID_DATE_RANGE }

        val rows = mealRepository.getMealEntries()
            .mapNotNull { meal -> mealDate(meal)?.let { meal to it } }
            .filter { (_, date) -> !date.isBefore(from) && !date.isAfter(to) }
            .flatMap { (meal, date) ->
                // A meal without foods contributes no row (there is no food name to write).
                meal.foods.map { food -> ExportRow(meal, food, date) }
            }
            .sortedWith(ROW_ORDER)

        val content = when (format) {
            ExportFormat.CSV -> renderCsv(rows)
            ExportFormat.TXT -> renderTxt(rows)
        }

        return ExportPayload(
            fileName = "myfoodtracker_logs_${from}_${to}.${format.fileExtension}",
            content = content,
            rowCount = rows.size,
            format = format
        )
    }

    // --- Row model -----------------------------------------------------------------

    private class ExportRow(
        val date: String,
        val dateValue: LocalDate,
        val time: String,
        val slot: String,
        val foodName: String,
        val weight: Double,
        val calories: Double,
        val protein: Double,
        val carbs: Double,
        val fat: Double
    ) {
        constructor(meal: MealEntry, food: Food, dateValue: LocalDate) : this(
            date = meal.date,
            dateValue = dateValue,
            time = meal.time,
            slot = meal.title,
            foodName = food.name,
            weight = food.weight,
            calories = food.calories,
            protein = food.protein,
            carbs = food.carbs,
            fat = food.fat
        )

        /** Cells in the canonical column order: Date, Meal Slot, Food Name, Quantity, Calories, Protein, Carbs, Fat. */
        fun cells(): List<String> = listOf(
            date,
            slot,
            foodName,
            formatNumber(weight),
            formatNumber(calories),
            formatNumber(protein),
            formatNumber(carbs),
            formatNumber(fat)
        )

        companion object {
            /**
             * Locale-independent number formatting: a `.` decimal separator, at most
             * two decimals, trailing zeros trimmed, never scientific notation.
             *
             * `String.format` with the default locale is deliberately avoided — a
             * German device would emit `12,5` and break the CSV column split.
             */
            fun formatNumber(value: Double): String {
                if (!value.isFinite()) return "0"
                val decimal = BigDecimal.valueOf(value)
                    .setScale(2, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                if (decimal.signum() == 0) return "0"
                return decimal.toPlainString()
            }
        }
    }

    // --- Ordering ------------------------------------------------------------------

    /**
     * Ascending by (date, time, food name). The stored time is an "HH:mm" string,
     * so lexicographic order is chronological. [sortedWith] is a stable sort, so
     * rows with equal keys keep their database order.
     */
    private val ROW_ORDER: Comparator<ExportRow> =
        compareBy<ExportRow> { it.dateValue }
            .thenBy { it.time }
            .thenBy { it.foodName }

    // --- Rendering -----------------------------------------------------------------

    private fun renderCsv(rows: List<ExportRow>): String {
        val builder = StringBuilder()
        builder.append(CSV_HEADER_LINE).append('\n')
        rows.forEach { row ->
            val cells = row.cells()
            cells.forEachIndexed { index, cell ->
                if (index > 0) builder.append(',')
                builder.append(csvField(cell))
            }
            builder.append('\n')
        }
        return builder.toString()
    }

    private fun renderTxt(rows: List<ExportRow>): String {
        val header = TXT_HEADERS
        val table = rows.map { row -> row.cells().map(::txtCell) }

        val widths = IntArray(header.size) { column ->
            var width = header[column].length
            table.forEach { cells ->
                val length = cells[column].length
                if (length > width) width = length
            }
            width
        }

        val lines = ArrayList<String>(table.size + 1)
        lines.add(renderTxtRow(header, widths))
        table.forEach { cells -> lines.add(renderTxtRow(cells, widths)) }

        return lines.joinToString(separator = "\n", postfix = "\n")
    }

    private fun renderTxtRow(cells: List<String>, widths: IntArray): String {
        val builder = StringBuilder()
        cells.forEachIndexed { index, cell ->
            if (index > 0) builder.append(TXT_COLUMN_SEPARATOR)
            builder.append(cell)
            if (index < cells.lastIndex) {
                val padding = widths[index] - cell.length
                if (padding > 0) builder.append(" ".repeat(padding))
            }
        }
        // Trailing spaces are stripped from every line.
        return builder.toString().trimEnd()
    }

    // --- Formatting ----------------------------------------------------------------

    /**
     * RFC 4180 escaping: a field is wrapped in `"` only when it contains
     * `,`, `"`, `\r` or `\n`; inner quotes are doubled.
     */
    private fun csvField(raw: String): String {
        val needsQuoting = raw.any { it == ',' || it == '"' || it == '\r' || it == '\n' }
        if (!needsQuoting) return raw
        return "\"" + raw.replace("\"", "\"\"") + "\""
    }

    /**
     * Normalizes line breaks and tabs to single spaces so each TXT row stays on
     * a single physical line and fixed-width column alignment is preserved.
     */
    private fun txtCell(raw: String): String =
        raw.replace("\r\n", " ")
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace('\t', ' ')

    /** Parses the stored `meals.date` ("YYYY-MM-DD"); a malformed date is ignored. */
    private fun mealDate(meal: MealEntry): LocalDate? = try {
        LocalDate.parse(meal.date)
    } catch (e: Exception) {
        null
    }

    companion object {
        const val INVALID_DATE_RANGE = "Invalid date range"

        const val CSV_HEADER_LINE = "Date,Meal Slot,Food Name,Quantity,Calories,Protein,Carbs,Fat"

        val TXT_HEADERS = listOf(
            "Date",
            "Meal Slot",
            "Food Name",
            "Quantity",
            "Calories",
            "Protein",
            "Carbs",
            "Fat"
        )

        const val TXT_COLUMN_SEPARATOR = "  "
    }
}
