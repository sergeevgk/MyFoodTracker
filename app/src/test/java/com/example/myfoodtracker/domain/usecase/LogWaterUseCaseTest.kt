package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.WaterLog
import com.example.myfoodtracker.domain.repository.WaterRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LogWaterUseCaseTest {

    private lateinit var fakeWaterRepository: FakeWaterRepository
    private lateinit var logWaterUseCase: LogWaterUseCase

    @Before
    fun setUp() {
        fakeWaterRepository = FakeWaterRepository()
        logWaterUseCase = LogWaterUseCase(fakeWaterRepository)
    }

    @Test
    fun invoke_delegatesAmountAndDateToRepository() {
        val total = logWaterUseCase(250, "2026-09-30")

        assertEquals(250, total)
        assertEquals(250, fakeWaterRepository.lastAmountMl)
        assertEquals("2026-09-30", fakeWaterRepository.lastDate)
    }

    @Test
    fun invoke_accumulatesMultipleLogsForSameDate() {
        logWaterUseCase(250, "2026-09-30")
        val total = logWaterUseCase(250, "2026-09-30")

        assertEquals(500, total)
    }

    @Test
    fun invoke_isolatesTotalsByDate() {
        logWaterUseCase(250, "2026-09-30")
        val otherDayTotal = logWaterUseCase(250, "2026-10-01")

        assertEquals(250, otherDayTotal)
        assertEquals(250, fakeWaterRepository.getWaterTotalMl("2026-09-30"))
        assertEquals(250, fakeWaterRepository.getWaterTotalMl("2026-10-01"))
    }

    private class FakeWaterRepository : WaterRepository {
        val amountsByDate = mutableMapOf<String, MutableList<Int>>()
        var lastAmountMl: Int? = null
        var lastDate: String? = null

        override fun getWaterLogs(date: String): List<WaterLog> {
            return (amountsByDate[date] ?: emptyList()).mapIndexed { index, amount ->
                WaterLog(id = "water-$index", profileId = "user-1", date = date, amountMl = amount)
            }
        }

        override fun getWaterTotalMl(date: String): Int {
            return amountsByDate[date]?.sum() ?: 0
        }

        override fun logWater(amountMl: Int, date: String): Int {
            lastAmountMl = amountMl
            lastDate = date
            amountsByDate.getOrPut(date) { mutableListOf() }.add(amountMl)
            return getWaterTotalMl(date)
        }
    }
}
