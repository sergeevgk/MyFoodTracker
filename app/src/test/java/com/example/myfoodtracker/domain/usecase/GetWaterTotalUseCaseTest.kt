package com.example.myfoodtracker.domain.usecase

import com.example.myfoodtracker.domain.model.WaterLog
import com.example.myfoodtracker.domain.repository.WaterRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GetWaterTotalUseCaseTest {

    private lateinit var fakeWaterRepository: FakeWaterRepository
    private lateinit var getWaterTotalUseCase: GetWaterTotalUseCase

    @Before
    fun setUp() {
        fakeWaterRepository = FakeWaterRepository()
        getWaterTotalUseCase = GetWaterTotalUseCase(fakeWaterRepository)
    }

    @Test
    fun invoke_withNoLogs_returnsZero() {
        assertEquals(0, getWaterTotalUseCase("2026-09-30"))
    }

    @Test
    fun invoke_delegatesDateToRepository() {
        fakeWaterRepository.logWater(250, "2026-09-30")

        val total = getWaterTotalUseCase("2026-09-30")

        assertEquals(250, total)
        assertEquals("2026-09-30", fakeWaterRepository.lastDate)
    }

    @Test
    fun invoke_sumsMultipleLogsForDate() {
        fakeWaterRepository.logWater(250, "2026-09-30")
        fakeWaterRepository.logWater(250, "2026-09-30")

        assertEquals(500, getWaterTotalUseCase("2026-09-30"))
    }

    private class FakeWaterRepository : WaterRepository {
        val amountsByDate = mutableMapOf<String, MutableList<Int>>()
        var lastDate: String? = null

        override fun getWaterLogs(date: String): List<WaterLog> {
            return (amountsByDate[date] ?: emptyList()).mapIndexed { index, amount ->
                WaterLog(id = "water-$index", profileId = "user-1", date = date, amountMl = amount)
            }
        }

        override fun getWaterTotalMl(date: String): Int {
            lastDate = date
            return amountsByDate[date]?.sum() ?: 0
        }

        override fun logWater(amountMl: Int, date: String): Int {
            amountsByDate.getOrPut(date) { mutableListOf() }.add(amountMl)
            return getWaterTotalMl(date)
        }
    }
}
