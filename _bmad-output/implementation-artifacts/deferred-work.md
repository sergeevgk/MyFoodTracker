# Deferred Work

## Deferred from: code review of story 1-2-passcode-authentication-and-startup-profile-lock (2026-09-13)

- **D-1: Single-iteration SHA-256 for 4-digit PINs** - PasscodeHasher uses SHA-256 (fast hash) for a 4-digit PIN keyspace (10K combinations). A slow KDF (PBKDF2/Argon2) would improve resistance to offline brute-force if the SQLite DB is extracted. Pre-existing design decision per spec.
- **D-2: No attempt rate limiting / lockout** - No failed-attempt counter, exponential backoff, or temporary lockout after repeated wrong passcodes. Desirable hardening but not specified in any acceptance criteria.
- **D-3: No logout action in settings screen** - AC #6 mentions "dashboard or settings" but no settings screen exists (template no-op). Dashboard logout is implemented. Settings screen is a future concern.
- **D-4: Dashboard has no ViewModel (MVVM violation)** - DashboardFragment directly injects SessionRepository and LogoutUseCase. Acceptable for the current placeholder; ViewModel expected when the dashboard gets real functionality in Epic 2.
- **D-5: Passcode hashes circulate through UI states** - UserProfile domain model includes passcodeHash, which flows into PasscodeAuthState.Content.profiles and the in-memory session. Refactoring to exclude sensitive data from presentation contexts is a broader model change.

## Deferred from: code review of 2-1-week-view-navigation-and-month-calendar-picker (2026-09-30)

- **D-1: `addMealEntry()` hard-codes `LocalDate.now()`** - `MealRepositoryImpl` generates meal timestamps using local device date (`LocalDate.now()`). Near midnight in non-UTC zones, this can mismatch the UTC-converted dates selected via `MaterialDatePicker`. A future update should accept a target date parameter. [MealRepositoryImpl.kt:33]
- **D-2: Hardcoded hex colors without central color resources** - Colors like `#1B4D3E`, `#718096`, and `#E2E8F0` are parsed inline in adapters and layouts instead of referencing Android `@color/` resources, which is an existing codebase pattern across multiple features. [WeekDayAdapter.kt:43]
- **D-3: No accessibility announcements on active date change** - Dynamic calendar and week navigation changes are not announced to screen readers (`announceForAccessibility` or live region). [DashboardFragment.kt:85]

## Deferred from: code review of 2-2-macro-progress-header-card-and-dynamic-target-display (2026-09-30)

- **D-1: Hardcoded light-theme colors in layout XML** - Macro progress header card and rows use hardcoded hex values (`#FFFFFF`, `#E2E8F0`, `#2D3748`, `#1B4D3E`, etc.) instead of theme attributes or `values-night` resources. Pre-existing codebase pattern previously deferred in Story 2.1 (D-2). [fragment_dashboard.xml:211]
- **D-2: Hardcoded UI string literals without `@string/` resources** - Title, nutrient labels, and format templates are defined directly as string literals rather than localized string resources, matching an existing project-wide pattern. [fragment_dashboard.xml:228, DashboardFragment.kt:117]

## Deferred from: code review of 2-3-quick-water-logging-plus-250ml (2026-09-30)

- **D-1: Instant tactile / haptic feedback on quick-add water button** - Tap does not trigger haptic feedback (`performHapticFeedback(KEYBOARD_TAP)`). Deferred by user decision as post-MVP tactile polish. [DashboardFragment.kt:89]

## Deferred from: code review of 2-4-quick-add-calorie-and-macro-logging (2026-10-01)

