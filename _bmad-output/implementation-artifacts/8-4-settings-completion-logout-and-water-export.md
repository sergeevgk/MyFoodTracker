---
story_id: 8.4
story_key: 8-4-settings-completion-logout-and-water-export
epic: 8
status: backlog
created: 2026-10-25
baseline_commit: 7fee17042735dbaf77040d24d5776cb22c976e4b
---

# Story 8.4: Settings Completion — Logout & Water Export

Status: backlog

## Story

As a user,
I want a Logout action in the Settings screen and my water history included in the log export,
So that account switching needs no dashboard detour and the export reflects everything I track.

## Acceptance Criteria

1. **Given** the Settings & Data Management screen, **When** it renders, **Then** a Logout button is present and invokes the existing logout use case (same behavior as dashboard logout) (resolves **1.2-D3** — register item still open per 5-1 note).
2. **Given** the user exports logs with water history present, **When** the export completes, **Then** water rows are exported **inside the existing CSV/TXT artifact** — no second file, no format extension (resolves **5.1-F1**, ExportLogsUseCase.kt flagged in register).
3. **Given** product decision (settled 2026-10-25): there will be **no consumers and no backward-compatibility requirement** until all work is done, so the existing CSV format/consumers are freely adjustable in future stories if needed. **Representation proposal — one row per water log within the same CSV**: `Date` formatted as existing, `Meal Slot` empty, `Food Name` = "Water", `Quantity` = millilitres, macro columns = `0.0`; the same line shape in TXT export. Adjustment is fine before dev — the binding constraint is only "fit into the existing data, no extra files".
4. **Given** any new export artifact, **When** produced, **Then** it flows through the same picker/`FileProvider` primitives as 5-1 (cache-path/exports only) and zero network requests is maintained (no INTERNET permission).

## Tasks / Subtasks

- [ ] Task 1: Settings logout (AC: #1) — button + `@string/` resource + existing use case wiring; TalkBack announcement (48dp targets).
- [ ] Task 2: Water export (AC: #2, #3, #4) — extend `ExportLogsUseCase`/Settings screen to include water rows in the single export artifact (no second file).
- [ ] Task 3: Tests — JVM tests for water-row serialization within the single CSV/TXT artifact (including the "Water" row shape) — no header-compatibility assertion needed; format is adjustable until all work is done.

## Dev Notes

- Source register: `deferred-work.md` **1.2-D3** (explicitly left open by story 5-1's code review note) and **5.1-F1**.
- Format note: no backward-compatibility claim exists — there are no external consumers until all work is done (product decision 2026-10-25). The format may be revised freely in later stories; prefer fitting water into the existing data over new files.
