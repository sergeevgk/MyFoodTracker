---
story_id: 7.1
story_key: 7-1-passcode-kdf-upgrade-and-lockout
epic: 7
status: backlog
created: 2026-10-25
baseline_commit: 7fee17042735dbaf77040d24d5776cb22c976e4b
---

# Story 7.1: Passcode KDF Upgrade & Failed-Attempt Lockout

Status: backlog

## Story

As a user on a shared device,
I want my 4-digit passcode hardened against offline brute-force,
So that my nutrition data stays private even if the SQLite file is extracted.

## Acceptance Criteria

1. **Given** profile creation or passcode change, **When** the passcode is stored, **Then** it is hashed with PBKDF2-HMAC-SHA256 (≥ 100k iterations, per-hash random salt ≥ 8 bytes) using a prefixed storage format `pbkdf2$<iters>$<base64-salt>$<base64-hash>` (resolves **1.2-D1**, PasscodeHasher flagged in register).
2. **Given** an existing profile with legacy plain SHA-256 hex hash, **When** the user logs in successfully with the legacy format, **Then** the stored hash is transparently upgraded to the prefixed format (one-time rehash on successful login; before login, legacy AND prefixed formats both verify).
3. **Given** repeated wrong passcode entries, **When** the failure count grows, **Then** an escalating lockout delay applies (proposal: 1 attempt free → wait 1s → 5s → 30s cumulative schedule), the counter **persists** per profile so an app restart does not reset it, and the UI shows the remaining wait (resolves **1.2-D2**).
4. **Given** hash-compute cost, **When** verification runs on-device, **Then** the KDF work is off the UI-thread feel (synchronous Room era: acceptable run cost itself, but the auth flow must not skip the delay feedback) — measured decision left to dev notes; no coroutines (AD-2).
5. **Given** regression safety, **When** any passcode test suite runs, **Then** it covers: prefixed verify, legacy verify, legacy→prefixed upgrade, incorrect-passcode denial, and the backoff schedule arithmetic (pure function).

## Tasks / Subtasks

- [ ] Task 1: KDF + format (AC: #1, #2)
  - [ ] Extend `PasscodeHasher` (or new `PasscodeHasherV2`): `hash()` → prefixed format; `verify()` → format-dispatched (legacy hex fast path, prefixed PBKDF2 path).
  - [ ] Auto-upgrade hook at successful verification call site (profile creation / login use case).
  - [ ] `users.passcodeHash` column unchanged — prefixed string fits TEXT; no schema migration.
- [ ] Task 2: Lockout (AC: #3)
  - [ ] Persist failed-attempt counter + last-failure timestamp per profile (existing local table or a small user-meta table — note `AppDatabase` version bump if new table; `fallbackToDestructiveMigration()` local-dev rule applies).
  - [ ] Schedule as pure function `LockoutPolicy.waitFor(nextAttemptMillis): Duration` — unit-testable without Android.
  - [ ] UI: remaining-wait message on `PasscodeAuthFragment`.
- [ ] Task 3: Tests (AC: #5) — red → green via `PasscodeHasherTest` + `LockoutPolicyTest` (pure JVM).

## Dev Notes

- Source register: `deferred-work.md` **1.2-D1**, **1.2-D2** (both "pre-existing design decision" / "not specified in AC" — this story is the deliberate hardening).
- ⚠️ Manual on-device: verify legacy→prefixed upgrade on a **real existing profile** and the perceived lockout delay before releasing.
- If a new persistence table is needed, increment `AppDatabase.version` and use `fallbackToDestructiveMigration()` per project-context.md (local dev) — flag: destructive migration would wipe user data; prefer counter storage that avoids a schema change if feasible.
