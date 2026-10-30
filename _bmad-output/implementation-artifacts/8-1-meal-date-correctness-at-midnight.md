---
story_id: 8.1
story_key: 8-1-meal-date-correctness-at-midnight
epic: 8
status: done
created: 2026-10-25
context_refreshed: 2026-10-30
baseline_commit: 8dcfaa513e0b8566d0e09f7e288f86f2b5826a6d (Implement Story 6.3)
---

# Story 8.1: Meal Date Correctness at Midnight Boundaries

Status: done

## Story

As a user logging an entry a minute before midnight,
I want the entry recorded on the date I have selected in the dashboard,
So that my log does not silently land on the wrong day.

## Acceptance Criteria

1. **Given** any intake write path, **When** `addMealEntry`, `logQuickAdd` or `logFoodEntry` executes, **Then** the target date is an explicit parameter chained from the caller and `LocalDate.now()` no longer appears inside `MealRepositoryImpl` (current violation: MealRepositoryImpl.kt:33 in `addMealEntry`). After the fix, `grep -n "LocalDate.now" MealRepositoryImpl.kt` returns zero hits.
2. **Given** multi-day usage with an active dashboard date (historical date or day switched via `MaterialDatePicker`), **When** an entry is written while the device clock crosses midnight between tap and write (or the write happens on a later day entirely), **Then** the entry lands on the UI-selected date, not the new device date.
3. **Given** all existing call sites and the 11 JVM test fakes implementing `MealRepository`, **When** the `addMealEntry` signature changes, **Then** everything compiles with no behavioral regression in any single-profile or swipe-delete flow, and regression tests prove the three write paths honor an explicit target date that differs from the device's current date.

## Tasks / Subtasks

