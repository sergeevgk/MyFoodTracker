---
baseline_commit: 974cf2d9209d405f8c3c6526be4aa5053f4f1acf
---

# Story 4.2: One-Tap Recipe Logging

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user,
I want to select a saved recipe and log it to a meal slot for the current day,
so that all constituent foods and macros are added at once.

## Acceptance Criteria

1. **Given** the dashboard is showing active date `D` (`DashboardUiState.activeDate`, `YYYY-MM-DD`) and the user opens My Recipes, **When** they tap a recipe card body (not the delete icon), **Then** a log dialog opens showing the recipe name, each constituent (name, stored grams, unit), summed macros (`tnum`), and a meal-slot picker (`BREAKFAST`, `LUNCH`, `DINNER`, `SNACK`, default `BREAKFAST`). No meal row is written until Log is confirmed.
2. **Given** that dialog, **When** the user picks a slot and taps Log, **Then** each ingredient is written as its own intake row on date `D` for the active profile: one `meals` row (`title` = slot, not the recipe name) plus one `foods` child (`name` = `ingredient.foodName`, `weight` = `quantityGrams`, macros and fiber copied from the snapshot). Daily macro totals equal the sum of those snapshots (full `Double`, no rescale, NFR-4 0.1%).
3. **Given** a successful log, **When** the write finishes, **Then** the recipe list pops back to the dashboard, `DashboardViewModel.selectDate(D)` reloads that date, meal rows and the macro header update immediately with no spinner (`< 50ms` local write+reload), and a Snackbar plus TalkBack announcement confirm `Logged {recipeName} to {slot}`.
4. **Given** a missing/blank/non-`YYYY-MM-DD` date, unknown recipe id, empty ingredient list, invalid slot, blank ingredient name, non-finite or `<= 0` grams, or non-finite/negative macros, **When** Log is attempted, **Then** the use case throws before any `logFoodEntry` call (no partial write) and the UI shows an explicit error. No active profile writes nothing.
5. **Given** this story's scope, **When** implementing, **Then** existing recipe delete, recipe builder, food search, quick-add, water, and swipe-to-delete keep working. No schema change, no catalog change, no search-sheet recipe results, no recipe editor, no serving multiplier, no grouped undo.

## Tasks / Subtasks