- **D-1: Lack of atomic database transaction across `insertMeal` and `insertFoods`** - `MealRepositoryImpl.logQuickAdd` performs two separate DAO operations sequentially without an atomic Room `@Transaction`. Pre-existing DAO design where inserting a meal and its foods are separate methods; wrapping in an atomic transaction method in `MealDao` should be addressed when DAO transactions are overhauled. [MealRepositoryImpl.kt:55] **RESOLVED** by story 6-1 (2026-10-25) — see "Resolved by" section below.
- **D-2: Hardcoded light-theme colors and string literals without central resources** - `dialog_quick_add_log.xml` and `DashboardFragment.kt` use hardcoded hex values (`#FFFFFF`, `#1B4D3E`, `#2D3748`, `#718096`) and string literals without `@color/` or `@string/` resources. Continues pre-existing project-wide pattern tracked under Story 2.1 (D-2) and Story 2.2 (D-1/D-2). [dialog_quick_add_log.xml:6, DashboardFragment.kt:185]

## Deferred from: code review of 2-5-swipe-to-delete-intake-logs-with-undo (2026-10-02)

- **D-1: Lack of atomic database transaction across `insertMeal` and `insertFoods` during restore** - `MealRepositoryImpl.restoreMealEntry` executes `mealDao.insertMeal` and `mealDao.insertFoods` in sequence without a Room `@Transaction`. Continues the pre-existing architectural pattern tracked under Story 2.4 (D-1), to be addressed when DAO operations are unified under an atomic transaction helper. [MealRepositoryImpl.kt:93-118] **RESOLVED** by story 6-1 (2026-10-25) — see "Resolved by" section below.

## Resolved by: story 2-6-centralize-colors-strings-and-ui-resources (2026-10-02)

- **2.1-D2: Hardcoded hex colors without central color resources** - RESOLVED. All palette/semantic tokens centralized in `res/values/colors.xml`; layouts, drawables (`bg_today_dot.xml`), `WeekDayAdapter`, and `DashboardFragment` swipe-delete background now reference `@color/` resources via `ContextCompat.getColor()`.
- **2.2-D1: Hardcoded light-theme colors in layout XML** - RESOLVED. All layout hex values replaced with `@color/` references; brand/surface/ink colors registered as Material3 theme attributes in `res/values/themes.xml` and `res/values-night/themes.xml`.
- **2.2-D2: Hardcoded UI string literals without `@string/` resources** - RESOLVED. All user-visible strings, parameterized format templates (`%1$s`, `%1$d`, `%1$.0f`), and TalkBack announcements centralized in `res/values/strings.xml`; presentation code uses `getString(R.string....)`.
- **2.4-D2: Hardcoded light-theme colors and string literals without central resources** - RESOLVED. `dialog_quick_add_log.xml` and `DashboardFragment.kt` (dialog titles, buttons, validation errors, snackbar, announcements) now use `@color/`, `@string/`, and `@dimen/` resources.
- Note: Story 2.6 AC1 also cites "2.5-D2", but the 2-5 deferred log contains only D-1 (DB transaction, still open). The swipe-delete hardcoded color (`#BA1A1A` + canvas `"Delete"`) from Story 2-5 is nonetheless centralized by this story (`R.color.danger_red`, `R.string.action_delete`).

## Deferred from: code review of story 3-4-remember-user-session-on-device (2026-10-02)

- **3.4-D1: `.agents/project_structure.md` tracking requirement dropped** - DROPPED / RESOLVED. Removed the obsolete "Structure Updates" rule requiring `.agents/project_structure.md` from `project-context.md` per user directive. Additional project structure file tracking is no longer required.
- **3.4-D2: Remembered session has no TTL and no "not you?" escape hatch** - `SharedPrefsSessionStorage` persists a bare UUID with no timestamp and `SessionStorage` exposes no expiry parameter, so a remembered profile auto-logs in indefinitely until explicit logout. The single storage slot plus a `PasscodeAuthState.Authenticated` state with no discriminator mean there is no on-screen way to decline a restored session; a multi-profile user must complete a full logout round-trip to switch profiles. Deferred by user decision: remember-me persisting until explicit logout matches the story's intent. Consider a TTL (e.g. 30 days) and a "Not you?" affordance if the threat model grows. [SharedPrefsSessionStorage.kt:19-27, PasscodeAuthFragment.kt:116-123]

