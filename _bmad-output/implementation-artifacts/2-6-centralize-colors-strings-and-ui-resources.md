---
baseline_commit: af1b7dc5b252a35b53986bf123adf27bb170fd1f
---

# Story 2.6: Centralize UI Colors, Strings, and Theming Resources

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user and developer,
I want all hardcoded hex colors, layout string literals, and UI format templates extracted into centralized Android resources (`colors.xml`, `strings.xml`, `dimens.xml`, and theme styles),
so that the codebase eliminates technical debt accumulated across Stories 1.1–2.5, ensures consistent design tokens, prevents visual regressions, and enables future localization and dark mode support.

## Acceptance Criteria

1. **Given** all existing layout XML files (`fragment_dashboard.xml`, `item_meal_log.xml`, `item_week_day.xml`, `dialog_quick_add_log.xml`, `fragment_passcode_auth.xml`, `fragment_profile_setup.xml`), **When** inspected for inline hex color values (`#...`), **Then** zero hardcoded hex color values remain; all colors reference centralized semantic or palette color resources defined in `res/values/colors.xml`. [Source: deferred items 2.1-D2, 2.2-D1, 2.4-D2, 2.5-D2]
2. **Given** all existing layout XML files and UI presentation code (`DashboardFragment.kt`, `WeekDayAdapter.kt`, `MealLogAdapter.kt`, `PasscodeAuthFragment.kt`, `ProfileSetupFragment.kt`), **When** inspected for hardcoded user-visible text literals, **Then** all user-visible strings (button labels, section headers, dialog titles, input hints, accessibility descriptions, and toasts) reference centralized string resources in `res/values/strings.xml`. [Source: deferred items 2.2-D2, 2.4-D2]
3. **Given** formatted dynamic strings (e.g., date formats, macro summaries `X kcal · P Yg · C Zg · F Wg`, calorie progress `X / Y kcal`, water summaries, meal count `N meal(s) logged`, snackbar notifications, and TalkBack announcements), **When** constructed at runtime, **Then** they use localized parameterized string resources with explicit format arguments (e.g., `%1$s`, `%2$d`) rather than raw string concatenation. [Source: deferred items 2.1-D3, 2.2-D2, 2.3-D1, 2.5-AC5]
4. **Given** the application theme in `res/values/themes.xml` and `res/values-night/themes.xml`, **When** the app runs, **Then** the primary brand colors (`#1B4D3E`, `#2D6A4F`), surface neutrals (`#F8F9FA`, `#FFFFFF`), text ink (`#2D3748`, `#718096`), stroke (`#E2E8F0`), and destructive/error (`#BA1A1A`) are properly registered as Material3 theme attributes without breaking current UI styling. [Source: ARCHITECTURE-SPINE AD-5, DESIGN.md §Color Tokens]
5. **Given** custom dimensions and spacing repeated across layouts (e.g., card corner radii 8dp, 12dp, 24dp, minimum touch target 48dp, standard horizontal/vertical margins), **When** referenced in layouts, **Then** they reference standardized dimension tokens in `res/values/dimens.xml`. [Source: EXPERIENCE.md §Touch Targets, DESIGN.md §Shapes]
6. **Given** the resource centralization refactor, **When** all unit tests and builds run, **Then** zero behavioral or visual regressions occur (`./gradlew testDebugUnitTest` passes 100%, and `./gradlew :app:assembleDebug` builds cleanly). [Source: PRD NFR-4, quality guard]
7. **Given** the scope of this story, **When** implementing, **Then** ViewModel business logic, database queries, use cases, domain entities, and repository implementations are NOT altered (this is strictly a presentation and resource layer refactoring). [Source: scope guard]

## Tasks / Subtasks

