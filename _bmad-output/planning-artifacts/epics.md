---
stepsCompleted:
  - step-01-validate-prerequisites
  - step-02-design-epics
  - step-03-create-stories
  - step-04-final-validation
inputDocuments:
  - _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/prd.md
  - _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/addendum.md
  - _bmad-output/planning-artifacts/architecture/architecture-MyFoodTracker-2026-09-06/ARCHITECTURE-SPINE.md
  - _bmad-output/planning-artifacts/ux-designs/ux-MyFoodTracker-2026-09-06/DESIGN.md
  - _bmad-output/planning-artifacts/ux-designs/ux-MyFoodTracker-2026-09-06/EXPERIENCE.md
---

# MyFoodTracker - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for MyFoodTracker, decomposing requirements from the PRD, Architecture Spine, and UX Design specification into implementable developer user stories.

## Requirements Inventory

### Functional Requirements

- **FR-1: Local Profile Creation**: A user can create a profile with a unique name, passcode (4+ digits), and daily macro/water targets. Allows skipping one or more targets (saved as null; progress hidden in UI for skipped metrics).
- **FR-2: Profile Switching and Authentication**: App prompts for passcode on startup. Users can log out and switch profiles. Enforces strict profile data isolation (`profile_id`).
- **FR-3: Offline Autocomplete Search**: Search uses prefix matching (e.g. "chick bre*") across pre-populated (~50,000 foods) and custom library foods via SQLite FTS5 in `< 50ms`. Supports out-of-order terms.
- **FR-4: Search Result Relevance Ranking**: Search results prioritize frequently logged custom items over generic pre-seeded database items.
- **FR-5: Food Intake Log Creation**: A user can log a food item with a specified serving quantity, unit conversion (`g`, `ml`, `servings`), and meal slot (`BREAKFAST`, `LUNCH`, `DINNER`, `SNACK`), automatically calculating macros.
- **FR-6: Quick-Add Calorie Log**: A user can log calories and macros directly without selecting an item from the search database (`intake_logs` entry with null `food_id` and custom values).
- **FR-7: Quick Water Log**: A user can tap a dashboard button to increment water intake by 250ml with instant progress animation.
- **FR-8: Custom Food Creation**: A user can define a custom food item (name, brand, base serving size, macros per serving) with `is_custom = 1`, saving to private library and indexing in SQLite FTS5 immediately.
- **FR-9: Reusable Recipes**: A user can save a set of logged foods as a recipe with constituent foods and weight ratios, and log the recipe to the active day.
- **FR-10: Week-View Slider**: A horizontal list of the current week's days allows quick logging navigation with instant date switching.
- **FR-11: Monthly Historical Navigation**: A monthly calendar overlay allows jumping to any historical date for viewing/logging.
- **FR-12: CSV / TXT Log Export**: Generates local CSV/TXT file of daily/weekly/monthly log summaries using Android's native file sharing capabilities.
- **FR-13: Local Database Backup and Restore**: A user can export the full SQLite database to a local file and restore state from a backup file with schema validation and restart prompt.

### NonFunctional Requirements

- **NFR-1: Offline-First & Zero Network Leak**: 0 bytes of external HTTP/HTTPS network communication, tracking, telemetry, or third-party analytics frameworks.
- **NFR-2: Search Latency**: Instant autocomplete search results return from SQLite FTS5 in `< 50ms` on average.
- **NFR-3: Meal Logging Speed**: Complete a meal log operation in `< 15` seconds (Open app → Search → Enter weight → Save).
- **NFR-4: Data Accuracy**: Calculated daily macro summaries must be within a `0.1%` tolerance compared to manual sum computations.
- **NFR-5: Data Isolation & Privacy**: Profiles, passcode hashes (bcrypt/scrypt), and intake records stored strictly in app-private storage (`/data/data/sergeevgk.myfoodtracker/databases/`).
- **NFR-6: Accessibility Floor**: Touch targets `≥ 48dp x 48dp`, color contrast ratio `≥ 4.5:1`, TalkBack screen reader support for macro header & water widget, font scaling up to 200%.

### Additional Requirements

