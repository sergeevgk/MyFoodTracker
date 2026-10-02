package com.example.myfoodtracker.data.repository

import com.example.myfoodtracker.data.db.FoodEntity
import com.example.myfoodtracker.data.db.MealDao
import com.example.myfoodtracker.data.db.MealEntryEntity
import com.example.myfoodtracker.data.db.MealWithFoods
import com.example.myfoodtracker.domain.model.DailyGoal
import com.example.myfoodtracker.domain.model.UserProfile
import com.example.myfoodtracker.domain.repository.SessionRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class MealRepositoryImplTest {

    private lateinit var fakeMealDao: FakeMealDao
    private lateinit var fakeSessionRepository: FakeSessionRepository
    private lateinit var repository: MealRepositoryImpl

    private val user1 = UserProfile(
        id = "user-1",
        username = "Alice",
        passcodeHash = "hash1",
        dailyGoal = DailyGoal()
    )

    private val user2 = UserProfile(
        id = "user-2",
        username = "Bob",
        passcodeHash = "hash2",
        dailyGoal = DailyGoal()
    )

    @Before
    fun setUp() {
        fakeMealDao = FakeMealDao()
        fakeSessionRepository = FakeSessionRepository()
        repository = MealRepositoryImpl(fakeMealDao, fakeSessionRepository)
    }

    @Test
    fun getMealEntries_noActiveSession_returnsEmptyList() {
        fakeSessionRepository.clearSession()
        val entries = repository.getMealEntries()
        assertTrue(entries.isEmpty())
    }

    @Test
    fun addMealEntry_withActiveSession_associatesMealWithActiveProfileId() {
        fakeSessionRepository.setActiveProfile(user1)

        val entries = repository.addMealEntry()

        assertEquals(1, entries.size)
        assertEquals(1, fakeMealDao.meals.size)
        assertEquals("user-1", fakeMealDao.meals[0].profileId)
    }

    @Test
    fun profileIsolation_differentUsersSeeOnlyTheirOwnMeals() {
        // User 1 adds 2 meals
        fakeSessionRepository.setActiveProfile(user1)
        repository.addMealEntry()
        repository.addMealEntry()
        assertEquals(2, repository.getMealEntries().size)

        // Switch to User 2: has 0 meals
        fakeSessionRepository.setActiveProfile(user2)
        assertEquals(0, repository.getMealEntries().size)

        // User 2 adds 1 meal
        repository.addMealEntry()
        assertEquals(1, repository.getMealEntries().size)

        // Total meals in DB is 3, but user 1 still sees only 2 and user 2 sees only 1
        assertEquals(3, fakeMealDao.meals.size)
        fakeSessionRepository.setActiveProfile(user1)
        assertEquals(2, repository.getMealEntries().size)
    }

    @Test
    fun updateMealEntry_onlyUpdatesMealIfProfileMatches() {
        fakeSessionRepository.setActiveProfile(user1)
        repository.addMealEntry()
        val user1Meal = repository.getMealEntries().first()

        // User 2 attempts to update User 1's meal
        fakeSessionRepository.setActiveProfile(user2)
        repository.updateMealEntry(user1Meal.id, "Malicious Title")

        // Switch back to User 1: meal title was not changed by user 2
        fakeSessionRepository.setActiveProfile(user1)
        val refreshed = repository.getMealEntries().first()
        assertEquals("", refreshed.title)

        // User 1 updates their own meal
        repository.updateMealEntry(user1Meal.id, "Healthy Breakfast")
        val updated = repository.getMealEntries().first()
        assertEquals("Healthy Breakfast", updated.title)
    }

    @Test
    fun deleteMealEntry_onlyDeletesMealIfProfileMatches() {
        fakeSessionRepository.setActiveProfile(user1)
        repository.addMealEntry()
        val user1Meal = repository.getMealEntries().first()

        // User 2 attempts to delete User 1's meal
        fakeSessionRepository.setActiveProfile(user2)
        repository.deleteMealEntry(user1Meal.id)

        // Switch back to User 1: meal was not deleted
        fakeSessionRepository.setActiveProfile(user1)
        assertEquals(1, repository.getMealEntries().size)

        // User 1 deletes their own meal
        repository.deleteMealEntry(user1Meal.id)
        assertEquals(0, repository.getMealEntries().size)
    }

    @Test
    fun getMealEntriesByDate_noActiveSession_returnsEmptyList() {
        fakeSessionRepository.clearSession()
        val entries = repository.getMealEntriesByDate("2026-09-30")
        assertTrue(entries.isEmpty())
    }

    @Test
    fun getMealEntriesByDate_filtersByProfileIdAndDate() {
        fakeSessionRepository.setActiveProfile(user1)
        val today = "2026-09-30"
        val yesterday = "2026-09-29"

        fakeMealDao.insertMeal(
            MealEntryEntity(
                id = "meal-1",
                profileId = "user-1",
                title = "User 1 Today Meal",
                date = today,
                time = "08:00"
            )
        )
        fakeMealDao.insertMeal(
            MealEntryEntity(
                id = "meal-2",
                profileId = "user-1",
                title = "User 1 Yesterday Meal",
                date = yesterday,
                time = "12:00"
            )
        )
        fakeMealDao.insertMeal(
            MealEntryEntity(
                id = "meal-3",
                profileId = "user-2",
                title = "User 2 Today Meal",
                date = today,
                time = "18:00"
            )
        )

        val todayEntries = repository.getMealEntriesByDate(today)
        assertEquals(1, todayEntries.size)
        assertEquals("meal-1", todayEntries[0].id)
        assertEquals("User 1 Today Meal", todayEntries[0].title)

        val yesterdayEntries = repository.getMealEntriesByDate(yesterday)
        assertEquals(1, yesterdayEntries.size)
        assertEquals("meal-2", yesterdayEntries[0].id)

        val emptyDateEntries = repository.getMealEntriesByDate("2026-10-01")
        assertTrue(emptyDateEntries.isEmpty())
    }

    @Test
    fun logQuickAdd_withActiveSession_createsMealWithSlotTitleAndFood() {
        fakeSessionRepository.setActiveProfile(user1)

        val entries = repository.logQuickAdd("Office lunch", 500.0, 20.0, 45.0, 15.0, "LUNCH", "2026-09-30")

        assertEquals(1, entries.size)
        assertEquals(1, fakeMealDao.meals.size)
        assertEquals(1, fakeMealDao.foods.size)
        assertEquals("LUNCH", fakeMealDao.meals[0].title)
        assertEquals("2026-09-30", fakeMealDao.meals[0].date)
        assertEquals("user-1", fakeMealDao.meals[0].profileId)
        assertEquals("Office lunch", fakeMealDao.foods[0].name)
        assertEquals(500.0, fakeMealDao.foods[0].calories, 0.001)
        assertEquals(20.0, fakeMealDao.foods[0].protein, 0.001)
        assertEquals(45.0, fakeMealDao.foods[0].carbs, 0.001)
        assertEquals(15.0, fakeMealDao.foods[0].fat, 0.001)
        assertEquals("LUNCH", entries[0].title)
    }

    @Test
    fun logQuickAdd_historicalDate_writesToGivenDateNotToday() {
        fakeSessionRepository.setActiveProfile(user1)

        repository.logQuickAdd("Old dinner", 700.0, 30.0, 60.0, 20.0, "DINNER", "2026-09-01")

        assertEquals("2026-09-01", fakeMealDao.meals[0].date)
        assertTrue(repository.getMealEntriesByDate("2026-09-30").isEmpty())
        assertEquals(1, repository.getMealEntriesByDate("2026-09-01").size)
    }

    @Test
    fun logQuickAdd_profileIsolation_otherUserSeesNothing() {
        fakeSessionRepository.setActiveProfile(user1)
        repository.logQuickAdd("Lunch", 500.0, 20.0, 45.0, 15.0, "LUNCH", "2026-09-30")

        fakeSessionRepository.setActiveProfile(user2)

        assertTrue(repository.getMealEntriesByDate("2026-09-30").isEmpty())
    }

    @Test
    fun logQuickAdd_noActiveSession_writesNothingAndReturnsEmpty() {
        fakeSessionRepository.clearSession()

        val entries = repository.logQuickAdd("Snack", 100.0, 1.0, 1.0, 1.0, "SNACK", "2026-09-30")

        assertTrue(entries.isEmpty())
        assertTrue(fakeMealDao.meals.isEmpty())
        assertTrue(fakeMealDao.foods.isEmpty())
    }

    @Test
    fun logQuickAdd_dateIsolation_eachDateHasOwnEntries() {        fakeSessionRepository.setActiveProfile(user1)
        repository.logQuickAdd("Lunch", 500.0, 20.0, 45.0, 15.0, "LUNCH", "2026-09-30")
        repository.logQuickAdd("Snack", 100.0, 1.0, 10.0, 5.0, "SNACK", "2026-09-29")

        assertEquals(1, repository.getMealEntriesByDate("2026-09-30").size)
        assertEquals(1, repository.getMealEntriesByDate("2026-09-29").size)
        assertEquals("LUNCH", repository.getMealEntriesByDate("2026-09-30")[0].title)
        assertEquals("SNACK", repository.getMealEntriesByDate("2026-09-29")[0].title)
    }

    @Test
    fun deleteMealEntry_removesParentAndCascadedFoods() {
        fakeSessionRepository.setActiveProfile(user1)
        repository.logQuickAdd("Lunch", 500.0, 20.0, 45.0, 15.0, "LUNCH", "2026-09-30")
        val mealId = fakeMealDao.meals[0].id
        assertEquals(1, fakeMealDao.foods.size)

        repository.deleteMealEntry(mealId)

        assertTrue(fakeMealDao.meals.isEmpty())
        assertTrue(fakeMealDao.foods.none { it.mealId == mealId })
        assertTrue(repository.getMealEntriesByDate("2026-09-30").isEmpty())
    }

    @Test
    fun deleteMealEntry_otherProfileCannotDeleteFoodsIntact() {
        fakeSessionRepository.setActiveProfile(user1)
        repository.logQuickAdd("Lunch", 500.0, 20.0, 45.0, 15.0, "LUNCH", "2026-09-30")
        val mealId = fakeMealDao.meals[0].id

        fakeSessionRepository.setActiveProfile(user2)
        repository.deleteMealEntry(mealId)

        fakeSessionRepository.setActiveProfile(user1)
        assertEquals(1, repository.getMealEntriesByDate("2026-09-30").size)
        assertEquals(1, fakeMealDao.foods.size)
    }

    @Test
    fun restoreMealEntry_reinsertsSameIdAndFoods() {
        fakeSessionRepository.setActiveProfile(user1)
        repository.logQuickAdd("Office lunch", 500.0, 20.0, 45.0, 15.0, "LUNCH", "2026-09-30")
        val deleted = repository.getMealEntriesByDate("2026-09-30").first()
        repository.deleteMealEntry(deleted.id)
        assertTrue(repository.getMealEntriesByDate("2026-09-30").isEmpty())

        val restored = repository.restoreMealEntry(deleted)

        assertEquals(1, restored.size)
        assertEquals(deleted.id, restored[0].id)
        assertEquals("LUNCH", restored[0].title)
        assertEquals("2026-09-30", restored[0].date)
        assertEquals(1, restored[0].foods.size)
        assertEquals("Office lunch", restored[0].foods[0].name)
        assertEquals(500.0, restored[0].foods[0].calories, 0.001)
        assertEquals(1, fakeMealDao.meals.size)
        assertEquals(1, fakeMealDao.foods.size)
    }

    @Test
    fun restoreMealEntry_noActiveSession_writesNothingAndReturnsEmpty() {
        fakeSessionRepository.setActiveProfile(user1)
        repository.logQuickAdd("Lunch", 500.0, 20.0, 45.0, 15.0, "LUNCH", "2026-09-30")
        val deleted = repository.getMealEntriesByDate("2026-09-30").first()

        fakeSessionRepository.clearSession()
        val result = repository.restoreMealEntry(deleted)

        assertTrue(result.isEmpty())
        // Pre-existing quick-add row untouched; restore added nothing.
        assertEquals(1, fakeMealDao.meals.size)
        assertEquals(1, fakeMealDao.foods.size)
    }

    @Test
    fun deleteMealEntry_dateIsolation_otherDatesUntouched() {
        fakeSessionRepository.setActiveProfile(user1)
        repository.logQuickAdd("Lunch", 500.0, 20.0, 45.0, 15.0, "LUNCH", "2026-09-30")
        repository.logQuickAdd("Snack", 100.0, 1.0, 10.0, 5.0, "SNACK", "2026-09-29")
        val todayId = repository.getMealEntriesByDate("2026-09-30").first().id

        repository.deleteMealEntry(todayId)

        assertTrue(repository.getMealEntriesByDate("2026-09-30").isEmpty())
        assertEquals(1, repository.getMealEntriesByDate("2026-09-29").size)
    }

    private class FakeSessionRepository : SessionRepository {
        private val current = AtomicReference<UserProfile?>(null)
        private var rememberedId: String? = null

        override fun getActiveProfile(): UserProfile? = current.get()
        override fun getActiveProfileId(): String? = current.get()?.id
        override fun setActiveProfile(profile: UserProfile, rememberDevice: Boolean) {
            current.set(profile)
            rememberedId = if (rememberDevice) profile.id else null
        }
        override fun getRememberedProfileId(): String? = rememberedId
        override fun forgetRememberedProfile() {
            rememberedId = null
        }
        override fun clearSession() {
            current.set(null)
            rememberedId = null
        }
        override fun isLoggedIn(): Boolean = current.get() != null
    }

    private class FakeMealDao : MealDao {
        val meals = mutableListOf<MealEntryEntity>()
        val foods = mutableListOf<FoodEntity>()

        override fun getMealsWithFoodsByProfileIdAndDate(profileId: String, date: String): List<MealWithFoods> {
            return meals.filter { it.profileId == profileId && it.date == date }.map { meal ->
                val associatedFoods = foods.filter { it.mealId == meal.id }
                MealWithFoods(meal, associatedFoods)
            }
        }

        override fun getMealsWithFoodsByProfileId(profileId: String): List<MealWithFoods> {
            return meals.filter { it.profileId == profileId }.map { meal ->
                val associatedFoods = foods.filter { it.mealId == meal.id }
                MealWithFoods(meal, associatedFoods)
            }
        }

        override fun getAllMealsWithFoods(): List<MealWithFoods> {
            return meals.map { meal ->
                val associatedFoods = foods.filter { it.mealId == meal.id }
                MealWithFoods(meal, associatedFoods)
            }
        }

        override fun insertMeal(meal: MealEntryEntity) {
            meals.removeAll { it.id == meal.id }
            meals.add(meal)
        }

        override fun insertFoods(foods: List<FoodEntity>) {
            this.foods.addAll(foods)
        }

        override fun updateMealTitle(id: String, profileId: String, title: String) {
            val index = meals.indexOfFirst { it.id == id && it.profileId == profileId }
            if (index != -1) {
                val existing = meals[index]
                meals[index] = existing.copy(title = title)
            }
        }

        override fun deleteMeal(id: String, profileId: String) {
            val removed = meals.removeAll { it.id == id && it.profileId == profileId }
            // Mirror real SQLite FK CASCADE: deleting the parent removes its foods.
            if (removed) {
                foods.removeAll { it.mealId == id }
            }
        }
    }
}
