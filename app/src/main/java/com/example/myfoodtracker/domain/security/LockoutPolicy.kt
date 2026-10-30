package com.example.myfoodtracker.domain.security

/**
 * Pure escalating-delay schedule for failed passcode attempts (deferred-work 1.2-D2).
 *
 * Index is the number of failures already recorded for a profile:
 * the 1st failure is free, the next attempt waits +1 s after the 2nd,
 * +5 s after the 3rd, and +30 s from the 4th onward (capped at 30 s).
 * No Android imports — fully unit-testable (AD-2 compliant, synchronous era).
 */
object LockoutPolicy {

    /** Delay (seconds) owed before the NEXT attempt, indexed by recorded failure count. */
    val delaysSeconds = listOf(0, 0, 1, 5, 30)

    /** Cap of the schedule (30 seconds, per AC 7.1-3). */
    val maxDelaySeconds: Int = delaysSeconds.last()

    fun delayForFailureCount(failureCount: Int): Int {
        if (failureCount <= 0) return 0
        return delaysSeconds[failureCount.coerceAtMost(delaysSeconds.lastIndex)]
    }

    /**
     * Remaining wait in seconds, elapsed time deducted, never negative.
     * Sub-second remainders round up (a partial second still blocks the attempt).
     */
    fun remainingWaitSeconds(failureCount: Int, lastFailureAtMillis: Long, nowMillis: Long): Long {
        val delaySeconds = delayForFailureCount(failureCount)
        if (delaySeconds <= 0) return 0
        val elapsedMillis = (nowMillis - lastFailureAtMillis).coerceAtLeast(0)
        val remainingMillis = delaySeconds * 1000L - elapsedMillis
        if (remainingMillis <= 0) return 0
        return (remainingMillis + 999) / 1000
    }
}
