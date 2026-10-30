package com.example.myfoodtracker.domain.security

/**
 * Machine-readable lockout signal (distinct from the non-distinguishing
 * "Incorrect passcode" failures): the attempt was blocked while a lockout
 * wait was still running. [remainingSeconds] feeds the UI countdown.
 */
class PasscodeLockoutException(val remainingSeconds: Int) :
    Exception("Passcode attempts locked, wait ".plus(remainingSeconds).plus(" s"))