- **ARCH-1: Clean Architecture Layer Boundaries (AD-1)**: Enforce strict `presentation`, `domain`, `data` package separation. `domain` has zero dependencies on Android, Room, or UI frameworks.
- **ARCH-2: Synchronous Room Access (AD-2)**: Room database queries run synchronously on the main thread via `.allowMainThreadQueries()`. No `suspend` functions on DAOs.
- **ARCH-3: Domain Model Mapping (AD-3)**: Repositories map Room Entities to pure Domain Models using private extensions. Entities must not leak past `data/`.
- **ARCH-4: UseCase Factory Pattern (AD-4)**: Single-purpose UseCases with `operator fun invoke()` registered as Koin `factory` bindings.
- **ARCH-5: View Binding Lifecycle Safety (AD-5)**: Fragments clear `_binding = null` in `onDestroyView()`. `LiveData` observed using `viewLifecycleOwner`.
- **ARCH-6: Centralized Koin DI (AD-6)**: Declarations centralized in `di/AppModule.kt`: `single` for Repos/DB, `factory` for UseCases, `viewModel` for ViewModels (`by viewModel()`).
- **ARCH-7: Database Cascades & Indexing (AD-7)**: `@ForeignKey` constraints with `onDelete = ForeignKey.CASCADE` and indexed foreign keys on child tables (`intake_logs`, `water_logs`, `user_daily_goals`, `food_nutrients`, `food_serving_units`).
- **ARCH-8: SQLite FTS5 Virtual Table & Auto-Sync Triggers (AD-8)**: `foods_fts` virtual table indexed on `name` and `brand` with `AFTER INSERT`, `AFTER DELETE`, `AFTER UPDATE` triggers.
- **ARCH-9: Verified Stack Alignment**: Android SDK Target 36 / Min 35, AGP 9.2.1, Koin 3.5.6, Room 2.6.1 (KSP 2.2.10-2.0.2), Jetpack Navigation 2.6.0, Material Components 1.10.0, AppCompat 1.6.1.
- **ARCH-10: Pre-populated Asset Loader**: Pre-populated SQLite DB (~50,000 foods) bundled in APK assets and loaded via Room `createFromAsset()`.

### UX Design Requirements

- **UX-DR1: Visual Identity & Color System**: Implement M3 organic palette (`surface-base` `#F8F9FA`, `surface-raised` `#FFFFFF`, `accent-primary` `#1B4D3E`, `water-primary` `#0288D1`, `accent-highlight` `#E9C46A`) with full Dark Mode token parity.
- **UX-DR2: Tabular Typography (`tnum`)**: Apply Material Design 3 type scales using monospace tabular figures (`fontFeatureSettings = "tnum"`) for numerical macro readouts to prevent layout shifts.
- **UX-DR3: Macro Progress Header Card**: Composite dashboard card featuring progress rings/gauges for Calories, Protein, Carbs, and Fats, dynamically updating in `< 50ms` and hiding rings for skipped targets.
- **UX-DR4: Quick-Log Water Widget**: Light blue surface container (`#E1F5FE` / `#002F4B`) with prominent `+250ml` button, subtle wave animation feedback, and total hydration readout.
- **UX-DR5: Food Search Bottom Sheet & Quantity Modal**: Bottom sheet overlay (`rounded/lg` corners) with full-width search input, clear button, two-line items with "Custom" tag, and log quantity modal with unit picker (`g`, `ml`, `servings`) and meal slot selector.
- **UX-DR6: Week-View Day Selector Strip**: Scrollable horizontal strip of 7 day pills (`rounded/xl`) with active day selection and instant date switching.
- **UX-DR7: Swipe-to-Delete with Undo**: Swipe-left gesture on logged food rows to delete entry from DB with a 5-second Snackbar `[Undo]` action.

### FR Coverage Map

- **FR-1**: Epic 1 — User Profile Creation & Target Setup
- **FR-2**: Epic 1 — Profile Authentication & Startup Passcode Lock
- **FR-3**: Epic 3 — Offline Autocomplete Search via SQLite FTS5
- **FR-4**: Epic 3 — Search Result Relevance Ranking
- **FR-5**: Epic 2 — Food Intake Log Creation & Macro Calculation
- **FR-6**: Epic 2 — Quick-Add Calorie/Macro Log
- **FR-7**: Epic 2 — Quick Water Intake Logging (+250ml)
- **FR-8**: Epic 3 — Custom Food Creation & Library Management
- **FR-9**: Epic 4 — Reusable Recipe Creation & Logging
- **FR-10**: Epic 2 — Week-View Day Selector Strip
- **FR-11**: Epic 2 — Monthly Historical Calendar Navigation
- **FR-12**: Epic 5 — CSV / TXT Log Export
- **FR-13**: Epic 5 — Local Database Backup and Restore

## Epic List

### Epic 1: App Foundation, User Profiles & Security
Users can set up local profile(s), define daily calorie and macro/water goals (with optional metrics), and securely lock/unlock the application with a 4-digit passcode on startup.
**FRs covered:** FR-1, FR-2

### Epic 2: Daily Nutrition & Water Logging Dashboard
Authenticated users can view their daily macro progress against targets on the week-slider dashboard, quickly log water (+250ml), log quick-add calories/macros directly, swipe to delete entries, and navigate historical dates via week-slider and month calendar.
**FRs covered:** FR-5, FR-6, FR-7, FR-10, FR-11