- [x] Task 1: Domain — `LogRecipeUseCase` fan-out (AC: #2, #4)
  - [x] Subtask 1.1: NEW `domain/usecase/LogRecipeUseCase.kt`. `class LogRecipeUseCase(private val recipeRepository: RecipeRepository, private val mealRepository: MealRepository)` with `operator fun invoke(recipeId: String, mealSlot: String, date: String): Int`. Zero `android.*` / Room imports (AD-1). No `suspend` (AD-2). Koin `factory` (AD-4).
  - [x] Subtask 1.2: Load `recipeRepository.getRecipeById(recipeId) ?: throw IllegalArgumentException("Recipe not found")`. Validate the whole ingredient list **before** the first write. Then call `mealRepository.logFoodEntry` once per ingredient. Return the ingredient count.
  - [x] Subtask 1.3: Pass snapshots through unchanged. Do **not** call `LogFoodEntryUseCase`, `logQuickAdd`, or `addMealEntry`. Do **not** re-query `FoodCatalogRepository`. Do **not** add a `MealRepository.logRecipe` method (that would force every existing fake to change).
- [x] Task 2: Presentation — slot dialog + card tap (AC: #1, #3, #5)
  - [x] Subtask 2.1: NEW `LogRecipeDialogFragment` + `dialog_log_recipe.xml`. Listener pattern copied from `LogFoodQuantityDialogFragment` (`parentFragment as? Listener`). Dialog collects the slot only. It does **not** take a `FoodItem`, quantity, or unit, and it does **not** `by viewModel()` (a dialog-scoped `RecipeViewModel` is a second instance).
  - [x] Subtask 2.2: `RecipeAdapter` gains `onRecipeClicked` separate from `onDeleteClicked`. Root click opens the dialog. `ib_delete_recipe` still deletes. Child click must not fire the row click.
  - [x] Subtask 2.3: `RecipeViewModel` appends `LogRecipeUseCase` as the 4th constructor arg. `fun logRecipe(recipeId: String, mealSlot: String, date: String): Boolean` catches `IllegalArgumentException` into a new `logError: LiveData<String?>`. It must not call `LocalDate.now()`, `selectDate`, or `refresh()` on success.
  - [x] Subtask 2.4: `RecipeListFragment` reads nav arg `activeDate`. On confirm, calls `viewModel.logRecipe`. Success: `setFragmentResult` then `popBackStack()`. Failure: return the mapped string to the dialog and leave it open. Preserve delete confirmation, FAB → builder, and `REQUEST_RECIPE_SAVED`.
- [x] Task 3: Dashboard date handoff + refresh (AC: #2, #3)
  - [x] Subtask 3.1: `DashboardFragment.btnRecipes` navigates with `bundleOf("activeDate" to activeDate.toString())` where `activeDate` is `viewModel.uiState.value?.activeDate`. If that state is null, do not navigate (do not substitute `LocalDate.now()`).
  - [x] Subtask 3.2: Declare required nav argument `activeDate` (`app:argType="string"`, no default) on `recipeListFragment`. There is **no** Safe Args plugin — do not add one. Read the arg with `arguments?.getString("activeDate")`.
  - [x] Subtask 3.3: `DashboardFragment.onViewCreated` registers `setFragmentResultListener(RecipeListFragment.REQUEST_RECIPE_LOGGED)` using the same Fragment extension the recipe list already uses for save results (same `parentFragmentManager` — do not use `activity.supportFragmentManager`). On result, `viewModel.selectDate(LocalDate.parse(date))`, Snackbar, `announceForAccessibility`. Guard with `isAdded`.
- [x] Task 4: Resources + DI (AC: #1, #3, #5)
  - [x] Subtask 4.1: New copy only in `strings.xml`. Reuse existing slot/preview/cancel strings. No hardcoded colors, strings, or dimens. `tnum` on macro readouts. Touch targets `≥ 48dp`. Row gets `android:clickable="true"`, `android:focusable="true"`, and `?attr/selectableItemBackground`.
  - [x] Subtask 4.2: `AppModule.kt` only: `factory { LogRecipeUseCase(get(), get()) }` and change `viewModel { RecipeViewModel(get(), get(), get()) }` to four `get()` calls. Do **not** touch the 9-arg `DashboardViewModel` line.
- [x] Task 5: Tests + verification (AC: #4, #5)
  - [x] Subtask 5.1: NEW `LogRecipeUseCaseTest` (JUnit4, hand-written fakes, no MockK). Cover the cases in Testing below.
  - [x] Subtask 5.2: NEW `RecipeViewModelTest` with `InstantTaskExecutorRule` (already on the classpath — `DashboardViewModelTest` uses it). Assert the historical date string is forwarded and `logError` is set on failure. Do not construct `DashboardViewModel` in this test.
  - [x] Subtask 5.3: No new `androidTest` (no DAO/schema change). Do not run the emulator.
  - [x] Subtask 5.4: `./gradlew :app:compileDebugSources` and `./gradlew :app:testDebugUnitTest` green. `./gradlew :app:assembleDebug` green.

### Review Findings

- [x] [Review][Defer] Check lifecycle and context before accessing resources in scroll.post [app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/LogRecipeDialogFragment.kt:94] — deferred per user decision as edge-case lifecycle hardening
- [x] [Review][Patch] Guard binding.root against null in observeRecipeLoggedResult [app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/DashboardFragment.kt:102]
- [x] [Review][Patch] Retain selected meal slot across dialog configuration changes [app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/LogRecipeDialogFragment.kt:57]
- [x] [Review][Patch] Use XML entity &#183; in tools:text to prevent character encoding issues [app/src/main/res/layout/dialog_log_recipe.xml:33]

## Dev Notes

### Locked design — read this before writing code

Story 4.1 stored absolute gram + macro snapshots so this story can fan out without touching the catalog. The real log tables are `meals` + `foods`, not the PRD addendum's `intake_logs`. Follow the code.

**Write path (the only legal one):**

```kotlin
mealRepository.logFoodEntry(
    foodName = ingredient.foodName,          // not recipe.name
    quantityGrams = ingredient.quantityGrams, // already grams; ml was stored 1:1
    calories = ingredient.calories,           // already scaled; do not multiply again
    proteinG = ingredient.proteinG,
    carbsG = ingredient.carbsG,
    fatG = ingredient.fatG,
    fiberG = ingredient.fiberG,               // DailySummary ignores fiber; FoodEntity must still store it
    mealSlot = slot,                          // becomes meals.title
    date = date                               // dashboard active date, never LocalDate.now()
)
```

`MealRepositoryImpl.logFoodEntry` (`MealRepositoryImpl.kt:93-128`) already inserts one `MealEntryEntity` (UUID, `title = slot`, `time = HH:mm`) and one `FoodEntity`, scoped by `sessionRepository.getActiveProfileId()`. Reuse it. Do not call `MealDao` from the use case.

**Why one meal per ingredient, not one meal with N foods:** `MealLogAdapter.bind` (`MealLogAdapter.kt:33`) shows only `foods.firstOrNull()?.name` and sums every food's macros. A single multi-food meal would display one ingredient name with the whole recipe's calories. `MealDao.insertFoods` can attach many children — do not use that shape here. N `logFoodEntry` calls match the existing one-food-per-row UI and the epic line "individual constituent intake logs".

**Why not `LogFoodEntryUseCase`:** that class treats `FoodItem` macros as per-100g and multiplies by `grams/100` (`LogFoodEntryUseCase.kt:26-34`). Recipe snapshots were already scaled in `NewRecipeIngredient.fromFoodItem`. Passing them in would double-count. The use-case test must assert a stored 180 kcal stays 180, not 270.

**Why not `logQuickAdd` / `addMealEntry`:** quick-add forces `weight = 0` and `fiber = 0`. `addMealEntry()` hard-codes `LocalDate.now()` (deferred 2.1-D1). Both are wrong for historical dates and for snapshots.

**Date handoff:** `DashboardViewModel` is fragment-scoped (`by viewModel()`), not activity-scoped. `RecipeListFragment` must not inject it — that creates a second VM whose `init()` calls `selectDate(today)`. Pass `activeDate` as a navigation argument. After the write, the dashboard's own VM still holds the pre-log meal list until `selectDate(D)` runs. The fragment result is what triggers that reload. Do not assume the write itself refreshes the dashboard.

**Undo:** there is no `recipe_id` or `group_id` on `meals` / `foods`. Swipe-delete removes one meal row and cascades its single food. Each constituent is independently undoable with the existing 5-second Snackbar. Grouped undo is out of scope — do not add a column and do not bump `AppDatabase` (stays version 4, `exportSchema = false`).

**Search sheet:** epic text says "recipe list or search sheet". Implement the recipe list only. `FoodSearchBottomSheet` is catalog FTS (`SearchFoodUseCase` → `FoodItem`). Do not insert recipes into `catalog_foods`, do not add a recipes tab, do not change `FoodSearchAdapter`. Story 4.1-D1 already deferred Quick-Add leakage on that shared sheet.

**No editor, no scaler:** card tap logs; it does not open the builder. Do not wire `RecipeDao.updateRecipeName` or `RecipeRepository.deleteIngredient` (both unused by UI). Do not add a yield / "log 0.5 servings" field. PRD "weight ratios" were implemented in 4.1 as absolute `quantityGrams`. Log the saved recipe as stored. Logging the same recipe twice writes two full copies — that is allowed.

**4.1-D2** (no ingredient inspection on the card) stays deferred as an editor/detail screen. The log dialog's ingredient list is confirmation copy only. Do not mark D2 done. Do not fix 4.1-D1, 4.1-D3 (rotation drafts), or 4.1-D4 / 3.2-D1 (non-atomic `insertMeal` + `insertFoods`). Validate-then-write is the partial-write guard this story owns; a crash mid-loop is the existing deferred debt.

### Current state of files this story touches

| File | Today | This story changes | Must preserve |
|---|---|---|---|
| `domain/repository/MealRepository.kt` | `logFoodEntry(...)` already exists | Nothing | Signature. Do not add methods. |
| `data/repository/MealRepositoryImpl.kt:93-128` | One meal + one food, session guard, slot whitelist, passed `date` | Nothing | Null session returns existing rows and does not insert. |
| `domain/usecase/LogFoodEntryUseCase.kt` | Scales per-100g `FoodItem` | Nothing | Do not call it from recipe logging. |
| `domain/repository/RecipeRepository.kt` | `getRecipeById(recipeId): Recipe?` is profile-scoped and returns null with no session | Nothing | Do not add `logRecipe`. |
| `domain/model/Recipe.kt` | `RecipeIngredient` snapshots + `Recipe.total*` sums | Nothing | Do not add Android/`Parcelable`. |
| `presentation/viewmodel/RecipeViewModel.kt` | 3-arg ctor: save, get, delete. `saveError` LiveData. `init { refresh() }` | Append `LogRecipeUseCase`. Add `logRecipe` + `logError` | `saveRecipe` / `deleteRecipe` / `refresh` behavior. List and builder do not share a VM instance. |
| `presentation/ui/recipe/RecipeListFragment.kt` | Adapter callback is delete-only. FAB opens builder. Listens for `REQUEST_RECIPE_SAVED` | Card tap → dialog. Read `activeDate`. Emit log result and pop | Delete confirm dialog, save snackbar, empty state, `_binding = null`, `viewLifecycleOwner`. |
| `presentation/ui/recipe/RecipeAdapter.kt` | `onDeleteClicked` only. `a11y_recipe_item` on the row | Add `onRecipeClicked`. Point row `contentDescription` at the log action | `DiffUtil` by `id` / `==`. Delete button keeps `cd_delete_recipe`. |
| `res/layout/item_recipe.xml` | `minHeight=@dimen/min_touch_target`. Delete `ImageButton` is 48dp | Make the root visibly clickable | Do not shrink the delete target. No new hex colors. |
| `presentation/ui/dashboard/DashboardFragment.kt:171-173` | `btnRecipes` navigates with no args | Pass `activeDate` bundle. Register log-result listener | Search sheet, quick-add, water, logout, week strip, swipe-undo. |
| `presentation/viewmodel/DashboardViewModel.kt` | 9-arg ctor. `selectDate` reloads meals + `DailySummary.summarize` + water | Nothing | Do not add a 10th dependency. `logFoodEntry` here expects a per-100g `FoodItem` — do not call it per ingredient. |
| `res/navigation/nav_graph.xml:47-54` | `recipeListFragment` has no arguments | Required `activeDate` string arg, no default | Builder action stays. No new destination for the dialog (it is a `childFragmentManager` dialog, like the quantity modal). |
| `di/AppModule.kt:111-122` | `RecipeViewModel(get(), get(), get())`. Dashboard VM has 9 `get()` calls | One new factory + one extra `get()` on `RecipeViewModel` | Dashboard VM line stays 9. All DI stays in this file. |
| `data/db/AppDatabase.kt:19` | version 4, `exportSchema = false` | Nothing | Do not bump. |
| `data/db/FoodCatalogDatabase.kt:18` | version 1, frozen | Nothing | A version bump wipes `is_custom` rows (3.1-F1). |

### Use case contract

```kotlin
operator fun invoke(recipeId: String, mealSlot: String, date: String): Int
```

Validation order, all before any write. Messages must be these exact literals (domain cannot reference `R.string`; the fragment maps them):

| Check | Message | UI string to display |
|---|---|---|
| `date` matches `^\d{4}-\d{2}-\d{2}$` after trim | `"Missing log date"` | `R.string.log_recipe_error_date` (same text) |
| slot trim+uppercase in `BREAKFAST`/`LUNCH`/`DINNER`/`SNACK` | `"Invalid meal slot"` | `R.string.log_food_error_slot` |
| `getRecipeById` returns null (missing id **or** no active profile) | `"Recipe not found"` | `R.string.log_recipe_error_missing` |
| `ingredients.isEmpty()` | `"Add at least one ingredient"` | `R.string.recipe_error_empty` |
| blank `foodName` | `"Enter a name"` | `R.string.quick_add_error_name` |
| grams not finite or `<= 0` | `"Enter a quantity greater than 0"` | `R.string.log_food_error_quantity` |
| any macro/fiber not finite or `< 0` | `"Enter 0 or more"` | `R.string.quick_add_error_number` |

Pass the trimmed date and uppercased slot into every `logFoodEntry` call. Do not sort ingredients (no `position` column; `getIngredientsByRecipeId` has no `ORDER BY`). Ignore `catalogFoodId` — it is a nullable informational `Long` with no FK, and logged `FoodEntity` has no food-id column. A recent-food ingredient may have stored `0` rather than null; still ignore it.

`logFoodEntry` trims `foodName`. Do not prefix the recipe name onto it. Search ranking counts logged names; a prefix would break Story 3.3 custom-first matching. Recipe logs have `weight > 0`, so they may later appear in Recent Foods. That is expected. Do not filter them.

### UI contract

Dialog (new):

- Title: `@string/log_recipe_title` ("Log Recipe").
- Body: recipe name, a scrollable ingredient block (cap height so a long recipe does not cover the screen), totals via existing `recipe_totals_format`, slot field via existing `log_food_hint_meal_slot`.
- Slot widget: copy the `AutoCompleteTextView` + `ArrayAdapter` setup from `LogFoodQuantityDialogFragment.kt:27-50`. Same hardcoded English slot literals, same default `BREAKFAST`. Do not invent `@string` slot labels in this story (pre-existing pattern).
- Buttons: `@string/action_cancel` and new `@string/action_log` ("Log"). Override the positive button click the same way `LogFoodQuantityDialogFragment.kt:69-98` does, so a bad slot does not dismiss the dialog.
- Pass only strings in the fragment arguments (`recipeId`, `name`, preformatted ingredients text, preformatted totals). Do not make `Recipe` `Parcelable`. The fragment joins ingredient lines with `\n` into one `TextView` inside a `ScrollView`. Do not add a second RecyclerView. Cap the scroll view with a new `@dimen/dialog_log_recipe_ingredients_max_height` (`240dp`).
- `Listener.onLogRecipeConfirmed(recipeId, slot): String?`. Parent performs the write and maps `logError` to a `@string`. Return `null` on success (dialog dismisses). Return the display string on failure (dialog sets `tv_log_recipe_error` and stays open). The dialog must not read `RecipeViewModel`.
- Null `_binding` in `onDestroyView()`, same as `LogFoodQuantityDialogFragment.kt:156-158`.
- Format grams/macros for display only (`%1$.0f` in string resources). Never round before `logFoodEntry`.

Navigation argument name is exactly `activeDate`. Bundle key and XML `android:name` must match.

```xml
<argument
    android:name="activeDate"
    app:argType="string" />
```

```kotlin
findNavController().navigate(
    R.id.action_dashboardFragment_to_recipeListFragment,
    bundleOf("activeDate" to date)
)
```

Fragment result keys (do not reuse `REQUEST_RECIPE_SAVED` / `EXTRA_RECIPE_NAME`):

- `RecipeListFragment.REQUEST_RECIPE_LOGGED = "recipe_logged"`
- `EXTRA_LOGGED_RECIPE_NAME = "logged_recipe_name"`
- `EXTRA_LOGGED_SLOT = "logged_slot"`
- `EXTRA_LOGGED_DATE = "logged_date"`

Set the result **before** `popBackStack()`. Snackbar text: `R.string.log_recipe_logged_format` = `Logged %1$s to %2$s`. Announcement: `R.string.a11y_recipe_logged` = `Logged %1$s to %2$s, %3$d foods added`. Row content description: `R.string.a11y_recipe_log_row` = `Log %1$s, %2$d ingredients, %3$d kilocalories`.

If `activeDate` is missing when the user taps a card, show `log_recipe_error_date` and do not open the dialog. Do not fall back to today.

New strings (add next to the existing recipe block in `strings.xml`):

- `action_log` = `Log`
- `log_recipe_title` = `Log Recipe`
- `log_recipe_logged_format` = `Logged %1$s to %2$s`
- `log_recipe_error_missing` = `Recipe not found`
- `log_recipe_error_date` = `Missing log date`
- `log_recipe_ingredient_line` = `%1$s · %2$s %3$s · %4$.0f kcal`
- `a11y_recipe_logged` = `Logged %1$s to %2$s, %3$d foods added`
- `a11y_recipe_log_row` = `Log %1$s, %2$d ingredients, %3$d kilocalories`

Reuse, do not duplicate: `action_cancel`, `log_food_hint_meal_slot`, `log_food_error_slot`, `log_food_error_quantity`, `log_food_preview_format`, `recipe_totals_format`, `recipe_error_empty`, `quick_add_error_name`, `quick_add_error_number`, `cd_delete_recipe`.

### Architecture compliance

- AD-1: use case and domain models stay free of Android/Room. Dialog and fragments own `R.string`.
- AD-2: synchronous. No coroutines, no `suspend`, no spinner (DESIGN.md: no loading spinners for local SQLite).
- AD-3: entities never leave `data/`. The use case sees `Recipe` / `RecipeIngredient` only.
- AD-4: `operator fun invoke()`, Koin `factory`.
- AD-5: new dialog nulls `_binding` if it holds one. `LiveData` observed with `viewLifecycleOwner`. `isAdded` before Snackbar / announce.
- AD-6: DI only in `AppModule.kt`.
- AD-7: cascade already exists on `foods.mealId`. Do not write a manual child delete.
- AD-8: do not touch FTS or `FoodCatalogDatabase`.
- NFR-1: no HTTP imports, no analytics.
- NFR-5: profile isolation is already inside `getRecipeById` and `logFoodEntry`. Do not pass `profileId` from the UI.
- NFR-6: 48dp targets, TalkBack on the row and on success, contrast unchanged (reuse theme colors).

### Library and framework requirements

Pinned stack. Do not upgrade and do not add dependencies:

- Room 3.0.3, Koin 3.5.6, Navigation 2.6.0, Material 1.10.0, AppCompat 1.6.1, AGP 9.2.1, SDK 36/35.
- No Safe Args plugin in `app/build.gradle.kts` or `libs.versions.toml`. A Bundle plus `arguments?.getString` is the project pattern.
- No MockK, no Robolectric. JUnit4 + fakes only (Stories 3.3, 3.5, 4.1).
- `androidx.arch.core:core-testing` is already present (`InstantTaskExecutorRule` in `DashboardViewModelTest`).

### Testing requirements

`LogRecipeUseCaseTest` fakes: in-memory `RecipeRepository` that returns a fixture or null, and a `MealRepository` fake that records each `logFoodEntry` call. Other `MealRepository` methods can throw `UnsupportedOperationException` — this use case must not call them.

Required cases:

- Stored 180 kcal / 34.5 protein / fiber 1.5 on a 150 g ingredient is written as those exact numbers, not `180 * 1.5`.
- Two ingredients → two calls, same slot and same date, names equal to `foodName`.
- Date `"2026-10-04"` is forwarded verbatim (the 2.1 midnight bug).
- Slot `" lunch "` becomes `"LUNCH"`.
- Second invoke appends another full set (no dedupe).
- Unknown id, empty ingredients, `BRUNCH`, `""`, `"today"`, `"2026/10/04"` → exception, zero writes.
- Ingredient 2 has `quantityGrams = 0` or `calories = -1` or blank name → exception, zero writes (ingredient 1 was not inserted).
- `catalogFoodId` is not read and not passed anywhere.

`RecipeViewModelTest`:

- `logRecipe("id", "DINNER", "2026-10-04")` calls the use case with those three strings and returns true. `recipes` list is unchanged (no `refresh()`).
- Use case throws → returns false, `logError.value` is the exception message, use case was not partially retried.

Do not modify `MealRepository` or its existing fakes. If a test fails to compile because a fake is missing a method, you added a method you were told not to add.

No emulator. After JVM tests pass, flag manual on-device checks:

- Dashboard on a past date → Recipes → tap card → Lunch → Log. Rows appear on that date, not today. Macro header matches the recipe sum. Snackbar and TalkBack fire.
- Delete icon still confirms and does not log. FAB still opens the builder.
- Second profile does not see or log the first profile's recipe.
- Swipe-delete removes one constituent only; Undo restores that one row.
- Food search sheet is unchanged (no recipe rows).

### Previous story intelligence

Story 4.1 (`4-1-recipe-builder-and-ingredient-grouping.md`, done, commit `974cf2d`):

- Recipes live in `meals_database` version 4, profile-scoped, `recipes` + `recipe_ingredients`. `catalog_food_id` is not a FK.
- `RecipeIngredientQuantityDialogFragment` exists because `LogFoodQuantityDialogFragment` forces a meal slot. This story needs the opposite: a slot picker with **no** quantity fields. Do not reuse the quantity dialog.
- List and builder share data via the DB plus `refresh()` in `onResume()`, not a shared VM. Save confirmation uses `setFragmentResult`. Copy that, with new keys.
- Review patches already applied: delete confirmation, non-gram unit scaling in `fromFoodItem`, nav labels as string resources, save-error LiveData, `DiffUtil` equality, profile-scoped `deleteIngredient`, duplicate-draft remove. Do not revert them.
- Deferred, leave them: Quick-Add on the shared search sheet (4.1-D1), card inspection/editor (4.1-D2), rotation drafts (4.1-D3), non-atomic recipe insert (4.1-D4).
- Test incident: a fake whose property name matched an override caused a JVM signature clash. Name fake backing fields differently from the function (`profileId` vs `getActiveProfileId()`).
- Build incident: `mergeDebugResources` NPE from a stale incremental blob. If that happens, run `:app:clean` once and rebuild. Do not rewrite unrelated XML.
- `RecipeViewModel` has no tests today. This story adds the first one. Do not change the existing 3 constructor parameter types or order; append only.

### Git intelligence

Recent commits: `974cf2d` Story 4.1 recipe builder, `69de783` Story 3.5 German catalog, `24fb76d` Story 3.3 custom foods, `5ecb1af` Story 3.2 search + meal-slot logging. Logging conventions to copy are in 3.2 (`logFoodEntry` + `selectDate` + Snackbar) and 2.5 (one swipe = one `MealEntry`).

The worktree is dirty with CRLF-only diffs on many files (`git diff --ignore-cr-at-eol` is empty for those paths). Do not `git add -A`. Do not normalize line endings. Stage only files this story creates or edits.

`local.properties` `sdk.dir` points at the Windows SDK. The AGP warning on Linux is harmless. Do not edit that file.

### Project context reference

Follow `_bmad-output/project-context.md` and `AGENTS.md`:

- Before Gradle: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`, Linux `java` 21, `ANDROID_HOME=$HOME/Android/Sdk`.
- Verify with `./gradlew :app:compileDebugSources` and `./gradlew :app:testDebugUnitTest`. Do not launch the emulator. Say so in the completion notes when manual testing is required.
- ViewModels must not hold `Context` or views. Pass resource ids / already-resolved display strings from the fragment.
- Room schema rule does not apply: this story must not change entities.

### Project Structure Notes

NEW:

- `app/src/main/java/com/example/myfoodtracker/domain/usecase/LogRecipeUseCase.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/LogRecipeDialogFragment.kt`
- `app/src/main/res/layout/dialog_log_recipe.xml`
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/LogRecipeUseCaseTest.kt`
- `app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/RecipeViewModelTest.kt`

UPDATE:

- `presentation/viewmodel/RecipeViewModel.kt`
- `presentation/ui/recipe/RecipeListFragment.kt`
- `presentation/ui/recipe/RecipeAdapter.kt`
- `presentation/ui/dashboard/DashboardFragment.kt`
- `di/AppModule.kt`
- `res/navigation/nav_graph.xml`
- `res/layout/item_recipe.xml`
- `res/values/strings.xml`
- `res/values/dimens.xml` (`dialog_log_recipe_ingredients_max_height` only)

Explicitly not modified: `AppDatabase.kt`, `FoodCatalogDatabase.kt`, `MealRepository.kt`, `MealRepositoryImpl.kt`, `MealDao.kt`, `MealEntryEntity.kt`, `FoodEntity.kt`, `LogFoodEntryUseCase.kt`, `DashboardViewModel.kt`, `FoodSearchBottomSheet.kt`, `RecipeBuilderFragment.kt`, `RecipeDao.kt`, `MealLogAdapter.kt`, `app/schemas/**`, `tools/seed/**`, catalog asset.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 4.2] — select a saved recipe, pick a slot, write constituent logs to the active date, totals update immediately.
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/prd.md#FR-9] — logging a recipe logs constituent foods onto the current day. Non-goal: pre-seeded recipes.
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/addendum.md#intake_logs] — stale sketch. Code tables `meals`/`foods` win.
- [Source: _bmad-output/planning-artifacts/architecture/architecture-MyFoodTracker-2026-09-06/ARCHITECTURE-SPINE.md#AD-1..AD-8] — layer, sync DAO, mapper, use case, binding, DI, cascade, FTS rules.
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-MyFoodTracker-2026-09-06/EXPERIENCE.md#Voice and Tone] — confirmation copy like "Logged 150g Chicken Breast." No gamification. No spinner. 48dp, TalkBack.
- [Source: app/src/main/java/com/example/myfoodtracker/data/repository/MealRepositoryImpl.kt:93-128] — the writer.
- [Source: app/src/main/java/com/example/myfoodtracker/domain/usecase/LogFoodEntryUseCase.kt:16-37] — scaler to avoid.
- [Source: app/src/main/java/com/example/myfoodtracker/domain/model/Recipe.kt] — snapshot fields.
- [Source: app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/MealLogAdapter.kt:33] — why fan-out is one food per meal.
- [Source: app/src/main/java/com/example/myfoodtracker/presentation/ui/search/LogFoodQuantityDialogFragment.kt:27-98] — slot widget + positive-button override.
- [Source: _bmad-output/implementation-artifacts/4-1-recipe-builder-and-ingredient-grouping.md] — schema, deferred items, fake/CRLF/clean lessons.
- [Source: _bmad-output/implementation-artifacts/deferred-work.md#4.1-D1..D4] — do not silently close these.
- [Source: _bmad-output/project-context.md] — stack pins, sync execution, no emulator.

## Dev Agent Record

### Agent Model Used

Space Bunny Free (opencode, provider `opencode`, model `space-bunny-free`)

### Debug Log References

- `:app:mergeDebugResources` failed with `NullPointerException: Cannot invoke "java.io.File.getName()" because the return value of "java.io.File.getParentFile()" is null`. This is the stale-incremental-resource-blob incident already documented in Story 4.1; resolved with a single `./gradlew :app:clean` followed by a rebuild. No unrelated XML was touched.
- `LogRecipeUseCaseTest > logRecipe_success_doesNotRefreshRecipeList` initially failed: the fake counted `getRecipes()` calls, but the ViewModel's `init {}` already calls `refresh()` once. Assertion was corrected to compare the counter before/after `logRecipe` rather than to an absolute value. No production code changed.
- Two compile errors resolved during Task 2/3: missing `androidx.fragment.app.setFragmentResult` import in `RecipeListFragment` and missing `androidx.fragment.app.setFragmentResultListener` import in `DashboardFragment`.

### Implementation Plan

1. `LogRecipeUseCase` (Task 1) validates date → slot → recipe → ingredient list *in that order*, all before the first `logFoodEntry`. Snapshots are forwarded verbatim; no per-100g rescaling, no catalog lookup, no new repository method.
2. Presentation (Task 2): new `LogRecipeDialogFragment` (slot picker only, positive button overridden so an invalid slot does not dismiss it), adapter split into `onRecipeClicked` / `onDeleteClicked`, `RecipeViewModel.logRecipe` returning `Boolean` and exposing `logError`, `RecipeListFragment` implementing the dialog `Listener` and mapping domain literals to `@string` resources.
3. Date handoff (Task 3): `activeDate` is a required nav argument, passed from `DashboardViewModel.uiState.value?.activeDate` with no fallback to today. The result bundle is set **before** `popBackStack()`; the dashboard re-selects the same date to reload meals and the macro header.
4. Resources/DI (Task 4): new strings only, one new dimen, one new nav argument, one new Koin factory plus a fourth `get()` on `RecipeViewModel`. The 9-arg `DashboardViewModel` line was not touched.
5. Tests (Task 5): `LogRecipeUseCaseTest` (15 cases) and `RecipeViewModelTest` (5 cases), hand-written fakes, no MockK, no Robolectric, no new `androidTest`.

### Completion Notes List

Ultimate context engine analysis completed - comprehensive developer guide created

Story 4.2 implemented end-to-end. Highlights:

- **Fan-out semantics (AC2).** Each `RecipeIngredient` becomes one `meals` row (`title` = slot) plus one `foods` child. `mealRepository.logFoodEntry` was reused unchanged — no `logRecipe` method was added to `MealRepository`, so no existing fake had to change. A stored 180 kcal / 150 g stays 180 kcal / 150 g; `LogRecipeUseCaseTest` asserts this against the `180 * 1.5` failure mode.
- **Validate-then-write (AC4).** The full ingredient list is validated before the first insert, so a bad second ingredient cannot leave a partial recipe on the dashboard. Verified by three tests asserting zero writes for zero grams, negative calories, and a blank name on ingredient 2.
- **Historical dates (AC2/AC3).** The date string comes from a required `activeDate` nav argument and is forwarded verbatim — no `LocalDate.now()` anywhere in the write path. `btnRecipes` does not navigate at all if `uiState.value?.activeDate` is null.
- **Immediate refresh (AC3).** After a successful write, `RecipeListFragment` sets the `recipe_logged` fragment result and then pops. `DashboardFragment` listens on the same `parentFragmentManager` the recipe list already uses, calls `viewModel.selectDate(parsedDate)`, then shows the Snackbar and the TalkBack announcement. No spinner, no new ViewModel dependency.
- **Failure path (AC1/AC4).** Domain literals are mapped to existing/new `@string` resources in `RecipeListFragment.mapLogError` and returned to the dialog, which keeps itself open and shows the message in `tv_log_recipe_error`. The dialog holds no ViewModel of its own.
- **AC5 preserved.** Recipe delete + confirmation, the builder FAB, `REQUEST_RECIPE_SAVED`, food search, quick-add, water and swipe-to-delete are untouched. Verified by the full suite: 248 JVM unit tests pass, including every pre-existing test. No entity, DAO, schema, or catalog change; `AppDatabase` stays at version 4 and `FoodCatalogDatabase` at version 1.

Deliberate deviations / judgement calls:

- `android:maxHeight` is not honoured by `ScrollView`, so the `dialog_log_recipe_ingredients_max_height` cap (240dp) is applied in `LogRecipeDialogFragment` after the first measurement. The dialog stays a single `ScrollView`-free layout with one `TextView` for ingredient lines — no second RecyclerView, no `Parcelable` `Recipe`.
- The fragment result carries an extra `EXTRA_LOGGED_INGREDIENT_COUNT` int because `a11y_recipe_logged` needs an ingredient count for the TalkBack announcement and the dashboard must not re-query the recipe. `RecipeListFragment` already has the full `Recipe` in memory, so no extra read is introduced.
- Grams are displayed with up to one decimal (whole numbers print without a decimal) so a 12.5 g ingredient is not shown as "13 g". This is display-only; the full `Double` reaches `logFoodEntry`.
- `a11y_recipe_item` is left in `strings.xml` though the row now announces `a11y_recipe_log_row`, which describes the log action.

**Manual on-device verification still required (no emulator was run, per project policy):**

- Dashboard on a past date → Recipes → tap card → Lunch → Log: rows appear on that date, not today; macro header equals the recipe sum; Snackbar and TalkBack fire.
- Delete icon still confirms and never logs; the FAB still opens the builder.
- A second profile neither sees nor logs the first profile's recipe.
- Swipe-delete removes one constituent only; Undo restores that one row.
- The food search sheet is unchanged (no recipe rows appear).

### File List

**New:**

- `app/src/main/java/com/example/myfoodtracker/domain/usecase/LogRecipeUseCase.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/LogRecipeDialogFragment.kt`
- `app/src/main/res/layout/dialog_log_recipe.xml`
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/LogRecipeUseCaseTest.kt`
- `app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/RecipeViewModelTest.kt`

**Modified:**

- `app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/RecipeViewModel.kt` — appended 4th ctor param `LogRecipeUseCase`, added `logRecipe`, `logError`, `clearLogError`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeListFragment.kt` — implements `LogRecipeDialogFragment.Listener`, opens the dialog on card tap, reads `activeDate`, maps domain errors, emits `REQUEST_RECIPE_LOGGED`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeAdapter.kt` — split `onRecipeClicked` from `onDeleteClicked`, row now announces the log action
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/DashboardFragment.kt` — passes `activeDate` on `btnRecipes`, registers the `recipe_logged` result listener, `selectDate` + Snackbar + announcement
- `app/src/main/java/com/example/myfoodtracker/di/AppModule.kt` — added `factory { LogRecipeUseCase(get(), get()) }`, `RecipeViewModel` now four `get()` calls
- `app/src/main/res/navigation/nav_graph.xml` — required `activeDate` string argument on `recipeListFragment`
- `app/src/main/res/layout/item_recipe.xml` — root is clickable/focusable with a selectable foreground ripple
- `app/src/main/res/values/strings.xml` — 8 new recipe-logging strings
- `app/src/main/res/values/dimens.xml` — `dialog_log_recipe_ingredients_max_height`

### Change Log

| Date | Change | Author |
|---|---|---|
| 2026-10-17 | Story 4.2 implemented: `LogRecipeUseCase` fan-out, log-recipe slot dialog, recipe-card tap, `activeDate` nav handoff, dashboard refresh via fragment result, 20 new JVM unit tests | Dev Agent (Space Bunny Free) |
