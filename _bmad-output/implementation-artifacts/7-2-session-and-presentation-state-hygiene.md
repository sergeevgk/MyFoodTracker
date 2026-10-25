---
story_id: 7.2
story_key: 7-2-session-and-presentation-state-hygiene
epic: 7
status: backlog
created: 2026-10-25
baseline_commit: 7fee17042735dbaf77040d24d5776cb22c976e4b
---

# Story 7.2: Session & Presentation-State Hygiene

Status: backlog

## Story

As a privacy-conscious user,
I want passcode hashes kept out of presentation state and a visible way to decline a remembered session,
So that my credentials never surface beyond the auth layer and profile switching is one tap.

## Acceptance Criteria

1. **Given** the passcode auth flow, **When** `PasscodeAuthState` and any `UserProfile` variant presented to fragments is built, **Then** `passcodeHash` is not carried in presentation state (hash verification happens inside domain/use-case internals; UI gets IDs + display names) (resolves **1.2-D5**).
2. **Given** a remembered session auto-logging in on app start, **When** the passcode/auth screen appears for the restored profile, **Then** a "Not you?" affordance appears that declines the restored session and returns to the profile list — no full logout round-trip needed (resolves **3.4-D2**, SharedPrefsSessionStorage.kt:19-27 / PasscodeAuthFragment.kt:116-123 flagged in register).
3. **Given** session TTL (e.g. 30 days), **When** the product decision gate is evaluated, **Then** it is **skipped by default** (3.4-D2 was deferred by user decision: "remember until explicit logout"); implement only on explicit request — document the decision in the story.
4. **Given** behavior parity, **When** all existing auth tests run, **Then** passcode profiles, isolation (`profile_id` scoping) and remember-me flows pass unchanged.

## Tasks / Subtasks

- [ ] Task 1: Strip hash from presentation (AC: #1)
  - [ ] Audit `PasscodeAuthState.Content.profiles` + in-memory session payload; introduce a presentation profile model without `passcodeHash`.
  - [ ] domain/data decoupling rule: entities→domain mapping stays in repository layer.
- [ ] Task 2: "Not you?" affordance (AC: #2)
  - [ ] `PasscodeAuthState.Authenticated` gains a discriminator or the fragment gains an explicit decline action that clears the stored remembered profile id (`SharedPrefsSessionStorage`).
- [ ] Task 3: TTL gate (AC: #3) — decision record only, unless product owner opts in.
- [ ] Task 4: Tests — JVM unit tests for state building (no hash present) + decline-flow use case.

## Dev Notes

- Source register: `deferred-work.md` **1.2-D5** and **3.4-D2**.
- 3.4-D2 was consciously deferred by user decision — treat the TTL as opt-in, do not silently change remember-me semantics.
