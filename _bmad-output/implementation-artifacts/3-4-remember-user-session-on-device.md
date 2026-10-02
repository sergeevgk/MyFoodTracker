---
baseline_commit: f97e891048b6c08d13264426569109000a6538be
---

# Story 3.4: Remember User Device Session & Auto-Login

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user,
I want a checkbox on the login screen to remember my profile on this device,
so that I can skip the passcode login screen on subsequent app launches until I explicitly log out.

## Acceptance Criteria

1. **Given** the passcode authentication screen (`fragment_passcode_auth.xml`), **When** displayed, **Then** a checkbox with label "Remember user for this device" (`@string/auth_cb_remember_device`) is displayed below the passcode field and above the Unlock button, defaulting to unchecked.
2. **Given** the user selects a profile, checks "Remember user for this device", and enters the correct 4-digit passcode, **When** tapping Unlock, **Then** the active profile is set in memory AND the profile's ID is persisted to local device preferences (`SessionStorage`), and the app navigates to `DashboardFragment`.
3. **Given** a cold launch or restart of the app when a valid remembered profile ID exists in `SessionStorage`, **When** `PasscodeAuthViewModel` initializes, **Then** it verifies the profile exists in local Room DB, populates `SessionRepository.setActiveProfile(profile, rememberDevice = true)`, and emits `PasscodeAuthState.Authenticated(profile)` immediately, navigating to `DashboardFragment` without displaying or blocking on the login screen.
4. **Given** a cold launch when the remembered profile ID no longer exists in Room DB (stale or corrupted data), **When** initialization runs, **Then** it automatically clears the persisted session in `SessionStorage` and presents the login screen normally without crashing.
5. **Given** an authenticated user on `DashboardFragment`, **When** the user explicitly taps "Log Out", **Then** `LogoutUseCase` clears the in-memory active profile AND clears the persisted remembered profile in `SessionStorage`, and navigates to the login screen where subsequent launches will require authentication.
6. **Given** the user enters their passcode with the checkbox unchecked, **When** authenticating successfully, **Then** any existing remembered session is cleared from device storage, keeping the session strictly in-memory for this run only.
7. **Given** unit tests across data, domain, and presentation layers, **When** executed on the JVM, **Then** all tests pass with zero Android framework dependencies via `InMemorySessionStorage`.

## Tasks / Subtasks

