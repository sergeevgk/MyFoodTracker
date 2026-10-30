---
story_id: 7.2
story_key: 7-2-session-and-presentation-state-hygiene
epic: 7
status: done
created: 2026-10-25
baseline_commit: b709aa5
---

# Story 7.2: Session & Presentation-State Hygiene

Status: done

## Story

As a privacy-conscious user,
I want passcode hashes kept out of presentation state and a visible way to decline a remembered session,
So that my credentials are never exposed beyond the auth layer and profile switching is a single tap.

## Acceptance Criteria

1. **Given** the passcode auth flow, **When** `PasscodeAuthState` and the `UserProfile` objects it carries (plus the in-memory session payload in `SessionRepositoryImpl`) are built, **Then** `passcodeHash` is not carried in any of them — hash verification happens inside domain/use-case internals over the data-layer entity, and fragments receive IDs, usernames, and goals only (resolves **1.2-D5**).
2. **Given** a remembered session on app start, **When** the auth screen shows the restored profile, **Then** it presents a "Continue as \<username\>" handler and a **"Not you?"** affordance that declines the restored session (clears the in-memory session AND the persisted remembered ID) and lands on the normal profile/passcode selection without a full logout round-trip (resolves **3.4-D2**).
3. **Given** a session TTL (e.g. 30 days), **When** the product decision gate is evaluated, **Then** it is **skipped by default** per the standing 3.4-D2 user decision ("remember until explicit logout"); implement only on explicit request — record the decision text in Dev Notes.
4. **Given** behavior parity, **When** the full unit-test suite runs, **Then** profile creation, passcode KDF/lockout (7.1), profile isolation (`profile_id` scoping), and remember-me save/restore/logout flows all pass unchanged (mechanical constructor fallout from AC #1 is allowed; behavior must not drift).

## Tasks / Subtasks

- [x] Task 1: Strip `passcodeHash` from presentation-reachable state (AC: #1)
  - [x] Remove `passcodeHash` from `domain/model/UserProfile.kt` (single source-of-truth model — see Dev Notes §Design Decision 1 for why no two-variant split).
  - [x] `UserRepositoryImpl.verifyPasscode` reads the hash from the data-layer entity directly: `userProfileDao.getUserById(profileId)?.user?.passcodeHash` (no detour through the domain model). Keep the transparent legacy→prefixed upgrade path exactly as in 7.1 (compare against the ENTITY's hash for the `FORMAT_PREFIX` check).
  - [x] `UserRepositoryImpl.createProfile` keeps its `passcodeHash` parameter (hash flows INTO the data layer; it just no longer flows back out in the returned `UserProfile`).
  - [x] `UserWithGoal.toDomain()` private mapping simply omits the hash (AD-3 mapping stays in the repository layer).
  - [x] Fix all direct-constructor fallout in main sources and tests (full inventory in Dev Notes §Fallout inventory).
- [x] Task 2: "Not you?" affordance for restored sessions (AC: #2)
  - [x] NEW `PasscodeAuthState.RestoredSession(profileId: String, username: String)` — emitted by `PasscodeAuthViewModel.loadProfiles()` when `RestoreRememberedSessionUseCase` returns a profile, replacing the current silent `Authenticated` emission for the restored path. Manual login keeps `Authenticated` (immediate nav) untouched.
  - [x] REUSE `LogoutUseCase` (already registered in `AppModule.kt`) for the decline: add it as a constructor param of `PasscodeAuthViewModel` and expose `declineRestoredSession()` = `logoutUseCase()` (clearSession = in-memory + persisted) followed by `loadProfiles()` → `Content`. Do NOT create a parallel "decline" use case.
  - [x] `PasscodeAuthFragment`: for `RestoredSession` hide the profile selector / passcode input / remember checkbox / Unlock (visibility `gone`) and show two new widgets in `fragment_passcode_auth.xml` (snake_case IDs): `@+id/btn_continue_as` ("Continue as %1$s") and `@+id/btn_not_you` ("Not you?"). Continue → guarded navigate to dashboard (same `currentDestination?.id` nav guard as the `Authenticated` branch). "Not you?" → `viewModel.declineRestoredSession()`.
  - [x] Cancel any running lockout `CountDownTimer` when a non-`Content` state arrives.
  - [x] New string resources in `strings.xml` (`auth_continue_as`, `auth_not_you`); colors/dimens via existing resources only (story 2.6 convention — no hardcoded values).
- [x] Task 3: TTL decision record (AC: #3)
  - [x] No code. Record the standing decision (see Dev Notes §TTL Decision Record) — the dev only confirms the text is present in the story's Dev Agent Record / deferred-work resolution note.
- [x] Task 4: Tests (AC: #1, #2, #4)
  - [x] `UserRepositoryImplTest`: verifyPasscode works via the new entity-level read; legacy-upgrade / not-rewritten cases still pass with the hash read from a DAO-level capture (the repository can no longer hand you the hash through a returned `UserProfile`).
  - [x] `PasscodeAuthViewModelTest`: restored session now maps to `RestoredSession` (update the previous restore→`Authenticated` assertions explicitly); decline → clears the fake `SessionRepository` (in-memory set to null, cleared remembered ID) and re-emits `Content` with profiles; manual-login `Authenticated` path unchanged.
  - [x] `CreateProfileUseCaseTest`: the stored-hash format assertion moves from the returned `UserProfile` to the fake repository's captured hash argument.
  - [x] Constructor fallout across `SessionRepositoryImplTest`, `MealRepositoryImplTest`, `DashboardViewModelTest`, `SettingsViewModelTest`, `RestoreRememberedSessionUseCaseTest`, `AuthenticateUserUseCaseTest`, `RecipeDaoTest` (androidTest source set — compile only, on-device execution per policy).
  - [x] `./gradlew compileDebugSources testDebugUnitTest` green (WSL: `JAVA_HOME`/`ANDROID_HOME` check first per AGENTS.md).
- [x] Task 5: Register + story hygiene
  - [x] Mark deferred-work **1.2-D5** and **3.4-D2** resolved in `deferred-work.md` (follow the 7.1 "Resolved by" section convention; 3.4-D2 note must state the TTL was skipped by user decision).
  - [x] Manual on-device verification checklist included in Completion Notes (no-emulator policy).

### Review Findings

- [x] [Review][Patch] Hide tvSubtitle instruction during RestoredSession gate and re-show in showAuthControls [app/src/main/java/com/example/myfoodtracker/presentation/ui/auth/PasscodeAuthFragment.kt:168]
- [x] [Review][Patch] Add defensive state guard to declineRestoredSession() [app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/PasscodeAuthViewModel.kt:148]
- [x] [Review][Patch] Remove unformatted string resource android:text from btn_continue_as in favor of tools:text [app/src/main/res/layout/fragment_passcode_auth.xml:56]

## Dev Notes

### Design Decision 1 — remove the hash at the source, not with a presentation copy (AC: #1)

Options were considered; **A was chosen**:

- **A (chosen):** Delete `passcodeHash` from the domain `UserProfile` field list. Everything presentation-facing (`PasscodeAuthState.Content.profiles`, `selectedProfile`, `Authenticated.profile`, `SessionRepositoryImpl.currentProfile` via `SessionRepository.getActiveProfile()`) carries `UserProfile`, so the hash disappears from ALL of them at once — including the in-memory session, which option B cannot fix. Zero new mapping layers.
- **B (rejected):** Keep the hash in the domain model and build a second presentation-only profile variant. This doubles mapping everywhere a fragment consumes profiles, leaves the hash inside `SessionRepositoryImpl`'s in-memory session (a register-1.2-D5 concern), and contradicts the AC's "auth compares hashes inside domain/use-case internals".

Why A is safe to compile through: verified at baseline `b709aa5` that NO main-source consumer reads `UserProfile.passcodeHash` except `UserRepositoryImpl` (create/verify/mapping) and `CreateProfileUseCase` (hash creation only) — presentation uses only `id`, `username`, `dailyGoal`. `verifyPasscode`'s hash compare + legacy-upgrade prefix check move to the entity-level read. `UserRepository.createProfile(username, passcodeHash, dailyGoal)` keeps the hash as an *input* — passing it INTO the data layer is not an exposure.

Security property delivered by A: the PBKDF2 hash exists only inside `UserRepositoryImpl` call stacks and the Room table; app memory (session singleton, LiveData states) never holds it beyond a verify call.

### Design Decision 2 — restored-session gate on the auth screen (AC: #2)

**Problem ($$current behavior):** `loadProfiles()` → remembered profile → `RestoreRememberedSessionUseCase` activates the session and persists remember — then emits `Authenticated`, and `PasscodeAuthFragment` navigates to dashboard instantly (PasscodeAuthFragment.kt:142-149). A multi-profile user must complete dashboard → Log Out → re-launch poetry to switch; there is no on-screen way to decline (register 3.4-D2 cites PasscodeAuthFragment.kt:116-123 → now the `Authenticated` branch + SharedPrefsSessionStorage.kt:19-27).

**Chosen:** keep `RestoreRememberedSessionUseCase` semantics (it still activates the session so "Continue" needs no passcode) but emit `RestoredSession(profileId, username)` instead of `Authenticated` for the remembered path. Fragment shows a Continue/"Not you?" gate. Decline = `LogoutUseCase`/`clearSession()` + `loadProfiles()` → normal `Content` state. The Remember checkbox was "remember me on NEXT launches" — declining must clear the persisted choice (that is the whole point of 3.4-D2), so storage.clear() semantics are correct.

- Rejected alternative: keep silent auto-login and put "Not you?" on the dashboard — contradicts AC #2 wording and adds dashboard chrome for an auth concern.
- Rejected alternative: new dedicated `DeclineRememberedSessionUseCase` — duplicates `LogoutUseCase`; reuse the registered one instead (wheel-reinvention guard).
- Note the deliberate UX change: remembered-session users now see one confirmation tap where they previously saw none. This is required by AC #2 ("a visible way to decline"); manual-entry login UX is untouched. Tests that asserted restore→`Authenticated` navigation MUST be updated (never quietly "fixed").

### Exact current state of files being modified (baseline b709aa5, verified 2026-10-30)

- `domain/model/UserProfile.kt` — 9 lines; `data class UserProfile(id, username, passcodeHash, createdAt = System.currentTimeMillis(), dailyGoal)`. Task 1 removes `passcodeHash`.
- `data/repository/UserRepositoryImpl.kt` — `verifyPasscode` (lines 56-70) currently fetches via `getProfileById` then `PasscodeHasher.verifyPasscode(passcode, user.passcodeHash)` with the 7.1 legacy-upgrade `runCatching` block keyed on `!user.passcodeHash.startsWith(PasscodeHasher.FORMAT_PREFIX)`; `toDomain()` (lines 80-88) maps `UserWithGoal` → domain including hash; `createProfile` returns a `UserProfile` carrying the input hash (lines 39-45).
- `data/dao/UserProfileDao.kt` — `getUserById(id): UserWithGoal?` (`@Embedded user` + `@Relation goal`); `updatePasscodeHash` (7.1) exists; all synchronous — NO new DAO methods needed for this story.
- `presentation/viewmodel/PasscodeAuthViewModel.kt` — sealed `PasscodeAuthState { Loading, NoProfiles, Content(profiles, selectedProfile, passcodeError, isAuthenticating, lockoutRemainingSeconds), Authenticated(profile) }`; `loadProfiles()` (lines 38-63) emits `Authenticated` on restore; constructor takes `userRepository, authenticateUserUseCase, restoreRememberedSessionUseCase, attemptStore` (3-arg use case + attempt store added by 7.1; VM stays Context-free — keep it so).
- `domain/usecase/RestoreRememberedSessionUseCase.kt` — returns remembered profile after `setActiveProfile(profile, rememberDevice = true)`; clears storage on stale IDs. For continue-parity this stays as-is.
- `data/repository/SessionRepositoryImpl.kt` — `AtomicReference<UserProfile?>` in-memory session; `clearSession()` nulls it AND `sessionStorage.clear()`.
- `domain/usecase/LogoutUseCase.kt` — 1-line `clearSession()`; already registered as factory (dashboard uses it).
- `data/local/SharedPrefsSessionStorage.kt` — `PREFS_NAME = "auth_session_prefs"`; `clear()` and setter both use `commit()` (crash-survival precedent — preserve).
- `presentation/ui/auth/PasscodeAuthFragment.kt` — `Authenticated` branch (lines 142-149) clears input + checkbox and navigates with the `currentDestination?.id == R.id.passcodeAuthFragment` guard; lockout `CountDownTimer` lives in the Fragment (7.1); `currentProfiles` caches the adapter list; `_binding = null` in `onDestroyView()`.
- `res/layout/fragment_passcode_auth.xml` — card with `tvTitle`, `tvSubtitle`, `tilProfileSelector`/`actvProfileSelector`, `tilPasscode`/`etPasscode`, `cbRememberDevice`, `btnUnlock`, `btnAddNewProfile`, `progressBar`. All colors/dimens/strings centralized (2.6).
- `res/values/strings.xml` — auth strings live together (`auth_title_welcome_back`, `auth_error_lockout` …); add the two new strings beside them.
- `di/AppModule.kt` — `PasscodeAttemptStore` singleton + 3-param `AuthenticateUserUseCase` factory (7.1); `LogoutUseCase` factory present. New VM param needs NO use-case registration (it exists).

### Fallout inventory (compile-level, expect Kotlin errors to enumerate exactly these)

Main sources: only `UserRepositoryImpl.kt` (2 constructor sites: `createProfile` return + `toDomain`) and `CreateProfileUseCase.kt` (no direct construction — verify only). Test sources constructing/hashing `UserProfile`: `SessionRepositoryImplTest` (~7 sites + assertion), `MealRepositoryImplTest`, `DashboardViewModelTest`, `SettingsViewModelTest`, `RestoreRememberedSessionUseCaseTest`, `AuthenticateUserUseCaseTest` (constructor + `FakeAuthUserRepository` delegation via `PasscodeHasher.verifyPasscode(passcode, profile.passcodeHash)` — switch to a captured raw hash), `CreateProfileUseCaseTest` (stored-format assertions), `UserRepositoryImplTest` (several hash assertions — re-anchor to DAO/fake captures), `RecipeDaoTest` (androidTest). Fix mechanically; no behavior drift.

### What must be preserved (do not break)

- **7.1 subsystem intact:** PBKDF2 verify + legacy fast path + auto-upgrade, `LockoutPolicy` short-circuit, persisted counters in `auth_session_prefs` (`lockout_<id>_*` keys, `commit()`), lockout countdown UI. Only the hash's *conduit* to verify changes (entity read), never its algorithm or failure messages.
- **"Incorrect passcode" non-distinction** for all manual-entry failure modes (use-case wording unchanged).
- **Restore semantics:** stale remembered ID cleanup (`forgetRememberedProfile` on missing profile) continues; Continue path still requires no passcode; login-explicit-logout round trip from the dashboard (LogoutUseCase) unchanged and loss-free.
- **SharedPreferences `auth_session_prefs` shared singleton:** `data/backup/PendingRestoreApplier.clearRememberedSession()` runs `prefs.edit().clear()` on the SAME file and will wipe the 7.1 lockout keys too (device handoff — acceptable today, do not add new keys that must survive restore without guarding; nothing new needs storage in this story).
- **Asynchronous-free world (AD-2):** no coroutines/`suspend`; everything synchronous on main.
- **Schema untouched:** no Room version change → `PRAGMA user_version` and the 5.2/6.3 backup validation decision table unaffected.
- **Lifecycle/UX invariants:** `viewLifecycleOwner` observation, nav guards, `_binding = null`, 48dp touch targets for the new buttons (reuse `@dimen/min_touch_target` or the 56dp-button precedent), TalkBack gains button labels via the new string resources automatically.
- **MVVM Context-free VM:** no strings/Context in the ViewModel; state carries data, Fragment renders.

### TTL Decision Record (AC: #3 — text to carry into deferred-work resolution)

"Session TTL (30 days) — SKIPPED by user decision recorded with 3.4-D2 (2026-10-02): remember-me persisting until explicit logout matches the story's intent. Story 7.2 (2026-10-30) implements the 'Not you?' decline affordance; a TTL is only to be implemented on explicit future request." — Copy this verbatim into the deferred-work 3.4-D2 resolution entry.

### Architecture compliance (hard rules from project-context.md / spine)

- Clean layers: model change in `domain/model/`; hash stays in `data/` (entity + repository internals); UI in `presentation/`. No Android imports in domain.
- AD-4: new VM dependency is an existing UseCase (`factory` binding) — no DI creep.
- AD-5/AD-6, naming (`snake_case` view IDs, `*ViewModel`, `@string/@color/@dimen` for every NEW user-visible value — 2.6 convention).
- DAO SQL isolation unchanged; no new DAO methods.

### Latest tech (evaluated 2026-10-30)

No new libraries and no version-sensitive dependencies are introduced: the work touches only pinned-stack APIs already exercised by this codebase (Kotlin data classes, `LiveData`/`ViewModel`, `SharedPreferences`, Material3 `MaterialButton`/`TextButton` from Material Components 1.10.0). Risks evaluated: none from Koin 3.5.6 constructor re-arguing (same pattern as 7.1's 3-arg use case), none from Room 3.0.3 (no schema/DAO change). No web-research surface exists for this story beyond the pinned stack.

### Testing standards summary

- JVM unit tests only in `test/`; hand-written fakes + JUnit4 + `InstantTaskExecutorRule`; **no MockK in the project** (project-context rule: MockK only when needed — not needed here; repository fakes follow `FakeAuthUserRepository` precedent).
- DAO integration tests belong to the `androidTest` source set with the in-memory builder and are NOT executed (no-emulator policy) — compile them via `assembleDebugAndroidTest` and flag for manual device runs.
- Machine-verified parity points: lockout behavior (7.1 tests pass untouched), remember-me save/restore/decline (new VM cases), hash-free auth state (the field's absence is compile-enforced; assert the observable states carry the expected data).

### Project Structure Notes

New files: none required (state added to the existing sealed class; no new use case; no new DAO method). Modified: `UserProfile.kt`, `UserRepositoryImpl.kt`, `PasscodeAuthViewModel.kt`, `PasscodeAuthFragment.kt`, `fragment_passcode_auth.xml`, `strings.xml`, and the listed test files. No `AppModule.kt` signature changes beyond the `PasscodeAuthViewModel` factory gaining the existing `LogoutUseCase` param.

### References

- [Source: epics.md#Epic-7 / Story 7.2 (ACs verbatim origin)]
- [Source: deferred-work.md — 1.2-D5 (hashes circulate UI states), 3.4-D2 (no TTL, no "not you?" escape hatch; user decision text)]
- [Source: 7-1-passcode-kdf-upgrade-and-lockout.md — Dev Notes patterns: prefs `commit()` precedent, no-MockK, VM Context-free, fallout workflow, "Resolved by" convention]
- [Source: 3-4-remember-user-session-on-device.md — remember-me semantics/AC origin]
- [Source: project-context.md — AD-1..AD-8, testing rules, build verification]
- [Codebase facts verified 2026-10-30 at baseline b709aa5 (files/lines cited above)]

### Manual on-device verification checklist (no-emulator policy — explicit notice)

JVM-verified by automation; the following need hands-on device passes: (a) remembered-session cold start → Continue gate renders, Continue reaches dashboard, next cold start re-gates; (b) "Not you?" → profile list without app restart, remembered choice cleared (relaunch does not auto-continue); (c) decline leaves the OTHER flows (manual login, lockout countdown) intact; (d) rotation during the RestoredSession gate preserves the state; (e) TalkBack announces the two new buttons sensibly.

## Dev Agent Record

### Agent Model Used

GLM-5.3-Flash Free (opencode) — BMAD dev-story workflow, 2026-10-30.

### Debug Log References

- Environment check (AGENTS.md step 1): `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`, Linux Java 21, `ANDROID_HOME=$HOME/Android/Sdk` — OK.
- `./gradlew compileDebugSources testDebugUnitTest` → `BUILD SUCCESSFUL in 1m 46s`; 386 tests, 0 failures, 0 errors (recounted from JUnit XML after the run).
- `./gradlew assembleDebugAndroidTest :app:assembleDebug` → `BUILD SUCCESSFUL in 18s` (androidTest source set compiles; no emulator execution per policy).

### Completion Notes List

- **AC #1 (hash stripped at the source):** `passcodeHash` removed from the domain `UserProfile`; `verifyPasscode` reads `userProfileDao.getUserById(profileId)?.user?.passcodeHash` (entity-level), 7.1 legacy→prefixed upgrade preserved keyed on the ENTITY hash; `createProfile` keeps the hash as INPUT param; `toDomain()` omits the hash. Compile-enforced hash-free state across `PasscodeAuthState` and the in-memory session.
- **AC #2 (decline gate):** `PasscodeAuthState.RestoredSession(profileId, username)` replaces the silent `Authenticated` restore path; `PasscodeAuthViewModel` gained the existing `LogoutUseCase` as ctor param and exposes `declineRestoredSession()` (clears in-memory session + persisted remembered ID → `loadProfiles()` → `Content`). fragment shows `btn_continue_as` / `btn_not_you` (56dp-button + `@dimen/min_touch_target` precedent); Content branch re-shows the hidden controls; a running lockout `CountDownTimer` is cancelled on any non-Content arrival so the wait can't leak across the gate. Manual-login `Authenticated` behavior untouched.
- **AC #3 (TTL gate):** Skipped by default per the standing 3.4-D2 user decision. Verbatim record: "Session TTL (30 days) — SKIPPED by user decision recorded with 3.4-D2 (2026-10-02): remember-me persisting until explicit logout matches the story's intent. Story 7.2 (2026-10-30) implements the 'Not you?' decline affordance; a TTL is only to be implemented on explicit future request." (Also present verbatim in `deferred-work.md` §Resolved by: story 7-2.)
- **AC #4 (parity):** 7.1 lockout, remember-me save/restore/logout, profile isolation, and creation flows all pass unchanged; fallout fixes were mechanical constructor deletions + fake-repository hash capture — zero behavior drift intended and none observed (386/386 green).
- **RecipeDaoTest note:** the fallout inventory expected a change there, but the androidTest file constructs `UserProfileEntity` (the data-layer entity, where the hash legitimately lives) — no change was needed; compile verified via `assembleDebugAndroidTest` (no device execution per the no-emulator policy).
- **Consistency guard in fakes:** `FakeAuthUserRepository`/`FakeUserRepository` now capture hashes at the repository boundary (`storedHashes` map); `CreateProfileUseCaseTest` asserts the stored `pbkdf2$…` format on the captured argument.
- **⚠️ Manual on-device verification required (no-emulator policy — JVM automation cannot cover these):**
  - (a) Remembered-session cold start → the Continue/"Not you?" gate renders; Continue reaches the dashboard; next cold start re-gates.
  - (b) "Not you?" → profile/passcode list without an app restart; remembered choice cleared (relaunch does NOT auto-continue).
  - (c) Decline leaves the other flows intact (manual login, 7.1 lockout countdown).
  - (d) Rotation while the RestoredSession gate is up preserves the state.
  - (e) TalkBack announces the two new buttons sensibly.
  - Plus existing manual 7.1 checks still hold (unlock with legacy passcode upgrades, lockout countdown UI).

### File List

- app/src/main/java/com/example/myfoodtracker/domain/model/UserProfile.kt
- app/src/main/java/com/example/myfoodtracker/data/repository/UserRepositoryImpl.kt
- app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/PasscodeAuthViewModel.kt
- app/src/main/java/com/example/myfoodtracker/presentation/ui/auth/PasscodeAuthFragment.kt
- app/src/main/java/com/example/myfoodtracker/di/AppModule.kt
- app/src/main/res/layout/fragment_passcode_auth.xml
- app/src/main/res/values/strings.xml
- app/src/test/java/com/example/myfoodtracker/data/repository/UserRepositoryImplTest.kt
- app/src/test/java/com/example/myfoodtracker/data/repository/SessionRepositoryImplTest.kt
- app/src/test/java/com/example/myfoodtracker/data/repository/MealRepositoryImplTest.kt
- app/src/test/java/com/example/myfoodtracker/domain/usecase/AuthenticateUserUseCaseTest.kt
- app/src/test/java/com/example/myfoodtracker/domain/usecase/CreateProfileUseCaseTest.kt
- app/src/test/java/com/example/myfoodtracker/domain/usecase/RestoreRememberedSessionUseCaseTest.kt
- app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/PasscodeAuthViewModelTest.kt
- app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/DashboardViewModelTest.kt
- app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/SettingsViewModelTest.kt
- _bmad-output/implementation-artifacts/deferred-work.md
- _bmad-output/implementation-artifacts/sprint-status.yaml

## Change Log

- 2026-10-30 — Story context fully elaborated from the 2026-10-25 backlog-sweep skeleton (ultimate context-engine pass): ACs locked to design decisions, baseline file states documented, fallout inventory compiled, TTL decision record fixed, tasks/tests enumerated. Status → ready-for-dev.
- 2026-10-30 — Implemented by dev-story workflow: `passcodeHash` removed from the domain `UserProfile` (entity-level verify read in `UserRepositoryImpl`, 7.1 upgrade path preserved) resolving 1.2-D5; `PasscodeAuthState.RestoredSession` gate + `declineRestoredSession()` (reused `LogoutUseCase`) + "Continue as …"/"Not you?" UI resolving the decline half of 3.4-D2 (TTL skipped by user decision, recorded verbatim); mechanical test fallout fixed; overload/parity suite green — 386 unit tests, 0 failures. Status → review.
