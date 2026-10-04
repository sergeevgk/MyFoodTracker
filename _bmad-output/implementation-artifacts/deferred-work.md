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

- **D-1: Lack of atomic database transaction across `insertMeal` and `insertFoods`** - `MealRepositoryImpl.logQuickAdd` performs two separate DAO operations sequentially without an atomic Room `@Transaction`. Pre-existing DAO design where inserting a meal and its foods are separate methods; wrapping in an atomic transaction method in `MealDao` should be addressed when DAO transactions are overhauled. [MealRepositoryImpl.kt:55]
- **D-2: Hardcoded light-theme colors and string literals without central resources** - `dialog_quick_add_log.xml` and `DashboardFragment.kt` use hardcoded hex values (`#FFFFFF`, `#1B4D3E`, `#2D3748`, `#718096`) and string literals without `@color/` or `@string/` resources. Continues pre-existing project-wide pattern tracked under Story 2.1 (D-2) and Story 2.2 (D-1/D-2). [dialog_quick_add_log.xml:6, DashboardFragment.kt:185]

## Deferred from: code review of 2-5-swipe-to-delete-intake-logs-with-undo (2026-10-02)

- **D-1: Lack of atomic database transaction across `insertMeal` and `insertFoods` during restore** - `MealRepositoryImpl.restoreMealEntry` executes `mealDao.insertMeal` and `mealDao.insertFoods` in sequence without a Room `@Transaction`. Continues the pre-existing architectural pattern tracked under Story 2.4 (D-1), to be addressed when DAO operations are unified under an atomic transaction helper. [MealRepositoryImpl.kt:93-118]

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
- **3.1-F2: Story 5.2 backup/restore must cover two databases** - Backup/restore currently assumes a single `meals_database`; it must later include the `food_catalog` file (at minimum the `is_custom = 1` rows, since the bundled asset itself is reinstallable).

## Deferred from: code review of 3-2-food-search-quantity-modal-and-meal-slot-logging (2026-10-04)

- **3.2-D1: Non-atomic composite meal logging (`insertMeal` and `insertFoods`)** - `MealRepositoryImpl.logFoodEntry` performs two separate DAO operations without an atomic Room `@Transaction`. Continues pre-existing DAO design debt 2.4-D1 / 2.5-D1, to be addressed when meal DAO operations are overhauled with unified transaction support. [MealRepositoryImpl.kt:80]
- **3.2-D2: Instrumented test coverage for `FoodCatalogDao` projection additions** - `FoodCatalogDao` added `base_serving_size` and `base_serving_unit` to the search projection. Integration verified via JVM unit tests; on-device instrumented test execution deferred per project policy forbidding automated emulator runs. Flagged for manual on-device verification. [FoodCatalogFtsTest.kt:50]


