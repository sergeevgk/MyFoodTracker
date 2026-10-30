package com.example.myfoodtracker.data.local

import android.content.SharedPreferences
import com.example.myfoodtracker.domain.repository.PasscodeAttemptStore

/**
 * SharedPreferences-backed passcode attempt store in the EXISTING
 * `auth_session_prefs` singleton (no Room table, no database version bump).
 * Keys are per-profile so the lockout survives process death and app restarts.
 */
class SharedPrefsPasscodeAttemptTracker(
    private val prefs: SharedPreferences
) : PasscodeAttemptStore {

    override fun recordFailure(profileId: String, nowMillis: Long) {
        val count = prefs.getInt(countKey(profileId), 0) + 1
        // commit() (not apply()) so the lockout counter survives abrupt process
        // death (same crash-survival precedent as SharedPrefsSessionStorage).
        prefs.edit()
            .putInt(countKey(profileId), count)
            .putLong(lastFailureAtKey(profileId), nowMillis)
            .commit()
    }

    override fun getFailureState(profileId: String): Pair<Int, Long>? {
        val count = prefs.getInt(countKey(profileId), 0)
        if (count <= 0) return null
        return count to prefs.getLong(lastFailureAtKey(profileId), 0L)
    }

    override fun reset(profileId: String) {
        prefs.edit()
            .remove(countKey(profileId))
            .remove(lastFailureAtKey(profileId))
            .commit()
    }

    private fun countKey(profileId: String) = COUNT_KEY_PREFIX.plus(profileId).plus(COUNT_KEY_SUFFIX)
    private fun lastFailureAtKey(profileId: String) =
        LAST_FAILURE_KEY_PREFIX.plus(profileId).plus(LAST_FAILURE_KEY_SUFFIX)

    private companion object {
        const val COUNT_KEY_PREFIX = "lockout_"
        const val COUNT_KEY_SUFFIX = "_count"
        const val LAST_FAILURE_KEY_PREFIX = "lockout_"
        const val LAST_FAILURE_KEY_SUFFIX = "_last_failure_at"
    }
}
