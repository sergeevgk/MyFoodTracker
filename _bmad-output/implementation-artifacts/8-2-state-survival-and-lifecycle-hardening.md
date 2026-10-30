---
story_id: 8.2
story_key: 8-2-state-survival-and-lifecycle-hardening
epic: 8
status: ready-for-dev
created: 2026-10-25
context_refreshed: 2026-10-30
baseline_commit: e276e4fe2beaf2123a137c2e2ba695c29bff22f2 (Implement Story 7.2)
---

# Story 8.2: State Survival & Lifecycle Hardening

Status: ready-for-dev

## Story

As a user,
I want rotation and configuration changes not to lose my recipe draft or my pending export,
So that long in-progress actions survive routine screen state changes.

## Acceptance Criteria

1. **Given** the recipe builder with a draft in progress (name typed, ingredients added with quantities), **When** the device rotates (or any configuration change recreates the fragment view), **Then** the draft — recipe name, chosen ingredients and their weights — is restored from a `RecipeBuilderViewModel` and re-renders in the RecyclerView, totals TextView and name field with no data loss (resolves deferred-work register item **4.1-D3**, `RecipeBuilderFragment.kt`).
2. **Given** the export flow with the system document picker foregrounded, **When** a configuration change occurs while the picker is open, **Then** the pending `ExportPayload` survives (moved out of the `SettingsFragment` field into `SettingsViewModel`) and the picked URI is written successfully after the activity result returns — no "Could not save the log file" (resolves **5.1-F2**, `SettingsFragment.kt`). Formats, MIME types, error toast literals and log messages of the 5.1 contract are unchanged.
3. **Given** `LogRecipeDialogFragment.capIngredientScrollHeight` posts a runnable, **When** it executes after the dialog fragment was detached, **Then** resources access is guarded and no `Resources$NotFoundException`/crash occurs (resolves **4.2-D1**, `LogRecipeDialogFragment.kt`).

