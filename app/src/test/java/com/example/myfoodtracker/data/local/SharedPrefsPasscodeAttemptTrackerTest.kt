package com.example.myfoodtracker.data.local

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SharedPrefsPasscodeAttemptTrackerTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private lateinit var tracker: SharedPrefsPasscodeAttemptTracker

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        tracker = SharedPrefsPasscodeAttemptTracker(fakePrefs)
    }

    @Test
    fun getFailureState_whenEmpty_returnsNull() {
        assertNull(tracker.getFailureState("user-1"))
    }

    @Test
    fun recordFailure_firstTime_recordsCountOneAndTimestamp() {
        val now = 100_000L
        tracker.recordFailure("user-1", now)

        val state = tracker.getFailureState("user-1")
        assertNotNull(state)
        assertEquals(1, state?.first)
        assertEquals(now, state?.second)
    }

    @Test
    fun recordFailure_multipleTimes_incrementsCountAndUpdatesTimestamp() {
        tracker.recordFailure("user-1", 100_000L)
        tracker.recordFailure("user-1", 105_000L)
        tracker.recordFailure("user-1", 110_000L)

        val state = tracker.getFailureState("user-1")
        assertNotNull(state)
        assertEquals(3, state?.first)
        assertEquals(110_000L, state?.second)
    }

    @Test
    fun reset_removesRecordedFailureState() {
        tracker.recordFailure("user-1", 100_000L)
        assertNotNull(tracker.getFailureState("user-1"))

        tracker.reset("user-1")
        assertNull(tracker.getFailureState("user-1"))
    }

    @Test
    fun multipleProfiles_isolateStateCleanly() {
        tracker.recordFailure("user-1", 100_000L)
        tracker.recordFailure("user-1", 105_000L)
        tracker.recordFailure("user-2", 200_000L)

        val state1 = tracker.getFailureState("user-1")
        val state2 = tracker.getFailureState("user-2")

        assertEquals(2, state1?.first)
        assertEquals(105_000L, state1?.second)

        assertEquals(1, state2?.first)
        assertEquals(200_000L, state2?.second)

        // Reset user-1 does not affect user-2
        tracker.reset("user-1")
        assertNull(tracker.getFailureState("user-1"))
        assertNotNull(tracker.getFailureState("user-2"))
        assertEquals(1, tracker.getFailureState("user-2")?.first)
    }

    @Test
    fun statePersistsAcrossTrackerInstancesSharingSamePrefs() {
        tracker.recordFailure("user-1", 500_000L)

        // New tracker instance reading same underlying SharedPreferences
        val newTracker = SharedPrefsPasscodeAttemptTracker(fakePrefs)
        val state = newTracker.getFailureState("user-1")

        assertNotNull(state)
        assertEquals(1, state?.first)
        assertEquals(500_000L, state?.second)
    }
}

/**
 * Minimal in-memory SharedPreferences fake for JVM testing of preferences-backed storage.
 */
class FakeSharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any>()

    override fun getAll(): MutableMap<String, *> = data

    override fun getString(key: String?, defValue: String?): String? =
        data[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        @Suppress("UNCHECKED_CAST")
        (data[key] as? MutableSet<String>) ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        (data[key] as? Int) ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        (data[key] as? Long) ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        (data[key] as? Float) ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        (data[key] as? Boolean) ?: defValue

    override fun contains(key: String?): Boolean = data.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(data)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private class FakeEditor(private val sharedData: MutableMap<String, Any>) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any>()
        private val removed = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null && value != null) pending[key] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null && values != null) pending[key] = values
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) removed.add(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) sharedData.clear()
            for (key in removed) sharedData.remove(key)
            sharedData.putAll(pending)
        }
    }
}