## Forward notes from: story 3-1-sqlite-fts5-search-engine-and-pre-populated-food-database (2026-10-03)

- **3.1-F1: Catalog version bump + `fallbackToDestructiveMigration` would wipe custom foods** - `FoodCatalogDatabase` uses `fallbackToDestructiveMigration()` with `createFromAsset`. When Story 3.3 stores user-created custom foods in `catalog_foods` (`is_custom = 1`), any future catalog `version` bump will destructively recopy the asset and delete those rows. Before bumping the catalog version, implement a migration that preserves `is_custom = 1` rows (export + re-import) or move custom foods to a separate table/DB. [di/AppModule.kt]
- **3.1-F2: Story 5.2 backup/restore must cover two databases** - RESOLVED by story 5-2-offline-full-database-backup-and-restore. The backup file carries a `myfoodtracker_custom_foods` table with every `catalog_foods` row where `is_custom = 1` (plus its `catalog_food_nutrients` row); the seeded catalog is not copied because the bundled asset is reinstallable. On restore the custom foods are re-inserted through `FoodCatalogDao` with freshly allocated ids, so a different catalog asset version cannot collide with the seeded rows.
  - Known limitation carried forward as **5.2-F1** below: `catalog_food_serving_units` rows of custom foods are not part of the backup format.

## Deferred from: code review of 3-2-food-search-quantity-modal-and-meal-slot-logging (2026-10-04)

- **3.2-D1: Non-atomic composite meal logging (`insertMeal` and `insertFoods`)** - `MealRepositoryImpl.logFoodEntry` performs two separate DAO operations without an atomic Room `@Transaction`. Continues pre-existing DAO design debt 2.4-D1 / 2.5-D1, to be addressed when meal DAO operations are overhauled with unified transaction support. [MealRepositoryImpl.kt:80] **RESOLVED** by story 6-1 (2026-10-25) — see "Resolved by" section below.
- **3.2-D2: Instrumented test coverage for `FoodCatalogDao` projection additions** - `FoodCatalogDao` added `base_serving_size` and `base_serving_unit` to the search projection. Integration verified via JVM unit tests; on-device instrumented test execution deferred per project policy forbidding automated emulator runs. Flagged for manual on-device verification. [FoodCatalogFtsTest.kt:50]



## Deferred from: code review of 3-3-custom-food-creation-and-relevance-ranking (2026-10-06)

- **3.3-D1: Main-thread synchronous `MealRepository` scan on every search keystroke** - `SearchFoodUseCase` scans all meal entries synchronously on the main thread during typing without debounce. Documented in Dev Notes §2: acceptable for small history; if manual on-device timing exceeds the 50ms average threshold, a future optimization should add an optimized `COUNT/GROUP BY` query to `MealDao`. [SearchFoodUseCase.kt:21]

## Deferred from: code review of 3-5-german-and-european-food-catalog-seed-pipeline (2026-10-07)

- **3.5-D1: Legal attribution for BLS 4.0 (CC BY 4.0) and Open Food Facts (ODbL) in UI** - Bundeslebensmittelschlüssel 4.0 is licensed under CC BY 4.0 (© Max Rubner-Institut, DOI 10.25826/Data20251217-134202-0) and Open Food Facts is licensed under ODbL 1.0 (© Open Food Facts contributors). When an About/Credits or Settings screen is introduced in a future story, attribution and licensing notices must be displayed to satisfy license requirements. [tools/seed/README.md]

## Resolved by: backlog-creation analysis sweep (2026-10-25)

