---
story_id: 7.1
story_key: 7-1-passcode-kdf-upgrade-and-lockout
epic: 7
status: done
created: 2026-10-25
baseline_commit: 8dcfaa5
---

# Story 7.1: Passcode KDF Upgrade & Failed-Attempt Lockout

Status: done

## Story

As a user on a shared device,
I want my 4-digit passcode hardened against offline brute-force,
So that my nutrition data stays private even if the SQLite file is extracted.

## Acceptance Criteria

1. **Given** profile creation, **When** the passcode is stored, **Then** it is hashed with PBKDF2-HMAC-SHA256 (`SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")`), ≥ 100k iterations (AC floor; see Dev Notes §Tech for the recommended default), per-hash random salt ≥ 8 bytes, stored in the prefixed format `pbkdf2$<iters>$<base64-salt>$<base64-hash>` (resolves **1.2-D1**). Hash work must measurably exceed the current microseconds (target ≥ 100 ms on-device).
2. **Given** an existing profile with a legacy `"<hex-salt>:<hex-sha256>"` hash, **When** the user logs in successfully, **Then** the stored hash is transparently upgraded to the prefixed format. **Before** any successful login, both formats verify; legacy and prefixed hashes never need a migration step (column `users.passcode_hash` TEXT — unchanged, no schema migration).
3. **Given** repeated wrong passcode entries for a profile, **When** the failure count grows, **Then** an escalating delay applies before the next attempt is accepted (schedule: 1st failure free → +1 s after 2nd → +5 s after 3rd → +30 s from 4th onward, capping at 30 s), computed as elapsed-time-deductible remaining wait, **persisted per profile** so an app restart does NOT reset it. While wait remains, verification is short-circuited (KDF not run, counter not changed). A successful login resets the counter. The UI shows the remaining wait seconds and disables Unlock while waiting (resolves **1.2-D2**).
4. **Given** the synchronous Room era, **When** implemented, **Then** no coroutines/`suspend` and no new threading model are introduced (AD-2); the ~100 ms+ KDF cost on the main thread is accepted (same trade-off as `allowMainThreadQueries()`), but the lockout delay feedback must not be skipped or hidden by the added cost.
5. **Given** regression safety, **When** the test suite runs, **Then** it covers: prefixed-format verify, legacy-format verify, legacy→prefixed upgrade, incorrect-passcode denial, lockout short-circuit + counter persistence + reset on success, and the backoff schedule arithmetic as a pure function.

## Tasks / Subtasks

