---
baseline_commit: 25d002438462d692b19a44109246e97a1c2fbcb8
---

# Story 1.1: Local Profile Creation & Goal Setup

Status: review

## Story

As a new user,
I want to create a local profile with a username, 4-digit passcode, and optional macro/water daily targets,
so that I can manage my personal health tracking on-device with privacy.

## Acceptance Criteria

1. **Given** a cold app launch with no existing profiles, **When** the user inputs a unique profile name, 4-digit passcode, and optional target values (leaving skipped targets blank), **Then** the profile is saved in local Room database (`users` and `user_daily_goals`) with blank goals stored as `null`. [Source: PRD FR-1, Addendum §1.1]
2. **Given** profile setup screen, **When** the user attempts to submit with an empty username or a passcode fewer than 4 digits, **Then** the system rejects creation and highlights the invalid field with an explicit validation error message. [Source: PRD FR-1]
3. **Given** profile setup screen, **When** the screen is displayed, **Then** a prominent medical disclaimer banner ("Targets are user-configured and not clinically audited") is rendered. [Source: PRD §10.1]
4. **Given** profile creation submission, **When** data is persisted, **Then** the passcode is securely hashed (bcrypt/scrypt or salted SHA-256) before storing in the `passcode_hash` column in the `users` table. [Source: PRD §4.1, Addendum §1.1]

## Tasks / Subtasks

