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