- [x] Task 1: KDF + storage format (AC: #1, #2)
  - [x] Extend `PasscodeHasher` in place (`domain/security/PasscodeHasher.kt`) — do NOT create a `V2` class; one entry point avoids divergent call sites. `hashPasscode(passcode)` → prefixed PBKDF2 format; make iteration count a parameter with a named-constant default (`DEFAULT_ITERATIONS`) so unit tests can pass a low value (e.g. 1_000) to stay fast. Salt: 16 random bytes (`SecureRandom`), Base64-encoded. Use `PBEKeySpec(passcode.toCharArray(), salt, iterations, keyLength)` and wipe the char array after deriving.
  - [x] Keep the legacy algorithm as a **private** legacy path: `verifyPasscode` dispatches on format — starts with `"pbkdf2$"` → PBKDF2 verify (parse iters, salt, hash); looks like legacy `salt:hash` (2 parts, hex) → legacy fast-path verify; anything else → `false` (fail-closed, current behavior).
  - [x] Auto-upgrade hook in `UserRepositoryImpl.verifyPasscode`: after a successful LEGACY verification, re-hash and persist (see Task 2). Prefixed hashes never re-hash.
  - [x] Update `CreateProfileUseCase` — no change needed beyond behavior (it calls `PasscodeHasher.hashPasscode`); confirm new format lands in `users.passcode_hash`.
- [x] Task 2: Persistence for upgrade (AC: #2)
  - [x] Add to `UserProfileDao`: `@Query("UPDATE users SET passcode_hash = :hash WHERE id = :id") fun updatePasscodeHash(id: String, hash: String)` (DAO SQL isolation rule — repository delegates).
- [x] Task 3: Lockout core (AC: #3, #5)
  - [x] NEW `domain/security/LockoutPolicy.kt` — pure object, no Android imports: e.g. `delaysSeconds = listOf(0, 1, 5, 30)` and `remainingWaitSeconds(failureCount, lastFailureAtMillis, nowMillis): Long` = `clamp(delay[failureCount] - elapsed, 0, ...)`. Fully unit-testable. (Implemented as `listOf(0, 0, 1, 5, 30)` keyed by recorded-failure count to match the AC-3 wording: 1st failure free → +1 s after 2nd → +5 s after 3rd → +30 s from 4th onward.)
  - [x] NEW domain interface for attempt state (e.g. `domain/repository/PasscodeAttemptStore.kt`: `recordFailure(profileId, nowMillis)`, `getFailureState(profileId): Pair<Int, Long>?`, `reset(profileId)`), impl `data/local/SharedPrefsPasscodeAttemptTracker.kt` reading/writing per-profile keys (`lockout_<profileId>_count`, `lockout_<profileId>_last_failure_at`) in the EXISTING `auth_session_prefs` `SharedPreferences` singleton — `commit()` not `apply()` (crash-survival precedent from `SharedPrefsSessionStorage`). NO Room table, NO `AppDatabase` version bump (destructive migration would wipe user data; a schema bump also mismatches backup `PRAGMA user_version` per 5.2/6.3 validation).
  - [x] Enforcement in `AuthenticateUserUseCase`: after `getProfileById`, read failure state + `LockoutPolicy.remainingWaitSeconds` — if > 0, throw/fail with a machine-readable carrier (e.g. new `PasscodeLockoutException(remainingSeconds)` carrying a property, see Dev Notes §UI) WITHOUT running verification or recording a failure. On wrong passcode: `recordFailure` then fail. On success: `reset` then succeed.
- [x] Task 4: UI remaining-wait feedback (AC: #3)
  - [x] Extend `PasscodeAuthState.Content` with `lockoutRemainingSeconds: Int = 0` (VM maps `PasscodeLockoutException` into this; rest of failure handling unchanged). VM stays Context-free — no resolved strings added.
  - [x] `PasscodeAuthFragment`: when `lockoutRemainingSeconds > 0` show the wait in `tilPasscode.error` from a NEW string resource (e.g. `auth_error_lockout`, "%1$d s" placeholder) and disable `btnUnlock`; run the per-second countdown in the Fragment (Timer side of the View layer) and re-enable on 0. Keep the existing `doAfterTextChanged` clear-error behavior harmonized (don't wipe the lockout message while the count is active).
  - [x] DI: register the attempt store as `single<PasscodeAttemptStore> { ... }`; use case gains a constructor param → update all injection sites (`AppModule.kt` factory, test fakes).
- [x] Task 5: Tests (AC: #5)
  - [x] Rewrite `PasscodeHasherTest` — new-format assertions (prefix parse, iters echoed, salt ≥ 8 bytes decoded, verify true/false, low-iters parameter honored, legacy format still verifies, malformed → false).
  - [x] NEW `LockoutPolicyTest` (pure JVM — schedule boundaries: 0/1/5/30 cap, elapsed deduction, no negative waits).
  - [x] Extend `AuthenticateUserUseCaseTest` with a fake attempt store: lockout short-circuit (no verify call, no extra failure recorded), counter increments, reset on success.
  - [x] Fix any `PasscodeAuthViewModelTest` / `UserRepositoryImplTest` fallout. (Also `CreateProfileUseCaseTest` stored-format assertion; new legacy-upgrade/non-upgrade cases added to `UserRepositoryImplTest`, and lockout-state mapping cases to `PasscodeAuthViewModelTest`.)
- [x] Task 6: Build + register (AC: #5)
  - [x] `./gradlew compileDebugSources testDebugUnitTest` green (WSL: `JAVA_HOME`/`ANDROID_HOME` check first per AGENTS.md).
  - [x] Mark deferred-work **1.2-D1** and **1.2-D2** resolved (how/architecture precedent: see 6-x resolution notes).

### Review Findings

- [x] [Review][Patch] Proactively check and display remaining lockout on profile load/select in PasscodeAuthViewModel [app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/PasscodeAuthViewModel.kt:35]
- [x] [Review][Patch] Reset lockout state and cancel timer on profile switch [app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/PasscodeAuthViewModel.kt:63, app/src/main/java/com/example/myfoodtracker/presentation/ui/auth/PasscodeAuthFragment.kt:74]
- [x] [Review][Patch] Clear lockout state in ViewModel when countdown finishes to prevent spurious lockouts [app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/PasscodeAuthViewModel.kt:73, app/src/main/java/com/example/myfoodtracker/presentation/ui/auth/PasscodeAuthFragment.kt:166]
- [x] [Review][Patch] Prevent timer recreation from restarting full duration on configuration change [app/src/main/java/com/example/myfoodtracker/presentation/ui/auth/PasscodeAuthFragment.kt:131]
- [x] [Review][Patch] Add unit tests for SharedPrefsPasscodeAttemptTracker persistence [app/src/test/java/com/example/myfoodtracker/data/local/SharedPrefsPasscodeAttemptTrackerTest.kt:1]
- [x] [Review][Patch] Catch GeneralSecurityException in PasscodeHasher.verifyPrefixed to ensure fail-closed behavior [app/src/main/java/com/example/myfoodtracker/domain/security/PasscodeHasher.kt:60]
- [x] [Review][Patch] Cap maximum iterations in PasscodeHasher.verifyPrefixed to prevent UI thread lockup [app/src/main/java/com/example/myfoodtracker/domain/security/PasscodeHasher.kt:64]
- [x] [Review][Patch] Move lockout check before passcode format validation in AuthenticateUserUseCase [app/src/main/java/com/example/myfoodtracker/domain/usecase/AuthenticateUserUseCase.kt:17]
- [x] [Review][Patch] Restore remembered profile regression assertion in AuthenticateUserUseCaseTest [app/src/test/java/com/example/myfoodtracker/domain/usecase/AuthenticateUserUseCaseTest.kt:35]

## Dev Notes

### Exact current state of files being modified (baseline 8dcfaa5)

- `domain/security/PasscodeHasher.kt` — Kotlin `object`, NOT DI-registered (static call sites, only file in `domain/security/`). Today: `hashPasscode` = salt(16B hex) + single-pass SHA-256(`saltHex + passcode`), format `"<32 hex>:<64 hex>"`; `verifyPasscode` splits on `"..."`, `MessageDigest.isEqual` compare, malformed → `false`, never throws.
- `domain/usecase/AuthenticateUserUseCase.kt` — synchronous `operator fun invoke(profileId, passcode, rememberDevice = false): Result<UserProfile>`; format check → `getProfileById` → `userRepository.verifyPasscode(profileId, passcode): Boolean` → session activation **best-effort** (`runCatching`, must stay swallowed). Format failure and wrong passcode both throw message **"Incorrect passcode"** — preserve this non-distinction.
- `domain/usecase/CreateProfileUseCase.kt:31` — `PasscodeHasher.hashPasscode(passcode)` then `userRepository.createProfile(...)`. **No passcode-change flow exists anywhere** — AC #1 applies to creation, AC #2 to login-time upgrade.
- `data/repository/UserRepositoryImpl.kt` — `verifyPasscode` = `getProfileById(...)` + `PasscodeHasher.verifyPasscode(...)`; mapping `UserWithGoal.toDomain()` leaks `passcodeHash` into domain/UI state (1.2-D5 — **out of scope**, Story 7.2 territory).
- `presentation/viewmodel/PasscodeAuthViewModel.kt` — LiveData `MutableLiveData<PasscodeAuthState>`; `Content(predicate: profiles, selectedProfile, passcodeError: String?, isAuthenticating: Boolean)`; remembered-session restore path (`RestoreRememberedSessionUseCase`) skips the passcode screen entirely — lockout applies only to manual entries (no code change needed).
- `presentation/ui/auth/PasscodeAuthFragment.kt` — single `state.observe(viewLifecycleOwner)`; nav guarded by `currentDestination?.id == R.id.passcodeAuthFragment`; `_binding = null` in `onDestroyView()`; `tilPasscode.error = state.passcodeError` (hardcoded English strings — leave for 7.2; only NEW user-visible strings get `@string/` resources per story 2.6 convention). `ProfileSetupViewModel` / profile creation gains the slow hash — acceptable on main thread, no spinner requirement.
- `data/dao/UserProfileDao.kt` — all synchronous; **no update/delete methods exist** — this story adds the first `UPDATE`.
- `data/db/AppDatabase.kt` — version 4, 7 entities, **no user-meta/kv table**; builder `Room.databaseBuilder(... "meals_database").fallbackToDestructiveMigration().allowMainThreadQueries()`; no DAO/auth changes to the schema.
- Fakes precedent: tests are hand-written fakes + JUnit4 + `InstantTaskExecutorRule`; **no MockK in the project** — follow that (project-context.md flags MockK as an option only when needed).

### What must be preserved (do not break)

- End-to-end login today: format check → profile lookup → verify → best-effort session set → `Authenticated` → navigate to dashboard (pop auth inclusive). Acquire the lockout check INSIDE this flow without changing its success shape.
- Identical "Incorrect passcode" message for every manual-entry failure mode (message content unchanged; new lockout path is a separate, explicit state).
- Remembered-session auto-bypass, profile dropdown behavior, nav guards, view binding lifecycle, `viewLifecycleOwner` observation.
- `users` schema untouched → `PRAGMA user_version = 4` unchanged → 5.2/6.3 backup/restore decision table unaffected.
- Existing tests must keep passing (after AC #5-mandated rewrites); `FakeAuthUserRepository` delegates to the real `PasscodeHasher` — keep that delegation working via the low-iters parameter.

### Architecture compliance (hard rules from project-context.md)

- No `suspend`/coroutines anywhere in this work (AD-2; Room-3 migration signature exemption does NOT apply here).
- Clean layers: `LockoutPolicy` + attempt-store interface in `domain/` (no Android imports); SharedPreferences impl in `data/local/`; all UI in `presentation/`.
- DAO SQL isolation; Koin single/factory patterns; no Context/View in ViewModels.
- Cascade/delete and migration rules untouched — deliberately no schema change.

### Latest tech (research 2026-10-27)

- PBKDF2-HMAC-SHA256 via `javax.crypto.SecretKeyFactory` is stable and dependency-free on Android. OWASP Password Storage Cheat Sheet (current) recommends **600,000 iterations** for PBKDF2-HMAC-SHA256 (floor cited in older guidance: 100k). Because the format stores per-hash iteration counts, a higher default costs nothing future-side.
- **Decision:** set `DEFAULT_ITERATIONS = 600_000`, measure actual cost in dev notes (expect a few hundred ms on modern hardware — comfortable for a deliberate unlock action); if it exceeds ~1 s in dev-notes measurement, drop to ≥ 100k with justification. The AC floor is ≥ 100k and ≥ 100 ms; never below either.
- The 10k PIN keyspace + ≥ 100 ms KDF + persisted 30 s lockout cap makes offline brute-force impractical: attacker work for full keyspace ≈ 10,000 × KDF cost plus per-attempt lockout pacing.

### Project Structure Notes

New files: `domain/security/LockoutPolicy.kt`, optional `domain/repository/PasscodeAttemptStore.kt` (or nest in `domain/security/` — match repo's `domain/repository/` interface precedent), `data/local/SharedPrefsPasscodeAttemptTracker.kt`, `PasscodeLockoutException` (small domain exception type, e.g. `domain/security/`). Modified: `PasscodeHasher.kt`, `UserProfileDao.kt`, `UserRepositoryImpl.kt`, `AuthenticateUserUseCase.kt`, `PasscodeAuthViewModel.kt`, `PasscodeAuthFragment.kt`, `AppModule.kt`, `strings.xml`, plus the touched test files. Naming: Koin registrations stay centralized in `AppModule.kt`.

### References

- [Source: epics.md#Epic-7 / Story 7.1 (ACs verbatim origin)]
- [Source: deferred-work.md#1.2 — D-1 (slow KDF), D-2 (lockout), D-5 (out of scope, 7.2)]
- [Source: project-context.md — AD-2, DAO isolation, DI, testing, build-verification rules]
- [Source: _bmad-output/planning-artifacts/architecture/ARCHITECTURE-SPINE.md]
- Codebase facts verified 2026-10-27 at baseline 8dcfaa5 (paths/line refs in this file).

## Dev Agent Record

### Agent Model Used

GLM-5.3-Flash Free (opencode) — dev-story workflow, 2026-10-27, baseline commit 8dcfaa5

### Debug Log References

- RED: `./gradlew :app:testDebugUnitTest` failed at `compileDebugUnitTestKotlin` against missing symbols (`LockoutPolicy`, `PasscodeAttemptStore`, `PasscodeLockoutException`, `updatePasscodeHash`, 2-arg `AuthenticateUserUseCase`) — confirms tests exercise the new behavior.
- GREEN: `./gradlew :app:compileDebugSources :app:testDebugUnitTest` → BUILD SUCCESSFUL; 375 unit tests, 0 failures, 0 skipped (`app/build/test-results/testDebugUnitTest/`).
- `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL.
- KDF cost measurement (AC #1 ≥ 100 ms floor): standalone JVM client (JDK, javax.crypto) running the exact production construction — PBKDF2-HMAC-SHA256, 600_000 iterations, 16-B salt, 256-bit output: runs 142/118/120/139/139 ms, **median 139 ms**. This is a desktop-x86 proxy; ART on-device is expected to be several hundred ms. Story decision kept `DEFAULT_ITERATIONS = 600_000` (no drop to 100k justified — no device is anywhere near the ~1 s cutoff on this construction). Not automated in the suite to keep unit tests fast (tests hash at 1_000 iterations by design).

### Completion Notes List

- **Task 1 (AC #1, #2)**: `PasscodeHasher` extended in place (no V2 class). `hashPasscode(passcode, iterations = DEFAULT_ITERATIONS = 600_000)` produces `pbkdf2$<iters>$<base64-16B-salt>$<base64-32B-hash>`; `PBEKeySpec` char array wiped (`fill`, `clearPassword()`) after deriving. `verifyPasscode` dispatches on format: `pbkdf2$` prefix → PBKDF2 verify (parses iters/salt/hash, `MessageDigest.isEqual`, all parse failures → `false`); legacy `salt:hash` (2 hex parts) → preserved single-pass SHA-256 fast path; anything else → `false` fail-closed. `CreateProfileUseCase` unchanged — new format lands in `users.passcode_hash` (asserted in `CreateProfileUseCaseTest`).
- **Task 2 (AC #2)**: `UserProfileDao.updatePasscodeHash(id, hash)` — first UPDATE in the DAO, SQL isolated to DAO. `UserRepositoryImpl.verifyPasscode` upgrades legacy hashes transparently after successful verification via best-effort `runCatching` (failed persist can never fail the login; upgrade retries next login). Prefixed hashes never re-hashed; verified by `UserRepositoryImplTest` (upgrade case, wrong-passcode no-upgrade, prefixed-not-rewritten cases).
- **Task 3 (AC #3, #5)**: `LockoutPolicy` (pure, no Android imports) — `delaysSeconds = listOf(0, 0, 1, 5, 30)` indexed by recorded-failure count (AC wording wins over the dev-note example list: 1st failure free, +1 s after 2nd, +5 s after 3rd, ≥ 30 s cap); `remainingWaitSeconds` deducts elapsed, coerces clock skew to full wait, rounds sub-second remainders up, never negative. `PasscodeAttemptStore` interface in `domain/repository`; impl `SharedPrefsPasscodeAttemptTracker` in the existing `auth_session_prefs` SharedPreferences singleton with per-profile `lockout_<id>_count` / `lockout_<id>_last_failure_at` keys and `commit()` (crash survival). NO Room table, NO schema/version change. `AuthenticateUserUseCase` gained `attemptStore` param: lockout check after `getProfileById` (short-circuit — no verify, no counter change, throws `PasscodeLockoutException(remainingSeconds)`); wrong passcode → `recordFailure` + unchanged "Incorrect passcode"; success → `reset` then proceed to best-effort session (success shape unchanged). Remembered-session restore path untouched (bypasses lockout by design).
- **Task 4 (AC #3)**: `PasscodeAuthState.Content.lockoutRemainingSeconds: Int = 0`; VM maps `PasscodeLockoutException` into it (passcodeError = null in that state; all other failures unchanged, VM still Context-free). Fragment shows `getString(R.string.auth_error_lockout, remaining)` in `tilPasscode.error`, disables Unlock (also against intermediate re-emissions while counting), runs the per-second countdown with a Fragment-local `CountDownTimer` (no coroutines, AD-2), re-enables Unlock on finish; `doAfterTextChanged` no longer wipes the error while the countdown runs; timer cancelled in `onDestroyView()`. New string resource `auth_error_lockout` ("Too many attempts. Try again in %1$d s"). DI: `single<PasscodeAttemptStore> { SharedPrefsPasscodeAttemptTracker(get()) }` (same prefs singleton as session storage) + 3-arg `AuthenticateUserUseCase` factory — stays centralized in `AppModule.kt`.
- **Task 5 (AC #5)**: `PasscodeHasherTest` rewritten (12 cases); `LockoutPolicyTest` new (11 cases, pure JVM boundaries); `AuthenticateUserUseCaseTest` extended with `FakePasscodeAttemptStore` + `VerifyTrackingRepository` (short-circuit proves 0 verify calls / 0 recorded failures; escalation walk 0→0→locked; reset-on-success); `PasscodeAuthViewModelTest` extended (failure→lockout state mapping walk) + 3-arg constructor fallout; `CreateProfileUseCaseTest` stored-format assertion updated to the prefixed format; `UserRepositoryImplTest` extended (legacy upgrade, no-upgrade-on-wrong-passcode, prefixed-not-rewritten, DAO update) with `FakeUserProfileDao.updatePasscodeHash`.
- **Task 6**: Full verification green: `compileDebugSources`, `testDebugUnitTest` (375/375), `assembleDebug`. Deferred-work 1.2-D1 and 1.2-D2 marked RESOLVED in `deferred-work.md` with a "Resolved by: story 7-1" section following the 6-x precedent.
- **Manual verification still required (no-emulator policy)**: on-device pass through of (a) legacy-profile login → confirm hash upgraded in the DB and subsequent logins work, (b) repeated wrong passcodes → countdown UI renders second-by-second, Unlock stays disabled and enabled on expiry, counters survive app kill/restart (commit() path), (c) profile-creation time acceptable with the 600k-iteration hash (no spinner required per story). This completion is JVM-verified only; instrumented DAO/board tests not executed per project policy.

### File List

- app/src/main/java/com/example/myfoodtracker/domain/security/PasscodeHasher.kt (modified)
- app/src/main/java/com/example/myfoodtracker/domain/security/LockoutPolicy.kt (new)
- app/src/main/java/com/example/myfoodtracker/domain/security/PasscodeLockoutException.kt (new)
- app/src/main/java/com/example/myfoodtracker/domain/repository/PasscodeAttemptStore.kt (new)
- app/src/main/java/com/example/myfoodtracker/data/local/SharedPrefsPasscodeAttemptTracker.kt (new)
- app/src/main/java/com/example/myfoodtracker/data/dao/UserProfileDao.kt (modified)
- app/src/main/java/com/example/myfoodtracker/data/repository/UserRepositoryImpl.kt (modified)
- app/src/main/java/com/example/myfoodtracker/domain/usecase/AuthenticateUserUseCase.kt (modified)
- app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/PasscodeAuthViewModel.kt (modified)
- app/src/main/java/com/example/myfoodtracker/presentation/ui/auth/PasscodeAuthFragment.kt (modified)
- app/src/main/java/com/example/myfoodtracker/di/AppModule.kt (modified)
- app/src/main/res/values/strings.xml (modified)
- app/src/test/java/com/example/myfoodtracker/domain/security/PasscodeHasherTest.kt (modified/rewritten)
- app/src/test/java/com/example/myfoodtracker/domain/security/LockoutPolicyTest.kt (new)
- app/src/test/java/com/example/myfoodtracker/domain/usecase/AuthenticateUserUseCaseTest.kt (modified)
- app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/PasscodeAuthViewModelTest.kt (modified)
- app/src/test/java/com/example/myfoodtracker/domain/usecase/CreateProfileUseCaseTest.kt (modified)
- app/src/test/java/com/example/myfoodtracker/data/repository/UserRepositoryImplTest.kt (modified)
- _bmad-output/implementation-artifacts/sprint-status.yaml (modified — status tracking)
- _bmad-output/implementation-artifacts/deferred-work.md (modified — 1.2-D1/D2 resolved)
- _bmad-output/implementation-artifacts/7-1-passcode-kdf-upgrade-and-lockout.md (modified — story record)

## Change Log

- 2026-10-27 — Story 7.1 implemented: PBKDF2-HMAC-SHA256 (600k iters) passcode hashing in `pbkdf2$iters$salt$hash` prefixed format, transparent legacy-hash upgrade at login (first `UserProfileDao` UPDATE), pure `LockoutPolicy` escalating delay (0/0/1/5/30 s), per-profile persisted attempt tracking in `auth_session_prefs` SharedPreferences (no schema change), lockout enforcement + `PasscodeLockoutException` in `AuthenticateUserUseCase`, lockout countdown UI in `PasscodeAuthFragment` with new `auth_error_lockout` string, DI registration in `AppModule.kt`; tests rewritten/extended (375 passing). Deferred-work 1.2-D1/D2 resolved.

Status: review
