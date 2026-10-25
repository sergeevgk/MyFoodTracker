---
story_id: 8.2
story_key: 8-2-state-survival-and-lifecycle-hardening
epic: 8
status: backlog
created: 2026-10-25
baseline_commit: 7fee17042735dbaf77040d24d5776cb22c976e4b
---

# Story 8.2: State Survival & Lifecycle Hardening

Status: backlog

## Story

As a user,
I want rotation and configuration changes not to lose my recipe draft or pending export,
So that long in-progress actions survive routine state changes.

## Acceptance Criteria

1. **Given** the recipe builder with a draft in progress, **When** the device rotates (or the config change recreates the fragment), **Then** the draft — recipe name, chosen ingredients, ingredient weights — is restored from a `RecipeBuilderViewModel` and survives rotation (resolves **4.1-D3**, RecipeBuilderFragment.kt:33 flagged in register).
2. **Given** the export flow with the system document picker open, **When** a configuration change occurs while the picker is foregrounded, **Then** the pending `ExportPayload` survives (moved into a ViewModel or `onSaveInstanceState`) and the file write completes after the activity result returns — no "Could not save the log file" (resolves **5.1-F2**, SettingsFragment.kt flagged in register).
3. **Given** `LogRecipeDialogFragment.capIngredientScrollHeight` posts a runnable, **When** it executes after the dialog fragment was detached, **Then** resources access is guarded by `isAdded`/`context != null` and no crash or `Resources$NotFoundException` occurs (resolves **4.2-D1**, LogRecipeDialogFragment.kt:94 flagged in register).

## Tasks / Subtasks

- [ ] Task 1: Recipe draft → ViewModel (AC: #1) — create/extend a builder ViewModel holding draft state; fragment reads/writes it (Koin `viewModel` registration, `AppModule.kt`).
- [ ] Task 2: Export payload → ViewModel (AC: #2) — move pending payload out of the Fragment field; keep the 5-1 contract for picker contract + error literals.
- [ ] Task 3: Lifecycle guard (AC: #3) — add the `isAdded` check in the posted runnable.
- [ ] Task 4: Tests — JVM unit tests: draft VM state machine; payload retention across simulated config change; guard logic where testable. `compileDebugSources` + `testDebugUnitTest` verification.
- [ ] Manual notice: rotation paths are UI-level — walk builder rotation + picker-open rotation on device (no emulator per policy).

## Dev Notes

- Source register: `deferred-work.md` **4.1-D3**, **5.1-F2**, **4.2-D1**.
- VM rule: ViewModels must not touch Context/views — keep payloads as data (project-context.md).