- [x] **Task 1: Domain & Data Layer — `SessionStorage` & `SessionRepository`** (AC: #2, #3, #4, #5, #6, #7)
  - [x] Subtask 1.1: NEW `domain/repository/SessionStorage.kt` — pure Kotlin interface with `getRememberedProfileId(): String?`, `setRememberedProfileId(profileId: String?)`, and `clear()`.
  - [x] Subtask 1.2: NEW `data/local/SharedPrefsSessionStorage.kt` — Android `SharedPreferences` implementation storing `remembered_profile_id`.
  - [x] Subtask 1.3: MODIFY `domain/repository/SessionRepository.kt` — update `setActiveProfile(profile: UserProfile, rememberDevice: Boolean = false)` and add `getRememberedProfileId(): String?`.
  - [x] Subtask 1.4: MODIFY `data/repository/SessionRepositoryImpl.kt` — accept `sessionStorage: SessionStorage` constructor parameter. Implement `setActiveProfile(profile, rememberDevice)` to write or clear `sessionStorage`. Implement `clearSession()` to clear both in-memory `AtomicReference` and `sessionStorage.clear()`.
  - [x] Subtask 1.5: MODIFY `domain/usecase/AuthenticateUserUseCase.kt` — accept `rememberDevice: Boolean = false` in `invoke` and pass to `sessionRepository.setActiveProfile(profile, rememberDevice)`.
  - [x] Subtask 1.6: MODIFY `di/AppModule.kt` — provide `SharedPreferences`, `SharedPrefsSessionStorage`, and pass to `SessionRepositoryImpl`.

- [x] **Task 2: UI & Layout — Remember Device Checkbox** (AC: #1)
  - [x] Subtask 2.1: MODIFY `res/values/strings.xml` — add `<string name="auth_cb_remember_device">Remember user for this device</string>`.
  - [x] Subtask 2.2: MODIFY `res/layout/fragment_passcode_auth.xml` — add `MaterialCheckBox` `@+id/cbRememberDevice` below `tilPasscode` and above `btnUnlock` with `@color/primary_forest` buttonTint and `@color/text_primary` textColor.

- [x] **Task 3: Presentation & Navigation — ViewModel & Fragment** (AC: #2, #3, #4, #5, #6)
  - [x] Subtask 3.1: MODIFY `presentation/viewmodel/PasscodeAuthViewModel.kt` — update `loadProfiles()` to check `sessionRepository.getRememberedProfileId()`. If found and matching profile exists, activate profile and emit `PasscodeAuthState.Authenticated`. If profile not found, clear remembered session and proceed to normal content. Update `authenticate(passcode: String, rememberDevice: Boolean)`.
  - [x] Subtask 3.2: MODIFY `presentation/ui/auth/PasscodeAuthFragment.kt` — on `btnUnlock` click, pass `binding.cbRememberDevice.isChecked` to `viewModel.authenticate(...)`.

- [x] **Task 4: Unit Testing & Verification** (AC: #7)
  - [x] Subtask 4.1: MODIFY `app/src/test/.../SessionRepositoryImplTest.kt` — test rememberDevice persistence and clearing.
  - [x] Subtask 4.2: MODIFY `app/src/test/.../AuthenticateUserUseCaseTest.kt` — test passing `rememberDevice`.
  - [x] Subtask 4.3: MODIFY `app/src/test/.../PasscodeAuthViewModelTest.kt` — test auto-login when remembered user exists vs empty or invalid.
  - [x] Subtask 4.4: RUN `./gradlew testDebugUnitTest` and verify 100% test pass.

### Review Findings

**Decision needed — all resolved 2026-10-02**

- [x] [Review][Decision] RESOLVED — keep the wipe semantics, make it explicit at the call site (tracked as the first patch below): `setActiveProfile(profile)` default silently wipes a persisted remembered session at an untouched call site — `SessionRepository.kt:8` defaults `rememberDevice = false`, and `SessionRepositoryImpl` treats that as "forget this device" (`sessionStorage?.setRememberedProfileId(null)`). `ProfileSetupFragment.kt:84` calls `sessionRepository.setActiveProfile(state.userProfile)` and was never reviewed in this story. Consequence: creating a second profile silently cancels remember-me for the first, and any future `setActiveProfile(p)` call does the same. Needs a decision on intended semantics (tri-state / explicit forget API / accept the wipe).

- [x] [Review][Decision] RESOLVED — deferred as 3.4-D2, remember-me persists until explicit logout (accepted by user): remembered session has no expiry and the single storage slot locks other profiles out — `SharedPrefsSessionStorage.kt:19,26` persist a bare UUID with no timestamp, and `SessionStorage.kt` exposes no TTL. Once written, the only clearing paths are explicit logout or a re-auth without the checkbox — and re-auth is unreachable while auto-login is active. `PasscodeAuthState.Authenticated` carries no discriminator, so `PasscodeAuthFragment.kt:116-123` cannot tell a restored session from a passcode login and offers no "not you?" escape hatch; a multi-profile user must complete a full logout round-trip to switch. Neither expiry nor escape hatch is specified in the story — product intent required.

- [x] [Review][Decision] DISMISSED — current behaviour accepted by user: AC #3 not fully met, the login screen still renders before navigation, and process-death restore bounces through it — the auto-login check lives in `PasscodeAuthViewModel.loadProfiles()`, invoked from `PasscodeAuthFragment.onResume()` (`:44-47`), not from `init`. `_state` starts as `Loading` so the full passcode form (now including the new checkbox) is inflated before navigation, and `DashboardFragment.kt:67-71` explicitly navigates to the auth fragment when the in-memory `getActiveProfile()` is `null` — which is exactly the state after process death — before auto-login bounces straight back. AC #3 requires navigating "without displaying or blocking on the login screen". Where the restore belongs (Application/MainActivity vs. a Dashboard-side check) is a design call.

**Patch**

- [x] [Review][Patch] Make the `rememberDevice = false` wipe explicit at `ProfileSetupFragment.kt:84` [ProfileSetupFragment.kt:84]
- [x] [Review][Patch] Nullable-with-default dependencies fail open and violate Subtask 1.4 [`SessionRepositoryImpl.kt:9`, `PasscodeAuthViewModel.kt:26`]
- [x] [Review][Patch] Session restore bypasses the use-case layer, violating the project UseCase Pattern rule [`PasscodeAuthViewModel.kt:39-49`]
- [x] [Review][Patch] Stale remembered-ID cleanup is unreachable when the profile list is empty [`PasscodeAuthViewModel.kt:34-37`]
- [x] [Review][Patch] Stale-ID repair calls `clearSession()`, which also destroys any live in-memory session [`PasscodeAuthViewModel.kt:47`]
- [x] [Review][Patch] Logout's persisted-session purge has no assertion in either logout test [`AuthenticateUserUseCaseTest.kt:116`, `DashboardViewModelTest.kt:159`]
- [x] [Review][Patch] Vacuous assertion: fresh fake already has a null remembered ID [`AuthenticateUserUseCaseTest.kt:46`]
- [x] [Review][Patch] Auto-login test seeds the fake into the exact state it then asserts [`PasscodeAuthViewModelTest.kt:118-125`]
- [x] [Review][Patch] Logout revocation uses async `apply()`, so abrupt process death can restore the session [`SharedPrefsSessionStorage.kt:24`]
- [x] [Review][Patch] `SharedPreferences` registered in Koin by framework type with an inline pref-file literal [`AppModule.kt:52-53`]
- [x] [Review][Patch] Remember-me checkbox is not reset when the user switches profile [`PasscodeAuthFragment.kt:65-71`]
- [x] [Review][Patch] A persistence failure inside `runCatching` is surfaced to the user as "Incorrect passcode" [`AuthenticateUserUseCase.kt:11-25`]
- [x] [Review][Patch] Story Dev Agent Record still reads "Pending execution" despite `Status: done` [`3-4-remember-user-session-on-device.md:57-66`]

**Deferred**

- [x] [Review][Defer] `.agents/project_structure.md` was not updated for the two new source files — deferred, pre-existing
- [x] [Review][Defer] Remembered session has no TTL and no "not you?" escape hatch from auto-login — deferred, accepted by user: remember-me persists until explicit logout

## Dev Notes

- **Zero Plaintext Credentials**: Never store passcodes, hashes, or sensitive credentials in `SharedPreferences`. Only the UUID `profile_id` is stored.
- **JVM Unit Test Compatibility**: `SessionRepositoryImpl` depends on `SessionStorage`, allowing tests to provide an in-memory implementation without needing `Robolectric` or Android mocks.
- **Logout Completeness**: Calling `logoutUseCase()` on `DashboardViewModel` invokes `sessionRepository.clearSession()`, which guarantees both the active in-memory session and the remembered profile ID on disk are purged.

## Dev Agent Record

### Agent Model Used
Gemini 3.8 Flash (Medium)

### Debug Log References
- Code review fixes (2026-10-02): `./gradlew testDebugUnitTest :app:assembleDebug` — BUILD SUCCESSFUL in 31s; 17 test classes, 121 tests, 0 failures / 0 errors / 0 skipped. Resolved the pre-existing SDK-location warning by relying on `ANDROID_HOME`.

### Completion Notes List
- Story implemented with `SharedPrefsSessionStorage` persistence, auto-login restore, logout purge, and checkbox opt-in.
- Code review (2026-10-02) applied 13 patches: session restore extracted to `RestoreRememberedSessionUseCase`; `SessionStorage`/`SessionRepository` dependencies made non-null; stale-ID repair now uses `forgetRememberedProfile()` so it cannot destroy a live session; cleanup runs before the empty-profile early return; `clear()`/removal use `commit()` for durable logout revocation; session activation is best-effort after passcode validation; remember-me checkbox resets on profile switch; pref file name centralized as `SharedPrefsSessionStorage.PREFS_NAME`; tests strengthened (storage-only seeding, logout purge assertions, dedicated use-case tests).
- Deferred by user decision: no TTL on the remembered session and no "not you?" escape hatch from auto-login (3.4-D2); `.agents/project_structure.md` update remains unsatisfiable (3.4-D1).

### File List
- `app/src/main/java/com/example/myfoodtracker/domain/repository/SessionStorage.kt`
- `app/src/main/java/com/example/myfoodtracker/data/local/SharedPrefsSessionStorage.kt`
- `app/src/main/java/com/example/myfoodtracker/domain/repository/SessionRepository.kt`
- `app/src/main/java/com/example/myfoodtracker/data/repository/SessionRepositoryImpl.kt`
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/AuthenticateUserUseCase.kt`
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/RestoreRememberedSessionUseCase.kt`
- `app/src/main/java/com/example/myfoodtracker/di/AppModule.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/layout/fragment_passcode_auth.xml`
- `app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/PasscodeAuthViewModel.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/auth/PasscodeAuthFragment.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/auth/ProfileSetupFragment.kt`
- `app/src/test/java/com/example/myfoodtracker/data/repository/SessionRepositoryImplTest.kt`
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/AuthenticateUserUseCaseTest.kt`
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/RestoreRememberedSessionUseCaseTest.kt`
- `app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/PasscodeAuthViewModelTest.kt`
- `app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/DashboardViewModelTest.kt`
- `app/src/test/java/com/example/myfoodtracker/data/repository/MealRepositoryImplTest.kt`
- `_bmad-output/planning-artifacts/epics.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
