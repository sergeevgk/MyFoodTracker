package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.ExportFormat
import com.example.myfoodtracker.domain.model.Food
import com.example.myfoodtracker.domain.model.MealEntry
import com.example.myfoodtracker.domain.repository.MealRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class ExportLogsUseCaseTest {

    private lateinit var fakeMealRepository: FakeMealRepository
    private lateinit var useCase: ExportLogsUseCase

    private val from = LocalDate.of(2026, 10, 1)
    private val to = LocalDate.of(2026, 10, 21)

    @Before
    fun setUp() {
        fakeMealRepository = FakeMealRepository()
        useCase = ExportLogsUseCase(fakeMealRepository)
    }

    // --- Header --------------------------------------------------------------------

    @Test
    fun invoke_csv_startsWithExactHeaderLine() {
        fakeMealRepository.entries = listOf(meal(id = "m1", date = "2026-10-04", time = "08:00"))

        val payload = useCase(ExportFormat.CSV, from, to)

        val lines = payload.content.split("\n")
        assertEquals("Date,Meal Slot,Food Name,Quantity,Calories,Protein,Carbs,Fat", lines[0])
    }

    @Test
    fun invoke_csv_containsNoBomAndUsesLineFeeds() {
        fakeMealRepository.entries = listOf(meal(id = "m1", date = "2026-10-04", time = "08:00"))

        val payload = useCase(ExportFormat.CSV, from, to)

        assertFalse(payload.content.startsWith("\uFEFF"))
        assertFalse(payload.content.contains("\r"))
    }

    @Test
    fun invoke_csv_endsWithTrailingNewline() {
        fakeMealRepository.entries = listOf(meal(id = "m1", date = "2026-10-04", time = "08:00"))

        val payload = useCase(ExportFormat.CSV, from, to)

        assertTrue(payload.content.endsWith("\n"))
    }

    // --- Row count and mapping -----------------------------------------------------

    @Test
    fun invoke_mealWithTwoFoods_producesTwoRows() {
        fakeMealRepository.entries = listOf(
            meal(
                id = "m1",
                date = "2026-10-04",
                time = "08:00",
                foods = listOf(
                    food("Apple", 150.0, 80.0, 1.0, 20.0, 0.5),
                    food("Yogurt", 200.0, 120.0, 8.0, 20.0, 6.0)
                )
            )
        )

        val payload = useCase(ExportFormat.CSV, from, to)

        assertEquals(2, payload.rowCount)
        assertEquals(3, payload.content.trimEnd('\n').split("\n").size) // header + 2 rows
    }

    @Test
    fun invoke_mealWithNoFoods_producesNoRow() {
        fakeMealRepository.entries = listOf(meal(id = "m1", date = "2026-10-04", time = "08:00", foods = emptyList()))

        val payload = useCase(ExportFormat.CSV, from, to)

        assertEquals(0, payload.rowCount)
        assertEquals("Date,Meal Slot,Food Name,Quantity,Calories,Protein,Carbs,Fat\n", payload.content)
    }

    @Test
    fun invoke_quantityCellIsStoredWeightInGrams() {
        fakeMealRepository.entries = listOf(
            meal(
                id = "m1",
                date = "2026-10-04",
                time = "08:00",
                foods = listOf(food("Chicken Breast", 150.0, 248.0, 46.5, 0.0, 5.4))
            )
        )

        val payload = useCase(ExportFormat.CSV, from, to)

        assertEquals(
            "2026-10-04,BREAKFAST,Chicken Breast,150,248,46.5,0,5.4",
            payload.content.trimEnd('\n').split("\n")[1]
        )
    }

    @Test
    fun invoke_macroCellsAreStoredAbsoluteValues_notRescaled() {
        // A stored 248 kcal stays 248 — it must never be multiplied by grams/100.
        fakeMealRepository.entries = listOf(
            meal(
                id = "m1",
                date = "2026-10-04",
                time = "08:00",
                foods = listOf(food("Chicken Breast", 150.0, 248.0, 46.5, 0.0, 5.4))
            )
        )

        val payload = useCase(ExportFormat.CSV, from, to)

        val row = payload.content.trimEnd('\n').split("\n")[1].split(",")
        assertEquals("248", row[4])
        assertEquals("46.5", row[5])
        assertEquals("0", row[6])
        assertEquals("5.4", row[7])
    }

    @Test
    fun invoke_mealSlotIsStoredVerbatim() {
        fakeMealRepository.entries = listOf(meal(id = "m1", date = "2026-10-04", time = "08:00", title = "BREAKFAST"))

        val payload = useCase(ExportFormat.CSV, from, to)

        assertTrue(payload.content.contains(",BREAKFAST,"))
    }

    // --- Ordering ------------------------------------------------------------------

    @Test
    fun invoke_sortsAscendingByDate() {
        fakeMealRepository.entries = listOf(
            meal(id = "m2", date = "2026-10-20", time = "08:00", foods = listOf(food("Later", 1.0, 1.0, 1.0, 1.0, 1.0))),
            meal(id = "m1", date = "2026-10-04", time = "08:00", foods = listOf(food("Earlier", 1.0, 1.0, 1.0, 1.0, 1.0)))
        )

        val payload = useCase(ExportFormat.CSV, from, to)

        val rows = payload.content.trimEnd('\n').split("\n").drop(1)
        assertEquals("2026-10-04", rows[0].split(",")[0])
        assertEquals("2026-10-20", rows[1].split(",")[0])
    }

    @Test
    fun invoke_sortsAscendingByTimeWithinSameDate() {
        fakeMealRepository.entries = listOf(
            meal(id = "m2", date = "2026-10-04", time = "18:30", foods = listOf(food("Dinner", 1.0, 1.0, 1.0, 1.0, 1.0))),
            meal(id = "m1", date = "2026-10-04", time = "07:15", foods = listOf(food("Breakfast", 1.0, 1.0, 1.0, 1.0, 1.0)))
        )

        val payload = useCase(ExportFormat.CSV, from, to)

        val rows = payload.content.trimEnd('\n').split("\n").drop(1)
        assertTrue(rows[0].contains("Breakfast"))
        assertTrue(rows[1].contains("Dinner"))
    }

    @Test
    fun invoke_sortsAscendingByFoodNameWhenDateAndTimeMatch() {
        fakeMealRepository.entries = listOf(
            meal(id = "m2", date = "2026-10-04", time = "08:00", foods = listOf(food("Zucchini", 1.0, 1.0, 1.0, 1.0, 1.0))),
            meal(id = "m1", date = "2026-10-04", time = "08:00", foods = listOf(food("Apple", 1.0, 1.0, 1.0, 1.0, 1.0)))
        )

        val payload = useCase(ExportFormat.CSV, from, to)

        val rows = payload.content.trimEnd('\n').split("\n").drop(1)
        assertTrue(rows[0].contains("Apple"))
        assertTrue(rows[1].contains("Zucchini"))
    }

    @Test
    fun invoke_duplicateRowsAreKeptInStableOrder() {
        fakeMealRepository.entries = listOf(
            meal(id = "m1", date = "2026-10-04", time = "08:00", foods = listOf(food("Apple", 1.0, 1.0, 1.0, 1.0, 1.0))),
            meal(id = "m2", date = "2026-10-04", time = "08:00", foods = listOf(food("Apple", 2.0, 2.0, 2.0, 2.0, 2.0)))
        )

        val payload = useCase(ExportFormat.CSV, from, to)

        assertEquals(2, payload.rowCount)
        val rows = payload.content.trimEnd('\n').split("\n").drop(1)
        assertEquals("Apple", rows[0].split(",")[2])
        assertEquals("Apple", rows[1].split(",")[2])
        // Stable sort keeps the DB order for equal keys.
        assertEquals("1", rows[0].split(",")[3])
        assertEquals("2", rows[1].split(",")[3])
    }

    // --- Range filter --------------------------------------------------------------

    @Test
    fun invoke_rangeIsInclusiveOnBothEnds() {
        fakeMealRepository.entries = listOf(
            meal(id = "m1", date = "2026-10-01", time = "08:00", foods = listOf(food("OnFrom", 1.0, 1.0, 1.0, 1.0, 1.0))),
            meal(id = "m2", date = "2026-10-21", time = "08:00", foods = listOf(food("OnTo", 1.0, 1.0, 1.0, 1.0, 1.0)))
        )

        val payload = useCase(ExportFormat.CSV, from, to)

        assertEquals(2, payload.rowCount)
    }

    @Test
    fun invoke_mealOutsideRangeIsExcluded() {
        fakeMealRepository.entries = listOf(
            meal(id = "m1", date = "2026-09-30", time = "08:00", foods = listOf(food("Before", 1.0, 1.0, 1.0, 1.0, 1.0))),
            meal(id = "m2", date = "2026-10-22", time = "08:00", foods = listOf(food("After", 1.0, 1.0, 1.0, 1.0, 1.0)))
        )

        val payload = useCase(ExportFormat.CSV, from, to)

        assertEquals(0, payload.rowCount)
    }

    // --- Escaping ------------------------------------------------------------------

    @Test
    fun invoke_fieldWithCommaIsQuoted() {
        fakeMealRepository.entries = listOf(
            meal(
                id = "m1",
                date = "2026-10-04",
                time = "08:00",
                foods = listOf(food("Cookie, with chocolate", 25.0, 118.0, 1.5, 16.2, 4.8))
            )
        )

        val payload = useCase(ExportFormat.CSV, from, to)

        assertTrue(payload.content.contains("\"Cookie, with chocolate\""))
    }

    @Test
    fun invoke_fieldWithQuoteIsQuotedAndInnerQuoteDoubled() {
        fakeMealRepository.entries = listOf(
            meal(
                id = "m1",
                date = "2026-10-04",
                time = "08:00",
                foods = listOf(food("Ben \"Ben\"'s", 25.0, 118.0, 1.5, 16.2, 4.8))
            )
        )

        val payload = useCase(ExportFormat.CSV, from, to)

        assertTrue(payload.content.contains("\"Ben \"\"Ben\"\"'s\""))
    }

    @Test
    fun invoke_fieldWithNewlineIsQuotedAndNewlineKept() {
        fakeMealRepository.entries = listOf(
            meal(
                id = "m1",
                date = "2026-10-04",
                time = "08:00",
                foods = listOf(food("Line1\nLine2", 25.0, 118.0, 1.5, 16.2, 4.8))
            )
        )

        val payload = useCase(ExportFormat.CSV, from, to)

        assertTrue(payload.content.contains("\"Line1\nLine2\""))
    }

    @Test
    fun invoke_fieldWithoutSpecialCharactersIsNotQuoted() {
        fakeMealRepository.entries = listOf(
            meal(
                id = "m1",
                date = "2026-10-04",
                time = "08:00",
                foods = listOf(food("Plain Name", 25.0, 118.0, 1.5, 16.2, 4.8))
            )
        )

        val payload = useCase(ExportFormat.CSV, from, to)

        assertTrue(payload.content.contains(",Plain Name,"))
    }

    // --- Number formatting ---------------------------------------------------------

    @Test
    fun invoke_formatsNumbersWithAtMostTwoDecimalsAndTrimmedZeros() {
        fakeMealRepository.entries = listOf(
            meal(
                id = "m1",
                date = "2026-10-04",
                time = "08:00",
                foods = listOf(
                    food("A", 150.0, 150.0, 12.5, 12.3456, 0.0),
                    food("B", 0.0, -0.0, 0.0, 0.0, 0.0)
                )
            )
        )

        val payload = useCase(ExportFormat.CSV, from, to)

        val rows = payload.content.trimEnd('\n').split("\n").drop(1)
        assertEquals("2026-10-04,BREAKFAST,A,150,150,12.5,12.35,0", rows[0])
        assertEquals("2026-10-04,BREAKFAST,B,0,0,0,0,0", rows[1])
    }

    @Test
    fun invoke_doesNotUseScientificNotation() {
        fakeMealRepository.entries = listOf(
            meal(
                id = "m1",
                date = "2026-10-04",
                time = "08:00",
                title = "LUNCH",
                foods = listOf(food("Big", 1.0E7, 1.0E7, 1.0, 1.0, 1.0))
            )
        )

        val payload = useCase(ExportFormat.CSV, from, to)

        assertFalse(payload.content.contains("E+"))
        assertFalse(payload.content.contains("E-"))
        assertTrue(payload.content.contains("10000000"))
    }

    @Test
    fun invoke_isLocaleIndependent_germanLocaleUsesDot() {
        fakeMealRepository.entries = listOf(
            meal(
                id = "m1",
                date = "2026-10-04",
                time = "08:00",
                foods = listOf(food("A", 12.5, 12.5, 12.5, 12.5, 12.5))
            )
        )

        val original = Locale.getDefault()
        val content: String
        try {
            Locale.setDefault(Locale.GERMANY)
            content = useCase(ExportFormat.CSV, from, to).content
        } finally {
            Locale.setDefault(original)
        }

        assertTrue(content.contains("12.5"))
        assertFalse(content.contains("12,5"))
    }

    // --- TXT format ----------------------------------------------------------------

    @Test
    fun invoke_txt_hasHeaderLineWithSameColumnNames() {
        fakeMealRepository.entries = listOf(meal(id = "m1", date = "2026-10-04", time = "08:00"))

        val payload = useCase(ExportFormat.TXT, from, to)

        val lines = payload.content.split("\n")
        val headerCells = lines[0].split(Regex(" {2,}"))
        assertEquals(
            listOf("Date", "Meal Slot", "Food Name", "Quantity", "Calories", "Protein", "Carbs", "Fat"),
            headerCells
        )
    }

    @Test
    fun invoke_txt_columnsAreSeparatedByTwoSpacesAndAligned() {
        fakeMealRepository.entries = listOf(
            meal(
                id = "m1",
                date = "2026-10-04",
                time = "08:00",
                title = "BREAKFAST",
                foods = listOf(food("Apple", 150.0, 80.0, 1.0, 20.0, 0.5))
            ),
            meal(
                id = "m2",
                date = "2026-10-05",
                time = "09:00",
                title = "SNACK",
                foods = listOf(food("Banana", 25.0, 118.0, 1.5, 16.2, 4.8))
            )
        )

        val payload = useCase(ExportFormat.TXT, from, to)

        val lines = payload.content.trimEnd('\n').split("\n")
        val header = lines[0]
        val dataLines = lines.drop(1)

        // Every column value starts at the same offset on every data line.
        val expectedOffsets = cellOffsets(dataLines[0])
        dataLines.forEach { line ->
            assertEquals(expectedOffsets, cellOffsets(line))
        }

        // The header column names start at the same offsets as the data columns.
        assertEquals(expectedOffsets[0], header.indexOf("Date"))
        assertEquals(expectedOffsets[1], header.indexOf("Meal Slot"))
        assertEquals(expectedOffsets[2], header.indexOf("Food Name"))
        assertEquals(expectedOffsets[3], header.indexOf("Quantity"))
        assertEquals(expectedOffsets[4], header.indexOf("Calories"))
        assertEquals(expectedOffsets[5], header.indexOf("Protein"))
        assertEquals(expectedOffsets[6], header.indexOf("Carbs"))
        assertEquals(expectedOffsets[7], header.indexOf("Fat"))

        // The separator is exactly two spaces once a cell fills its column width
        // (the Date column is always 10 characters wide).
        dataLines.forEach { line ->
            assertEquals(2, gapAfterFirstCell(line))
        }
    }

    @Test
    fun invoke_txt_hasNoTrailingSpacesOnAnyLine() {
        fakeMealRepository.entries = listOf(
            meal(
                id = "m1",
                date = "2026-10-04",
                time = "08:00",
                foods = listOf(food("Apple", 150.0, 80.0, 1.0, 20.0, 0.5))
            )
        )

        val payload = useCase(ExportFormat.TXT, from, to)

        payload.content.split("\n").forEach { line ->
            assertEquals(line, line.trimEnd())
        }
    }

    @Test
    fun invoke_txt_hasSameValuesAndOrderAsCsv() {
        fakeMealRepository.entries = listOf(
            meal(
                id = "m1",
                date = "2026-10-04",
                time = "08:00",
                foods = listOf(food("Apple", 150.0, 80.0, 1.0, 20.0, 0.5))
            ),
            meal(
                id = "m2",
                date = "2026-10-05",
                time = "09:00",
                foods = listOf(food("Banana", 25.0, 118.0, 1.5, 16.2, 4.8))
            )
        )

        val csv = useCase(ExportFormat.CSV, from, to)
        val txt = useCase(ExportFormat.TXT, from, to)

        val csvCells = csv.content.trimEnd('\n').split("\n").drop(1).map { it.split(",") }
        // The TXT separator is two spaces plus the padding of the previous cell,
        // so split on a run of two or more spaces.
        val txtCells = txt.content.trimEnd('\n').split("\n").drop(1).map { line ->
            line.split(Regex(" {2,}"))
        }
        assertEquals(csvCells.size, txtCells.size)
        csvCells.forEachIndexed { index, expected ->
            assertEquals(expected, txtCells[index])
        }
    }

    @Test
    fun invoke_txt_endsWithTrailingNewline() {
        fakeMealRepository.entries = listOf(meal(id = "m1", date = "2026-10-04", time = "08:00"))

        val payload = useCase(ExportFormat.TXT, from, to)

        assertTrue(payload.content.endsWith("\n"))
    }

    @Test
    fun invoke_txt_fieldWithNewlinesOrTabs_sanitizesToSingleLineAndPreservesAlignment() {
        fakeMealRepository.entries = listOf(
            meal(
                id = "m1",
                date = "2026-10-04",
                time = "08:00",
                title = "BREAK\tFAST",
                foods = listOf(food("Line1\nLine2\r\nLine3", 25.0, 118.0, 1.5, 16.2, 4.8))
            )
        )

        val payload = useCase(ExportFormat.TXT, from, to)

        val lines = payload.content.trimEnd('\n').split("\n")
        assertEquals(2, lines.size) // header + 1 single-line data row
        assertFalse(lines[1].contains("\r"))
        assertFalse(lines[1].contains("\t"))
        assertTrue(lines[1].contains("BREAK FAST"))
        assertTrue(lines[1].contains("Line1 Line2 Line3"))
    }

    // --- File name -----------------------------------------------------------------

    @Test
    fun invoke_csv_fileNameUsesRangeAndExtension() {
        fakeMealRepository.entries = listOf(meal(id = "m1", date = "2026-10-04", time = "08:00"))

        val payload = useCase(ExportFormat.CSV, from, to)

        assertEquals("myfoodtracker_logs_2026-10-01_2026-10-21.csv", payload.fileName)
    }

    @Test
    fun invoke_txt_fileNameUsesTxtExtension() {
        fakeMealRepository.entries = listOf(meal(id = "m1", date = "2026-10-04", time = "08:00"))

        val payload = useCase(ExportFormat.TXT, from, to)

        assertEquals("myfoodtracker_logs_2026-10-01_2026-10-21.txt", payload.fileName)
    }

    // --- Validation ----------------------------------------------------------------

    @Test
    fun invoke_fromAfterTo_throwsInvalidDateRange() {
        val exception = try {
            useCase(ExportFormat.CSV, to, from)
            null
        } catch (e: IllegalArgumentException) {
            e
        }

        assertTrue(exception is IllegalArgumentException)
        assertEquals("Invalid date range", exception!!.message)
    }

    @Test
    fun invoke_invalidRange_doesNotTouchTheRepository() {
        try {
            useCase(ExportFormat.CSV, to, from)
        } catch (e: IllegalArgumentException) {
            // expected
        }

        assertEquals(0, fakeMealRepository.getMealEntriesCallCount)
    }

    @Test
    fun invoke_emptyRange_hasZeroRowsAndHeaderOnlyContent() {
        fakeMealRepository.entries = emptyList()

        val payload = useCase(ExportFormat.CSV, from, to)

        assertEquals(0, payload.rowCount)
        assertEquals("Date,Meal Slot,Food Name,Quantity,Calories,Protein,Carbs,Fat\n", payload.content)
    }

    // --- Helpers -------------------------------------------------------------------

    /** Start offset of every non-whitespace run (i.e. every column cell) in a line. */
    private fun cellOffsets(line: String): List<Int> {
        val offsets = mutableListOf<Int>()
        for (match in Regex("\\S+").findAll(line)) {
            offsets.add(match.range.first)
        }
        return offsets
    }

    /** Number of spaces between the first and the second cell of a line. */
    private fun gapAfterFirstCell(line: String): Int {
        val runs = Regex("\\S+").findAll(line).toList()
        return runs[1].range.first - runs[0].range.last - 1
    }

    private fun meal(
        id: String,
        date: String,
        time: String,
        title: String = "BREAKFAST",
        foods: List<Food> = listOf(food("Apple", 150.0, 80.0, 1.0, 20.0, 0.5))
    ) = MealEntry(
        id = id,
        title = title,
        date = date,
        time = time,
        foods = foods
    )

    private fun food(
        name: String,
        weight: Double,
        calories: Double,
        protein: Double,
        carbs: Double,
        fat: Double
    ) = Food(
        name = name,
        weight = weight,
        calories = calories,
        carbs = carbs,
        fat = fat,
        protein = protein,
        fiber = 0.0
    )

    private class FakeMealRepository : MealRepository {
        var entries: List<MealEntry> = emptyList()
        var getMealEntriesCallCount: Int = 0

        override fun getMealEntries(): List<MealEntry> {
            getMealEntriesCallCount++
            return entries
        }

        override fun getMealEntriesByDate(date: String): List<MealEntry> =
            throw UnsupportedOperationException("Not used by ExportLogsUseCase")

        override fun addMealEntry(): List<MealEntry> =
            throw UnsupportedOperationException("Not used by ExportLogsUseCase")

        override fun updateMealEntry(id: String, newTitle: String): List<MealEntry> =
            throw UnsupportedOperationException("Not used by ExportLogsUseCase")

        override fun deleteMealEntry(id: String): List<MealEntry> =
            throw UnsupportedOperationException("Not used by ExportLogsUseCase")

        override fun logQuickAdd(
            name: String,
            calories: Double,
            proteinG: Double,
            carbsG: Double,
            fatG: Double,
            mealSlot: String,
            date: String
        ): List<MealEntry> = throw UnsupportedOperationException("Not used by ExportLogsUseCase")

        override fun logFoodEntry(
            foodName: String,
            quantityGrams: Double,
            calories: Double,
            proteinG: Double,
            carbsG: Double,
            fatG: Double,
            fiberG: Double,
            mealSlot: String,
            date: String
        ): List<MealEntry> = throw UnsupportedOperationException("Not used by ExportLogsUseCase")

        override fun restoreMealEntry(entry: MealEntry): List<MealEntry> =
            throw UnsupportedOperationException("Not used by ExportLogsUseCase")
    }
}