### Epic 3: Offline Food Search & Custom Food Library
Users can search over 50,000 pre-populated foods instantly (< 50ms) using SQLite FTS5 prefix search, create custom foods with full macro breakdowns, and have frequently logged custom items automatically ranked higher in search results.
**FRs covered:** FR-3, FR-4, FR-8

### Epic 4: Reusable Recipe Management
Users can group multiple logged foods or custom ingredients into saved recipes with custom weight ratios, and log an entire recipe to a meal slot in a single action.
**FRs covered:** FR-9

### Epic 5: Data Portability, CSV Export & Full Database Backup/Restore
Users can export their nutrition history to CSV/TXT files for sharing, and create full local SQLite database backups or restore application state from a local `.db` file completely offline.
**FRs covered:** FR-12, FR-13

---

## Epic 1: App Foundation, User Profiles & Security

Users can set up local profile(s), define daily calorie and macro/water goals (with optional metrics), and securely lock/unlock the application with a 4-digit passcode on startup.

### Story 1.1: Local Profile Creation & Goal Setup

As a new user,
I want to create a local profile with a username, 4-digit passcode, and optional macro/water daily targets,
So that I can manage my personal health tracking on-device with privacy.

**Acceptance Criteria:**

**Given** a cold app launch with no existing profiles,
**When** the user inputs a unique profile name, 4-digit passcode, and optional target values (leaving skipped targets blank),
**Then** the profile is saved in local Room database (`users` and `user_daily_goals`) with blank goals stored as `null`.
**And** the system rejects creation if the username is empty or passcode is fewer than 4 digits.
**And** a medical disclaimer banner ("Targets are user-configured and not clinically audited") is displayed during profile setup.

### Story 1.2: Passcode Authentication & Startup Profile Lock

As a registered user,
I want the app to prompt for my passcode on startup and allow profile switching/logout,
So that my personal nutrition data remains private on shared devices.

**Acceptance Criteria:**

**Given** the application is launched,
**When** the passcode auth screen appears,
**Then** entering the correct 4-digit passcode grants access to the main dashboard.
**And** entering an incorrect passcode denies access and displays "Incorrect passcode" error message.
**And** all database queries enforce profile data isolation strictly by `profile_id`.

---

## Epic 2: Daily Nutrition & Water Logging Dashboard

Authenticated users can view their daily macro progress against targets on the week-slider dashboard, quickly log water (+250ml), log quick-add calories/macros directly, swipe to delete entries, and navigate historical dates via week-slider and month calendar.

### Story 2.1: Week-View Navigation & Month Calendar Picker

As a user,
I want a horizontal 7-day strip and a monthly calendar overlay on my dashboard,
So that I can quickly switch between current week days and view historical dates.

**Acceptance Criteria:**

**Given** the user is viewing the Dashboard,
**When** the user taps a date pill on the 7-day horizontal strip,
**Then** the active date context changes immediately and displays that date's intake logs.
**And** tapping the calendar icon opens a full month grid; selecting any date closes the overlay and updates the dashboard active date context.

### Story 2.2: Macro Progress Header Card & Dynamic Target Display

As a user,
I want to view my total calorie and macronutrient intake against my daily targets in a progress card on the dashboard,
So that I can monitor my nutrition status at a glance.

**Acceptance Criteria:**

**Given** the dashboard active date context,
**When** intake logs exist for the day,
**Then** the header card displays Calories, Protein, Carbs, and Fats using monospace tabular figures (`fontFeatureSettings = "tnum"`).
**And** if a macro goal was skipped during profile creation, its corresponding progress ring is hidden, displaying only active goals.

### Story 2.3: Quick Water Logging (+250ml)

As a user,
I want a quick-add button to log 250ml of water with one tap on the dashboard,
So that I can track my hydration effortlessly.

**Acceptance Criteria:**

**Given** the dashboard surface,
**When** the user taps the `+250ml` button on the water widget,
**Then** a `water_log` record (+250ml) is persisted to Room DB and the water fill ring updates immediately with a wave animation.
**And** TalkBack screen reader announces "+250 milliliters water added. Total: X milliliters".

### Story 2.4: Quick-Add Calorie & Macro Logging

As a user,
I want to log calories and macros directly without selecting a food item from the search database,
So that I can track meals when specific food items aren't needed.

**Acceptance Criteria:**

**Given** the dashboard FAB / Quick-Add option,
**When** the user inputs a name/description, Calories, Protein, Carbs, Fat, and selects a meal slot (`BREAKFAST`, `LUNCH`, `DINNER`, `SNACK`),
**Then** an `intake_log` record with a `null` `food_id` is created and dashboard macros update instantly.

### Story 2.5: Swipe-to-Delete Intake Logs with Undo

