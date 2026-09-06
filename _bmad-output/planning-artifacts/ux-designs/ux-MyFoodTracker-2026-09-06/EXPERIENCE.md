---
name: MyFoodTracker
status: final
sources:
  - {planning_artifacts}/prds/prd-MyFoodTracker-2026-08-08/prd.md
  - {planning_artifacts}/prds/prd-MyFoodTracker-2026-08-08/addendum.md
updated: 2026-09-06
---

# EXPERIENCE.md: MyFoodTracker Experience & Information Architecture

## Foundation

MyFoodTracker is built as a single-device, multi-surface Android application (optimizing for mobile phones and tablet/foldable layouts). It uses Android Material Design 3 guidelines (with Jetpack Compose / View Binding UI parity). 

Visual identity tokens, colors, shapes, and typography are governed by [DESIGN.md](file:///c:/Users/Xenae/Documents/source/repos/AndroidStudioProjects/MyFoodTracker/_bmad-output/planning-artifacts/ux-designs/ux-MyFoodTracker-2026-09-06/DESIGN.md). This document owns information architecture, component behavior, interaction rules, state treatments, and key user flows.

---

## Information Architecture

The app is organized around a primary **Dashboard** surface with a top calendar navigation strip, bottom sheet overlays for search, and a dedicated **Settings** screen for data management.

| Surface | Reached From | Purpose | Key Components |
|---|---|---|---|
| **Passcode Lock Screen** | App launch (cold/warm) | Authenticate user profile locally | PIN Pad, Profile Selector, Unlock Button |
| **Week-View Dashboard** | Post-auth main screen | View daily macro progress, water intake, and logged meals for the selected date | Week-Day Slider, Macro Header Card, Meal Slot Cards (`Breakfast`, `Lunch`, `Dinner`, `Snack`), Quick Water Widget (`+250ml`), FAB (+) |
| **Food Search & Autocomplete Sheet** | Dashboard FAB (+) or Meal Card (+) | Search 50,000+ local foods or custom library | Search Bar, Autocomplete List, "Create Custom Food" Button, Quick-Add Calories Option |
| **Log Food Quantity Modal** | Selecting a food in search | Input weight/servings, select meal slot, log entry | Quantity Slider/Input, Unit Dropdown (`g`, `ml`, `servings`), Slot Picker, Save Button |
| **Custom Food Editor Screen** | Search Sheet ("Create Custom Food") | Add new custom food item with macro breakdown | Text Fields (Name, Brand), Macro Inputs (Calories, Protein, Carbs, Fat per 100g), Save to Library Checkbox |
| **Month Calendar History Screen** | Dashboard calendar icon | Select historical date for viewing/logging | Full Month Grid with logged-day indicators, Back to Today FAB |
| **Settings & Data Management Screen** | Dashboard top overflow menu | Manage profile goals, export CSV logs, and backup/restore database | Profile Goal Editor, CSV Log Export Button, DB Backup/Restore Buttons |

---

## Voice and Tone

Microcopy is clear, precise, unobtrusive, and respectful of the user's digital autonomy.

| Scenario | Recommended Microcopy | Anti-pattern / Avoid |
|---|---|---|
| **First Launch Profile** | "Set your daily targets (optional). You can set calories now and add macros later." | "Let's kick off your weight loss journey!" |
| **Save Confirmation** | "Logged 150g Chicken Breast." (Toast / Snackbar) | "Awesome job! You earned 50 points!" |
| **Offline Performance** | "All data stays on this device." | "Offline mode active — cloud sync suspended." |
| **Empty Meal Slot** | "No foods logged for Breakfast yet." | "You haven't eaten breakfast! Log now!" |
| **Database Backup** | "Database exported to local downloads." | "Success! Your cloud backup is synced." |

---

## Component Patterns

### 1. Week-Day Horizontal Slider
- **Behavior**: Horizontal scrolling strip showing 7 days centered on the selected date. Tapping a day switches active date context immediately without page reload.
- **Swipe gesture**: Swiping left/right shifts the week window.

### 2. Search Autocomplete List
- **Behavior**: Typing triggers instant SQLite FTS5 prefix search (`query*`). Results render in `< 50ms`.
- **Ranking**: Frequently logged custom items appear at the top.
- **Zero Results**: Displays "No foods matching '[query]'." with a prominent button: `[+ Create Custom Food]`.

### 3. Quick-Add Water Widget
- **Behavior**: Tapping `+250ml` increments the daily water total instantly.
- **Feedback**: A subtle wave animation updates the fill percentage. Long-pressing the widget allows logging custom water volume (post-MVP).

### 4. Swipe-to-Delete Intake Logs
- **Behavior**: Swiping a logged food item row left reveals a red Delete action background. Tapping Delete removes the record from Room DB and updates the macro header instantly with a Snackbar `[Undo]` action (5-second timeout).

---

## State Patterns

| State | Surface | Treatment |
|---|---|---|
| **Cold Open / Auth Required** | Passcode Screen | Prompts for 4-digit PIN. If incorrect, highlights field in red with "Incorrect passcode". |
| **Empty Meal Slot** | Dashboard Cards | Displays subtle outline card with text "No items logged" and a small `+ Add` text button. |
| **Skipped Macro Targets** | Dashboard Header Card | If user skipped setting Protein/Carb/Fat targets during profile creation, the corresponding progress ring/bar is hidden, displaying only the Calorie gauge. |
| **Empty Search Query** | Search Sheet | Displays "Recent Foods" list (last 10 logged items) for one-tap re-logging. |
| **Database Restore Warning** | Settings | Modal alert: "Restoring a database backup will overwrite current local logs. Proceed?" with explicit `[Cancel]` and `[Overwrite & Restore]` buttons. |

---

## Interaction Primitives

- **Single Tap**: Select item, trigger action, toggle navigation tab, add +250ml water.
- **Typeahead Typing**: Instant keystroke autocomplete filtering.
- **Swipe Left**: Delete food log entry with Undo option.
- **Drag Horizontal**: Scroll week-day selector.
- **Banned Gestures / Elements**: No pull-to-refresh (data is local, zero network latency), no ads, no full-screen popups, no mandatory video/interstitial dialogs.

---

## Accessibility Floor

- **Touch Targets**: Minimum `48dp x 48dp` for all buttons, FABs, and day selector pills.
- **Color Contrast**: Text to background contrast ratio `≥ 4.5:1` in both light and dark themes.
- **Screen Reader Support (TalkBack)**:
  - Macro header announces progress: `"Calories: 1,450 of 2,000 kilocalories consumed, 72 percent"`.
  - Water widget announces: `"+250 milliliters water added. Total: 1,500 milliliters"`.
- **Dynamic Text Scaling**: Supports system font scaling up to 200% without overlapping or clipped macro values.

---

## Key Flows

### Flow 1 — Search & Log Breakfast Item
1. User opens app and authenticates with passcode.
2. User taps the FAB `(+)` or `+ Add` button on the Breakfast card.
3. Search bottom sheet opens with focus on search field.
4. User types `"chick"`. Autocomplete displays `"Chicken Breast (Cooked)"` in `< 50ms`.
5. User taps `"Chicken Breast (Cooked)"`. Log Food Modal opens.
6. User inputs `"150"` grams and selects `"Breakfast"`.
7. User taps `[Save]`.
8. **Climax**: Modal closes, Breakfast card shows `"Chicken Breast (Cooked) - 150g"`, and Calorie/Protein progress rings animate to updated totals.

### Flow 2 — Quick Water Increment
1. User views Dashboard.
2. User taps `+250ml` on the Water Widget.
3. **Climax**: Water ring fills with blue wave animation and total increments from `1,000ml` to `1,250ml`.

### Flow 3 — Create Custom Food
1. User opens Search Sheet and types a unique local food brand name `"MyProtein Whey"`.
2. Search returns zero items and surfaces `[+ Create Custom Food]`.
3. User taps `[+ Create Custom Food]`. Custom Food Editor opens.
4. User inputs Name (`MyProtein Whey`), Serving (`30g`), Calories (`120`), Protein (`24g`), Carbs (`3g`), Fat (`1g`).
5. User taps `[Save to Custom Library & Log]`.
6. **Climax**: Food is persisted to custom DB, indexed for future FTS search, and logged to current meal slot.

### Flow 4 — Full Database Backup & Restore
1. User opens Settings from top app menu.
2. User taps `[Backup Database]`.
3. Native system file picker opens; user saves `myfoodtracker_backup_2026-09-06.db` to local storage.
4. On a new device, user opens Settings and taps `[Restore Database]`.
5. User selects the `.db` file; app validates schema, restores database, and reloads active profile.
6. **Climax**: User sees full historical logs restored completely offline.
