package com.example.myfoodtracker.domain.repository

/**
 * Per-profile bookkeeping of failed passcode attempts, consumed by
 * [LockoutPolicy][com.example.myfoodtracker.domain.security.LockoutPolicy].
 *
 * Deliberately NOT a Room table: a schema bump would trigger the destructive
 * fallback (wiping user data) and shift `PRAGMA user_version`, which the 5.2/6.3
 * backup/restore decision table depends on. Implementations persist externally
 * (e.g. SharedPreferences) so an app restart does NOT reset the lockout.
 */
interface PasscodeAttemptStore {

    /**
     * Records one failed passcode attempt for [profileId] at [nowMillis].
     */
    fun recordFailure(profileId: String, nowMillis: Long)

    /**
     * Returns `(failureCount, lastFailureAtMillis)` for [profileId], or `null`
     * when no failures are recorded.
     */
    fun getFailureState(profileId: String): Pair<Int, Long>?

    /**
     * Clears the recorded state for [profileId] (after a successful login).
     */
    fun reset(profileId: String)
}
