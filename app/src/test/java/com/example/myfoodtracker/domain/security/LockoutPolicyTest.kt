package com.example.myfoodtracker.domain.security

import org.junit.Assert.*
import org.junit.Test

class LockoutPolicyTest {

    private val now: Long = 10_000_000_000L

    // Schedule (AC 7.1-3): 1st failure free → +1 s after 2nd → +5 s after 3rd → +30 s from 4th onward

    @Test
    fun noFailures_noWait() {
        assertEquals(0L, LockoutPolicy.remainingWaitSeconds(0, now, now))
    }

    @Test
    fun firstFailure_isFree() {
        assertEquals(0L, LockoutPolicy.remainingWaitSeconds(1, now, now))
    }

    @Test
    fun secondFailure_waitOneSecond() {
        assertEquals(1L, LockoutPolicy.remainingWaitSeconds(2, now, now))
    }

    @Test
    fun thirdFailure_waitFiveSeconds() {
        assertEquals(5L, LockoutPolicy.remainingWaitSeconds(3, now, now))
    }

    @Test
    fun fourthFailure_waitsThirtySeconds_cap() {
        assertEquals(30L, LockoutPolicy.remainingWaitSeconds(4, now, now))
        assertEquals(30L, LockoutPolicy.remainingWaitSeconds(5, now, now))
        assertEquals(30L, LockoutPolicy.remainingWaitSeconds(10_000, now, now))
    }

    @Test
    fun elapsedSecondsAreDeducted() {
        // 5 s delay, 2 s elapsed → 3 s remaining
        assertEquals(3L, LockoutPolicy.remainingWaitSeconds(3, now, now + 2_000))
    }

    @Test
    fun elapsedBeyondDelay_noWait() {
        assertEquals(0L, LockoutPolicy.remainingWaitSeconds(3, now, now + 5_000))
        assertEquals(0L, LockoutPolicy.remainingWaitSeconds(3, now, now + 60_000))
    }

    @Test
    fun noNegativeWaits_whenClockRunsBackwards() {
        // lastFailure in the "future" (clock skew) must not produce a negative or growing wait
        assertEquals(5L, LockoutPolicy.remainingWaitSeconds(3, now + 60_000, now))
        assertEquals(1L, LockoutPolicy.remainingWaitSeconds(2, now + 60_000, now))
        assertEquals(30L, LockoutPolicy.remainingWaitSeconds(4, now + 60_000, now))
    }

    @Test
    fun negativeFailureCount_treatedAsNoFailures() {
        assertEquals(0L, LockoutPolicy.remainingWaitSeconds(-3, now, now))
    }

    @Test
    fun subSecondRemainder_roundsUpToOneSecond() {
        // 1 s delay with 400 ms elapsed: 600 ms still owed → reported as 1 s
        assertEquals(1L, LockoutPolicy.remainingWaitSeconds(2, now, now + 400))
        // boundary: exactly 1000 ms elapsed → wait over
        assertEquals(0L, LockoutPolicy.remainingWaitSeconds(2, now, now + 1_000))
    }

    @Test
    fun delaysList_matchesSchedule() {
        assertEquals(listOf(0, 0, 1, 5, 30), LockoutPolicy.delaysSeconds)
    }
}