**Scope note (AC #1):** the epics text also mentions "the process is recreated" for AC #1; in this story the restored state must only survive **configuration change** (ViewModel survival). Process-death persistence (e.g. `SavedStateHandle`) is deliberately out of scope — see Dev Notes "SavedStateHandle" — and the register item 4.1-D3 is only closed for the rotation scenario.

## Tasks / Subtasks

- [ ] Task 1: Recipe draft → `RecipeBuilderViewModel` (AC: #1)
  - [ ] 1.1 Create `presentation/viewmodel/RecipeBuilderViewModel.kt` holding: `drafts` (`MutableLiveData<List<NewRecipeIngredient>>`, init `emptyList()`), `name` (`MutableLiveData<String>`, init `""`) and a `saveError` (`MutableLiveData<String?>`, init `null`). Inject the existing `SaveRecipeUseCase` (factory-registered in `AppModule.kt:134`).
  - [ ] 1.2 Implement: `addIngredient(NewRecipeIngredient)`, `removeIngredient(position: Int)`, `setName(String)`, `saveRecipe(name: String): Boolean` mirroring `RecipeViewModel.saveRecipe`'s try/catch-`IllegalArgumentException` → `saveError` contract (do NOT touch `RecipeViewModel` itself).
  - [ ] 1.3 Rewrite `RecipeBuilderFragment` to read/write the draft via the VM: remove the `drafts` fragment field, drive `refreshDrafts()` from observing `viewModel.drafts`, restore `binding.etRecipeName` from `viewModel.name` on view creation, write back in `doAfterTextChanged` (restore the text **before** attaching the text watcher).
  - [ ] 1.4 Register `viewModel { RecipeBuilderViewModel(get()) }` in `AppModule.kt` next to the other recipe ViewModels (≈ line 152).
- [ ] Task 2: Export payload → ViewModel (AC: #2)
  - [ ] 2.1 In `SettingsViewModel` add the pending-save carrier: `setPendingSavePayload(ExportPayload)` and `consumePendingSavePayload(): ExportPayload?` (returns and clears; a second consume returns `null`). Keep it a plain field + methods — the VM must not hold any `Uri`/`File` (project rule).
  - [ ] 2.2 In `SettingsFragment`: delete the `pendingSavePayload` field; `startSaveToFile` stores the payload in the VM; the `createDocumentLauncher` callback calls `consumePendingSavePayload()` first and keeps the existing behavior exactly (RESULT_OK check, null-uri → `export_error_save_failed`, write via `fileWriter.writeToUri`, success snackbar, log literals).
  - [ ] 2.3 Do NOT move `pendingAction` (`ExportAction.SHARE/SAVE`) into the VM: `export()` is synchronous, so `handleReady` fires in the same main-thread frame — there is no rotation window for it (Dev Notes explains). Leave it as a Fragment field.
- [ ] Task 3: Lifecycle guard (AC: #3)
  - [ ] 3.1 In `LogRecipeDialogFragment.capIngredientScrollHeight`, inside `scroll.post { … }` add as first lines: `if (!isAdded) return@post` (covers the detached `resources` access; `_binding?.scrollLogRecipeIngredients ?: return` at the top already covers the null-binding case).
- [ ] Task 4: Tests (AC: #1, #2) — JVM only, per project policy (no emulator).
  - [ ] 4.1 New `presentation/viewmodel/RecipeBuilderViewModelTest.kt`: hand-rolled `FakeRecipeRepository` (reuse the `RecordingMealRepository`/fake pattern from `RecipeViewModelTest`) under `InstantTaskExecutorRule`; cover add → drafts emitted, remove by position, remove with invalid index no-ops, save success → true + no error, save with blank-name `IllegalArgumentException` → false + `saveError` set.
  - [ ] 4.2 Extend `SettingsViewModelTest`: `setPendingSavePayload` then `consumePendingSavePayload` returns the payload and clears it; second consume returns `null`; if never set, consume returns `null`.
  - [ ] 4.3 Guard logic of Task 3 is not JVM-testable (Fragment resources) — cover it by compile check + the manual-verification notice below.
  - [ ] 4.4 Verify: `./gradlew :app:compileDebugSources` and `./gradlew :app:testDebugUnitTest` (WSL; Windows: `gradlew.bat`). All existing tests stay green.
  - [ ] 4.5 On completion, append `**RESOLVED** by story 8-2 (2026-XX-XX)` paragraphs to deferred-work items **4.1-D3**, **5.1-F2**, **4.2-D1** (match the established format, e.g. 6-1's resolutions). Do not change `sprint-status.yaml` statuses as the dev (dev-story → code-review flow owns them).
- [ ] Manual notice (explicit per project policy): rotation paths are UI-level and cannot be emulator-tested — on-device walk-through required: (a) build a recipe draft (name + ≥2 ingredients), rotate, confirm name/list/totals intact and Save still completes; (b) open an export, tap "Save to file…", rotate with the picker foregrounded, pick a destination, confirm the file is written with the success snackbar (no "Could not save the log file"); (c) log a long recipe (> cap height) and rotate while the dialog is open — no crash.

## Dev Notes

### Current state of the three files (all read in full, HEAD e276e4f)

**`RecipeBuilderFragment.kt`** (`presentation/ui/recipe/`):
- Draft state lives ONLY in fragment memory: `private val drafts = mutableListOf<NewRecipeIngredient>()` (line 34). `drafts.add(...)` in `onIngredientConfirmed` (line 122–126), remove-by-position in the adapter callback (line 48–53), totals computed in `refreshDrafts()` (line 128–151). A rotation destroys all of it; the new fragment view starts empty. Same for the recipe name — it exists only in `binding.etRecipeName`.
- The fragment currently injects `RecipeViewModel` and calls `viewModel.saveRecipe(name, drafts.toList())` (line 98) → on success `setFragmentResult(REQUEST_RECIPE_SAVED, …)` + `findNavController().popBackStack()`; on failure the use case's `IllegalArgumentException` message lands in `saveError` and is observed (line 60–72).
- What must be preserved exactly: the save-success side effect (fragment result + popBackStack), the error-routing logic (name-blank error → `tilRecipeName`, list error → `tvDraftsError`), the `isAdded` guards at every entry point, `LocalDate.now().toString()` passed to `FoodSearchBottomSheet.newInstance` (line 76 — recipe-builder ingredient search doesn't target a log date; leaving this is not a regression of 8.1's scope), and `onDestroyView` `_binding = null`.
- `onIngredientConfirmed` is the tail of a chain: `FoodSearchBottomSheet` (Listener) → `RecipeIngredientQuantityDialogFragment` (Listener) → drafts add. Those dialogs re-create across rotation but the *confirmed ingredient* must land in the VM-stored list (the VM also makes the post-rotation dialog re-show harmless).

**`SettingsFragment.kt`** (`presentation/ui/settings/`):
- `pendingSavePayload: ExportPayload?` (line 63) is set in `startSaveToFile` (line 264–283) and consumed in the `createDocumentLauncher` callback (line 71–95: clear first, then RESULT_OK check, null-uri → `showError(export_error_save_failed)`, `fileWriter.writeToUri(uri, payload.content)`, `showSuccess(payload)`, catch → log + `export_error_save_failed`). With the field, a rotation while the picker is foregrounded loses the payload → the callback reports "Could not save the log file" — that is bug **5.1-F2**.
- The backup flow already survived rotation correctly: `handleBackupDestination` reads the deterministic `BackupState.Ready(fileName)` from the **ViewModel**, not from a Fragment field (comment at line 309–312). Follow that precedent — payload into the VM, URI stays a local of the callback.
- Keep the file-level doc string ("The Fragment — not the ViewModel — performs the file IO and launches the Intents") true; only the *pending payload carrier* moves.
- `pendingAction` must NOT move: `SettingsViewModel.export()` runs synchronously and sets `exportState`; LiveData `setValue` on the main thread dispatches observers synchronously, so `handleReady` consumes `pendingAction` in the same frame the button handler runs. There is no async gap → no rotation window. Moving it would add state without fixing a real bug.

**`LogRecipeDialogFragment.kt`** (`presentation/ui/recipe/`):
- `capIngredientScrollHeight()` (line 93–105) captures the ScrollView, then `scroll.post { resources.getDimensionPixelSize(R.dimen.dialog_log_recipe_ingredients_max_height) … }`. When the runnable fires after `onDetach`, `Fragment.resources` throws (`IllegalStateException`/`Resources$NotFoundException`) — bug **4.2-D1**. The `_binding?.scrollLogRecipeIngredients ?: return` at the top guards the null-binding case only; the posted lambda needs its own `isAdded` guard at line 96 (`resources` access is there; the `scroll` reference captured is stale after detach, so the guard must prevent *both* the resources call and the layout mutation).
- `onSaveInstanceState`/slot restore (line 113–118) already works — do not touch it.

### Architectural guardrails (non-negotiable)

- **Do NOT extend `RecipeViewModel` with draft state.** It is injected by `RecipeListFragment` (line 26) for the list/log flows; builder-only draft state would sit unused there and widen the shared VM's surface. A dedicated `RecipeBuilderViewModel` is the ownership-correct split. Both VMs get their own instance per fragment (Koin `by viewModel()` scopes to the Owner's `ViewModelStore`), so there is no cross-fragment leakage today, and after this change: builder → `RecipeBuilderViewModel`, list → `RecipeViewModel` (untouched, `RecipeViewModelTest` keeps passing unmodified).
- **Reuse the existing save path.** `RecipeBuilderViewModel.saveRecipe` must delegate to the factory-registered `SaveRecipeUseCase` (`AppModule.kt:134`, domain use case operating on `RecipeRepository`), reproducing the same small try/catch-`IllegalArgumentException` → error-`LiveData` contract as `RecipeViewModel.saveRecipe` (lines 37–52). `GetRecipesUseCase` is NOT needed in the builder VM — `RecipeListFragment` refreshes itself via the `REQUEST_RECIPE_SAVED` fragment result (RecipeListFragment.kt:178 `viewModel.refresh()`); do not wire a recipes list into the builder VM.
- **ViewModel purity:** no `Context`, no views, no `Uri`/`File`. Keep payloads/state as data classes (`ExportPayload`, `NewRecipeIngredient`). Never persist LiveData into the VM's saved state manually.
- **Synchronous execution:** no `suspend`, no coroutines (AD-2).
- **DI:** all Koin declarations in `di/AppModule.kt`; `viewModel { RecipeBuilderViewModel(get()) }` in the ViewModels section (≈ line 152). No new Gradle dependencies.
- **Error strings:** `saveError` flows use case `e.message` and are mapped in the Fragment, exactly like today (`RecipeBuilderFragment.kt:60–72` compares against `getString(R.string.recipe_error_name)`). VM-side error literals (`ERROR_NO_ACTIVE_PROFILE` etc.) in `SettingsViewModel` are untouched by this story.

### Naming / structure

- New file `app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/RecipeBuilderViewModel.kt` (package `presentation.viewmodel`, suffix `*ViewModel`).
- Test file `app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/RecipeBuilderViewModelTest.kt`.
- No layout, string, color, dimen or navigation changes — this story is behavior-preserving presentation-state plumbing except AC #3's one-line guard.

### Testing standards (from project-context.md + existing suite)

- JVM unit tests only; hand-rolled fakes; **no MockK, no Robolectric** (explicitly listed as absent — 386-test suite is pure JVM with fakes). `InstantTaskExecutorRule` for LiveData tests (see `SettingsViewModelTest.kt:28–29`).
- Fakes implement the *repository interface* (`RecipeRepository` for the builder VM — mirror `RecipeViewModelTest`'s recording fake, incl. recipes-list capture). The `SettingsViewModelTest` fake set (`FakeSessionRepository`, `FakeMealRepository`, real `ExportLogsUseCase`, `BackupDatabaseUseCase()`) is reusable as-is for the payload-carrier tests.
- Verification commands from WSL: `./gradlew :app:compileDebugSources` then `./gradlew :app:testDebugUnitTest` (`gradlew.bat` on Windows). `local.properties` SDK-dir warnings under WSL are harmless (AGENTS.md).

### Previous story intelligence

- Story 8.1 (`8-1-meal-date-correctness-at-midnight.md`) is `ready-for-dev`, **not implemented yet** — there are no 8.1 dev learnings to inherit. File-overlap check vs 8.1: 8.1 touches `MealRepositoryImpl`/`MealViewModel`/use-case fakes; 8.2 touches `RecipeBuilderFragment`/`RecipeBuilderViewModel`/`SettingsFragment`/`SettingsViewModel`/`LogRecipeDialogFragment`/`AppModule`. One expected collision: 8.1 changes `addMealEntry(date)` and therefore slightly edits shared test fakes in `SettingsViewModelTest` (its `FakeMealRepository.addMealEntry` override) and `RecipeViewModelTest`. If 8.1 lands first, rebase trivially; if 8.2 lands first, 8.1's fake updates still apply mechanically. No logical conflict either order.
- Established dev-accountability patterns from stories 6.x–7.2 (git e276e4f, b709aa5, 8dcfaa5): explicit test-assertion updates (never "quiet" edits), one-paragraph dated `**RESOLVED**` entries in `deferred-work.md`, no dev-side sprint-status edits, full-suite green before completion.

### Git intelligence

- Recent commits (`Implement Story 7.2/7.1/6.3/6.2/6.1`) show small, surgical, single-concern presentation/data changes with paired JVM tests and register closures — match that style.
- Note: `git status` shows ~170 files as modified (line-ending churn from WSL/Windows editing and per-story doc updates already committed or staged along the way). Do not "fix" line endings; leave unrelated modified files untouched.

### Latest tech information

- Web research not needed: this story adds zero libraries. Stack is pinned (Koin 3.5.6, AGP 9.2.1, Min 35 / Target 36, Java 11 compatibility — `project-context.md`). Lifecycle facts relied on: Fragments' `ViewModelStore` (Koin `by viewModel()`) survives configuration change but not process death; `Fragment.resources` is unavailable after detach. Both are stable framework behavior, not version-sensitive.

### SavedStateHandle (out of scope, extension point)

- `SavedStateHandle` in Koin 3.5.6 would extend draft survival to process death, but: the draft is a short-lived artifact (minutes), the codebase uses plain `MutableLiveData` in every ViewModel (no precedent), and it complicates the hand-rolled-fake test pattern. Story consciously scopes AC #1 to rotation/config change (register item 4.1-D3 wording: "saving draft state across process recreation … deferred as post-MVP presentation state refinement"). If 4.1-D3 is later judged closed only for process death too, that is a follow-up decision — do not preempt it in this story.

### Project Structure Notes

- `RecipeViewModel` stays at `presentation/viewmodel/RecipeViewModel.kt` unchanged despite the new sibling — intentional one-VM-per-screen-role split; do not "clean up" the shared VM in this story.
- The `LocalDate.now()` at RecipeBuilderFragment.kt:76 predates 8.1's AC-1 rule ("`LocalDate.now()` no longer inside `MealRepositoryImpl`") — it is a navigation argument for the search sheet, not a meal write; leave as-is to minimize regression surface.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Epic 8 / Story 8.2]
- [Source: _bmad-output/implementation-artifacts/deferred-work.md — 4.1-D3 (line ~80), 4.2-D1 (line ~85), 5.1-F2 (line ~94)]
- [Source: app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeBuilderFragment.kt]
- [Source: app/src/main/java/com/example/myfoodtracker/presentation/ui/settings/SettingsFragment.kt]
- [Source: app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/LogRecipeDialogFragment.kt]
- [Source: app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/RecipeViewModel.kt, SettingsViewModel.kt]
- [Source: app/src/main/java/com/example/myfoodtracker/di/AppModule.kt (lines 117–154)]
- [Source: _bmad-output/planning-artifacts/architecture/…/ARCHITECTURE-SPINE.md#MVVM layering & DI rules]
- [Source: _bmad-output/project-context.md (all sections — language/framework/testing/quality rules)]

## Dev Agent Record

### Agent Model Used

### Debug Log References

### Completion Notes List

### File List
