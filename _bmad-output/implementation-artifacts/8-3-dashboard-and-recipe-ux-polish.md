---
story_id: 8.3
story_key: 8-3-dashboard-and-recipe-ux-polish
epic: 8
status: backlog
created: 2026-10-25
baseline_commit: 7fee17042735dbaf77040d24d5776cb22c976e4b
---

# Story 8.3: Dashboard & Recipe UX Polish

Status: backlog

## Story

As a user,
I want tactile confirmation on quick-add water, no misleading Quick Add button in the recipe flow, and an inspection view for saved recipes,
So that the UI responds to what I do and cannot take me down a wrong path.

## Acceptance Criteria

1. **Given** a tap on the +250ml water button, **When** the log is written, **Then** haptic feedback is triggered (`performHapticFeedback(KEYBOARD_TAP)`) (resolves **2.3-D1**, DashboardFragment.kt:89 flagged in register).
2. **Given** `FoodSearchBottomSheet` opened from `RecipeBuilderFragment`, **When** it renders, **Then** the "Quick Add" button is hidden in the recipe flow and its unavailable snackbar action can never be reached (resolves **4.1-D1**, RecipeBuilderFragment.kt:95-102 flagged in register).
3. **Given** the recipe list, **When** the user taps a saved recipe card, **Then** a read-only detail view (dialog or screen) shows the ingredient breakdown — name, quantity, macro per ingredient — plus the macro sum (resolves **4.1-D2**, RecipeListFragment.kt:34 flagged in register; **editing is out of scope**).
4. **Given** accessibility rules from story 2-6/5-2, **When** the new detail view renders, **Then** all strings go through `@string/` resources, 48dp touch targets, and TalkBack announcements where state changes.

## Tasks / Subtasks

- [ ] Task 1: Haptics (AC: #1) — one-line add at the water quick-add call site in `DashboardFragment`.
- [ ] Task 2: Conditional Quick Add (AC: #2) — pass a flag/arg into `FoodSearchBottomSheet` for the recipe-flow instantiation; hide button per flag.
- [ ] Task 3: Recipe detail view (AC: #3, #4) — read-only view fed by existing recipe+ingredients repository query (recipe entity + `@Relation` pattern per Room 3 rules).
- [ ] Task 4: Unit tests where logic exists (detail mapper); UI work verified by manual device walkthrough (no emulator policy).

## Dev Notes

- Source register: `deferred-work.md` **2.3-D1**, **4.1-D1**, **4.1-D2**.
- 2.3-D1 was a user-designated "post-MVP tactile polish" — this epic is that post-MVP.
