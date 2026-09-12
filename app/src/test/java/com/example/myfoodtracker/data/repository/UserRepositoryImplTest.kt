package com.example.myfoodtracker.data.repository

import com.example.myfoodtracker.data.dao.UserProfileDao
import com.example.myfoodtracker.data.dao.UserWithGoal
import com.example.myfoodtracker.data.entity.UserDailyGoalEntity
import com.example.myfoodtracker.data.entity.UserProfileEntity
import com.example.myfoodtracker.domain.model.DailyGoal
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UserRepositoryImplTest {

    private lateinit var fakeDao: FakeUserProfileDao
    private lateinit var repository: UserRepositoryImpl

    @Before
    fun setUp() {
        fakeDao = FakeUserProfileDao()
        repository = UserRepositoryImpl(fakeDao)
    }

    @Test
    fun createProfile_persistsUserAndGoalsCorrectly() {
        val dailyGoal = DailyGoal(
            targetCalories = 2200.0,
            targetProteinG = 180.0,
            targetWaterMl = 3000
        )

        val profile = repository.createProfile("Georgii", "hashed_passcode_123", dailyGoal)

        assertNotNull(profile)
        assertTrue(profile.id.isNotEmpty())
        assertEquals("Georgii", profile.username)
        assertEquals("hashed_passcode_123", profile.passcodeHash)
        assertNotNull(profile.dailyGoal)
        assertEquals(profile.id, profile.dailyGoal?.profileId)
        assertEquals(2200.0, profile.dailyGoal?.targetCalories)
        assertEquals(180.0, profile.dailyGoal?.targetProteinG)
        assertNull(profile.dailyGoal?.targetCarbsG)
        assertEquals(3000, profile.dailyGoal?.targetWaterMl)

        assertTrue(repository.hasProfiles())
        assertEquals(1, repository.getProfiles().size)
    }

    @Test
    fun createProfile_withBlankGoals_persistsGoalsWithNullTargets() {
        val profile = repository.createProfile("Georgii", "hashed_passcode_123", DailyGoal())

        assertNotNull(profile)
        assertEquals("Georgii", profile.username)
        assertNotNull(profile.dailyGoal)
        assertEquals(profile.id, profile.dailyGoal?.profileId)
        assertNull(profile.dailyGoal?.targetCalories)
        assertNull(profile.dailyGoal?.targetProteinG)
    }

    @Test
    fun isUsernameTaken_returnsCorrectValue() {
        assertFalse(repository.isUsernameTaken("Georgii"))

        repository.createProfile("Georgii", "hashed_passcode_123", DailyGoal())

        assertTrue(repository.isUsernameTaken("Georgii"))
        assertTrue(repository.isUsernameTaken("georgii"))
        assertFalse(repository.isUsernameTaken("Alex"))
    }
}

class FakeUserProfileDao : UserProfileDao {
    private val users = mutableListOf<UserProfileEntity>()
    private val goals = mutableListOf<UserDailyGoalEntity>()

    override fun insertUser(user: UserProfileEntity) {
        users.add(user)
    }

    override fun insertGoal(goal: UserDailyGoalEntity) {
        goals.add(goal)
    }

    override fun insertUserWithGoal(user: UserProfileEntity, goal: UserDailyGoalEntity) {
        insertUser(user)
        insertGoal(goal)
    }

    override fun getUsersWithGoals(): List<UserWithGoal> {
        return users.map { user ->
            val goal = goals.firstOrNull { it.profileId == user.id }
            UserWithGoal(user, goal)
        }
    }

    override fun getUserCount(): Int = users.size

    override fun isUsernameTaken(username: String): Boolean {
        return users.any { it.username.equals(username, ignoreCase = true) }
    }
}
