---
story_id: 8.3
story_key: 8-3-dashboard-and-recipe-ux-polish
epic: 8
status: done
created: 2026-10-25
context_refreshed: 2026-10-30
baseline_commit: 8868800ada189d204a88ffa113d22fc221e9be7d (Implement Story 8.2)
---

# Story 8.3: Dashboard & Recipe UX Polish

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user,
I want tactile confirmation on quick-add water, no misleading Quick Add button in the recipe flow, and an inspection view for saved recipes,
So that the UI responds to what I do and cannot take me down a wrong path.

## Acceptance Criteria

1. **Given** a tap on the +250ml water button in `DashboardFragment`, **When** the log is written, **Then** haptic feedback is triggered via `performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)` (resolves **2.3-D1**, `DashboardFragment.kt:213-228` flagged in register).
2. **Given** `FoodSearchBottomSheet` opened from `RecipeBuilderFragment`, **When** it renders, **Then** the "Quick Add" button is hidden in the recipe flow and its unavailable snackbar action can never be reached (resolves **4.1-D1**, `RecipeBuilderFragment.kt:85-89, 122-129` flagged in register).
3. **Given** the recipe list in `RecipeListFragment`, **When** the user taps a saved recipe card, **Then** a read-only detail view (dialog or screen) shows the full ingredient breakdown — name, quantity, unit, macro per ingredient (calories, protein, carbs, fat) — plus the macro sum (resolves **4.1-D2**, `RecipeListFragment.kt:48-51` flagged in register; **editing is out of scope**).
4. **Given** that detail view, **When** `activeDate` is present (navigated from dashboard), **Then** an action affordance (e.g., "Log" button) allows initiating the existing meal-slot logging flow (`LogRecipeDialogFragment`), while tapping "Close" (or back/outside) dismisses without logging. If `activeDate` is null, the recipe inspection is still accessible and the logging action is disabled/hidden.
5. **Given** accessibility and design rules from stories 2-6 and 5-2, **When** the new detail view renders, **Then** all strings use `@string/` resources, touch targets meet or exceed 48dp (`@dimen/min_touch_target`), tabular figures (`fontFeatureSettings="tnum"`) are used for numbers, and TalkBack announcements fire where state changes.

## Tasks / Subtasks

