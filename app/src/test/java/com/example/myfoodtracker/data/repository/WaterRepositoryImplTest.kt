package com.example.myfoodtracker.data.repository

import com.example.myfoodtracker.data.db.WaterLogDao
import com.example.myfoodtracker.data.db.WaterLogEntity
import com.example.myfoodtracker.domain.model.DailyGoal
import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.SessionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WaterRepositoryImplTest {

    private lateinit var fakeWaterLogDao: FakeWaterLogDao
    private lateinit var fakeSessionRepository: FakeSessionRepository
    private lateinit var repository: WaterRepositoryImpl

    private val user1 = UserProfile(
        id = "user-1",
        username = "Alice",
        dailyGoal = DailyGoal()
    )

    private val user2 = UserProfile(
        id = "user-2",
        username = "Bob",
        dailyGoal = DailyGoal()
    )

    @Before
    fun setUp() {
        fakeWaterLogDao = FakeWaterLogDao()
        fakeSessionRepository = FakeSessionRepository()
        repository = WaterRepositoryImpl(fakeWaterLogDao, fakeSessionRepository)
    }

    @Test
    fun getAllWaterLogs_noActiveSession_returnsEmptyList() {
        fakeSessionRepository.clearSession()
        fakeWaterLogDao.insertWaterLog(
            WaterLogEntity(
                id = "w1",
                profileId = "user-1",
                date = "2026-10-31",
                amountMl = 250,
                loggedAt = 1000L
            )
        )
        val logs = repository.getAllWaterLogs()
        assertTrue(logs.isEmpty())
    }

    @Test
    fun getAllWaterLogs_withActiveSession_returnsOnlyActiveProfileLogs() {
        fakeSessionRepository.setActiveProfile(user1)
        fakeWaterLogDao.insertWaterLog(
            WaterLogEntity(
                id = "w1",
                profileId = "user-1",
                date = "2026-10-30",
                amountMl = 250,
                loggedAt = 1000L
            )
        )
        fakeWaterLogDao.insertWaterLog(
            WaterLogEntity(
                id = "w2",
                profileId = "user-2",
                date = "2026-10-30",
                amountMl = 500,
                loggedAt = 1100L
            )
        )
        fakeWaterLogDao.insertWaterLog(
            WaterLogEntity(
                id = "w3",
                profileId = "user-1",
                date = "2026-10-31",
                amountMl = 300,
                loggedAt = 1200L
            )
        )

        val logs = repository.getAllWaterLogs()
        assertEquals(2, logs.size)
        assertEquals("w1", logs[0].id)
        assertEquals("2026-10-30", logs[0].date)
        assertEquals(1000L, logs[0].loggedAt)
        assertEquals(250, logs[0].amountMl)
        assertEquals("user-1", logs[0].profileId)
        assertEquals("w3", logs[1].id)
        assertEquals(300, logs[1].amountMl)
        assertEquals("user-1", logs[1].profileId)
    }

    @Test
    fun getWaterLogs_withActiveSession_delegatesAndFiltersByDate() {
        fakeSessionRepository.setActiveProfile(user1)
        fakeWaterLogDao.insertWaterLog(
            WaterLogEntity(
                id = "w1",
                profileId = "user-1",
                date = "2026-10-30",
                amountMl = 250,
                loggedAt = 1000L
            )
        )
        fakeWaterLogDao.insertWaterLog(
            WaterLogEntity(
                id = "w2",
                profileId = "user-1",
                date = "2026-10-31",
                amountMl = 500,
                loggedAt = 1100L
            )
        )

        val logs = repository.getWaterLogs("2026-10-31")
        assertEquals(1, logs.size)
        assertEquals("w2", logs[0].id)
        assertEquals(500, logs[0].amountMl)
    }

    @Test
    fun logWater_insertsAndReturnsTotal() {
        fakeSessionRepository.setActiveProfile(user1)
        val total1 = repository.logWater(250, "2026-10-31")
        assertEquals(250, total1)

        val total2 = repository.logWater(300, "2026-10-31")
        assertEquals(550, total2)

        val totalDifferentDate = repository.logWater(400, "2026-11-01")
        assertEquals(400, totalDifferentDate)

        assertEquals(550, repository.getWaterTotalMl("2026-10-31"))
    }

    private class FakeWaterLogDao : WaterLogDao {
        val logs = mutableListOf<WaterLogEntity>()

        override fun getWaterLogsByProfileIdAndDate(profileId: String, date: String): List<WaterLogEntity> {
            return logs.filter { it.profileId == profileId && it.date == date }
        }

        override fun getTotalWaterMlByProfileIdAndDate(profileId: String, date: String): Int {
            return logs.filter { it.profileId == profileId && it.date == date }.sumOf { it.amountMl }
        }

        override fun getWaterLogsByProfileId(profileId: String): List<WaterLogEntity> {
            return logs.filter { it.profileId == profileId }
        }

        override fun insertWaterLog(waterLog: WaterLogEntity) {
            logs.add(waterLog)
        }
    }

    private class FakeSessionRepository : SessionRepository {
        private var activeProfile: UserProfile? = null

        override fun getActiveProfile(): UserProfile? = activeProfile
        override fun setActiveProfile(profile: UserProfile, rememberDevice: Boolean) { activeProfile = profile }
        override fun getActiveProfileId(): String? = activeProfile?.id
        override fun clearSession() { activeProfile = null }
        override fun isLoggedIn(): Boolean = activeProfile != null
        override fun getRememberedProfileId(): String? = null
        override fun forgetRememberedProfile() {}
    }
}
