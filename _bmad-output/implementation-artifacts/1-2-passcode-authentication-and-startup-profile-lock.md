---
baseline_commit: f83f8993b5d2661ce50d2565a635552e0580bde4
---
# Story 1.2: Passcode Authentication & Startup Profile Lock

Status: done

## Story

As a registered user,
I want the app to prompt for my passcode on startup and allow profile switching/logout,
so that my personal nutrition data remains private on shared devices.

## Acceptance Criteria

1. **Given** a cold or warm app launch, **When** profiles exist in the local database, **Then** the application presents the `PasscodeAuthFragment` prompting for a 4-digit PIN; **If** no profiles exist, **Then** the application redirects to `ProfileSetupFragment`. [Source: PRD FR-2, UX EXPERIENCE.md §State Patterns]
2. **Given** the passcode authentication screen, **When** the user enters the correct 4-digit passcode for the selected profile, **Then** access is granted, the user profile is registered in the active session, and navigation proceeds to the main dashboard (`DashboardFragment`). [Source: PRD FR-2, UJ-1]
3. **Given** the passcode authentication screen, **When** the user enters an incorrect passcode or fewer than 4 digits, **Then** access is denied, the input field is highlighted with an error state, and "Incorrect passcode" is displayed in red without advancing navigation. [Source: PRD FR-2, UX EXPERIENCE.md §State Patterns]
4. **Given** multiple profiles exist on the device, **When** viewing the authentication screen, **Then** the user can select which profile to unlock via a profile selector dropdown/picker, enter that profile's passcode, or tap an "Add New Profile" action to create another profile. [Source: PRD FR-2, UX EXPERIENCE.md §Information Architecture]
5. **Given** an authenticated user, **When** data queries are executed across the app, **Then** all database queries and repository operations strictly isolate data by the active session's `profile_id` (enforcing zero cross-profile data leakage). [Source: PRD FR-2, NFR-5, ARCH-1]
6. **Given** an authenticated session on the dashboard or settings, **When** the user triggers the logout action, **Then** the active session is cleared and the app returns to `PasscodeAuthFragment`, locking the profile until re-authenticated. [Source: PRD FR-2]

## Tasks / Subtasks