- **1.2-D4: Dashboard has no ViewModel** - RESOLVED (verified). `DashboardViewModel` exists at `presentation/viewmodel/DashboardViewModel.kt` and is wired in `AppModule.kt:134`; `DashboardFragment` injects via `by viewModel()`.
- **2.1-D3: No accessibility announcements on active date change** - RESOLVED (verified). `announceForAccessibility` is used project-wide post-story 2.6 (DashboardFragment.kt ×6 call sites, SettingsFragment.kt, RecipeListFragment.kt, FoodSearchBottomSheet.kt).
- Triaged as **measure-first, story-blocked**: 3.3-D1 (main-thread search scan — act only if on-device timing exceeds the 50ms threshold) and 5.2-F3/5.2-D2 (sync backup/restore — act only if measured ≥ 1s). No story created.
- Triaged as **manual device verification only**: 5.2-F2 (SQLite restore path checklist in story 5-2 Completion Notes) and 3.2-D2 (FoodCatalogDao projection on-device). No story created.

## Deferred from: code review of 4-1-recipe-builder-and-ingredient-grouping (2026-10-15)

- **4.1-D1: Shared `FoodSearchBottomSheet` shows "Quick Add" button that displays unavailable snackbar in recipe flow** - The shared `FoodSearchBottomSheet` component exposes the "Quick Add" button even when opened from `RecipeBuilderFragment`. Tapping it dismisses the sheet and displays a snackbar. Modifying `FoodSearchBottomSheet` to conditionally hide Quick Add is deferred as a cross-cutting component enhancement. [RecipeBuilderFragment.kt:95-102]
- **4.1-D2: Recipe card in `RecipeListFragment` has no detail/inspection view for existing recipe ingredients** - Tapping a saved recipe card in the list does not show its ingredient breakdown or quantities (only the macro sum). Recipe inspection/editing is deferred to Story 4.2 / future stories as Story 4.1 is scoped to creation and listing. [RecipeListFragment.kt:34]
- **4.1-D3: Preserve builder drafts across screen rotation / configuration changes** - Drafts are currently held in Fragment memory during the build session; saving draft state across process recreation or configuration change is deferred as post-MVP presentation state refinement. [RecipeBuilderFragment.kt:33]
- **4.1-D4: Wrap recipe and ingredient insertion in an atomic `@Transaction`** - `RecipeRepositoryImpl.saveRecipe` calls `insertRecipe` and `insertIngredients` separately without an enclosing Room `@Transaction`. Continues the codebase architectural pattern tracked under 2.4-D1 / 2.5-D1 / 3.2-D1, to be addressed when DAO transactions are unified across repositories. [RecipeRepositoryImpl.kt:23-48] **RESOLVED** by story 6-1 (2026-10-25) — see "Resolved by" section below.

## Deferred from: code review of 4-2-one-tap-recipe-logging (2026-10-17)

- **4.2-D1: Check lifecycle and context before accessing resources in `scroll.post`** - `LogRecipeDialogFragment.capIngredientScrollHeight` calls `resources.getDimensionPixelSize` in a posted runnable without an `isAdded` / `context != null` guard. Deferred per user decision as edge-case lifecycle hardening. [LogRecipeDialogFragment.kt:94]

## Resolved by: story 5-1-csv-and-txt-log-export (2026-10-21)

- **3.5-D1: Legal attribution for BLS 4.0 (CC BY 4.0) and Open Food Facts (ODbL) in UI** - RESOLVED. Story 5.1 introduced the first Settings screen ("Settings & Data Management", reachable from the dashboard overflow menu) and displays the attribution/privacy notice verbatim as `@string/settings_attribution` in `fragment_settings.xml`: "Food catalog data: BLS data © Max Rubner-Institut, licensed CC BY 4.0 (DOI 10.25826/Data20251217-134202-0); OFF data © Open Food Facts contributors, licensed ODbL 1.0. All data stays on this device." The notice is static text, visible without scrolling into a hidden tab, and independent of the export state.

## Forward notes from: story 5-1-csv-and-txt-log-export (2026-10-21)