- [x] **Task 1: Define Domain Models, Repository Interface & UseCase** (AC: #1, #2, #4)
  - [x] Subtask 1.1: Create `UserProfile.kt` and `DailyGoal.kt` in `domain/model/` (pure Kotlin models with nullable goal fields)
  - [x] Subtask 1.2: Create `UserRepository.kt` interface in `domain/repository/` with synchronous operations (`createProfile`, `getProfiles`, `hasProfiles`)
  - [x] Subtask 1.3: Create `CreateProfileUseCase.kt` in `domain/usecase/` exposing `operator fun invoke(username, passcode, targets)`

- [x] **Task 2: Implement Room Entities, DAOs, Database & Repository Implementation** (AC: #1, #4)
  - [x] Subtask 2.1: Create `UserProfileEntity.kt` (`users` table) and `UserDailyGoalEntity.kt` (`user_daily_goals` table with `@ForeignKey` to `users(id)` and `onDelete = CASCADE`) in `data/entity/`
  - [x] Subtask 2.2: Create `UserProfileDao.kt` in `data/dao/` using synchronous queries (`@Insert`, `@Query`)
  - [x] Subtask 2.3: Create `AppDatabase.kt` in `data/db/` configured with `.allowMainThreadQueries()`
  - [x] Subtask 2.4: Create `UserRepositoryImpl.kt` in `data/repository/` implementing `UserRepository` with private entity-to-domain mapping extension functions

- [x] **Task 3: Configure Centralized Koin Dependency Injection** (AC: #1)
  - [x] Subtask 3.1: Register Room database (`single`), `UserRepository` (`single`), `CreateProfileUseCase` (`factory`), and `ProfileSetupViewModel` (`viewModel`) in `di/AppModule.kt`

- [x] **Task 4: Implement Profile Setup Presentation UI & View Binding** (AC: #1, #2, #3)
  - [x] Subtask 4.1: Create `fragment_profile_setup.xml` featuring Material Design 3 text fields, 4-digit PIN input, optional goal fields, and medical disclaimer banner
  - [x] Subtask 4.2: Create `ProfileSetupViewModel.kt` in `presentation/viewmodel/` managing state via `LiveData<ProfileSetupState>`
  - [x] Subtask 4.3: Create `ProfileSetupFragment.kt` in `presentation/ui/auth/` observing ViewModel state and handling View Binding lifecycle safety (`_binding = null` in `onDestroyView()`)
  - [x] Subtask 4.4: Implement instant field validation for username (non-empty) and passcode (4+ digits) with error messages

## Dev Notes

- **Architecture Rules (AD-1..AD-7)**:
  - `domain/` must contain 0 Android framework dependencies (`android.*`) or Room annotations.
  - Room DAO methods run synchronously on the main thread via `.allowMainThreadQueries()` in `AppDatabase` builder (AD-2). No `suspend` functions.
  - Repositories map Room entities to domain models via private extensions before returning data (AD-3).
  - UseCase exposes `operator fun invoke()` and is registered as Koin `factory` (AD-4).
  - Fragment clears `_binding = null` in `onDestroyView()` and observes LiveData via `viewLifecycleOwner` (AD-5).
  - All Koin dependencies registered centrally in `di/AppModule.kt` (AD-6).
  - Foreign key `@ForeignKey(entity = UserProfileEntity::class, parentColumns = ["id"], childColumns = ["profile_id"], onDelete = ForeignKey.CASCADE)` on `user_daily_goals` table (AD-7).

- **M3 Styling & Tokens**:
  - Surface Background: `#F8F9FA` (`surface-base`)
  - Raised Card: `#FFFFFF` (`surface-raised`)
  - Accent Primary: `#1B4D3E` (Deep Emerald Green)
  - Corner Radii: 8dp (`rounded/sm`) for text inputs, 12dp (`rounded/md`) for cards, 28dp (`rounded/xl`) for primary submit button.

### Project Structure Notes

Files created in `app/src/main/java/com/example/myfoodtracker/`:
- `domain/model/UserProfile.kt`
- `domain/model/DailyGoal.kt`
- `domain/repository/UserRepository.kt`
- `domain/usecase/CreateProfileUseCase.kt`
- `data/entity/UserProfileEntity.kt`
- `data/entity/UserDailyGoalEntity.kt`
- `data/dao/UserProfileDao.kt`
- `data/db/AppDatabase.kt`
- `data/repository/UserRepositoryImpl.kt`
- `presentation/ui/auth/ProfileSetupFragment.kt`
- `presentation/viewmodel/ProfileSetupViewModel.kt`
- `di/AppModule.kt`

Layouts created in `app/src/main/res/layout/`:
- `fragment_profile_setup.xml`

### References

- [PRD Vision & Profile Requirements](file:///c:/Users/Xenae/Documents/source/repos/AndroidStudioProjects/MyFoodTracker/_bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/prd.md#41-local-user-profiles)
- [PRD Addendum Schema Specifications](file:///c:/Users/Xenae/Documents/source/repos/AndroidStudioProjects/MyFoodTracker/_bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/addendum.md#11-table-specifications)
- [Architecture Spine Rules](file:///c:/Users/Xenae/Documents/source/repos/AndroidStudioProjects/MyFoodTracker/_bmad-output/planning-artifacts/architecture/architecture-MyFoodTracker-2026-09-06/ARCHITECTURE-SPINE.md#invariants--rules)
- [UX Visual Identity Tokens](file:///c:/Users/Xenae/Documents/source/repos/AndroidStudioProjects/MyFoodTracker/_bmad-output/planning-artifacts/ux-designs/ux-MyFoodTracker-2026-09-06/DESIGN.md#colors)

## Dev Agent Record

### Agent Model Used
Gemini 3.6 Flash (High)

### Debug Log References
- Extracted requirement FR-1, FR-2, AD-1 through AD-7, and UX tokens from primary planning artifacts.
- Resolved LiveData test main looper issue in `ProfileSetupViewModelTest` using `InstantTaskExecutorRule`.

### Completion Notes List
- Initialized comprehensive story spec file with BDD acceptance criteria, subtasks, Clean Architecture guardrails, and file mapping.
- Implemented pure domain models `UserProfile` and `DailyGoal` with nullable goal targets.
- Created `UserRepository` interface and `CreateProfileUseCase` with input validation and SHA-256 passcode hashing.
- Implemented `UserProfileEntity`, `UserDailyGoalEntity` (with CASCADE foreign key deletion), `UserProfileDao`, and updated `AppDatabase`.
- Created `UserRepositoryImpl` with entity-to-domain extension mappings.
- Registered all Room DB, DAO, Repository, UseCase, and ViewModel components in `di/AppModule.kt`.
- Created `fragment_profile_setup.xml` matching M3 design tokens and prominent medical disclaimer banner.
- Created `ProfileSetupViewModel` and `ProfileSetupFragment` with View Binding lifecycle safety and field validation.
- Added comprehensive unit tests for `CreateProfileUseCase`, `UserRepositoryImpl`, and `ProfileSetupViewModel`. All unit tests compiled and passed cleanly (100% success rate).

### File List
- `_bmad-output/implementation-artifacts/1-1-local-profile-creation-and-goal-setup.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
- `app/build.gradle.kts`
- `app/src/main/java/com/example/myfoodtracker/domain/model/DailyGoal.kt`
- `app/src/main/java/com/example/myfoodtracker/domain/model/UserProfile.kt`
- `app/src/main/java/com/example/myfoodtracker/domain/repository/UserRepository.kt`
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/CreateProfileUseCase.kt`
- `app/src/main/java/com/example/myfoodtracker/data/entity/UserProfileEntity.kt`
- `app/src/main/java/com/example/myfoodtracker/data/entity/UserDailyGoalEntity.kt`
- `app/src/main/java/com/example/myfoodtracker/data/dao/UserProfileDao.kt`
- `app/src/main/java/com/example/myfoodtracker/data/db/AppDatabase.kt`
- `app/src/main/java/com/example/myfoodtracker/data/repository/UserRepositoryImpl.kt`
- `app/src/main/java/com/example/myfoodtracker/di/AppModule.kt`
- `app/src/main/res/layout/fragment_profile_setup.xml`
- `app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/ProfileSetupViewModel.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/auth/ProfileSetupFragment.kt`
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/CreateProfileUseCaseTest.kt`
- `app/src/test/java/com/example/myfoodtracker/data/repository/UserRepositoryImplTest.kt`
- `app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/ProfileSetupViewModelTest.kt`

## Change Log
- Implemented Story 1.1 Local Profile Creation & Goal Setup (Date: 2026-09-06)
