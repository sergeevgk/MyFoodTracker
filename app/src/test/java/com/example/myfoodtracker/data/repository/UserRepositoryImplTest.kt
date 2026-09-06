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
            calorieTarget = 2200,
            proteinTargetGrams = 180,
            waterTargetMl = 3000
        )

        val profile = repository.createProfile("Georgii", "hashed_passcode_123", dailyGoal)

        assertNotNull(profile)
        assertEquals(1L, profile.id)
        assertEquals("Georgii", profile.username)
        assertEquals("hashed_passcode_123", profile.passcodeHash)
        assertNotNull(profile.dailyGoal)
        assertEquals(2200, profile.dailyGoal?.calorieTarget)
        assertEquals(180, profile.dailyGoal?.proteinTargetGrams)
        assertNull(profile.dailyGoal?.carbTargetGrams)
        assertEquals(3000, profile.dailyGoal?.waterTargetMl)

        assertTrue(repository.hasProfiles())
        assertEquals(1, repository.getProfiles().size)
    }

    @Test
    fun createProfile_withoutGoals_persistsUserWithNullGoal() {
        val profile = repository.createProfile("Georgii", "hashed_passcode_123", null)

        assertNotNull(profile)
        assertEquals("Georgii", profile.username)
        assertNull(profile.dailyGoal)
    }
}

class FakeUserProfileDao : UserProfileDao {
    private val users = mutableListOf<UserProfileEntity>()
    private val goals = mutableListOf<UserDailyGoalEntity>()
    private var userIdCounter = 1L
    private var goalIdCounter = 1L

    override fun insertUser(user: UserProfileEntity): Long {
        val id = userIdCounter++
        val entity = user.copy(id = id)
        users.add(entity)
        return id
    }

    override fun insertGoal(goal: UserDailyGoalEntity): Long {
        val id = goalIdCounter++
        val entity = goal.copy(id = id)
        goals.add(entity)
        return id
    }

    override fun getUsersWithGoals(): List<UserWithGoal> {
        return users.map { user ->
            val goal = goals.firstOrNull { it.profileId == user.id }
            UserWithGoal(user, goal)
        }
    }

    override fun getUserCount(): Int = users.size
}