- **5.1-F1: Water logs are not part of the log export** - The export header is fixed by FR-12 (`Date,Meal Slot,Food Name,Quantity,Calories,Protein,Carbs,Fat`) and has no water column, so `water_logs` rows are not exported. Adding a water section (or a second file) is a product decision for a future story. [domain/usecase/ExportLogsUseCase.kt]
- **5.1-F2: "Save to file…" payload is held in a Fragment field and is lost on rotation** - While the system document picker is open, the pending `ExportPayload` lives in a `SettingsFragment` field (per the story contract). A configuration change while the picker is open therefore loses the payload; the activity result then reports "Could not save the log file" instead of writing the file. Moving the pending payload into the ViewModel (or `onSaveInstanceState`) would remove this edge case. [presentation/ui/settings/SettingsFragment.kt]
- Note: deferred item `1.2-D3` (logout action in the settings screen) stays **open** — Story 5.1 did not add a logout button; the dashboard logout is unchanged.

## Forward notes from: story 5-2-offline-full-database-backup-and-restore (2026-10-22)

- **5.2-F1: `catalog_food_serving_units` of custom foods are not backed up** - The backup format carries `catalog_foods` (`is_custom = 1`) plus `catalog_food_nutrients`, but not serving units. Custom foods are currently created without serving units, so nothing is lost today; if custom foods ever gain serving units, `format_version` must be bumped and the format extended. [data/backup/DatabaseBackupManager.kt]
  - Note (2026-10-25): story 6-2 added an additive `source_catalog_id` column to the backup-only `myfoodtracker_custom_foods` table while keeping `FORMAT_VERSION = 1` (additive, probe-compatible). This does **not** pre-empt 6.3's planned `format_version` bump to 2 for serving units.
- **5.2-F2: The SQLite-level restore path is only covered by JVM tests up to the validation decision table** - The WAL checkpoint, the journal-mode conversion of the copy, the identity-hash comparison against the live file, the `Context.deleteDatabase` swap and the cold-start custom-food re-insert can only be exercised on a device. No emulator run was performed (project policy); the manual checks are listed in the story's Completion Notes. [data/backup/PendingRestoreApplier.kt]
- **5.2-F3: The backup/restore flow runs synchronously on the main thread** - By design (AD-2, no coroutines) and acceptable because only the user's own logs (< 1 MB) are copied; the ~10–20 MB catalog is never copied. If an on-device measurement ever exceeds ~1 s, only the file copy may move to a `java.util.concurrent.Executor`, and the checkpoint/copy pair must then be serialized against Room writes. [presentation/ui/settings/SettingsFragment.kt]
- **5.2-F4: A staged restore may be included in the platform auto-backup** - With the unchanged `backup_rules.xml` / `data_extraction_rules.xml`, `allowBackup="true"` includes `files/`, so `filesDir/pending_restore/` could be uploaded by the OS between staging and restart. This is the user's own data and was accepted consciously; harden the backup rules if that changes. [MainApplication.kt, res/xml/backup_rules.xml] **RESOLVED** by story 6-2 (2026-10-25) — see "Resolved by" section below.

## Deferred from: code review of story-5.2 (2026-10-22)

- **5.2-D1: Discarded custom food IDs in `recipe_ingredients` after restore** - When custom foods are restored into `FoodCatalogDao`, fresh IDs are allocated to prevent collision with the seeded catalog asset, leaving `catalog_food_id` in `recipe_ingredients` unlinked to the new IDs. [RecipeIngredientEntity.kt] **RESOLVED** by story 6-2 (2026-10-25) — see "Resolved by" section below.
- **5.2-D2: Synchronous main-thread execution of backup and restore operations** - Backup/restore runs synchronously on the main thread per project architectural constraint AD-2 (no coroutines). Tracked as forward note 5.2-F3. [SettingsFragment.kt]

## Resolved by: story 6-1-unify-atomic-room-transactions (2026-10-25)