- [x] **Task 1: Domain Session & Authentication Contracts** (AC: #2, #5, #6)
  - [x] Subtask 1.1: Create `SessionRepository.kt` in `domain/repository/` (`getActiveProfile()`, `getActiveProfileId()`, `setActiveProfile()`, `clearSession()`, `isLoggedIn()`)
  - [x] Subtask 1.2: Standardize salted SHA-256 verification and hashing into reusable utility/domain helper `PasscodeHasher.kt`
  - [x] Subtask 1.3: Update `UserRepository.kt` with `getProfileById(id: String): UserProfile?` and `verifyPasscode(profileId: String, passcode: String): Boolean`
  - [x] Subtask 1.4: Create `AuthenticateUserUseCase.kt` in `domain/usecase/` exposing `operator fun invoke(profileId: String, passcode: String): Result<UserProfile>`
  - [x] Subtask 1.5: Create `LogoutUseCase.kt` in `domain/usecase/` exposing `operator fun invoke()`

- [x] **Task 2: Data Layer Persistence & Session Implementation** (AC: #2, #5, #6)
  - [x] Subtask 2.1: Update `UserProfileDao.kt` with `@Query("SELECT * FROM users WHERE id = :id")` and `fun getUserById(id: String): UserWithGoal?`
  - [x] Subtask 2.2: Implement updated methods in `UserRepositoryImpl.kt` leveraging `PasscodeHasher`
  - [x] Subtask 2.3: Create `SessionRepositoryImpl.kt` in `data/repository/` with thread-safe in-memory session holding `AtomicReference<UserProfile?>`

- [x] **Task 3: Centralized Koin Dependency Injection Registration** (AC: #1, #2, #6)
  - [x] Subtask 3.1: Register `SessionRepository` as `single` in `di/AppModule.kt`
  - [x] Subtask 3.2: Register `AuthenticateUserUseCase` and `LogoutUseCase` as `factory` in `di/AppModule.kt`
  - [x] Subtask 3.3: Register `PasscodeAuthViewModel` as `viewModel` in `di/AppModule.kt`

- [x] **Task 4: Presentation Layer UI, ViewModel & Navigation Routing** (AC: #1, #2, #3, #4, #6)
  - [x] Subtask 4.1: Create `fragment_passcode_auth.xml` with M3 organic palette (`#F8F9FA` base, `#FFFFFF` card, `#1B4D3E` button, 28dp pill button, 4-digit obscured PIN input, profile dropdown selector, and "Add New Profile" link)
  - [x] Subtask 4.2: Create `PasscodeAuthViewModel.kt` in `presentation/viewmodel/` managing `LiveData<PasscodeAuthState>` (loading profiles, profile selection, PIN submission, error handling)
  - [x] Subtask 4.3: Create `PasscodeAuthFragment.kt` in `presentation/ui/auth/` observing `PasscodeAuthState` with View Binding lifecycle safety (`_binding = null` in `onDestroyView()`)
  - [x] Subtask 4.4: Create placeholder `DashboardFragment.kt` and `fragment_dashboard.xml` in `presentation/ui/dashboard/` displaying welcome banner, active `profile_id`, and "Log Out" button
  - [x] Subtask 4.5: Update `nav_graph.xml` with `passcodeAuthFragment`, `profileSetupFragment`, and `dashboardFragment` with proper navigation actions and backstack management (`popUpTo` / `inclusive`)
  - [x] Subtask 4.6: Update `MainActivity.kt` and `ProfileSetupFragment.kt` to coordinate initial launch routing and transition upon new profile creation

- [x] **Task 5: Comprehensive Unit & Component Tests** (AC: #1, #2, #3, #4, #5, #6)
  - [x] Subtask 5.1: Unit tests for `AuthenticateUserUseCaseTest.kt` (valid passcode, incorrect passcode, non-existent profile)
  - [x] Subtask 5.2: Unit tests for `SessionRepositoryImplTest.kt` (set, get, clear, isolation check)
  - [x] Subtask 5.3: Unit tests for `UserRepositoryImplTest.kt` updates (getting user by ID and verifying passcode hash)
  - [x] Subtask 5.4: Unit tests for `PasscodeAuthViewModelTest.kt` (profile list loading, selection changes, valid authentication success, error on wrong PIN)

### Review Findings

- [x] [Review][Patch] **DN-1: Meal Data Profile Isolation (AC #5)** — Implemented: added profile_id foreign key, column, and index to MealEntryEntity, filtered Room queries in MealDao, updated MealRepositoryImpl to use SessionRepository.getActiveProfileId(), and added MealRepositoryImplTest.
- [x] [Review][Patch] **DN-2: Error Message for Short Passcode (AC #3)** — Fixed: PasscodeAuthViewModel and AuthenticateUserUseCase display "Incorrect passcode" on fewer than 4 digits or non-numeric input per AC #3.
- [x] [Review][Patch] **P-1: Process Death Bypasses Auth Lock** [DashboardFragment.kt:35-40] — Fixed: added null check on activeProfile in onViewCreated() to redirect to passcodeAuthFragment.
- [x] [Review][Patch] **P-2: Backstack Leak on "Add New Profile" Flow** [nav_graph.xml:27-30] — Fixed: action_profileSetupFragment_to_dashboardFragment pops up to passcodeAuthFragment inclusive.
- [x] [Review][Patch] **P-3: LiveData Re-emission Crashes on Config Change** [PasscodeAuthFragment.kt, ProfileSetupFragment.kt] — Fixed: guarded navigation calls with currentDestination checks.
- [x] [Review][Ignored] **P-4: ProfileSetupFragment Missing from AppBarConfiguration** [MainActivity.kt:41-43] — Ignored per user request.
- [x] [Review][Ignored] **P-5: Double-Click Navigation Crash** [PasscodeAuthFragment.kt:60-62] — Ignored per user request.
- [x] [Review][Ignored] **P-6: Fragment Directly Mutates Session** [ProfileSetupFragment.kt:84] — Ignored per user request.
- [x] [Review][Ignored] **P-7: Redundant DB Query on Passcode Verify** [AuthenticateUserUseCase.kt:16-19] — Ignored per user request.
- [x] [Review][Ignored] **P-8: loadProfiles() Clears Error on Rotation** [PasscodeAuthViewModel.kt:40-45] — Ignored per user request.
- [x] [Review][Patch] **P-9: Adapter Rebuilt on Every Keystroke** [PasscodeAuthFragment.kt:96-105] — Fixed: skip state emission when error is already null, and cache adapter instantiation.
- [x] [Review][Patch] **P-10: Dead Navigation Action + Missing Newline** [nav_graph.xml] — Fixed: removed unused action and added trailing newline.
- [x] [Review][Patch] **P-11: SQLite Exceptions Unhandled** [AuthenticateUserUseCase.kt:11-25] — Fixed: wrapped invocation in runCatching and added unit test.
- [x] [Review][Defer] **D-1: Single-iteration SHA-256 for 4-digit PINs** [PasscodeHasher.kt] — deferred, pre-existing design decision per spec
- [x] [Review][Defer] **D-2: No attempt rate limiting / lockout** [AuthenticateUserUseCase.kt] — deferred, not in AC scope
- [x] [Review][Defer] **D-3: No logout in settings** [MainActivity.kt] — deferred, no settings screen exists yet
- [x] [Review][Defer] **D-4: Dashboard has no ViewModel** [DashboardFragment.kt] — deferred, placeholder for Epic 2
- [x] [Review][Defer] **D-5: Passcode hashes in UI states** [UserProfile.kt] — deferred, pre-existing model design

## Dev Notes

- **Architecture Rules (AD-1..AD-7)**:
  - `domain/` must contain 0 Android framework dependencies (`android.*`) or Room annotations.
  - Room DAO methods run synchronously on the main thread via `.allowMainThreadQueries()` in `AppDatabase` builder (AD-2). No `suspend` functions.
  - Repositories map Room entities to domain models via private extensions before returning data (AD-3).
  - UseCase exposes `operator fun invoke()` and is registered as Koin `factory` (AD-4).
  - Fragment clears `_binding = null` in `onDestroyView()` and observes LiveData via `viewLifecycleOwner` (AD-5).
  - All Koin dependencies registered centrally in `di/AppModule.kt` (AD-6).
  - `SessionRepository` provides `getActiveProfileId(): String?` which downstream repositories (`IntakeRepository`, `WaterLogDao`) will use to enforce strict profile data isolation (`profile_id`).

- **M3 Styling & Tokens**:
  - Surface Background: `#F8F9FA` (`surface-base`)
  - Raised Card: `#FFFFFF` (`surface-raised`)
  - Accent Primary: `#1B4D3E` (Deep Emerald Green)
  - Error Color: `#BA1A1A`
  - Corner Radii: 8dp (`rounded/sm`) for text inputs, 12dp (`rounded/md`) for cards, 28dp (`rounded/xl`) for primary submit button.

### Project Structure Notes

Files to create or update in `app/src/main/java/com/example/myfoodtracker/`:
- `domain/security/PasscodeHasher.kt` [NEW]
- `domain/repository/SessionRepository.kt` [NEW]
- `domain/repository/UserRepository.kt` [MODIFY]
- `domain/usecase/AuthenticateUserUseCase.kt` [NEW]
- `domain/usecase/LogoutUseCase.kt` [NEW]
- `domain/usecase/CreateProfileUseCase.kt` [MODIFY - delegate to PasscodeHasher]
- `data/dao/UserProfileDao.kt` [MODIFY]
- `data/repository/UserRepositoryImpl.kt` [MODIFY]
- `data/repository/SessionRepositoryImpl.kt` [NEW]
- `presentation/viewmodel/PasscodeAuthViewModel.kt` [NEW]
- `presentation/ui/auth/PasscodeAuthFragment.kt` [NEW]
- `presentation/ui/auth/ProfileSetupFragment.kt` [MODIFY - navigation on success]
- `presentation/ui/dashboard/DashboardFragment.kt` [NEW - placeholder for Epic 2]
- `presentation/MainActivity.kt` or `MainActivity.kt` [MODIFY]
- `di/AppModule.kt` [MODIFY]

Layouts in `app/src/main/res/layout/`:
- `fragment_passcode_auth.xml` [NEW]
- `fragment_dashboard.xml` [NEW]

Navigation in `app/src/main/res/navigation/`:
- `nav_graph.xml` [MODIFY]

Tests in `app/src/test/java/com/example/myfoodtracker/`:
- `domain/usecase/AuthenticateUserUseCaseTest.kt` [NEW]
- `data/repository/SessionRepositoryImplTest.kt` [NEW]
- `data/repository/UserRepositoryImplTest.kt` [MODIFY]
- `presentation/viewmodel/PasscodeAuthViewModelTest.kt` [NEW]

### References

- [PRD Profile Switching & Authentication](_bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/prd.md#41-local-user-profiles)
- [UX Experience Passcode Lock Screen & State Patterns](_bmad-output/planning-artifacts/ux-designs/ux-MyFoodTracker-2026-09-06/EXPERIENCE.md#state-patterns)
- [Architecture Spine Invariants & Capabilities Map](_bmad-output/planning-artifacts/architecture/architecture-MyFoodTracker-2026-09-06/ARCHITECTURE-SPINE.md#invariants--rules)

## Dev Agent Record

### Agent Model Used
Gemini 3.8 Flash

### Debug Log References
- Story file initialized following BMAD create-story workflow standards.
- Identified need for pure in-memory domain `SessionRepository` to enforce `profile_id` isolation across current and future stories.
- Factored out salted SHA-256 verification and hashing to `PasscodeHasher` to maintain zero duplicated cryptographic logic.
- Followed Red-Green-Refactor cycle: verified failing tests prior to implementation, implemented minimal passing code, and verified entire unit test suite.
- Re-verified all existing tests and new tests with `.\gradlew.bat testDebugUnitTest` and checked clean compilation with `.\gradlew.bat compileDebugSources`.

### Completion Notes List
- Implemented `SessionRepository` and thread-safe `SessionRepositoryImpl` storing active profile via `AtomicReference`.
- Standardized salted SHA-256 hashing and constant-time verification in `PasscodeHasher`.
- Updated `CreateProfileUseCase` to delegate hashing to `PasscodeHasher`.
- Extended `UserProfileDao` with `@Query("SELECT * FROM users WHERE id = :id")` `getUserById(id: String): UserWithGoal?`.
- Extended `UserRepository` and `UserRepositoryImpl` with `getProfileById` and `verifyPasscode`.
- Implemented `AuthenticateUserUseCase` and `LogoutUseCase` handling authentication, error states, and session storage/clear.
- Registered all new domain, data, and presentation dependencies in `AppModule.kt`.
- Built Material 3 `fragment_passcode_auth.xml` adhering to design tokens (28dp pill button, `#1B4D3E`, `#F8F9FA`, 12dp rounded cards, exposed dropdown profile picker).
- Built `PasscodeAuthViewModel` and `PasscodeAuthFragment` managing loading, selection, PIN verification, and navigation with lifecycle-safe View Binding (`_binding = null` in `onDestroyView()`).
- Built placeholder `DashboardFragment` and `fragment_dashboard.xml` with active profile greeting and logout button.
- Updated `nav_graph.xml` with top-level destinations, backstack popUpTo inclusive flags, and actions connecting `passcodeAuthFragment`, `profileSetupFragment`, and `dashboardFragment`.
- Configured `MainActivity` `AppBarConfiguration` with top-level destinations `setOf(R.id.passcodeAuthFragment, R.id.dashboardFragment)`.
- Authored comprehensive unit tests covering all components: `PasscodeHasherTest`, `AuthenticateUserUseCaseTest`, `SessionRepositoryImplTest`, `UserRepositoryImplTest`, and `PasscodeAuthViewModelTest`. All tests pass 100%.

### File List
- `app/src/main/java/com/example/myfoodtracker/MainActivity.kt` (modified)
- `app/src/main/java/com/example/myfoodtracker/data/dao/UserProfileDao.kt` (modified)
- `app/src/main/java/com/example/myfoodtracker/data/repository/SessionRepositoryImpl.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/data/repository/UserRepositoryImpl.kt` (modified)
- `app/src/main/java/com/example/myfoodtracker/di/AppModule.kt` (modified)
- `app/src/main/java/com/example/myfoodtracker/domain/model/UserProfile.kt` (referenced)
- `app/src/main/java/com/example/myfoodtracker/domain/repository/SessionRepository.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/domain/repository/UserRepository.kt` (modified)
- `app/src/main/java/com/example/myfoodtracker/domain/security/PasscodeHasher.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/AuthenticateUserUseCase.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/CreateProfileUseCase.kt` (modified)
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/LogoutUseCase.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/auth/PasscodeAuthFragment.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/auth/ProfileSetupFragment.kt` (modified)
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/DashboardFragment.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/PasscodeAuthViewModel.kt` (new)
- `app/src/main/res/layout/fragment_dashboard.xml` (new)
- `app/src/main/res/layout/fragment_passcode_auth.xml` (new)
- `app/src/main/res/navigation/nav_graph.xml` (modified)
- `app/src/main/java/com/example/myfoodtracker/data/db/AppDatabase.kt` (modified)
- `app/src/main/java/com/example/myfoodtracker/data/db/MealDao.kt` (modified)
- `app/src/main/java/com/example/myfoodtracker/data/db/MealEntryEntity.kt` (modified)
- `app/src/main/java/com/example/myfoodtracker/data/repository/MealRepositoryImpl.kt` (modified)
- `app/src/test/java/com/example/myfoodtracker/data/repository/MealRepositoryImplTest.kt` (new)
- `app/src/test/java/com/example/myfoodtracker/data/repository/SessionRepositoryImplTest.kt` (new)
- `app/src/test/java/com/example/myfoodtracker/data/repository/UserRepositoryImplTest.kt` (modified)
- `app/src/test/java/com/example/myfoodtracker/domain/security/PasscodeHasherTest.kt` (new)
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/AuthenticateUserUseCaseTest.kt` (new)
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/CreateProfileUseCaseTest.kt` (modified)
- `app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/PasscodeAuthViewModelTest.kt` (new)

### Change Log
- Initialized Story 1.2 spec file with BDD acceptance criteria, subtasks, Clean Architecture guardrails, and file mapping (Date: 2026-09-12).
- Implemented Story 1.2: Passcode Authentication & Startup Profile Lock across domain, data, presentation, navigation, and tests. All subtasks complete and verified green (Date: 2026-09-13).
- Resolved Code Review findings DN-1 and DN-2: Enforced meal profile isolation in database and repository layers via SessionRepository; aligned passcode format error messaging to "Incorrect passcode" per AC #3; added comprehensive profile isolation unit tests (Date: 2026-09-20).