- [ ] **Task 1: Design Tokens & Palette Extraction (`colors.xml`, `dimens.xml`, `themes.xml`)** (AC: #1, #4, #5)
  - [ ] Subtask 1.1: MODIFY `app/src/main/res/values/colors.xml` — Define palette tokens: brand forest green (`primary_forest` `#1B4D3E`, `primary_forest_medium` `#2D6A4F`), neutral surfaces (`surface_app_bg` `#F8F9FA`, `surface_card_bg` `#FFFFFF`, `surface_stroke` `#E2E8F0`), typography ink (`text_primary` `#2D3748`, `text_secondary` `#718096`), macro accents (`macro_calories` `#1B4D3E`, `macro_protein` `#2D6A4F`, `macro_carbs` `#E9C46A`, `macro_fat` `#E76F51`), water accents (`water_primary` `#0288D1`, `water_card_bg` `#E1F5FE`, `water_progress_track` `#B3E5FC`), status/feedback (`danger_red` `#BA1A1A`, `warning_banner_bg` `#FFF3CD`, `warning_banner_stroke` `#FFEBAA`, `warning_banner_text` `#856404`).
  - [ ] Subtask 1.2: MODIFY `app/src/main/res/values/dimens.xml` — Define standard card corner radii (`radius_card_small` 8dp, `radius_card_medium` 12dp, `radius_day_pill` 24dp), touch target (`min_touch_target` 48dp), and common padding tokens.
  - [ ] Subtask 1.3: MODIFY `app/src/main/res/values/themes.xml` and `values-night/themes.xml` — Map application primary and background colors to `Base.Theme.MyFoodTracker` attributes.

- [ ] **Task 2: Centralize UI Strings and Format Specifiers (`strings.xml`)** (AC: #2, #3)
  - [ ] Subtask 2.1: MODIFY `app/src/main/res/values/strings.xml` — Add all static UI text: navigation, buttons ("Today", "Logout", "Quick Add", "Save", "Cancel", "Delete", "Undo", "Unlock", "Create Profile"), dialog titles ("Quick Add", "Select Date"), section headers ("Today's Meals", "Enter Passcode", "Create Profile"), and input hints.
  - [ ] Subtask 2.2: Add all parameterized format strings: `macro_value_with_target` ("%1$s / %2$s kcal", "%1$s / %2$s ml"), `macro_percent_of_goal` ("%1$d%% of daily goal"), `macro_row_format` ("%1$s%2$s"), `macro_row_target_format` (" / %1$s%2$s"), `meal_title_time_format` ("%1$s · %2$s"), `meal_macros_format` ("%.0f kcal · P %.0fg · C %.0fg · F %.0fg"), `meal_count_format` ("%1$d meal(s) logged"), `snackbar_deleted_format` ("Deleted %1$s from %2$s"), `toast_profile_created` ("Profile '%1$s' created successfully!").
  - [ ] Subtask 2.3: Add all accessibility TalkBack announcement templates: calorie announcement, water announcement, macro row announcement, day pill description, meal item description, delete/restore announcements.

- [ ] **Task 3: Refactor Dashboard Layouts & Adapters** (AC: #1, #2, #3, #5)
  - [ ] Subtask 3.1: MODIFY `app/src/main/res/layout/fragment_dashboard.xml` — Replace all inline hex colors, text literals, and corner radii with `@color/...`, `@string/...`, and `@dimen/...`.
  - [ ] Subtask 3.2: MODIFY `app/src/main/res/layout/item_meal_log.xml` — Replace `#FFFFFF`, `#E2E8F0`, `#2D3748`, `#718096` with `@color/surface_card_bg`, `@color/surface_stroke`, `@color/text_primary`, `@color/text_secondary`.
  - [ ] Subtask 3.3: MODIFY `app/src/main/res/layout/item_week_day.xml` — Replace inline colors with `@color/surface_stroke`, `@color/text_primary`, `@color/text_secondary`.
  - [ ] Subtask 3.4: MODIFY `app/src/main/res/layout/dialog_quick_add_log.xml` — Replace background with `@color/surface_card_bg` and strings/hints with `@string/...`.
  - [ ] Subtask 3.5: MODIFY `presentation/ui/dashboard/WeekDayAdapter.kt` — Replace `Color.parseColor(...)` with `ContextCompat.getColor(context, R.color....)` and use parameterized string templates for accessibility descriptions.
  - [ ] Subtask 3.6: MODIFY `presentation/ui/dashboard/MealLogAdapter.kt` — Replace hardcoded formatting with `context.getString(R.string.meal_macros_format, ...)` and accessibility templates.
  - [ ] Subtask 3.7: MODIFY `presentation/ui/dashboard/DashboardFragment.kt` — Replace swipe delete `Color.parseColor("#BA1A1A")` with `ContextCompat.getColor(requireContext(), R.color.danger_red)`, replace canvas `"Delete"` with `getString(R.string.action_delete)`, and replace all hardcoded strings in dialogs, snackbars, and accessibility announcements with `getString(R.string....)`.

- [ ] **Task 4: Refactor Auth and Profile Setup Screens** (AC: #1, #2, #3)
  - [ ] Subtask 4.1: MODIFY `app/src/main/res/layout/fragment_passcode_auth.xml` — Replace inline colors and text strings with `@color/...` and `@string/...`.
  - [ ] Subtask 4.2: MODIFY `app/src/main/res/layout/fragment_profile_setup.xml` — Replace disclaimer banner colors (`#FFF3CD`, `#FFEBAA`, `#856404`), card backgrounds, button tints, and text with `@color/...` and `@string/...`.
  - [ ] Subtask 4.3: MODIFY `presentation/ui/auth/ProfileSetupFragment.kt` — Replace `Toast.makeText(..., "Profile '${state.userProfile.username}' created successfully!", ...)` with `getString(R.string.toast_profile_created, state.userProfile.username)`.

- [ ] **Task 5: Update Deferred Debt Log & Verify Builds** (AC: #6, #7)
  - [ ] Subtask 5.1: MODIFY `_bmad-output/implementation-artifacts/deferred-work.md` — Mark deferred items 2.1-D2, 2.2-D1, 2.2-D2, 2.4-D2 as resolved by Story 2.6.
  - [ ] Subtask 5.2: RUN `./gradlew testDebugUnitTest` and verify all tests pass.
  - [ ] Subtask 5.3: RUN `./gradlew :app:assembleDebug` and verify the build completes without errors or resource conflicts.

## Dev Notes

- **Preserve ViewModel Purity**: Do not introduce Android `Context` or `@StringRes` into ViewModels (`DashboardViewModel`, `PasscodeAuthViewModel`, `ProfileSetupViewModel`). Strings returned by ViewModels for error handling (e.g. `"Incorrect passcode"`) are tested directly by unit tests without Robolectric. View layers can display them directly or map them to localized strings.
- **Resource Naming Conventions**:
  - Colors: `<category>_<name>` or `<semantic_role>` (e.g., `primary_forest`, `macro_carbs`, `surface_app_bg`).
  - Strings: `<screen>_<element>` or `<category>_<name>` (e.g., `dashboard_title_meals`, `action_quick_add`, `accessibility_water_added`).
  - Dimens: `<type>_<element>` (e.g., `radius_card_small`, `min_touch_target`).
- **Tabular Figures (`tnum`)**: Ensure all `android:fontFeatureSettings="tnum"` in layouts remain intact on numeric TextViews.

### References
- Deferred Debt Register: `_bmad-output/implementation-artifacts/deferred-work.md`
- Stories 2.1–2.5 Implementation Specs: `_bmad-output/implementation-artifacts/2-*.md`
- Architecture Guidelines: `_bmad-output/planning-artifacts/architecture.md`

## Dev Agent Record

### Agent Model Used
Gemini 3.8 Flash (Medium)

### Debug Log References
- Pending implementation

### Completion Notes List
- Pending implementation

### File List
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values-night/themes.xml`
- `app/src/main/res/layout/fragment_dashboard.xml`
- `app/src/main/res/layout/item_meal_log.xml`
- `app/src/main/res/layout/item_week_day.xml`
- `app/src/main/res/layout/dialog_quick_add_log.xml`
- `app/src/main/res/layout/fragment_passcode_auth.xml`
- `app/src/main/res/layout/fragment_profile_setup.xml`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/WeekDayAdapter.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/MealLogAdapter.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/DashboardFragment.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/auth/ProfileSetupFragment.kt`
- `_bmad-output/implementation-artifacts/deferred-work.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
- `_bmad-output/planning-artifacts/epics.md`