- **2.4-D1: Lack of atomic database transaction across `insertMeal` and `insertFoods`** - RESOLVED. `MealRepositoryImpl.logQuickAdd` now writes the meal and its food through the single `@Transaction` DAO method `MealDao.insertMealWithFoods` (a Room 3 default-body interface method delegating to the existing REPLACE-annotated primitives). Delegation is guarded by the JVM test `logQuickAdd_writesThroughTransactionalCompositeMethod`.
- **2.5-D1: Lack of atomic database transaction across `insertMeal` and `insertFoods` during restore** - RESOLVED. `MealRepositoryImpl.restoreMealEntry` uses the same `insertMealWithFoods` composite. The REPLACE re-insert-same-id semantics and parent-first FK order are inherited from the primitives and unchanged; the "Parent first (FK order)" comment remains accurate. Guarded by `restoreMealEntry_writesThroughTransactionalCompositeMethod` and `restoreMealEntry_reinsertsSameIdAndFoods`.
- **3.2-D1: Non-atomic composite meal logging (`insertMeal` and `insertFoods`)** - RESOLVED. `MealRepositoryImpl.logFoodEntry` writes through the same `insertMealWithFoods` composite. Guarded by `logFoodEntry_writesThroughTransactionalCompositeMethod`.
- **4.1-D4: Wrap recipe and ingredient insertion in an atomic `@Transaction`** - RESOLVED. `RecipeRepositoryImpl.saveRecipe` writes through the new `@Transaction` `RecipeDao.insertRecipeWithIngredients` composite. Guarded by `saveRecipe_writesThroughTransactionalCompositeMethod` (JVM) and `insertRecipeWithIngredients_roundTripsRecipeAndIngredients` (androidTest; executed on device only per the no-emulator policy).
- Note: single-table inserts are intentionally not wrapped (`MealRepositoryImpl.addMealEntry` calls `insertMeal` alone — nothing to make atomic). No schema, signature, DI wiring, or conflict-strategy changes were made.

## Deferred from: code review of 6-1-unify-atomic-room-transactions (2026-10-25)

- **6.1-D1: Missing instrumented test coverage for `MealDao.insertMealWithFoods`** - While `RecipeDaoTest.kt` contains an instrumented test for `RecipeDao.insertRecipeWithIngredients`, `MealDao` lacks an instrumented test class in `androidTest`. Delegated execution verified via JVM unit tests (`MealRepositoryImplTest`); on-device execution deferred per project policy forbidding automated emulator runs. [MealDao.kt:29]

## Resolved by: story 6-2-remap-custom-food-ids-after-restore (2026-10-25)

- **5.2-D1: Discarded custom food IDs in `recipe_ingredients` after restore** - RESOLVED. The backup now carries the backup-side catalog id (`source_catalog_id` column in the backup-only `myfoodtracker_custom_foods` table; `FORMAT_VERSION` unchanged at 1, additive and probe-compatible). On restore, `FoodCatalogDao.replaceCustomFoods` returns the old→new id mapping built during re-insertion, and `RecipeIngredientLinkRemapper.remapAfterRestore` consumes it **before the first recipe read**: affected `recipe_ingredients` rows are captured by primary key before any write (chain-safe — entry A→B can never re-catch rows a later B→C already wrote), mapped links are rewritten to the new ids, and links that no longer resolve in the rebuilt catalog (food deleted before the backup, or a legacy pre-6.2 backup without carried ids) are nulled explicitly (`catalog_food_id = NULL`) — never left dangling. Valid seeded/unchanged references are untouched. Guarded by JVM tests in `RecipeIngredientLinkRemapperTest` (8 cases incl. the chain regression guard) and `RecipeDaoTest.remapAndClearCatalogFoodLinks_roundTripsRemapAndClear` (androidTest, on-device execution per policy).
- **5.2-F4: A staged restore may be included in the platform auto-backup** - RESOLVED. `filesDir/pending_restore/` is excluded in `res/xml/backup_rules.xml` (`<full-backup-content>`, API < 31) and in `res/xml/data_extraction_rules.xml` inside **both** `<cloud-backup>` and `<device-transfer>` (API ≥ 31). The restore candidate and cached backup live in `cacheDir`, which the system never auto-backs-up, so no further exclusions are needed; `file_paths.xml`/the `FileProvider` scope is intentionally untouched (5.2 kept backups out of the provider on purpose).





