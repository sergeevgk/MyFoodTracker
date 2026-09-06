---
title: MyFoodTracker PRD
status: final
created: 2026-08-08
updated: 2026-08-08
---

# PRD: MyFoodTracker

## 0. Document Purpose
This PRD outlines the requirements for MyFoodTracker, a lightweight, privacy-first mobile application designed to help users log food and water intake, track macronutrients, and monitor history offline. It is written for developers and stakeholders to align on features, user journeys, functional constraints, and success criteria. 

As a primary development reference project, this PRD aligns with the technical boundaries defined in [project-context.md](file:///c:/Users/Xenae/Documents/source/repos/AndroidStudioProjects/MyFoodTracker/_bmad-output/project-context.md). Detailed technical designs and DB schema definitions are decoupled from this document and reside in the [addendum.md](file:///c:/Users/Xenae/Documents/source/repos/AndroidStudioProjects/MyFoodTracker/_bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/addendum.md).

## 1. Vision
MyFoodTracker provides a fast, offline-first, ad-free, and subscription-free utility for digital sovereignty in personal nutrition tracking. The app stores all user profiles and intake data locally on-device. By eliminating network-dependent autocomplete API calls and commercial trackers, MyFoodTracker guarantees instant feedback and absolute privacy, offering a direct contrast to bloated commercial health platforms.

## 2. Target User

### 2.1 Jobs To Be Done
* **Functional**: Log daily meals, calorie counts, macronutrient levels (Protein, Carbs, Fats), and water intake instantly without internet connectivity.
* **Contextual**: Look up common foods quickly using instant search autocomplete while offline.
* **Emotional / Privacy**: Track sensitive dietary history with peace of mind, knowing that no health profiling, data brokering, or advertising network receives their data.
* **Technical Reference**: Demonstrate a high-quality implementation of Kotlin MVVM, Clean Architecture, Koin dependency injection, and Room local databases.

### 2.2 Non-Users (v1)
* Users who require cloud synchronization, web access, or multi-device real-time sync in MVP.
* Users who expect pre-seeded recipes or a social feed for sharing meals.
* Users who require automatic barcode resolution via external cloud APIs in MVP.

### 2.3 Key User Journeys
The primary journeys are numbered globally as UJ-1 through UJ-4:

* **UJ-1. User searches and logs a breakfast item**
  * **Persona + context**: User is tracking macros to optimize health.
  * **Entry state**: Authenticated via local passcode screen, viewing the Week-View Dashboard on a mobile screen.
  * **Path**: User taps the FAB (+), typing "chick" in the search box. Instant autocomplete displays "Chicken Breast (Raw)" and "Chicken Breast (Cooked)". User selects "Chicken Breast (Cooked)", inputs "150" in the weight field (g), and taps the "Save" checkmark.
  * **Climax**: The dashboard UI updates instantly (< 50ms) to reflect the added macros against daily targets.
  * **Resolution**: User is returned to the dashboard. The macro progress rings animate to show the updated totals.
  * **Edge case**: If the food is not found in the search results, a prominent "Create Custom Food" shortcut is displayed.

* **UJ-2. User logs water intake**
  * **Persona + context**: User wants to stay hydrated during the day.
  * **Entry state**: Viewing the Week-View Dashboard.
  * **Path**: User taps the "+250ml" quick-increment button.
  * **Climax**: The water progress circle fills up instantly with a subtle wave animation.
  * **Resolution**: The total water counter increments by 250ml.

* **UJ-3. User manages and logs custom foods**
  * **Persona + context**: User prepares a custom meal that is not present in the pre-populated database.
  * **Entry state**: In the search overlay, unable to find desired brand.
  * **Path**: User taps "Create Custom Food". User inputs the name, brand, base serving size (100g), and macronutrient values per serving. User checks the "Save to Custom Library" box.
  * **Climax**: The custom food is persisted in local custom food library and immediately pre-selected for intake logging.
  * **Resolution**: User logs the weight and saves the item, adding it to daily intake logs.

* **UJ-4. User exports historical logs**
  * **Persona + context**: User wishes to share monthly macro intake.
  * **Entry state**: On the Month Calendar screen.
  * **Path**: User taps the "Export Logs" icon, selects "CSV", and picks the current month.
  * **Climax**: A native file picker launches to save the formatted CSV file locally.
  * **Resolution**: User saves the file and can share it using standard system sharing intents.

## 3. Glossary
* **User Profile**: A local partition containing profile information (username, passcode, and daily macro/water goals).
* **Pre-populated Food Database**: A read-only SQLite database bundled with the application containing USDA/Open Food Facts entries.
* **Custom Food Library**: A user-managed set of custom foods stored in the user's local database.
* **Intake Log**: A record mapping a timestamp, quantity, and specific food item to a user profile for a given daily slot.
* **Meal Slot**: A categorization for logs: `Breakfast`, `Lunch`, `Dinner`, or `Snack`.
* **Water Log**: A historical record of water volume in milliliters logged by a user.
* **FTS5 Virtual Table**: A virtual SQLite table used to index food names and brands to support prefix-matched full-text search.

## 4. Features

### 4.1 Local User Profiles
**Description:** On first launch, the app prompts the user to create a profile, set daily macro and water goals, and establish a password/PIN for data access. Multiple user profiles can be stored locally on the same device. The app shall allow the user to skip setting up one or more goal values (water, specific macros). For example, a user can provide only a calorie goal without specifying carb, fat, or protein targets.
[ASSUMPTION: We assume a simple local passcode/PIN screen storing a bcrypt/scrypt hash locally is sufficient for security without complex device-level biometrics in MVP.]

**Functional Requirements:**
#### FR-1: Local Profile Creation
*   **Description**: A user can create a profile with a unique name, passcode, and macro/water targets.
*   **Consequences (testable)**: 
    *   System must reject profile creation if username is empty or passcode is less than 4 digits.
    *   Profile targets are saved in the local database.
    *   The app allows skipping setting up one or more goal values. Blank targets are saved as null, and the UI dynamically hides progress tracking for skipped metrics.

#### FR-2: Profile Switching and Authentication
*   **Description**: The app prompts for the passcode on startup. Users can log out and switch profiles.
*   **Consequences (testable)**:
    *   Entering an incorrect passcode denies access and shows an error message.
    *   Profile data isolation must be enforced: queries for logs must filter by `profile_id`.

---

### 4.2 Offline Food Search & Autocomplete
**Description:** Users search for foods in the search sheet. Autocomplete must be instant (< 50ms) using a local virtual index.
[ASSUMPTION: The pre-populated USDA/Open Food Facts database containing approximately 50,000 common foods is bundled as a static database asset in the APK.]

**Functional Requirements:**
#### FR-3: Offline Autocomplete Search
*   **Description**: The search query uses prefix matching on terms (e.g. "chick bre*") to match both pre-populated foods and custom library foods. Realizes UJ-1.
*   **Consequences (testable)**:
    *   Search latency must be `< 50ms` on average.
    *   Typing terms out of order (e.g., "breast chicken") must return matching results.

#### FR-4: Search Result Relevance Ranking
*   **Description**: Search results must prioritize frequently logged custom items over generic pre-seeded database items.
*   **Consequences (testable)**:
    *   A custom food logged 5 times in the last week must rank higher in search results than a pre-seeded food with the same token prefix match score.

---

### 4.3 Food & Water Intake Logging
**Description:** Users record food consumption by inputting quantities (in grams/milliliters/servings) against a specific meal slot, and log water intake with a single tap.

**Functional Requirements:**
#### FR-5: Food Intake Log Creation
*   **Description**: A user can log a food item with a specified serving quantity, unit conversion, and meal slot. Realizes UJ-1.
*   **Consequences (testable)**:
    *   Saving a log adds an entry in the local database with calculated macros:
        $$\text{Calculated Macro} = \text{Base Nutrient per 100g} \times \frac{\text{Quantity in Grams}}{100}$$
    *   View model observers must update the UI state instantly upon database commit.

#### FR-6: Quick-Add Calorie Log
*   **Description**: A user can log calories and macros directly without selecting an item from the search database.
*   **Consequences (testable)**:
    *   Creating a quick-add log creates an `intake_log` record with a null `food_id` but filled custom values.

#### FR-7: Quick Water Log
*   **Description**: A user can tap a dashboard button to increment their water intake by 250ml. Realizes UJ-2.
*   **Consequences (testable)**:
    *   Tapping the `+250ml` button creates a new `water_log` record.
    *   The total water progress display updates immediately.

---

### 4.4 Custom Food & Recipe Manager
**Description:** Allows creating custom foods with specific macro breakdowns, and grouping multiple ingredients into recipes.

**Functional Requirements:**
#### FR-8: Custom Food Creation
*   **Description**: A user can define a custom food item (name, brand, macros per serving) and persist it to their private library. Realizes UJ-3.
*   **Consequences (testable)**:
    *   Custom foods must have `is_custom = 1` and be indexed in the SQLite FTS5 table immediately upon creation.

#### FR-9: Reusable Recipes
*   **Description**: A user can save a set of logged foods as a recipe for fast logging later.
*   **Consequences (testable)**:
    *   A recipe tracks constituent foods and weight ratios. Logging a recipe logs its constituent foods mapped to the current day.

---

### 4.5 Week-View Dashboard & Monthly Navigation
**Description:** The landing dashboard features calorie and macro targets alongside a week-switcher. A monthly view supports historical logging lookup.

**Functional Requirements:**
#### FR-10: Week-View Slider
*   **Description**: A horizontal list of the current week's days allows quick logging navigation.
*   **Consequences (testable)**:
    *   Tapping a day in the horizontal slider switches the current active date context.
    *   The dashboard updates to show that date's intake log.

#### FR-11: Monthly Historical Navigation
*   **Description**: A monthly calendar overlay allows jumping to any historical date. Realizes UJ-4.
*   **Consequences (testable)**:
    *   Selecting a date in the calendar closes the overlay and updates the dashboard active date context.

---

### 4.6 Data Portability & Export
**Description:** Users can export their local log data into text or CSV tables.

**Functional Requirements:**
#### FR-12: CSV / TXT Log Export
*   **Description**: Generates a local CSV or TXT file of the daily/weekly/monthly log summaries using Android's native file sharing capabilities. Realizes UJ-1.
*   **Consequences (testable)**:
    *   The generated file must contain column headers (`Date`, `Meal Slot`, `Food Name`, `Quantity`, `Calories`, `Protein`, `Carbs`, `Fat`).
    *   No external server requests may be triggered during export.

#### FR-13: Local Database Backup and Restore
*   **Description**: A user can backup the entire SQLite database to a local file, and restore the application state from a previously exported backup file.
*   **Consequences (testable)**:
    *   Exporting creates a single DB backup file saved via native system sharing.
    *   Restoring validates database integrity and schemas before overwriting the active DB, and prompts for an application restart.

---

## 5. Non-Goals (Explicit)
*   **No Network Communications**: The app must not make external HTTP/HTTPS API calls for logging, search, or user profiles.
*   **No Barcode API Sync**: Scanning barcodes in MVP is restricted to local lookup. Offline barcode detection parses OCR/raw barcodes, but no cloud API lookup is performed.
*   **No Third-Party Analytics**: No Google Analytics, Firebase, or other tracking frameworks are permitted.

## 6. MVP Scope

### 6.1 In Scope
*   Local multi-profile creation and passcode lock.
*   Offline autocomplete search against pre-seeded database (SQLite FTS5).
*   Macro intake logging (Calories, Protein, Carbs, Fats) and Quick Water Log (+250ml).
*   Custom food builder and library.
*   Week-View Dashboard and Monthly navigation calendar.
*   Data export (CSV / plain text tables).
*   Local database backup and restore (full DB import/export).

### 6.2 Out of Scope for MVP
*   Customizing the quick-log water volume (e.g. changing from static 250ml to custom values like 330ml).
*   Camera-based food/portion AI classification.
*   Smart meal combinations, dynamic autocomplete suggestions based on historical combinations.
*   Self-hosted cloud sync (Nextcloud/ownCloud sync).
*   Wearable and activity tracker integrations.

## 7. Success Metrics & Counter-Metrics
*   **Primary Metric (Offline speed)**: Local SQLite FTS5 search results must return and logs must save in under `50 ms`.
*   **Primary Metric (Usability)**: A user can log a meal in under `15` seconds (Open app → Search → Enter weight → Save).
*   **Secondary Metric (Accuracy)**: Calculated daily macro summaries must be within a tolerance of `0.1%` compared to manual sum computations.
*   **Counter-Metric (Zero-Network-Leak)**: The application must register `0` bytes of network telemetry or data transfer over Wi-Fi or cellular networks.

## 8. Open Questions
1.  **Water Container Customization**: Resolved: quick-log water volume customization is deferred to post-MVP (v2).
2.  **Pre-populated Food Database Size**: Resolved: include the larger dataset (~50,000 items) in the APK to ensure rich offline capability.
3.  **Local Backup/Restore**: Resolved: full database backup/restore is added to the MVP scope.

## 9. Assumptions Index
*   **Passcode Security**: Storing a simple hashed passcode locally is sufficient for offline profile privacy.
*   **Pre-populated DB distribution**: The initial read-only food database is bundled as an asset in the APK, not downloaded dynamically.
*   **Single-device usage**: No cloud syncing is needed in MVP; users will manage data portability using files.

---

## 10. Cross-cutting Quality & Guardrails

### 10.1 Safety & Medical Disclaimers
*   The application does not provide medical or clinical nutritional advice. The profile setup must display a disclaimer stating that targets are user-configured and not clinically audited.

### 10.2 Privacy Guardrails
*   All databases and profile targets must be stored in private application directories (`/data/data/sergeevgk.myfoodtracker/databases/`) that are inaccessible to other applications without root privileges.
