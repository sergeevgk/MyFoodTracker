---
story_id: 8.1
story_key: 8-1-meal-date-correctness-at-midnight
epic: 8
status: backlog
created: 2026-10-25
baseline_commit: 7fee17042735dbaf77040d24d5776cb22c976e4b
---

# Story 8.1: Meal Date Correctness at Midnight Boundaries

Status: backlog

## Story

As a user logging an entry a minute before midnight,
I want the entry recorded on the date I selected in the dashboard,
So that my log does not silently land on the wrong day.

## Acceptance Criteria

1. **Given** `MealRepositoryImpl` and an active dashboard date selected by the user (including `MaterialDatePicker` historical dates), **When** `addMealEntry`, `logQuickAdd` or `logFoodEntry` executes, **Then** the target `LocalDate` is an explicit parameter chained from the caller (DashboardViewModel), and `LocalDate.now()` no longer appears inside repository write paths (resolves **2.1-D1**, MealRepositoryImpl.kt:33 flagged in register).
2. **Given** multi-day usage, **When** an entry is written while the device clock crosses midnight between tap and write, **Then** the entry lands on the UI-selected date, not the new device date.
3. **Given** existing callers (swipe-delete restore flows, recipe logging), **When** signatures change, **Then** all call sites pass the selected date and compile succeeds with no behavioral regression in single-profile flows.

## Tasks / Subtasks

- [ ] Task 1: Signature refactor (AC: #1, #3) — add `targetDate: LocalDate` to write-path repository methods → use-case classes → `DashboardViewModel` passes `selectedDate`.
- [ ] Task 2: Repository cleanup (AC: #1) — remove the internal `LocalDate.now()` (read-only now()/today defaults, if any, resolve at the ViewModel boundary).
- [ ] Task 3: Unit tests (AC: #1, #2) — MockK JVM tests: 23:59 write vs next-day target; historical-date write.

## Dev Notes

- Source register: `deferred-work.md` **2.1-D1** (dev notes there: mismatch scenario = non-UTC zones, `MaterialDatePicker` UTC conversion).
- Keep the week-slider/calendar selection flow as the single source of truth for the date (project rule: ViewModel owns state, fragments observe with `viewLifecycleOwner`).