As a user,
I want to swipe left on a logged food entry to delete it and have a quick Undo option,
So that I can correct accidental meal entries.

**Acceptance Criteria:**

**Given** a meal slot card on the dashboard with logged items,
**When** the user swipes an item row left and taps Delete,
**Then** the record is removed from Room DB and a Snackbar with `[Undo]` appears for 5 seconds.
**And** tapping `[Undo]` restores the record and recalculates dashboard macro totals instantly.

---

## Epic 3: Offline Food Search & Custom Food Library

Users can search over 50,000 pre-populated foods instantly (< 50ms) using SQLite FTS5 prefix search, create custom foods with full macro breakdowns, and have frequently logged custom items automatically ranked higher in search results.

### Story 3.1: SQLite FTS5 Search Engine & Pre-populated Food Database

As a user,
I want instant prefix autocomplete search (< 50ms) across a bundled database of over 50,000 foods offline,
So that I can search items without an internet connection.

**Acceptance Criteria:**

**Given** the food search bottom sheet,
**When** the user types search terms (e.g. "chick bre*"),
**Then** SQLite FTS5 prefix search returns matching pre-populated foods in `< 50ms`.
**And** out-of-order search terms (e.g. "breast chicken") return matching results.

### Story 3.2: Food Search Quantity Modal & Meal Slot Logging

As a user,
I want to select a search result, enter serving weight/units, and assign it to a meal slot,
So that it calculates and logs exact macronutrients to my daily log.

**Acceptance Criteria:**

**Given** search autocomplete results,
**When** the user selects a food item, inputs serving weight/quantity, selects serving unit (`g`, `ml`, `servings`), picks meal slot, and saves,
**Then** calculated macros ($Base \times \frac{Quantity}{100}$) are written to `intake_logs` and dashboard macro rings update in `< 50ms`.

### Story 3.3: Custom Food Creation & Relevance Ranking

As a user,
I want to create custom food entries with brand names and macro specs, and have frequently logged custom items rank higher in search results,
So that my personal food items are prioritized.

**Acceptance Criteria:**

**Given** the search bottom sheet surfaces "Create Custom Food",
**When** the user inputs food name, brand, base serving size (100g), and macro values per serving and saves,
**Then** the food is saved with `is_custom = 1` and SQLite FTS triggers index it immediately.
**And** frequently logged custom foods rank higher in search results than pre-seeded items with identical prefix match scores.

---

## Epic 4: Reusable Recipe Management

Users can group multiple logged foods or custom ingredients into saved recipes with custom weight ratios, and log an entire recipe to a meal slot in a single action.

### Story 4.1: Recipe Builder & Ingredient Grouping

As a user,
I want to save a group of foods and ingredient quantities as a reusable recipe,
So that I can log complex meals in a single step later.

**Acceptance Criteria:**

**Given** the recipe builder interface,
**When** the user enters a recipe name, adds constituent foods with quantities/ratios, and saves,
**Then** the recipe and its constituent ingredient mappings are persisted in `recipes` and `recipe_ingredients` database tables.

### Story 4.2: One-Tap Recipe Logging

As a user,
I want to select a saved recipe and log it to a meal slot for the current day,
So that all constituent foods and macros are added at once.

**Acceptance Criteria:**

**Given** the recipe list or search sheet,
**When** the user selects a saved recipe, picks a meal slot, and logs it,
**Then** individual constituent intake logs are written to the active date context and daily macro totals update immediately.

---

## Epic 5: Data Portability, CSV Export & Full Database Backup/Restore

Users can export their nutrition history to CSV/TXT files for sharing, and create full local SQLite database backups or restore application state from a local `.db` file completely offline.

### Story 5.1: CSV and TXT Log Export

As a user,
I want to export my daily/weekly/monthly log summaries as CSV/TXT files via Android's native file sharing,
So that I can analyze or share my nutrition history offline.

**Acceptance Criteria:**

**Given** the Settings screen,
**When** the user selects "Export CSV Logs" and picks a date range,
**Then** a formatted CSV file with standard headers (`Date`, `Meal Slot`, `Food Name`, `Quantity`, `Calories`, `Protein`, `Carbs`, `Fat`) is generated and launched via Android's native share picker.
**And** zero network requests are triggered.

### Story 5.2: Offline Full Database Backup & Restore

As a user,
I want to back up my entire SQLite database to a local file and restore it on another device,
So that my data is portable without cloud sync.

**Acceptance Criteria:**

**Given** the Settings screen,
**When** the user selects "Backup Database",
**Then** the entire SQLite database file is saved to local storage via system file picker.
**And** when the user selects "Restore Database" and picks a `.db` file, a modal alert warns of overwrite, validates schema integrity, overwrites active DB, and prompts an application restart.