- [x] Task 1: Haptic feedback on quick-add water (AC: #1)
  - [x] 1.1 In `DashboardFragment.kt:213-228` (inside `binding.btnAddWater250.setOnClickListener` or `playWaterTapFeedback`), call `binding.btnAddWater250.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)`.
  - [x] 1.2 Ensure `android:hapticFeedbackEnabled="true"` is maintained and no special vibration permissions are added (standard View haptic feedback).
- [x] Task 2: Conditional Quick Add in `FoodSearchBottomSheet` (AC: #2)
  - [x] 2.1 In `FoodSearchBottomSheet.kt`: define argument key `ARG_SHOW_QUICK_ADD = "show_quick_add"`. Update `newInstance(activeDate: String, showQuickAdd: Boolean = true)` to store the boolean flag in the arguments bundle.
  - [x] 2.2 In `FoodSearchBottomSheet.onViewCreated`: read `arguments?.getBoolean(ARG_SHOW_QUICK_ADD, true) ?: true`. Toggle `binding.btnSearchQuickAdd.visibility = if (showQuickAdd) View.VISIBLE else View.GONE`.
  - [x] 2.3 In `RecipeBuilderFragment.kt:85-89`: pass `showQuickAdd = false` when opening `FoodSearchBottomSheet.newInstance(LocalDate.now().toString(), showQuickAdd = false)`.
  - [x] 2.4 In `RecipeBuilderFragment.kt:122-129`: retain `onQuickAddRequested()` for `FoodSearchBottomSheet.Listener` interface compliance, with the existing or no-op guard.
- [x] Task 3: Read-only Recipe Detail Inspection Dialog (AC: #3, #4, #5)
  - [x] 3.1 Create `presentation/ui/recipe/RecipeDetailFormatter.kt` with pure functions to format ingredient breakdown lines (name, quantity, unit, kcal, protein, carbs, fat) and recipe totals summary.
  - [x] 3.2 Create layout `dialog_recipe_detail.xml` featuring: recipe title, scrollable container for ingredients (with height capping or ScrollView), totals readout with `fontFeatureSettings="tnum"`, and buttons for "Close" and optionally "Log" (when `activeDate` is available). Ensure min touch targets (`@dimen/min_touch_target`) and proper padding/styling.
  - [x] 3.3 Create `presentation/ui/recipe/RecipeDetailDialogFragment.kt` (inheriting from `DialogFragment` using `MaterialAlertDialogBuilder` or View Binding):
    - Reads recipe data from arguments (or bundle).
    - Sets title, formatted ingredient lines, and totals.
    - Sets negative button to "Close" (`R.string.action_close`).
    - If `activeDate` is non-null, sets positive button to "Log" (`R.string.action_log`) which triggers logging confirmation (`LogRecipeDialogFragment`). If `activeDate` is null, omits or disables the log action.
    - Employs lifecycle guard `if (!isAdded) return` for any posted runnables or callbacks.
  - [x] 3.4 In `RecipeListFragment.kt`:
    - Update `adapter = RecipeAdapter(onDeleteClicked = { recipe -> confirmDelete(recipe) }, onRecipeClicked = { recipe -> openRecipeDetailDialog(recipe) })`.
    - Implement `openRecipeDetailDialog(recipe: Recipe)` showing `RecipeDetailDialogFragment`.
    - If user proceeds to log from detail dialog, delegate to existing `openLogDialog(recipe)`.
  - [x] 3.5 In `RecipeAdapter.kt`: update `binding.root.contentDescription` from `a11y_recipe_log_row` to an inspection-appropriate string (e.g., `a11y_recipe_item` or `a11y_recipe_detail_row` in `strings.xml`).
  - [x] 3.6 In `strings.xml`: add required localized strings (`recipe_detail_title`, `recipe_detail_ingredient_line`, `action_close`, `a11y_recipe_detail_row`).
- [x] Task 4: Tests and Verification (AC: #1, #2, #3, #4, #5)
  - [x] 4.1 Create `app/src/test/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeDetailFormatterTest.kt` (pure JUnit4, no Android framework dependencies, no MockK/Robolectric) testing:
    - Formatting of single and multi-ingredient recipes.
    - Whole vs fractional quantities (e.g., `150 g` vs `150.5 g`).
    - Macro calculations and formatting (kcal, P, C, F, fiber).
    - Zero/empty ingredients edge cases.
  - [x] 4.2 Verify existing unit tests (`testDebugUnitTest`) pass with 0 regressions.
  - [x] 4.3 Verify compilation (`compileDebugSources`) and assemble (`assembleDebug`).
  - [x] 4.4 Mark deferred-work items **2.3-D1**, **4.1-D1**, and **4.1-D2** as resolved in `deferred-work.md`.
  - [x] 4.5 Explicit manual testing notice (per project policy, no emulator):
    - Water button: tap +250ml water on a physical device, verify tactile haptic click.
    - Recipe builder: tap "+ Add ingredient", verify "Quick Add" button is completely hidden in the search bottom sheet.
    - Recipe list: tap a saved recipe card, verify read-only detail dialog opens showing name, all ingredients with quantities and individual macros (kcal, P, C, F), and totals summary.
    - Recipe logging from detail: tap "Log" in detail dialog, verify slot picker dialog opens and logs successfully to active date.
    - Null active date: verify recipe detail dialog still opens and inspects cleanly when navigated without an active date.

### Review Findings

- [x] [Review][Patch] Hardcoded UI strings in `RecipeDetailFormatter` and missing `recipe_detail_ingredient_line` resource in `strings.xml` (AC #5, Task 3.6) [RecipeDetailFormatter.kt:21-49, RecipeListFragment.kt:97-100, strings.xml:189-192]
- [x] [Review][Patch] Near-whole fractional quantities format with trailing `.0` in `RecipeDetailFormatter.formatQuantity` and duplicate `formatQuantity` in `RecipeListFragment` [RecipeDetailFormatter.kt:9-15, RecipeListFragment.kt:155-162]
- [x] [Review][Patch] Unsafe `_binding!!` access in `RecipeDetailDialogFragment.capIngredientScrollHeight()` [RecipeDetailDialogFragment.kt:67-78]
- [x] [Review][Patch] Silent no-op when `onLogRecipeRequested` cannot find `recipeId` in `viewModel.recipes.value` [RecipeListFragment.kt:112-116]
- [x] [Review][Defer] Pre-formatted `DialogFragment` argument strings do not re-localize across live locale/configuration changes [RecipeListFragment.kt:97-151] — deferred, pre-existing

## Dev Notes

### Current state of files being modified

**`DashboardFragment.kt`** (`presentation/ui/dashboard/`):
- `binding.btnAddWater250.setOnClickListener` (lines 213–228) invokes `viewModel.logWaterPlus250()` and `playWaterTapFeedback()`.
- Haptics: Calling `binding.btnAddWater250.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)` immediately provides the tactile click requested by 2.3-D1.
- What must be preserved: All animations in `playWaterTapFeedback()`, accessibility announcement via `binding.cardWaterWidget.announceForAccessibility(...)`, date-scoped logging via `viewModel.logWaterPlus250()`.

**`FoodSearchBottomSheet.kt`** (`presentation/ui/search/`):
- Currently exposes `binding.btnSearchQuickAdd` (lines 76–80) which triggers `getListener()?.onQuickAddRequested()`.
- `newInstance(activeDate: String)` only accepts `activeDate`.
- In `onViewCreated`, `btnSearchQuickAdd` is always visible.
- Adding `showQuickAdd: Boolean = true` allows callers that do not support quick-add (like `RecipeBuilderFragment`) to cleanly hide this entry point.
- What must be preserved: Existing behavior when `showQuickAdd == true` (used by `DashboardFragment`), search debounce, FTS catalog lookup, custom food creation dialog.

**`RecipeBuilderFragment.kt`** (`presentation/ui/recipe/`):
- Line 87 calls `FoodSearchBottomSheet.newInstance(LocalDate.now().toString())`.
- Lines 122–129 implement `onQuickAddRequested()` by showing a Snackbar: `"Quick-add isn't available for recipe ingredients - pick a food instead"`.
- By passing `showQuickAdd = false`, the Quick Add button is hidden so the user never encounters the disabled action.
- What must be preserved: The ViewModel draft preservation introduced in Story 8.2 (`RecipeBuilderViewModel`), ingredient quantity dialog, save recipe use case, error handling.

**`RecipeListFragment.kt` & `RecipeAdapter.kt`** (`presentation/ui/recipe/`):
- `RecipeListFragment` lines 48–51 bind `onRecipeClicked = { recipe -> openLogDialog(recipe) }`.
- `openLogDialog` (lines 93–127) requires `activeDate != null` and directly opens `LogRecipeDialogFragment`. If `activeDate` is missing, it shows `log_recipe_error_date` ("Missing log date") and blocks inspection.
- The new flow opens `RecipeDetailDialogFragment`, which is a read-only view displaying the ingredient breakdown with quantities, individual ingredient macros (calories, protein, carbs, fat), and macro totals.
- From `RecipeDetailDialogFragment`, the user can dismiss with "Close" or, if `activeDate` is present, tap "Log" to proceed to meal-slot logging (`openLogDialog(recipe)`).
- What must be preserved: `confirmDelete(recipe)`, FAB navigation to `RecipeBuilderFragment`, fragment result listeners, `onLogRecipeConfirmed` use case invocation.

### Architectural Guardrails (Non-Negotiable)

- **Domain Model Purity (AD-1)**: Zero Android framework (`android.*`) or Room imports in `domain/`.
- **Synchronous Execution (AD-2)**: No `suspend`, no coroutines. All database operations and presentation updates run synchronously.
- **ViewModel Purity**: ViewModels must never reference Android `Context`, Views, or resources. Formatting logic that requires `Context` / resources belongs in the UI/formatter layer.
- **Room / DAO Invariants**: No schema change, no migration required. Recipe and ingredient queries already fetch `RecipeWithIngredients` via Room `@Relation`.
- **DI Registration (AD-4)**: If any new use case or ViewModel is created, register in `di/AppModule.kt`. Formatter utilities should be pure Kotlin singletons or helper objects.
- **Accessibility & Resources (Story 2-6)**: All strings in `res/values/strings.xml`. Touch targets $\ge 48\text{dp}$. `fontFeatureSettings="tnum"` on tabular numbers.

### Testing Standards Summary

- **Pure JVM Unit Tests**: Hand-rolled fakes; **no MockK, no Robolectric**.
- Create `RecipeDetailFormatterTest.kt` to thoroughly test formatting of ingredient lines, fractional grams/ml, and macro sums.
- Run tests via `./gradlew :app:testDebugUnitTest` (or `.\gradlew.bat testDebugUnitTest` on Windows).
- Run compile checks via `./gradlew :app:compileDebugSources`.
- Do NOT launch or run the Android emulator (project policy). All on-device testing must be flagged for manual developer execution.

### References

- Epic definition: `_bmad-output/planning-artifacts/epics.md#Story 8.3: Dashboard & Recipe UX Polish`
- Architecture spine: `_bmad-output/planning-artifacts/architecture/architecture-MyFoodTracker-2026-09-06/ARCHITECTURE-SPINE.md`
- Deferred work items: `_bmad-output/implementation-artifacts/deferred-work.md` (2.3-D1, 4.1-D1, 4.1-D2)
- Story 4.1 (Recipe builder & grouping): `_bmad-output/implementation-artifacts/4-1-recipe-builder-and-ingredient-grouping.md`
- Story 4.2 (One-tap recipe logging): `_bmad-output/implementation-artifacts/4-2-one-tap-recipe-logging.md`
- Story 8.2 (State survival & lifecycle hardening): `_bmad-output/implementation-artifacts/8-2-state-survival-and-lifecycle-hardening.md`
- Project context rules: `_bmad-output/project-context.md`

## Dev Agent Record

### Agent Model Used

Gemini 3.8 Flash (Medium)

### Debug Log References

- Verified Red phase on `RecipeDetailFormatterTest`: compilation failure on unresolved reference `RecipeDetailFormatter`.
- Implemented `RecipeDetailFormatter` and verified Green phase: all 6 tests in `RecipeDetailFormatterTest` passed.
- Full test suite run (`testDebugUnitTest`): 29 actionable tasks executed successfully with 0 failures or regressions.
- Full compile check (`compileDebugSources`): verified without compilation errors.
- Full APK assembly (`:app:assembleDebug`): completed successfully.

### Completion Notes List

- **Task 1 (Haptic feedback, 2.3-D1)**: In `DashboardFragment.kt`, added `binding.btnAddWater250.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)` when the +250ml water button is tapped, providing instant tactile feedback without requiring additional OS vibration permissions.
- **Task 2 (Conditional Quick Add, 4.1-D1)**: In `FoodSearchBottomSheet.kt`, added `showQuickAdd: Boolean = true` parameter (persisted under `ARG_SHOW_QUICK_ADD`). When `false`, `btnSearchQuickAdd` visibility is toggled to `View.GONE`. In `RecipeBuilderFragment.kt`, passed `showQuickAdd = false` when opening food search, completely removing the Quick Add affordance from the recipe builder flow and eliminating the unreachable/disabled quick-add snackbar.
- **Task 3 (Read-only Recipe Detail Inspection Dialog, 4.1-D2)**:
  - Created pure presentation helper `RecipeDetailFormatter` for formatting ingredient lines with individual macros (kcal, protein, carbs, fat) and recipe totals.
  - Created `dialog_recipe_detail.xml` layout with scrollable ingredients container, tabular numerals (`fontFeatureSettings="tnum"`), and accessible styling.
  - Created `RecipeDetailDialogFragment` displaying recipe details with "Close" action, and "Log" action when active date is present (`activeDate != null`). Includes `if (!isAdded) return@post` lifecycle guard.
  - In `RecipeListFragment.kt`, updated card click to open `RecipeDetailDialogFragment`. Implemented `RecipeDetailDialogFragment.Listener` to delegate to `openLogDialog(recipe)` when logging is requested.
  - In `RecipeAdapter.kt`, updated item content description to `a11y_recipe_detail_row` ("View %1$s, %2$d ingredients, %3$d kilocalories").
  - In `strings.xml`, added `recipe_detail_title`, `action_close`, and `a11y_recipe_detail_row`.
- **Task 4 (Tests and Verification)**:
  - Added pure JUnit4 test suite `RecipeDetailFormatterTest` covering single, multiple, and empty ingredients, as well as integer vs fractional quantity rounding.
  - Ran full unit test suite (`testDebugUnitTest`) and compile checks (`compileDebugSources`, `assembleDebug`) with 0 regressions.
  - Updated `deferred-work.md` marking 2.3-D1, 4.1-D1, and 4.1-D2 as resolved.

### File List

- `app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/DashboardFragment.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/search/FoodSearchBottomSheet.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeBuilderFragment.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeDetailFormatter.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeDetailDialogFragment.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeListFragment.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeAdapter.kt`
- `app/src/main/res/layout/dialog_recipe_detail.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeDetailFormatterTest.kt`
- `_bmad-output/implementation-artifacts/deferred-work.md`
- `_bmad-output/implementation-artifacts/8-3-dashboard-and-recipe-ux-polish.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
