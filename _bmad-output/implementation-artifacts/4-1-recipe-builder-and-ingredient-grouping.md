---
baseline_commit: 69de783e441f238db1ea7302852d3048ad3bb26e
---

# Story 4.1: Recipe Builder & Ingredient Grouping

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user,
I want to save a group of foods and ingredient quantities as a reusable recipe,
so that I can log complex meals in a single step later.

## Acceptance Criteria

1. **Given** the recipe builder interface, **When** the user enters a recipe name, adds constituent foods with quantities/ratios, and saves, **Then** the recipe and its constituent ingredient mappings are persisted in `recipes` and `recipe_ingredients` database tables.
2. **Given** a saved recipe, **When** viewed in the recipe list, **Then** it shows the recipe name, ingredient count, and summed macros per recipe (computed from ingredient snapshots, `Double` precision, no premature rounding — NFR-4 0.1%).
3. **Given** the recipe builder, **When** the user enters a blank name, zero/negative/non-finite quantity, or saves with zero ingredients, **Then** save is rejected with an explicit inline error (no silent default).
4. **Given** multiple profiles on one device, **When** recipes are queried, **Then** only the active profile's recipes are returned (`profile_id` isolation, FR-2/NFR-5).
5. **Given** JVM unit tests, **When** `./gradlew testDebugUnitTest` runs, **Then** all existing tests pass plus new recipe UseCase/repository tests pass with zero regressions.

## Tasks / Subtasks