- [x] Task 1: Make the target date explicit on the `addMealEntry` chain (AC: #1, #3)
  - [x] 1.1 `MealRepository.addMealEntry()` → `addMealEntry(date: String): List<MealEntry>` (ISO-8601 `LocalDate.toString()`, same convention as `logQuickAdd`/`logFoodEntry`).
  - [x] 1.2 `MealRepositoryImpl.addMealEntry` — use `date` for `MealEntryEntity.date`; keep `time = LocalTime.now()` pattern("HH:mm") unchanged (see Dev Notes — time semantics are explicitly out of scope).
  - [x] 1.3 `AddMealEntryUseCase.invoke(date: String)` — same pass-through style as `LogQuickAddUseCase`.
  - [x] 1.4 `MealViewModel` (`presentation/MealViewModel.kt`) — its `addMealEntry()` forwards a `date: String` parameter. **Note: MealViewModel is DI-registered but has no fragment callers today (legacy M1 screen). Do NOT delete it in this story** — keep the surface, thread the param; deletion is a future cleanup decision outside 8.1's scope.
  - [x] 1.5 Keep `date: String` (not `LocalDate`). Rationale: `logQuickAdd`, `logFoodEntry`, `LogRecipeUseCase` (DATE_PATTERN-validated `date: String`), and the DAO (`getMealsWithFoodsByProfileIdAndDate`) all operate on ISO-8601 strings; a second type convention inside the same interface would be the "wrong library" class of mistake.
- [x] Task 2: Update all test fakes implementing `MealRepository` (AC: #3) — mechanical param addition to the `addMealEntry` override in:
  - [x] 2.1 `SearchFoodUseCaseTest.kt` (FakeMealRepository)
  - [x] 2.2 `SettingsViewModelTest.kt` (FakeMealRepository — its override asserts a thrown error, preserve assertion)
  - [x] 2.3 `GetMealEntriesByDateUseCaseTest.kt` (FakeMealRepository)
  - [x] 2.4 `ExportLogsUseCaseTest.kt` (FakeMealRepository)
  - [x] 2.5 `LogRecipeUseCaseTest.kt` (RecordingMealRepository — override throws UnsupportedOperationException, preserve)
  - [x] 2.6 `RecipeViewModelTest.kt` (RecordingMealRepository — throws, preserve)
  - [x] 2.7 `LogQuickAddUseCaseTest.kt` (FakeMealRepository)
  - [x] 2.8 `RestoreMealEntryUseCaseTest.kt` (FakeMealRepository)
  - [x] 2.9 `LogFoodEntryUseCaseTest.kt` (FakeMealRepository)
  - [x] 2.10 `DashboardViewModelTest.kt` (FakeMealRepository inside DashboardViewModel constructor fake)
  - [x] 2.11 `FoodSearchViewModelTest.kt` (FakeMealRepository)
  - [x] 2.12 Also update the direct `repository.addMealEntry()` calls inside `MealRepositoryImplTest` (profile-isolation and update/delete tests, ~6 call sites) to pass an explicit date such as `"2026-09-30"`.
- [x] Task 3: Midnight-boundary regression tests (AC: #2, #1) — extend `MealRepositoryImplTest` following its existing hand-rolled FakeMealDao pattern (do NOT introduce MockK here; this test class deliberately uses JVM fakes):
  - [x] 3.1 `addMealEntry_usesExplicitTargetDate`: pass a date that is **not** today (e.g. `"2026-01-01"`), assert the persisted `MealEntryEntity.date == "2026-01-01"` — this proves the repo never re-derives the date from `LocalDate.now()` and covers the "23:59 tap, write after midnight" scenario by construction.
  - [x] 3.2 `logQuickAdd_historicalDate_landsOnTargetDate`: already covered pattern-wise (logQuickAdd has explicit date) — assert the persisted entity date equals a historical/near-midnight target date, not `LocalDate.now().toString()`.
  - [x] 3.3 `logFoodEntry_historicalDate_landsOnTargetDate`: same assertion for the food-entry path.
- [x] Task 4: Verify & close the register item (AC: #1)
  - [x] 4.1 `./gradlew :app:compileDebugSources` and `./gradlew :app:testDebugUnitTest` pass (run from repo root in WSL; see Testing requirements below).
  - [x] 4.2 Append a `**RESOLVED** by story 8-1 (2026-XX-XX)` suffix with a one-paragraph resolution summary to the `D-1` entry in `_bmad-output/implementation-artifacts/deferred-work.md` (line 13), matching the format used for 2.4-D1/3.2-D1/4.1-D4.
  - [x] 4.3 No `sprint-status.yaml` changes by the dev (statuses move via dev-story → code-review flow).

### Review Findings

- [x] [Review][Patch] Document Structure and Register Formatting in deferred-work.md [_bmad-output/implementation-artifacts/deferred-work.md:13]
- [x] [Review][Defer] Zero Date String Validation in Repository Layer [app/src/main/java/com/example/myfoodtracker/data/repository/MealRepositoryImpl.kt:30] — deferred, pre-existing
- [x] [Review][Defer] Repeated Runtime Formatter Allocation in MealRepositoryImpl [app/src/main/java/com/example/myfoodtracker/data/repository/MealRepositoryImpl.kt:32] — deferred, pre-existing
- [x] [Review][Defer] Test Fake Duplication Across JVM Unit Tests [app/src/test/java/com/example/myfoodtracker/] — deferred, pre-existing
- [x] [Review][Defer] Missing Unit Test Coverage for Legacy MealViewModel and AddMealEntryUseCase [app/src/main/java/com/example/myfoodtracker/presentation/MealViewModel.kt] — deferred, pre-existing

## Dev Notes

### Current write-path map (verified 2026-10-30, HEAD 8dcfaa5)

| Path | Chain | Date handling today | Verdict |
|---|---|---|---|
| Quick Add | `DashboardViewModel.logQuickAdd` (DashboardViewModel.kt:102, takes `_uiState.activeDate` :110) → `LogQuickAddUseCase` → `MealRepositoryImpl.logQuickAdd(date: String)` (MealRepositoryImpl.kt:58) | explicit, already correct | only needs regression tests |
| Food entry | `DashboardViewModel.logFoodEntry` (DashboardViewModel.kt:119, date :125) → `LogFoodEntryUseCase` → `MealRepositoryImpl.logFoodEntry(date)` (MealRepositoryImpl.kt:96) | explicit, already correct | only needs regression tests |
| Recipe fan-out | `LogRecipeUseCase.invoke(recipeId, mealSlot, date: String)` (LogRecipeUseCase.kt:19, DATE_PATTERN-validated) → N × `logFoodEntry`, args `date = logDate` (LogRecipeUseCase.kt:49-60) | explicit, already correct | must keep compiling (Task 1 ripple) |
| **Empty meal entry** | `MealViewModel.addMealEntry` → `AddMealEntryUseCase` → `MealRepositoryImpl.addMealEntry()` (MealRepositoryImpl.kt:31-44) | **`LocalDate.now().toString()` at line 33 — the D-1 defect** | fix in this story |
| Swipe-delete restore | `DashboardViewModel.restoreLastDeleted` → `RestoreMealEntryUseCase` → `MealRepositoryImpl.restoreMealEntry` — re-inserts with `entry.date` (MealRepositoryImpl.kt:143), never `now()` | explicit, already correct | must keep compiling |

### The caller-chain correction (important — do not follow the old stub blindly)

The previous stub of this story claimed `DashboardViewModel` is the caller of `addMealEntry`. **It is not.** The only production caller of `MealRepository.addMealEntry` today is the legacy `MealViewModel` (`presentation/MealViewModel.kt`, registered in `AppModule.kt:148` but used by no fragment — the dashboard drives all intake writes itself). `MealViewModel.addMealEntry()` is reached via `AddMealEntryUseCase` only. This story still fixes the repository write path per register item D-1 ("should accept a target date parameter"), but **do not invent UI wiring for MealViewModel** and **do not delete it** — both out of scope.

### What must never change

- `MealRepositoryImpl` write order and semantics: `insertMealWithFoods(meal, foods)` composite from Story 6-1, REPLACE on same id, parent-first FK order — untouched.
- `restoreMealEntry` must keep re-inserting the original `entry.id`, `entry.date`, `entry.time` (undo semantics of Story 2-5) — untouched.
- Dashboard boundary fallbacks `?: LocalDate.now()` inside `DashboardViewModel` (lines 54, 83, 88, 93, 97, 110, 125, 139, 168) are **allowed and correct** — `now()` belongs at the ViewModel boundary, not below it. Do not "fix" these.
- `DashboardFragment.showDatePicker` (DashboardFragment.kt:546-563) already converts `MaterialDatePicker` millis correctly via UTC anchoring (`atStartOfDay(ZoneId.of("UTC"))` → `Instant.ofEpochMilli(selection).atZone(UTC).toLocalDate()`). The non-UTC mismatch risk from D-1 was purely the repo's internal `now()`; don't touch the picker.
- `MealEntryEntity.time` stays `LocalTime.now()` formatted "HH:mm" in all write paths. A tap at 23:59 whose write lands at 00:01 stores `time=00:01` under yesterday's date — accepted semantic (the *date* is the correctness contract; a finger-crossing wall-clock skew of ≤ one minute is invisible in UI and not worth spreading more params into).
- Do not introduce coroutines/`suspend` (project rule: synchronous main-thread Room under `allowMainThreadQueries()`; only local DB writes touch this story anyway).
- Do not touch `WaterRepository` (its `logWater` already takes an explicit date) or `RecipeRepository`.

### Testing requirements

- **JVM unit tests only.** Project policy forbids launching the Android emulator — no `connectedDebugAndroidTest`; verify with `./gradlew :app:compileDebugSources` and `./gradlew :app:testDebugUnitTest` (WSL side; `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`, `ANDROID_HOME=$HOME/Android/Sdk`, java must be Linux — see AGENTS.md WSL build notes). If `local.properties` path issues surface SDK errors, use the documented temp-swap procedure and restore immediately.
- Match existing patterns: `MealRepositoryImplTest` uses hand-rolled `FakeMealDao` (in-memory maps) + `FakeSessionRepository` — extend them, don't wire in MockK.
- The 11 fake updates are interface-compliance edits only; their behavior assertions stay byte-identical.
- Manual testing notice for the developer: on-device verification of a real midnight crossing cannot be automated here; flag it for manual testing in the completion record (e.g., log a quick-add at 23:58 with the dashboard on today, let the device cross midnight before the write, confirm the row appears under today).

### Previous work intelligence (Epic 6 → Epic 8)

- Story 6-1 (2026-10-25) established `MealDao.insertMealWithFoods` and its JVM regression tests in `MealRepositoryImplTest` (`logQuickAdd_writesThroughTransactionalCompositeMethod`, `logFoodEntry_...`, `restoreMealEntry_...`) — new Task-3 tests slot alongside them in the same class.
- Recent commit style: `Implement Story X.Y. <Title>`; sprint-status is updated by the dev/review flow, not the create step.
- Deferred resolutions are recorded as `**RESOLVED** by story N-M (date)` suffixes directly on the register entry in `deferred-work.md`, with a "Resolved by" summary below — replicate that for D-1.
- Story 7-1 (in review) touched auth/session code only; no overlap with meal write paths, nothing to inherit.

### Architecture compliance (from project-context.md / ARCHITECTURE-SPINE.md)

- Clean layering: only `data/repository/`, `domain/repository/`, `domain/usecase/`, `presentation/` files listed below; no android imports leak into domain beyond existing patterns.
- Entities never cross into domain/presentation — the `date: String` param does not change the domain model (`MealEntry.date` is already a String).
- Resource naming, view-binding lifecycle, `viewLifecycleOwner` observation — no UI files change in this story; Task 1.4 is ViewModel-only.
- No database schema change: the `meals.date` column format (ISO-8601 text) is unchanged → no Room version bump, no migration, schema `app/schemas/.../1.json` untouched.

### Files to touch (exhaustive)

UPDATE:
- `app/src/main/java/com/example/myfoodtracker/domain/repository/MealRepository.kt`
- `app/src/main/java/com/example/myfoodtracker/data/repository/MealRepositoryImpl.kt` (drop the `LocalDate` import if unused after the fix)
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/AddMealEntryUseCase.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/MealViewModel.kt`
- 11 test files listed in Task 2
- `app/src/test/java/com/example/myfoodtracker/data/repository/MealRepositoryImplTest.kt` (calls + new tests)

NO-TOUCH:
- `MealDao`, `MealEntryEntity`, `AppDatabase`, `AppModule` (Koin bindings unchanged — factory signatures still satisfy DI), `DashboardViewModel`, `DashboardFragment`, `FoodSearchViewModel`, `LogRecipeUseCase`, all recipe/water files.

### References

- Register item: `_bmad-output/implementation-artifacts/deferred-work.md` line 13 (D-1), incl. non-UTC + MaterialDatePicker mismatch scenario.
- Epic definition: `_bmad-output/planning-artifacts/epics.md` §Epic 8, Story 8.1 (lines 465-480).
- Architecture spine: `_bmad-output/planning-artifacts/architecture/architecture-MyFoodTracker-2026-09-06/ARCHITECTURE-SPINE.md` (sync Room rule, DashboardViewModel owns active date).
- Agent rules: `_bmad-output/project-context.md` and root `AGENTS.md` (WSL build + emulator policy).
- Web research: N/A — pure Kotlin `java.time` signature refactor, no third-party versions involved; stack is pinned by project-context (Room 3.0.3, AGP 9.2.1, Koin 3.5.6), nothing to upgrade.

### Project Structure Notes

- All changed files live in established packages (`domain/usecase`, `domain/repository`, `data/repository`, `presentation`) — no new directories, no new Gradle deps.
- Story file lives at `_bmad-output/implementation-artifacts/8-1-meal-date-correctness-at-midnight.md` (this file).

## Dev Agent Record

### Agent Model Used

GLM-5.3-Flash Free (opencode provider) — OpenCode harness, WSL Linux side (Ubuntu, JDK 21.0.12.1)

### Debug Log References

- `./gradlew :app:compileDebugSources` — BUILD SUCCESSFUL (first invocation emitted a Kotlin incremental-compiler internal stack trace from the incremental cache; a subsequent invocation compiled clean — harmless WSL/junction artifact, no code consequence).
- `./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL. Full JVM suite: **389 tests, 0 failures, 0 errors** across 36 test-classes. `MealRepositoryImplTest` now runs 23 tests including the 3 new midnight-boundary regressions (`addMealEntry_usesExplicitTargetDate`, `logQuickAdd_historicalDate_landsOnTargetDate`, `logFoodEntry_historicalDate_landsOnTargetDate`), all green.
- `grep -n "LocalDate.now" MealRepositoryImpl.kt` → **0 hits** (AC #1 contract verified).
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL (final gate).
- Red→green note: the three Task-3 tests target the new `addMealEntry(date: String)` surface; against the pre-change code they could not compile (no-arg signature) and the old behavior persisted `LocalDate.now().toString()` — the minimal Task-1 change turned them green.

### Completion Notes List

- **What was implemented:** the full `addMealEntry` caller chain now threads an explicit ISO-8601 target date: `MealRepository.addMealEntry(date: String)` → `MealRepositoryImpl.addMealEntry(date)` (persists `date` verbatim on `MealEntryEntity.date`) → `AddMealEntryUseCase.invoke(date: String)` → `MealViewModel.addMealEntry(date: String)`. The repository no longer calls `LocalDate.now()` (import removed); `LocalTime.now()` "HH:mm" wall-clock time semantics kept unchanged per the story contract.
- **Architecture guards respected:** `MealViewModel` preserved (param threaded, NOT deleted, no invented UI wiring per Dev Notes); `restoreMealEntry` undo semantics (`entry.id`/`entry.date`/`entry.time`) untouched; composite `insertMealWithFoods` write order untouched since `addMealEntry` uses standalone `insertMeal` (no composite pairing to preserve); `DashboardViewModel` boundary fallbacks and `DashboardFragment.showDatePicker` untouched; no schema/DI/format changes (Koin `factory { AddMealEntryUseCase(get()) }` still satisfies DI since all collaborators are type-injected); no coroutines introduced.
- **Test fakes:** all 11 `MealRepository` test fakes updated as byte-identical mechanical param additions — throwing overrides (Settings/ExportLogs/LogRecipe/RecipeViewModel) keep their exact exception messages.
- **Call sites:** `MealRepositoryImplTest` direct `addMealEntry` call sites (6) now pass explicit `"2026-09-30"`.
- **Register item:** deferred-work `2-1`-section D-1 closed with a fused `**RESOLVED** by story 8-1 (2026-10-30)` suffix + one-paragraph resolution summary on the entry line, matching the register's fused-resolve format.
- **⚠️ Manual testing notice (developer):** on-device verification of a real midnight crossing cannot be automated here (no-emulator policy). Manual check: with the dashboard on today's date, log a quick-add at ~23:58, let the device cross midnight before the write lands, and confirm the row appears under the UI-selected (pre-midnight) date; also historical-date writes via `MaterialDatePicker` remain flagged as escaped from automated coverage in prior stories.

### File List

- app/src/main/java/com/example/myfoodtracker/domain/repository/MealRepository.kt (modified)
- app/src/main/java/com/example/myfoodtracker/data/repository/MealRepositoryImpl.kt (modified)
- app/src/main/java/com/example/myfoodtracker/domain/usecase/AddMealEntryUseCase.kt (modified)
- app/src/main/java/com/example/myfoodtracker/presentation/MealViewModel.kt (modified)
- app/src/test/java/com/example/myfoodtracker/domain/usecase/SearchFoodUseCaseTest.kt (modified)
- app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/SettingsViewModelTest.kt (modified)
- app/src/test/java/com/example/myfoodtracker/domain/usecase/GetMealEntriesByDateUseCaseTest.kt (modified)
- app/src/test/java/com/example/myfoodtracker/domain/usecase/ExportLogsUseCaseTest.kt (modified)
- app/src/test/java/com/example/myfoodtracker/domain/usecase/LogRecipeUseCaseTest.kt (modified)
- app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/RecipeViewModelTest.kt (modified)
- app/src/test/java/com/example/myfoodtracker/domain/usecase/LogQuickAddUseCaseTest.kt (modified)
- app/src/test/java/com/example/myfoodtracker/domain/usecase/RestoreMealEntryUseCaseTest.kt (modified)
- app/src/test/java/com/example/myfoodtracker/domain/usecase/LogFoodEntryUseCaseTest.kt (modified)
- app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/DashboardViewModelTest.kt (modified)
- app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/FoodSearchViewModelTest.kt (modified)
- app/src/test/java/com/example/myfoodtracker/data/repository/MealRepositoryImplTest.kt (modified — call sites + 3 new regression tests)
- _bmad-output/implementation-artifacts/deferred-work.md (modified — D-1 story-2-1 register entry resolved)

### Change Log

- 2026-10-30: Story 8.1 implemented. `MealRepository.addMealEntry` chain made date-explicit (`date: String` param through repo → use case → `MealViewModel`); repository no longer derives the date internally (`LocalDate.now()` removed); 11 test fakes + 6 direct test call sites updated; 3 midnight-boundary regression tests added to `MealRepositoryImplTest`; deferred-work register item D-1 (story 2-1 section) marked RESOLVED. Full JVM suite 389/0/0; `compileDebugSources` and `assembleDebug` green.