- [x] Task 1: Data layer — `RecipeEntity`, `RecipeIngredientEntity`, `RecipeDao` in `AppDatabase` v4 (AC: #1, #4)
  - [x] Subtask 1.1: Create `data/db/RecipeEntity.kt` (`recipes`: `id TEXT UUID PK`, `profile_id FK→users CASCADE` + index, `name`, `created_at/updated_at INTEGER`).
  - [x] Subtask 1.2: Create `data/db/RecipeIngredientEntity.kt` (`recipe_ingredients`: autoincrement `id`, `recipe_id FK→recipes CASCADE` + index, nullable `catalog_food_id`, snapshot `food_name/quantity_grams/unit/calories/protein/carbs/fat/fiber` as `REAL Double`).
  - [x] Subtask 1.3: Create `data/db/RecipeDao.kt` — synchronous (no `suspend`, AD-2): `insertRecipe/insertIngredients/getRecipesByProfileId/getIngredientsByRecipeId/updateRecipeName/deleteRecipe/deleteIngredient`; `@Transaction getRecipesWithIngredientsByProfileId`.
  - [x] Subtask 1.4: Bump `AppDatabase` v3 → v4, add both entities, expose `recipeDao()`; keep `.fallbackToDestructiveMigration().allowMainThreadQueries()` (project-context rule). Do NOT touch `FoodCatalogDatabase` v1.
- [x] Task 2: Domain layer — models, repository interface, UseCases (AC: #1–#4)
  - [x] Subtask 2.1: `domain/model/Recipe.kt` + `RecipeIngredient.kt` (pure Kotlin, zero `android.*`/Room imports, AD-1) with `totalCalories/protein/carbs/fat/fiber` derived sums.
  - [x] Subtask 2.2: `domain/repository/RecipeRepository.kt` interface (`saveRecipe/getRecipes/getRecipeById/deleteRecipe/deleteIngredient`).
  - [x] Subtask 2.3: `data/repository/RecipeRepositoryImpl.kt` — session-scoped via `SessionRepository.getActiveProfileId()`, private extension mappers (AD-3), never leak entities past `data/`.
  - [x] Subtask 2.4: `SaveRecipeUseCase` (`operator fun invoke(name, ingredients)` + validation), `GetRecipesUseCase`, `DeleteRecipeUseCase` — Koin `factory` (AD-4).
- [x] Task 3: Presentation — recipe list + builder reusing search/quantity patterns (AC: #1–#3)
  - [x] Subtask 3.1: `RecipeListFragment` + `RecipeBuilderFragment` (or dialog) + `RecipeViewModel`; entry point from Dashboard (FAB overflow/menu); Jetpack Navigation 2.6.0 args.
  - [x] Subtask 3.2: Ingredient picking reuses `FoodSearchBottomSheet` + `LogFoodQuantityDialogFragment` pattern (food → quantity/unit); quantities stored as grams via `ServingUnit` conversion (`SERVINGS × baseServingSize`, cf. `LogFoodEntryUseCase`).
  - [x] Subtask 3.3: ViewBinding `_binding = null` in `onDestroyView()`, `viewLifecycleOwner` observers (AD-5); `tnum` macro readouts, `≥48dp` targets, TalkBack labels; zero hardcoded colors/strings/dimens — use `@color/@string/@dimen` (Story 2.6).
- [x] Task 4: DI + tests + verification (AC: #5)
  - [x] Subtask 4.1: Register in `di/AppModule.kt` only: `single<RecipeRepository>`, `factory` UseCases, `viewModel` (AD-6). No constructor-signature changes to existing classes.
  - [x] Subtask 4.2: JVM unit tests (JUnit4 + hand-written fakes, cf. Story 3.3/3.5 precedent): validation rejects, profile isolation, macro-sum accuracy, mapper correctness.
  - [x] Subtask 4.3: `androidTest` DAO test via `Room.inMemoryDatabaseBuilder` + `tearDown().close()` (flag for manual on-device run — no emulator per project rule).
  - [x] Subtask 4.4: Verify `./gradlew :app:assembleDebug` + `testDebugUnitTest` green.

### Review Findings

- [x] [Review][Decision] Dashboard navigation entry point placed in body instead of FAB menu — Approved design decision to retain outlined button in dashboard body.
- [x] [Review][Patch] Add confirmation dialog before recipe deletion in `RecipeListFragment` [app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeListFragment.kt:37-41]
- [x] [Review][Patch] Fix unit and quantity distortion for non-gram serving units in `Recipe.fromFoodItem` [app/src/main/java/com/example/myfoodtracker/domain/model/Recipe.kt:47-59]
- [x] [Review][Defer] Preserve builder drafts across screen rotation / configuration changes [app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeBuilderFragment.kt:33] — deferred, post-MVP presentation state
- [x] [Review][Defer] Wrap recipe and ingredient insertion in an atomic `@Transaction` [app/src/main/java/com/example/myfoodtracker/data/repository/RecipeRepositoryImpl.kt:23-48] — deferred, continues existing architecture pattern
- [x] [Review][Patch] Replace hardcoded destination labels in navigation graph with string resources [app/src/main/res/navigation/nav_graph.xml:50,59]
- [x] [Review][Patch] Correct error placement and LiveData observation for recipe save in `RecipeBuilderFragment` [app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeBuilderFragment.kt:77-81]
- [x] [Review][Patch] Fix referential equality in `RecipeIngredientAdapter.IngredientDiffCallback` [app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeIngredientAdapter.kt:60-65]
- [x] [Review][Patch] Scope `RecipeDao.deleteIngredient` to profile and recipe ID [app/src/main/java/com/example/myfoodtracker/data/db/RecipeDao.kt:33-35]
- [x] [Review][Patch] Fix `drafts.remove(draft)` in `RecipeBuilderFragment` removing wrong item when duplicates exist [app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeBuilderFragment.kt:48]
- [x] [Review][Patch] Add `TextWatcher` to clear recipe name error on typing [app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeBuilderFragment.kt:61-68]
- [x] [Review][Defer] Shared `FoodSearchBottomSheet` shows "Quick Add" button that displays unavailable snackbar in recipe flow [app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeBuilderFragment.kt:95-102] — deferred, pre-existing
- [x] [Review][Defer] Recipe card in `RecipeListFragment` has no detail/inspection view for existing recipe ingredients [app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeListFragment.kt:34] — deferred, pre-existing

## Dev Notes

- Relevant architecture patterns and constraints: Clean Architecture + MVVM ([Source: _bmad-output/planning-artifacts/architecture/architecture-MyFoodTracker-2026-09-06/ARCHITECTURE-SPINE.md#Design Paradigm]); sync Room DAOs, no `suspend` (AD-2); private entity→domain mappers (AD-3); `operator fun invoke()` + Koin `factory` (AD-4); `_binding = null` + `viewLifecycleOwner` (AD-5); all DI in `AppModule.kt` (AD-6); FK CASCADE + index on every child table (AD-7).
- **Do NOT reinvent — reuse inventory (read these files first):**
  - `data/db/MealEntryEntity.kt:10-28` — FK CASCADE + `Index(profile_id)` pattern to copy for `recipes.profile_id` and `recipe_ingredients.recipe_id`. Note Room 3 plural `parentColumns`/`entityColumns`.
  - `data/db/FoodEntity.kt:8-30` — child-entity shape (`mealId` FK + index, `Double` macro snapshot fields) — `RecipeIngredientEntity` mirrors this with `recipeId` instead of `mealId`.
  - `data/db/MealDao.kt:6-30` — synchronous DAO style to copy (no `suspend`); add one `@Transaction` join query for recipes-with-ingredients (cf. `MealWithFoods.kt` `@Relation` pattern).
  - `data/repository/MealRepositoryImpl.kt:93-128` — `logFoodEntry` session-scoping (`getActiveProfileId() ?: return ...`), UUID meal creation, `Slot` validation, private `toDomain()` mappers — copy all four habits.
  - `domain/usecase/LogFoodEntryUseCase.kt:9-38` — validation idiom (`require(name.isNotBlank())`, finite `quantity > 0`, slot whitelist, `ServingUnit`→grams + `factor = grams/100`) — `SaveRecipeUseCase` applies the same per ingredient.
  - `data/dao/FoodCatalogDao.kt:13-33` — catalog is `MATCH`-only FTS read path; ingredient picker searches the catalog but **snapshots** macros into `recipe_ingredients` (recipes keep working if catalog asset changes).
  - `di/AppModule.kt:46-112` — `meals_database` builder (`fallbackToDestructiveMigration + allowMainThreadQueries`); add `single<RecipeRepository>`, `factory` UseCases, `viewModel` in the same blocks.
- **Critical scoping decisions (do not deviate without asking):**
  - Recipes live in `AppDatabase` (`meals_database`, user data, v3→v4) — NEVER in `FoodCatalogDatabase` (frozen v1; Story 3.5 forbids version bump / would wipe `is_custom` rows).
  - Cross-database FK is impossible (two Room DBs): `recipe_ingredients.catalog_food_id` is a nullable informational reference only, NO `@ForeignKey` to `catalog_foods`. Integrity comes from macro snapshots, not FK.
  - `AppDatabase` has `exportSchema = false` (`AppDatabase.kt:18`) — no `app/schemas` JSON churn for v4.
  - Story 4.1 = builder/CRUD only. One-tap logging to a meal slot is Story 4.2 — but store per-ingredient absolute macros + `quantity_grams` so 4.2 can fan out to `MealEntryEntity`+`FoodEntity` rows without re-querying the catalog.
  - Actual `meals`/`foods` tables diverge from `addendum.md` `intake_logs` sketch — follow the CODE (`MealEntryEntity`/`FoodEntity`/`MealDao`), not the addendum, for all new-table conventions.
- **What must be preserved:** `FoodCatalogDatabase` v1 untouched; `BundledSQLiteDriver` catalog wiring; Story 3.3 custom-first ranking; dashboard macro totals <50ms (recipe builder adds no dashboard queries); NFR-1 zero network (no HTTP imports in `app/`); `Double` throughout, no premature rounding (NFR-4).
- Testing standards summary: JUnit4 + hand-written fakes only (no MockK/Robolectric per 3.3/3.5 precedent); `InstantTaskExecutorRule` only if a VM test needs it; DAO integration tests in `androidTest` with in-memory builder, closed in `tearDown()`; no emulator — flag on-device/instrumented runs for manual testing; env preflight per `AGENTS.md` (`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`, Linux java 21); `local.properties` SDK warning is harmless, do not touch.

### Project Structure Notes

- Alignment: NEW `data/db/RecipeEntity.kt`, `data/db/RecipeIngredientEntity.kt`, `data/db/RecipeDao.kt` (+`RecipeWithIngredients.kt` `@Relation`), `domain/model/Recipe.kt`, `domain/model/RecipeIngredient.kt`, `domain/repository/RecipeRepository.kt`, `data/repository/RecipeRepositoryImpl.kt`, `domain/usecase/SaveRecipeUseCase.kt`, `GetRecipesUseCase.kt`, `DeleteRecipeUseCase.kt`, `presentation/ui/recipe/*`, `presentation/viewmodel/RecipeViewModel.kt`. UPDATE `data/db/AppDatabase.kt` (v4 + entities + `recipeDao()`), `di/AppModule.kt`, nav graph, `res/` (strings/colors/dimens only).
- Explicitly NOT modified: `FoodCatalogDatabase.kt`, `data/entity/catalog_*`, `FoodCatalogDao.kt`, `FtsQueryBuilder.kt`, `SearchFoodUseCase`, `CreateCustomFoodUseCase`, `tools/seed/*`, `app/schemas/**`, catalog asset.
- Variance note: `epics.md` Story 4.1 names tables `recipes`/`recipe_ingredients` and `addendum.md` ER diagram confirms them but gives no column specs — column design above (profile-scoped, snapshot macros, CASCADE) is this story's normative refinement; no epic rewrite needed.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 4.1] — story statement + AC (`recipes` + `recipe_ingredients` tables).
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/prd.md#FR-9] — recipe tracks constituent foods + weight ratios; logging maps constituents to current day.
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/addendum.md#1. Database Schema] — ER confirms `recipes ||--o{ recipe_ingredients`; code tables take precedence over the stale `intake_logs` sketch.
- [Source: _bmad-output/planning-artifacts/architecture/architecture-MyFoodTracker-2026-09-06/ARCHITECTURE-SPINE.md#AD-1..AD-7, #Capability Map] — layer rules; FR-8/FR-9 row points at `FoodDao` + `customfood/` as the extension point.
- [Source: app/src/main/java/.../data/db/AppDatabase.kt:9-24] — v3 entities, `exportSchema=false`.
- [Source: app/src/main/java/.../di/AppModule.kt:46-112] — DB builders, repo/UseCase/VM registration blocks.
- [Source: app/src/main/java/.../domain/usecase/LogFoodEntryUseCase.kt] — validation + grams-factor idiom.
- [Source: app/src/main/java/.../data/repository/MealRepositoryImpl.kt] — session scoping + mapper precedent.
- [Source: _bmad-output/implementation-artifacts/3-5-german-and-european-food-catalog-seed-pipeline.md#Dev Notes] — frozen catalog v1, fake-based tests, CRLF/`git add -A` trap, no-emulator policy.
- [Source: _bmad-output/project-context.md] — stack pins, sync-execution, Koin/ViewBinding/testing/workflow rules.
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-MyFoodTracker-2026-09-06/DESIGN.md#Components] — bottom-sheet/quantity-modal/card patterns, `tnum`, 48dp, TalkBack floor.

## Dev Agent Record

### Agent Model Used

Muse Spark 1.3 Contributor (opencode-go)

### Debug Log References

- Fresh implementation from `ready-for-dev` (baseline `69de783`); no review continuation.
- RED: wrote 4 JVM test files first (`RecipeTest`, `SaveRecipeUseCaseTest`, `RecipeQueryUseCasesTest`, `RecipeRepositoryImplTest`) referencing not-yet-existing classes.
- GREEN: implemented data layer (Task 1) → domain layer (Task 2) → DI (Task 4.1) → presentation (Task 3) → androidTest DAO test (Task 4.3).
- Build incident: first `compileDebugSources` failed in `mergeDebugResources` with NPE (`ResourceSet.createFileAndItemsFromXml`) — stale incremental merge blob, unrelated to new XML. Fixed by `:app:clean`; full rebuild green.
- Test incident: `FakeSessionRepository` used `var activeProfileId` + `override fun getActiveProfileId()` → JVM signature clash. Fixed by renaming backing field to `profileId`.
- Design deviation from story text: `RecipeIngredientQuantityDialogFragment` created instead of reusing `LogFoodQuantityDialogFragment` directly — the existing dialog forces a meal-slot pick, which is wrong UX inside a recipe builder. New dialog copies its validation/preview idiom (quantity+unit only) and reuses `log_food_preview_format` / `log_food_error_quantity` strings.
- Builder/list share data via DB + `refresh()` in `onResume()` (fragment-scoped Koin VMs); save confirmation passed via `setFragmentResult`.
- `local.properties` SDK warning is harmless per AGENTS.md — ignored, no file change.

### Completion Notes List

- Data: `recipes` (UUID PK, `profile_id` FK CASCADE + index) + `recipe_ingredients` (autoincrement PK, `recipe_id` FK CASCADE + index, nullable `catalog_food_id` reference, `Double` macro snapshots); `RecipeDao` fully synchronous; `AppDatabase` v3→v4 with `fallbackToDestructiveMigration`; `FoodCatalogDatabase` v1 untouched.
- Domain: pure-Kotlin `Recipe`/`RecipeIngredient`/`NewRecipeIngredient` (`fromFoodItem` scaling mirrors `LogFoodEntryUseCase`); `RecipeRepository` interface; `RecipeRepositoryImpl` session-scoped with private mappers; `SaveRecipeUseCase` (blank-name/empty/quantity/macro validation), `GetRecipesUseCase`, `DeleteRecipeUseCase`.
- Presentation: `RecipeListFragment` (list + delete + FAB + empty state + save-result snackbar), `RecipeBuilderFragment` (name + FTS search bottom-sheet ingredient picking + quantity dialog + live totals + inline errors), `RecipeViewModel`; entry via Dashboard "Recipes" outlined button; nav graph extended; all strings/colors/dimens centralized; `tnum` readouts; TalkBack labels; `_binding = null` + `viewLifecycleOwner` throughout.
- Tests: 23 new JVM tests (5 model + 10 save-validation + 4 query + 4 repository) — suite total 228, 0 failures/errors; `:app:assembleDebug` + `:app:assembleDebugAndroidTest` green (androidTest compiles, incl. new `RecipeDaoTest` cascade/isolation/round-trip tests).
- MANUAL TESTING NEEDED (no emulator per project rule): recipe builder e2e on device (search → quantity → save → list totals), delete flow, profile isolation with 2 profiles, fresh-install DB v4 creation via `fallbackToDestructiveMigration`.
- ✅ Resolved: all 5 acceptance criteria satisfied (persist recipes+ingredients; list shows name/count/summed macros; blank/zero/negative/non-finite rejected inline; profile-scoped queries; full suite green).

### File List

- `app/src/main/java/com/example/myfoodtracker/data/db/RecipeEntity.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/data/db/RecipeIngredientEntity.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/data/db/RecipeWithIngredients.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/data/db/RecipeDao.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/data/db/AppDatabase.kt` (v4 + `recipeDao()`)
- `app/src/main/java/com/example/myfoodtracker/domain/model/Recipe.kt` (new: `Recipe`, `RecipeIngredient`, `NewRecipeIngredient`)
- `app/src/main/java/com/example/myfoodtracker/domain/repository/RecipeRepository.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/data/repository/RecipeRepositoryImpl.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/SaveRecipeUseCase.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/GetRecipesUseCase.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/DeleteRecipeUseCase.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/RecipeViewModel.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeListFragment.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeBuilderFragment.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeIngredientQuantityDialogFragment.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeAdapter.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/recipe/RecipeIngredientAdapter.kt` (new)
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/DashboardFragment.kt` (recipes navigation)
- `app/src/main/java/com/example/myfoodtracker/di/AppModule.kt` (recipe DAO/repo/UseCases/VM)
- `app/src/main/res/layout/fragment_recipe_list.xml` (new)
- `app/src/main/res/layout/fragment_recipe_builder.xml` (new)
- `app/src/main/res/layout/item_recipe.xml` (new)
- `app/src/main/res/layout/item_recipe_ingredient.xml` (new)
- `app/src/main/res/layout/dialog_recipe_ingredient_quantity.xml` (new)
- `app/src/main/res/layout/fragment_dashboard.xml` (Recipes button)
- `app/src/main/res/navigation/nav_graph.xml` (recipe destinations + actions)
- `app/src/main/res/values/strings.xml` (recipe strings + a11y)
- `app/src/test/java/com/example/myfoodtracker/domain/model/RecipeTest.kt` (new, 5 tests)
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/SaveRecipeUseCaseTest.kt` (new, 10 tests)
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/RecipeQueryUseCasesTest.kt` (new, 4 tests)
- `app/src/test/java/com/example/myfoodtracker/data/repository/RecipeRepositoryImplTest.kt` (new, 4 tests)
- `app/src/androidTest/java/com/example/myfoodtracker/data/RecipeDaoTest.kt` (new, 3 tests — on-device only)
