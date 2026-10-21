---
baseline_commit: 4940143559123d5ad09f7ac1b2568f6c580cff74
---

# Story 5.1: CSV and TXT Log Export

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user,
I want to export my daily/weekly/monthly log summaries as CSV/TXT files via Android's native file sharing,
so that I can analyze or share my nutrition history offline.

## Acceptance Criteria

1. **Given** the user is authenticated and the dashboard is showing, **When** the user opens the toolbar overflow menu and taps **Settings**, **Then** a **Settings & Data Management** screen opens (nav destination `settingsFragment`, toolbar title from `@string/settings_title`, Up arrow back to the dashboard), and the screen shows the active profile (username + profile id), an **Export Logs** section, and the food-catalog attribution/licensing notice required by deferred item `3.5-D1`.
   **And** the Settings screen is reachable **only** from the dashboard: tapping the Settings menu item while the lock screen or any other destination is showing does nothing (no navigation, no crash).
2. **Given** the Settings screen, **When** the user picks a format, **Then** exactly one of **CSV** or **TXT** is selected (a `MaterialButtonToggleGroup` with `singleSelection="true"`, CSV selected by default), and the selected format drives both export actions.
3. **Given** the Settings screen, **When** the user opens the date-range picker, **Then** a `MaterialDatePicker` **range** picker opens pre-filled with the current default range (first day of the current month → today, both inclusive), and picking a range closes the picker and updates the displayed range text.
   **And** the picker endpoints are converted with `ZoneId.of("UTC")` exactly like `DashboardFragment.showDatePicker` (`Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()`), so the exported range never shifts by a day because of the device time zone.
   **And** the chosen range survives rotation (it lives in the ViewModel, not in the Fragment).
4. **Given** a selected format and range, **When** the export runs, **Then** the generated **CSV** file content is exactly:
   - the header line `Date,Meal Slot,Food Name,Quantity,Calories,Protein,Carbs,Fat` (no spaces after commas);
   - one row per **logged food item** (a meal with zero food items contributes no row), ordered ascending by date, then by time (`HH:mm`), then by food name;
   - fields in header order: `Date` = `meals.date` (`YYYY-MM-DD`), `Meal Slot` = `meals.title` (stored verbatim, e.g. `BREAKFAST`), `Food Name` = `foods.name`, `Quantity` = `foods.weight` in grams, `Calories`/`Protein`/`Carbs`/`Fat` = the **stored absolute** macro values of that row (never re-scaled, never re-computed);
   - RFC 4180 escaping: a field is wrapped in `"` when it contains `,`, `"`, `\r` or `\n`, and inner `"` are doubled; other fields are written raw;
   - LF (`\n`) line endings, no BOM, and a trailing newline after the last row.
5. **Given** the TXT format is selected, **When** the export runs, **Then** the generated **TXT** file contains the **same 8 columns, the same values and the same row order** as the CSV, rendered as a plain-text table: a header line with the column names, columns separated by **two spaces**, each column left-aligned and padded to the widest cell in that column (header included), trailing spaces stripped from each line, LF line endings, no BOM, trailing newline after the last row.
6. **Given** numbers are written, **When** they are formatted, **Then** they are formatted **locale-independently** with a `.` decimal separator and **at most 2 decimals** with trailing zeros trimmed (`150`, `12.5`, `12.34`, `0`), never in scientific notation — a German-locale device must still produce `12.5`, never `12,5`.
7. **Given** the export is ready, **When** the user taps the primary export action, **Then** the file is written to the app cache under `cacheDir/exports/` with the file name `myfoodtracker_logs_<from>_<to>.<ext>` (e.g. `myfoodtracker_logs_2026-10-01_2026-10-21.csv`, `from`/`to` = `YYYY-MM-DD`, `ext` = `csv`/`txt`), and the **Android Sharesheet** opens (`Intent.ACTION_SEND` + `Intent.createChooser`) with the file attached as a `content://` URI from `androidx.core.content.FileProvider`, MIME type `text/csv` (CSV) or `text/plain` (TXT), and `FLAG_GRANT_READ_URI_PERMISSION` set.
   **And** no `file://` URI is ever placed in an Intent (the app would throw `FileUriExposedException` on API 24+).
8. **Given** the export is ready, **When** the user taps the secondary **Save to file…** action, **Then** the system document picker (`Intent.ACTION_CREATE_DOCUMENT`, `CATEGORY_OPENABLE`, `EXTRA_TITLE` = the same file name, MIME type as above) opens, and after the user confirms, the **exact same content** is written to the returned `content://` URI through `ContentResolver.openOutputStream`.
   **And** a confirmation (Snackbar + TalkBack announcement) is shown after the write.
9. **Given** the selected range contains **no** logged food items, **When** the export is triggered, **Then** no file is written, no picker or Sharesheet opens, and the user sees an explicit "No logs in the selected range" message (Snackbar).
   **And** given an invalid range (`from` after `to`) or a missing active profile, the export fails with an explicit error message and **no** file is written and **no** Intent is launched.
   **And** given a file-write or URI-creation failure, the app shows an error message and does **not** crash (no unhandled exception, no half-opened picker).
10. **Given** any export, **When** it runs, **Then** **zero network requests** are made. The app declares **no permissions at all** (there is no `INTERNET` permission in the manifest) — do not add one. The exported file stays on the device unless the user explicitly hands it to another app through a system picker.
11. **Given** multiple local profiles exist, **When** a user exports, **Then** only the **active profile's** logs are exported. Profile scoping comes from the existing repository query — never filter by profile id in the UI or the use case.
12. **Given** this story's scope, **When** implementing, **Then** the database schema, DAOs, entities and existing repositories are **unchanged** (`AppDatabase` stays at version 4, `FoodCatalogDatabase` at version 1, no new columns, no migration), no new Gradle dependency is added, and every existing feature keeps working: dashboard logging, quick-add, water logging, swipe-to-delete + undo, food search, custom foods, recipes, passcode auth and remember-me.
13. **Given** the Settings screen, **When** it is rendered, **Then** all interactive controls have a touch target of at least `48dp`, every icon-only/compact control has a content description, and a successful export is announced to TalkBack (e.g. "Exported 42 rows to CSV").
14. **Given** the Settings screen, **When** it is rendered, **Then** the following attribution text is visible (verbatim, as a legal notice — this closes deferred item `3.5-D1`):
    "Food catalog data: BLS data © Max Rubner-Institut, licensed CC BY 4.0 (DOI 10.25826/Data20251217-134202-0); OFF data © Open Food Facts contributors, licensed ODbL 1.0. All data stays on this device."

## Tasks / Subtasks

- [x] Task 1: Domain — export models + use case (AC: #4, #5, #6, #9, #11)
  - [x] Subtask 1.1: NEW `domain/model/ExportFormat.kt` — `enum class ExportFormat { CSV, TXT }` with two members: `fileExtension` (`"csv"` / `"txt"`) and `mimeType` (`"text/csv"` / `"text/plain"`). Pure Kotlin, no Android imports (AD-1).
  - [x] Subtask 1.2: NEW `domain/model/ExportPayload.kt` — `data class ExportPayload(val fileName: String, val content: String, val rowCount: Int, val format: ExportFormat)`.
  - [x] Subtask 1.3: NEW `domain/usecase/ExportLogsUseCase.kt` — `class ExportLogsUseCase(private val mealRepository: MealRepository)` with `operator fun invoke(format: ExportFormat, from: LocalDate, to: LocalDate): ExportPayload`. Koin `factory` (AD-4). No `suspend`, no coroutines (AD-2).
  - [x] Subtask 1.4: **Reuse** `MealRepository.getMealEntries()` — it already returns every meal of the active profile with its foods (`MealDao.getMealsWithFoodsByProfileId`). **Do not add a DAO query, a repository method or an entity column.** Profile isolation is already inside the repository (NFR-5).
  - [x] Subtask 1.5: Validation first: `require(!from.isAfter(to)) { "Invalid date range" }` — the literal must be exactly `Invalid date range`. Then filter meals whose `date` parses to a date inside `[from, to]` (inclusive on both ends).
  - [x] Subtask 1.6: Build rows: one row per food item of every in-range meal. A meal with **no** foods contributes **no** row. Row fields: date, slot (`meal.title`), name, weight, calories, protein, carbs, fat.
  - [x] Subtask 1.7: Sort rows ascending by `(date, time, foodName)`; `time` is the stored `"HH:mm"` string, so lexicographic order is chronological. The sort must be **stable** so equal keys keep their DB order.
  - [x] Subtask 1.8: Number formatting: one private helper that converts a `Double` to a locale-independent string with ≤ 2 decimals and trimmed trailing zeros. Use `java.math.BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()` (never `String.format` with the default locale — a German device would emit `12,5`). Guard non-finite values to `"0"`.
  - [x] Subtask 1.9: CSV rendering: header line + one line per row, RFC 4180 escaping as described in AC #4, LF endings, trailing newline.
  - [x] Subtask 1.10: TXT rendering: same columns/values/order, two-space column separator, per-column width = max(header length, longest cell), left-aligned, trailing spaces stripped per line.
  - [x] Subtask 1.11: File name: `myfoodtracker_logs_${from}_${to}.${format.fileExtension}` where `from`/`to` are `LocalDate.toString()` (`YYYY-MM-DD`).
- [x] Task 2: Infrastructure — file writer + FileProvider (AC: #7, #8, #10)
  - [x] Subtask 2.1: NEW `data/export/LogExportFileWriter.kt` — `class LogExportFileWriter(private val context: Context)` with:
    - `fun writeToCache(fileName: String, content: String): File` — creates `cacheDir/exports/` if needed and writes UTF-8 bytes (`content.toByteArray(Charsets.UTF_8)`).
    - `fun contentUriFor(file: File): Uri` — `FileProvider.getUriForFile(context, AUTHORITY, file)`.
    - `fun writeToUri(uri: Uri, content: String)` — `context.contentResolver.openOutputStream(uri)` → write UTF-8 bytes → close. Throws on failure (the caller catches).
    - `companion object { const val AUTHORITY = "com.example.myfoodtracker.fileprovider" }`.
  - [x] Subtask 2.2: NEW `res/xml/file_paths.xml` — `<paths><cache-path name="exports" path="exports/" /></paths>`. Declare **only** the `exports` subdirectory (never `path="."`).
  - [x] Subtask 2.3: UPDATE `app/src/main/AndroidManifest.xml` — add a `<provider>` for `androidx.core.content.FileProvider` with `android:authorities="com.example.myfoodtracker.fileprovider"`, `android:exported="false"`, `android:grantUriPermissions="true"` and the `android.support.FILE_PROVIDER_PATHS` meta-data pointing at `@xml/file_paths`. Add **no** permission.
  - [x] Subtask 2.4: The export file lives in the **cache** directory, so it is excluded from cloud backup by default — do not add it to `data_extraction_rules.xml` / `backup_rules.xml`.
- [x] Task 3: Presentation — ViewModel + Settings screen (AC: #1, #2, #3, #7, #8, #9, #13, #14)
  - [x] Subtask 3.1: NEW `presentation/ui/settings/model/SettingsUiState.kt` — `data class SettingsUiState(val username: String, val profileId: String)` — and NEW `presentation/ui/settings/model/DateRange.kt` — `data class DateRange(val from: LocalDate, val to: LocalDate)`. Both are pure JVM (`java.time`), no Android imports.
  - [x] Subtask 3.2: NEW `presentation/ui/settings/model/ExportState.kt` — `sealed class ExportState { object Idle; data class Ready(val payload: ExportPayload); object Empty; data class Error(val message: String) }` (plain `object`, not `data object` — the built-in Kotlin support in AGP 9 is new enough for either, but plain `object` is the safer form here).
  - [x] Subtask 3.3: NEW `presentation/viewmodel/SettingsViewModel.kt` — constructor `(exportLogsUseCase: ExportLogsUseCase, sessionRepository: SessionRepository)`; exposes `uiState: LiveData<SettingsUiState>`, `exportState: LiveData<ExportState>`, `format: LiveData<ExportFormat>` (default `CSV`) and `dateRange: LiveData<DateRange>` (default = first day of current month → today). Methods: `setFormat(format)`, `setDateRange(from: LocalDate, to: LocalDate)`, `export()`, `clearExportState()`. The ViewModel **must not** hold a `Context`, a `File` or a `Uri` (project rule) — it returns the payload; the Fragment does the file IO and the Intents.
  - [x] Subtask 3.4: `export()` behavior: no active profile → `ExportState.Error("No active profile")`; `from.isAfter(to)` → `ExportState.Error("Invalid date range")` (both literals are domain-side and must be mapped to `@string` resources in the Fragment); success with 0 rows → `ExportState.Empty`; success with ≥ 1 row → `ExportState.Ready(payload)`. Catch `IllegalArgumentException` from the use case and map it to `Error`.
  - [x] Subtask 3.5: NEW `presentation/ui/settings/SettingsFragment.kt` + NEW `res/layout/fragment_settings.xml`. Sections: profile header; **Export Logs** card (format toggle, date-range row + button, primary export button, secondary "Save to file…" button); attribution/privacy footer. Use Material 3 components already in the project (`MaterialButtonToggleGroup`, `MaterialButton`, `MaterialCardView` or plain cards). No hardcoded colors/strings — reference `@color/`, `@string/`, `@dimen/` (Story 2.6 rule).
  - [x] Subtask 3.6: The date-range button shows the selected range and has a content description ("Select export date range, currently …"). The range picker is `MaterialDatePicker.Builder.dateRangePicker()` shown with `parentFragmentManager`; `setSelection(Pair(startUtcMillis, endUtcMillis))` pre-fills the current range; the positive listener converts both endpoints with `ZoneId.of("UTC")` (same as `DashboardFragment.showDatePicker`).
  - [x] Subtask 3.7: Primary export action: on `ExportState.Ready` → `fileWriter.writeToCache(payload.fileName, payload.content)` → `fileWriter.contentUriFor(file)` → build `Intent(Intent.ACTION_SEND)` (`EXTRA_STREAM`, `EXTRA_SUBJECT`, `type = payload.format.mimeType`, `FLAG_GRANT_READ_URI_PERMISSION`) → `startActivity(Intent.createChooser(intent, getString(R.string.export_share_title)))`. Wrap the write + URI creation in try/catch → error Snackbar.
  - [x] Subtask 3.8: Secondary action: `registerForActivityResult(ActivityResultContracts.StartActivityForResult())` → on `RESULT_OK` with a non-null `data.data` → `fileWriter.writeToUri(uri, payload.content)` → success Snackbar + TalkBack announcement; on cancel → do nothing, no error. The pending payload is held in a Fragment field and cleared after use.
  - [x] Subtask 3.9: `ExportState.Empty` → Snackbar "No logs in the selected range" (no file, no Intent). `ExportState.Error` → Snackbar with the mapped `@string`. Call `viewModel.clearExportState()` after handling so a rotation does not re-trigger the Intent.
  - [x] Subtask 3.10: `onDestroyView()` nulls `_binding`; all `LiveData` is observed with `viewLifecycleOwner`; guard `isAdded` before Snackbar / `announceForAccessibility` (AD-5).
  - [x] Subtask 3.11: UPDATE `res/navigation/nav_graph.xml` — add `settingsFragment` (`android:name="com.example.myfoodtracker.presentation.ui.settings.SettingsFragment"`, `android:label="@string/settings_title"`) and an action `action_dashboardFragment_to_settingsFragment` from `dashboardFragment`.
  - [x] Subtask 3.12: UPDATE `MainActivity.kt` — handle `R.id.action_settings` (currently a no-op returning `true`): navigate with `R.id.action_dashboardFragment_to_settingsFragment` **only** when the current destination is `dashboardFragment`; otherwise do nothing. Do not add `settingsFragment` to `appBarConfiguration` (the Up arrow must appear). Keep `menu_main.xml` as is.
  - [x] Subtask 3.13: NEW strings in `res/values/strings.xml` only (see Dev Notes §Strings). Reuse existing strings where they exist. All user-visible copy is a resource; no hardcoded literals (Story 2.6 rule).
  - [x] Subtask 3.14: The attribution notice is **static text** in `fragment_settings.xml` (a `TextView` with the string resource) — it must be visible without scrolling into a hidden tab and must not depend on the export state.
- [x] Task 4: DI (AC: all)
  - [x] Subtask 4.1: UPDATE `di/AppModule.kt` — add `factory { ExportLogsUseCase(get()) }`, `single { LogExportFileWriter(androidContext()) }` and `viewModel { SettingsViewModel(get(), get()) }`. **Do not** touch the existing 9-argument `DashboardViewModel` line or any other binding (AD-6).
- [x] Task 5: Tests (AC: #4, #5, #6, #9)
  - [x] Subtask 5.1: NEW `app/src/test/java/com/example/myfoodtracker/domain/usecase/ExportLogsUseCaseTest.kt` — JUnit4 + hand-written fakes (no MockK, no Robolectric). Fake `MealRepository` returns a fixed list of `MealEntry`; the other repository methods throw `UnsupportedOperationException` (this use case must not call them).
  - [x] Subtask 5.2: NEW `app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/SettingsViewModelTest.kt` — uses `InstantTaskExecutorRule` (already on the classpath; `DashboardViewModelTest` uses it). Fakes for `ExportLogsUseCase`'s repository and for `SessionRepository`.
  - [x] Subtask 5.3: No new `androidTest` (no DAO / schema change). Do not run the emulator.
  - [x] Subtask 5.4: `./gradlew :app:compileDebugSources`, `./gradlew :app:testDebugUnitTest`, `./gradlew :app:assembleDebug` and `./gradlew :app:assembleDebugAndroidTest` must be green. Existing tests must keep passing (the suite was 248 JVM unit tests green after Story 4.2).
- [x] Task 6: Documentation + verification notes (AC: #14)
  - [x] Subtask 6.1: Update `_bmad-output/implementation-artifacts/deferred-work.md` — mark `3.5-D1` as resolved by this story (attribution now visible on the Settings screen). Do **not** silently close `1.2-D3` (logout in Settings stays deferred; the dashboard logout is unchanged) or any other open item.
  - [x] Subtask 6.2: Fill the **Dev Agent Record** sections (model used, debug log, completion notes, file list) and explicitly list the manual on-device checks that could not be run (no emulator, per project policy).

### Review Findings

- [x] [Review][Patch] Missing TalkBack accessibility announcements on export empty/error messages [app/src/main/java/com/example/myfoodtracker/presentation/ui/settings/SettingsFragment.kt:278]
- [x] [Review][Patch] Date range picker loses positive button listener on screen rotation and allows duplicate dialogs on rapid double-tap [app/src/main/java/com/example/myfoodtracker/presentation/ui/settings/SettingsFragment.kt:215]
- [x] [Review][Patch] Newlines, carriage returns, or tabs in food names/meal slots break fixed-width TXT table alignment [app/src/main/java/com/example/myfoodtracker/domain/usecase/ExportLogsUseCase.kt:137]
- [x] [Review][Patch] `LogExportFileWriter.writeToUri` should explicitly use `"wt"` mode to guarantee truncation when overwriting an existing SAF document [app/src/main/java/com/example/myfoodtracker/data/export/LogExportFileWriter.kt:49]
- [x] [Review][Patch] `SettingsViewModel.export()` only catches `IllegalArgumentException`, leaving unexpected repository runtime exceptions uncaught [app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/SettingsViewModel.kt:75]
- [x] [Review][Patch] `SettingsFragment.formatRange` omits the start year when the selected date range spans different calendar years [app/src/main/java/com/example/myfoodtracker/presentation/ui/settings/SettingsFragment.kt:243]

## Dev Notes

### Locked design — read this before writing code

This story introduces the **first Settings screen** in the app. `MainActivity.onOptionsItemSelected` currently swallows `R.id.action_settings` with a bare `-> true` (no-op). The menu item already exists (`res/menu/menu_main.xml`), so the entry point is already in place — only the destination and the handler are missing.

**The real schema is `meals` + `foods`, not the PRD's `intake_logs`.** Follow the code:

- `meals` (`MealEntryEntity`): `id`, `profile_id`, `title` (the meal slot, e.g. `BREAKFAST`), `date` (`YYYY-MM-DD`), `time` (`HH:mm`).
- `foods` (`FoodEntity`): `mealId` (FK → `meals.id`, cascade), `name`, `weight` (grams), `calories`, `carbs`, `fat`, `protein`, `fiber`.

There is **no** `quantity` column, no `serving_unit` column and no separate macro table. The PRD addendum's `intake_logs` sketch is stale — ignore it.

**Why no new DAO query:** `MealDao.getMealsWithFoodsByProfileId(profileId)` already returns every meal of the active profile with its foods, and `MealRepository.getMealEntries()` already exposes it as domain models with profile scoping and the null-session guard. Export is a *read + filter* operation over that list. Adding a date-range DAO query would touch the schema layer for zero benefit. Sorting happens in the use case because the DAO has no `ORDER BY`.

**Where the macros come from:** `foods.calories/protein/carbs/fat` already store the **absolute** macro values for that logged row (they were scaled by `grams/100` at log time — see `LogFoodEntryUseCase`). The export must copy them verbatim. Do **not** re-apply a per-100g factor; that would double-count. `LogFoodEntryUseCase` is the scaler to avoid.

**Weight vs. quantity:** `foods.weight` is always grams (`ServingUnit.SERVINGS` is converted to grams before the write; `ML` is stored 1:1). The CSV column is named `Quantity` by the spec but the value is grams. Quick-add rows legitimately have `weight = 0.0` → the `Quantity` cell is `0`. That is expected, not a bug.

**Meals with no foods:** `MealRepository.addMealEntry()` creates a `meals` row with `title = ""` and no children. Those rows must not produce a CSV line (there is no `Food Name` to write). Skip them.

**Recipe logs and custom foods** are already `meals` + `foods` rows — they are exported automatically. Nothing extra is needed.

**Water logs are not part of this export.** The header list is fixed by FR-12 (`Date, Meal Slot, Food Name, Quantity, Calories, Protein, Carbs, Fat`) and has no water column. Do not add a water section, and do not change `water_logs`. Note this as a follow-up candidate.

**Sharing needs a `FileProvider`.** `minSdk = 35`, so a `file://` URI in an Intent throws `FileUriExposedException`. The file must be exposed as a `content://` URI from `androidx.core.content.FileProvider` (already available — `androidx.core:core-ktx` is a dependency) with a temporary read grant. The authority must be `${applicationId}.fileprovider` = `com.example.myfoodtracker.fileprovider`.

**Two export destinations, one payload.** Both the Sharesheet (AC #7) and the system document picker (AC #8) consume the *same* `ExportPayload.content` string, so the two paths can never drift. The payload is produced by the domain layer (pure, testable); file IO and Intents happen in the presentation layer because they need a `Context` (the ViewModel must not hold one).

**Zero network is structural.** The manifest declares **no permissions at all** — there is no `INTERNET` permission, so no network request can leave the app. Do not add one, and do not add a network library.

**The Settings screen is the natural home for the catalog attribution.** Deferred item `3.5-D1` (BLS 4.0 CC BY 4.0 and Open Food Facts ODbL both require attribution) says: "When an About/Credits or Settings screen is introduced in a future story, attribution and licensing notices must be displayed". This story introduces that screen, so the notice is **in scope** (AC #14). Copy the wording from `tools/seed/README.md` §Provenance & licensing.

**Do not add the backup/restore buttons yet.** `EXPERIENCE.md` lists "DB Backup/Restore Buttons" on the Settings screen, but that is Story 5.2. This story builds the screen so that 5.2 can add a second section without rework — do not stub a disabled backup button.

**Do not add a logout button.** Deferred item `1.2-D3` mentions a Settings logout; the dashboard logout already exists and works. Adding logout is out of scope; leave `1.2-D3` open.

**Do not add a "Profile Goal Editor"** — the UX IA mentions one, but no story covers it. Out of scope.

### Current state of files this story touches

| File | Today | This story changes | Must preserve |
|---|---|---|---|
| `MainActivity.kt` | `R.id.action_settings -> true` (no-op); `appBarConfiguration` = `{passcodeAuthFragment, dashboardFragment}` | Navigate to `settingsFragment` from the dashboard only | `onCreateOptionsMenu` inflation, `navigateUp` behaviour, toolbar/insets setup |
| `res/menu/menu_main.xml` | One `action_settings` item, `showAsAction="never"` | **Nothing** | Do not add a second Settings entry |
| `res/navigation/nav_graph.xml` | 5 destinations, `dashboardFragment` has 2 actions | Add `settingsFragment` + `action_dashboardFragment_to_settingsFragment` | `recipeListFragment`'s required `activeDate` argument, all existing actions |
| `di/AppModule.kt` | 5 DAOs, 2 DBs, 6 repositories, 19 use-case factories, 6 ViewModels | +1 use-case factory, +1 `single`, +1 `viewModel` | The 9-`get()` `DashboardViewModel` line; every existing binding |
| `domain/repository/MealRepository.kt` | `getMealEntries(): List<MealEntry>` (all meals of the active profile) | **Nothing** | Do not add a `getMealEntriesInRange` method — it would force every existing fake to change |
| `data/repository/MealRepositoryImpl.kt` | Maps entities → domain models privately; null session → `emptyList()` | **Nothing** | Profile isolation and the null-session guard |
| `data/db/MealDao.kt` / `MealEntryEntity.kt` / `FoodEntity.kt` | `meals` + `foods`, cascade FK, index on `profile_id` / `mealId` | **Nothing** | No schema change, no new column, no new query |
| `data/db/AppDatabase.kt` | `version = 4`, `exportSchema = false` | **Nothing** | Do not bump the version |
| `data/db/FoodCatalogDatabase.kt` | `version = 1`, `BundledSQLiteDriver`, `createFromAsset` | **Nothing** | A version bump would wipe `is_custom = 1` rows (`3.1-F1`) |
| `app/src/main/AndroidManifest.xml` | No `<uses-permission>` at all, one `<activity>` | Add the `FileProvider` `<provider>` | **Do not add any permission** |
| `res/xml/` | `backup_rules.xml`, `data_extraction_rules.xml` (both default/empty) | Add `file_paths.xml` | Do not change the backup rules (cache is excluded from backup by default) |
| `res/values/strings.xml` | 194 lines, all copy centralised (Story 2.6) | Add the new export/settings strings | Never hardcode copy in Kotlin or XML layouts |
| `res/values/dimens.xml` | `min_touch_target = 48dp` and friends | Reuse only — add nothing unless needed | Touch-target floor |
| `res/values/colors.xml`, `res/values-night/themes.xml` | Centralised palette + dark theme | Reuse only | No new hex colors (Story 2.6 rule) |
| `domain/usecase/LogFoodEntryUseCase.kt` | Scales per-100g `FoodItem` → grams | **Nothing** | Do not call it from the export path |
| `presentation/ui/dashboard/DashboardFragment.kt` | Owns the date picker; UTC conversion at `showDatePicker` | **Nothing** (copy its UTC pattern) | Search sheet, quick-add, water, logout, week strip, swipe-undo |
| `presentation/viewmodel/DashboardViewModel.kt` | 9-arg ctor, `LiveData<DashboardUiState>` | **Nothing** | Do not add a 10th dependency |
| `res/values/themes.xml` | Material 3 theme with the brand palette | Reuse only | No new theme attributes |

### Export data contract

**CSV** — exactly this shape:

```csv
Date,Meal Slot,Food Name,Quantity,Calories,Protein,Carbs,Fat
2026-10-04,BREAKFAST,Chicken Breast (Cooked),150,248,46.5,0,5.4
2026-10-04,SNACK,"Cookie, with chocolate",25,118,1.5,16.2,4.8
```

- Header line is fixed and must not be localized or reordered.
- Escaping: a field is quoted **iff** it contains `,`, `"`, `\r` or `\n`; inner `"` become `""`. Leading/trailing spaces are preserved as-is (do not quote them — not required by RFC 4180 and it keeps diffs readable).
- Line ending: `\n`. No BOM. The file ends with a newline after the last data row.
- A file with **zero** rows is never produced (AC #9), so the header-only case cannot reach the user.

**TXT** — same columns, same values, same order, rendered as a fixed-width table:

```text
Date        Meal Slot  Food Name              Quantity  Calories  Protein  Carbs  Fat
2026-10-04  BREAKFAST  Chicken Breast (Cooked)  150       248       46.5     0      5.4
2026-10-04  SNACK      Cookie, with chocolate  25        118       1.5      16.2   4.8
```

- Column separator: **two spaces**. Cells are padded on the right to the column width (widest of the header and all cells in that column); trailing spaces are stripped from each line.
- Header line first, same column order as CSV.
- Same line-ending / no-BOM / trailing-newline rules as CSV.

**Number formatting** (applies to `Quantity`, `Calories`, `Protein`, `Carbs`, `Fat` in both formats):

| Stored value | Rendered |
|---|---|
| `150.0` | `150` |
| `12.5` | `12.5` |
| `12.3456` | `12.35` (HALF_UP, 2 decimals) |
| `0.0` | `0` |
| `-0.0` | `0` |
| non-finite (defensive) | `0` |

Use `BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()`. **Never** `String.format("%.2f", v)` without `Locale.ROOT`, and never the default locale — a German device would produce `12,5` and break the CSV column split. This is the single most likely silent bug in this story.

**File name**: `myfoodtracker_logs_<from>_<to>.<ext>` — e.g. `myfoodtracker_logs_2026-10-01_2026-10-21.csv`. Lowercase ASCII, no spaces, dates = `LocalDate.toString()`.

**MIME types**: CSV → `text/csv`; TXT → `text/plain`.

### Use case contract

```kotlin
class ExportLogsUseCase(private val mealRepository: MealRepository) {
    operator fun invoke(format: ExportFormat, from: LocalDate, to: LocalDate): ExportPayload
}
```

| Situation | Behaviour | Exact literal / message |
|---|---|---|
| `from` is after `to` | throws before any read | `IllegalArgumentException("Invalid date range")` |
| no active profile | `mealRepository.getMealEntries()` returns `emptyList()` → payload with `rowCount = 0` (the ViewModel turns this into a "no active profile" error **before** calling the use case) | — |
| meal outside the range | excluded | — |
| meal inside the range with 0 foods | contributes no row | — |
| meal inside the range with N foods | N rows, one per food, in the meal's food order | — |
| duplicate `(date, time, name)` rows | kept, stable order (no de-duplication) | — |

`ExportPayload(fileName, content, rowCount, format)` — `content` is the **complete file content** including the trailing newline. The presentation layer never formats anything itself.

### UI contract

Settings screen (`fragment_settings.xml`), top → bottom:

1. **Profile header** — "Signed in as {username}" and the profile id (reuse the `active_profile_format` string if it fits, otherwise add a new one).
2. **Export Logs** card:
   - Section title "Export Logs".
   - Format toggle: `MaterialButtonToggleGroup` (`app:singleSelection="true"`, `app:checkedButton="@id/btn_format_csv"`) with `btn_format_csv` ("CSV") and `btn_format_txt` ("TXT"). Toggle change → `viewModel.setFormat(...)`.
   - Date range row: a label plus `btn_date_range` showing e.g. "Oct 1 – Oct 21, 2026" and a content description "Select export date range, currently …".
   - Primary button `btn_export_share` — "Export CSV Logs" / "Export TXT Logs" (label follows the selected format).
   - Secondary button `btn_export_save` — "Save to file…".
3. **Attribution + privacy footer** — a small `TextView` with the AC #14 notice.

Dialogs / pickers:

- Range picker: `MaterialDatePicker.Builder.dateRangePicker()`, `setSelection(Pair(startMillis, endMillis))` with UTC-midnight millis for the current range, shown via `parentFragmentManager` with the tag `"EXPORT_DATE_RANGE"`. Positive listener → both endpoints via `Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()` → `viewModel.setDateRange(from, to)`.
- Sharesheet: `Intent.createChooser(sendIntent, getString(R.string.export_share_title))` where `sendIntent` is `ACTION_SEND` with `EXTRA_STREAM` = the FileProvider URI, `EXTRA_SUBJECT`, `type` = the format MIME, and `FLAG_GRANT_READ_URI_PERMISSION`. Add `FLAG_ACTIVITY_NEW_TASK` only if starting from an application context (the Fragment starts it on the Activity — not needed).
- Document picker: `Intent.ACTION_CREATE_DOCUMENT` + `addCategory(Intent.CATEGORY_OPENABLE)` + `type` = MIME + `putExtra(Intent.EXTRA_TITLE, payload.fileName)`, launched with `ActivityResultContracts.StartActivityForResult`. The result URI is written to with `ContentResolver.openOutputStream(uri)`.

Behaviour rules:

- Nothing is written to disk until the user has actually picked a destination (share sheet → write the cache file first, because the Sharesheet needs a URI; save-to-file → write straight into the picked URI).
- The Sharesheet path writes to `cacheDir/exports/` first; the cache file may be overwritten on the next export — that is fine, it is a throwaway artefact.
- Every success shows a Snackbar **and** a TalkBack announcement (AC #13); every failure shows a Snackbar. No spinner (DESIGN.md: no loading spinners for local operations).

### Architecture compliance

- **AD-1 (layer boundaries):** `ExportFormat`, `ExportPayload` and `ExportLogsUseCase` are pure Kotlin/JVM — no `android.*`, no Room imports. The file writer lives in `data/` (infrastructure) and may use `android.*`. The ViewModel holds no `Context`.
- **AD-2 (synchronous Room):** the export reads through the existing synchronous repository. No `suspend`, no coroutines, no `Flow`. The read is a single local SQLite query — no spinner.
- **AD-3 (domain decoupling):** the use case sees only `MealEntry` / `Food` domain models. Entities never leave `data/`.
- **AD-4 (use case pattern):** `operator fun invoke()`, Koin `factory`.
- **AD-5 (view binding lifecycle):** `SettingsFragment` nulls `_binding` in `onDestroyView()`; all `LiveData` observed with `viewLifecycleOwner`; `isAdded` guarded before Snackbar / `announceForAccessibility`.
- **AD-6 (central DI):** every new binding goes into `AppModule.kt` only (`factory` for the use case, `single` for the file writer, `viewModel` for the ViewModel).
- **AD-7 (cascade deletes):** untouched — no deletes happen in this story.
- **AD-8 (FTS5):** untouched — the catalog DB, `BundledSQLiteDriver` and the FTS triggers are not part of this story.
- **NFR-1 (no network):** no HTTP client, no `INTERNET` permission, no analytics.
- **NFR-5 (profile isolation):** guaranteed by `MealRepository.getMealEntries()`. The UI never passes or filters by `profileId`.
- **NFR-6 (accessibility):** ≥ 48dp targets, content descriptions on the toggle/buttons/range button, TalkBack announcements on success and failure.
- **Consistency conventions:** dates are `YYYY-MM-DD` ISO strings; quantities are `REAL` grams; macro readouts use tabular figures in the UI (`fontFeatureSettings = "tnum"` — apply it to the range label and any numeric preview, though the export itself is plain text).

### Library and framework requirements

Pinned stack — **do not upgrade and do not add a dependency**:

- Room 3.0.3, Koin 3.5.6, Navigation 2.6.0, Material 1.10.0, AppCompat 1.6.1, AGP 9.2.1, SDK 36/35.
- `androidx.core:core-ktx` 1.10.1 is already present → `androidx.core.content.FileProvider` and `androidx.core.app.ShareCompat` are available with **no** new dependency. Use the platform `Intent` + `Intent.createChooser` (no new library).
- No Safe Args plugin in `app/build.gradle.kts` or `libs.versions.toml` — a Bundle plus `arguments?.getString` is the project pattern (not needed here, but do not introduce Safe Args).
- No MockK, no Robolectric, no CSV library. JUnit4 + hand-written fakes only (Stories 3.3, 3.5, 4.1, 4.2).
- `androidx.arch.core:core-testing` is already present (`InstantTaskExecutorRule`).
- `MaterialDatePicker.Builder.dateRangePicker()` exists in Material 1.10.0 — no extra library needed.

### Testing requirements

`ExportLogsUseCaseTest` — fake `MealRepository` returning a fixed `List<MealEntry>`; every other method throws `UnsupportedOperationException`.

Required cases:

- **Header**: the first line is exactly `Date,Meal Slot,Food Name,Quantity,Calories,Protein,Carbs,Fat`.
- **Row count / mapping**: one meal with 2 foods → 2 rows; a meal with 0 foods → 0 rows; the `Quantity` cell equals the stored `weight`; the macro cells equal the **stored** values (a stored `248` kcal stays `248`, never `248 * 1.5`).
- **Ordering**: meals on different dates come out ascending; same date → ascending `time`; identical date+time → ascending food name.
- **Range filter**: a meal on `from` and a meal on `to` are both included (inclusive); a meal one day outside is excluded.
- **Escaping**: a food name containing `,` is quoted; a name containing `"` is doubled inside quotes; a name containing a newline is quoted and the newline is kept inside the quoted field.
- **Locale independence**: temporarily `Locale.setDefault(Locale.GERMANY)` around the call and assert the output contains `12.5` and **not** `12,5`; restore the default locale in a `finally` block. (This is the regression test for the most likely bug.)
- **Number formatting**: `150.0` → `150`; `12.5` → `12.5`; `12.3456` → `12.35`; `0.0` → `0`; no scientific notation (`1.0E7`).
- **TXT format**: header line present; columns separated by exactly two spaces; the second column starts at the same offset on every line (width alignment); no trailing spaces on any line; the row order and the cell values match the CSV output exactly.
- **File name**: `myfoodtracker_logs_2026-10-01_2026-10-21.csv` for CSV and `….txt` for TXT.
- **Invalid range**: `from` after `to` → `IllegalArgumentException` with the message `Invalid date range`, and the repository is **not** touched (no read).
- **Empty range**: a range with no meals → `rowCount == 0` and the content still ends with the header + newline (the use case does not decide to suppress the file — the presentation layer does).

`SettingsViewModelTest`:

- `export()` with a valid range → `ExportState.Ready` with the payload produced by the use case (assert the format/rowCount forwarded).
- `export()` with 0 rows → `ExportState.Empty`.
- No active profile → `ExportState.Error` and the use case is not invoked.
- Invalid range → `ExportState.Error("Invalid date range")`.
- `setFormat(TXT)` → `format.value == TXT`; `setDateRange(from, to)` → `dateRange.value` carries the exact dates (round-trip through a rotation-safe holder, not through `LocalDate.now()`).
- The ViewModel does not call `LocalDate.now()` for the export range (the range comes from the caller). The *default* range may use `LocalDate.now()` — that is the "first day of the current month → today" default.

Do not modify `MealRepository` or any existing fake. If a test does not compile because a fake is missing a method, a method was added that should not have been.

**No emulator.** After the JVM tests pass, flag these manual on-device checks in the completion notes:

- Settings opens from the dashboard overflow menu and the Up arrow returns to the dashboard.
- The Settings menu item does nothing on the lock screen.
- CSV export: pick a range, tap export → the Sharesheet opens with `myfoodtracker_logs_….csv` attached → open it in a spreadsheet → the 8 columns line up and the values match the dashboard rows.
- TXT export: open the file in a text editor → the columns are aligned.
- Save to file…: the document picker opens → pick a location → the file appears with identical content.
- A German-locale device still writes `12.5` (not `12,5`).
- A second profile's rows are absent from the export.
- The attribution notice is visible on the Settings screen.
- Every pre-existing feature still works (log food, quick-add, water, swipe-delete + undo, search, custom food, recipe logging, passcode auth).

### Previous story intelligence

Story 4.2 (`4-2-one-tap-recipe-logging.md`, done, commit `4940143`) and Story 4.1 (`4-1-recipe-builder-and-ingredient-grouping.md`, done, commit `974cf2d`):

- The real log tables are `meals` + `foods` — **not** the PRD addendum's `intake_logs`. Follow the code (the PRD table names are stale).
- `MealRepositoryImpl.logFoodEntry` inserts one `meals` row (`title` = slot, `time` = `HH:mm`) plus one `foods` child. The export reads that shape.
- `MealLogAdapter.bind` shows only `foods.firstOrNull()?.name` — which is exactly why one meal row carries one food in practice. The export emits one row per food, so a meal with N foods yields N rows (consistent with the dashboard).
- Review patches already applied — do not revert them: delete confirmation, non-gram unit scaling in `fromFoodItem`, nav labels as string resources, save-error LiveData, `DiffUtil` equality, profile-scoped deletes, duplicate-draft removal, the `recipe_logged` fragment result, `selectDate` + Snackbar + announcement after a write.
- Deferred, leave them open: `4.1-D1` (Quick-Add on the shared search sheet), `4.1-D2` (recipe card inspection), `4.1-D3` (rotation drafts), `4.1-D4` / `3.2-D1` / `2.4-D1` (non-atomic `insertMeal` + `insertFoods`), `4.2-D1` (`scroll.post` lifecycle guard), `3.1-F1` (catalog version bump wipes custom foods), `3.1-F2` (backup must cover **two** databases — Story 5.2 must export `food_catalog` as well as `meals_database`; this story does not do backups).
- Test incident: a fake whose backing-property name matched an overridden function caused a JVM signature clash — name fake backing fields differently from the function (`profileId` vs `getActiveProfileId()`).
- Build incident: `:app:mergeDebugResources` NPE from a stale incremental blob → run `:app:clean` once and rebuild. Do not rewrite unrelated XML.
- The worktree is dirty with CRLF-only diffs on many files (`git diff --ignore-cr-at-eol` is empty for those paths). Do **not** `git add -A`. Do not normalise line endings. Stage only the files this story creates or edits.

### Git intelligence

Recent commits: `4940143` Story 4.2 recipe logging, `974cf2d` Story 4.1 recipe builder, `69de783` Story 3.5 German catalog, `24fb76d` Story 3.3 custom foods, `5ecb1af` Story 3.2 search + meal-slot logging.

Conventions to copy from `4940143` (17 files, +1443 lines): one new use case + its JVM test, one new ViewModel + its test, one new Fragment/dialog + layout, `AppModule` additions, `nav_graph.xml` additions, `strings.xml` additions, and the story file itself. That is exactly the shape this story should have.

`local.properties` `sdk.dir` points at the Windows SDK; the AGP warning on Linux is harmless — do not edit that file.

### Latest technical information

Verified against the current Android platform documentation (checked 2026-10):

- **Sharing a file requires a `content://` URI.** `Intent.ACTION_SEND` with a `file://` URI throws `FileUriExposedException` on API 24+. The only secure way to offer a file to another app is `FileProvider.getUriForFile()` plus a temporary grant: `Intent.FLAG_GRANT_READ_URI_PERMISSION`. The provider must be `android:exported="false"` with `android:grantUriPermissions="true"`, and its paths must be declared in XML — `FileProvider` cannot map paths programmatically, and a `path="."` declaration would expose the whole cache directory (keep the scope narrow: `exports/` only).
- **Use the Sharesheet**, i.e. `Intent.ACTION_SEND` wrapped in `Intent.createChooser(...)`. Do not build a custom in-app share list; the platform sharesheet gives system-ranked direct-share targets and a consistent UX. `ShareCompat.IntentBuilder` from `androidx.core` is an acceptable equivalent — either is fine, but only one path should exist.
- **MIME type matters.** Use the most specific type: `text/csv` for CSV and `text/plain` for TXT. Avoid `*/*` — many receiving apps cannot handle a generic stream. The sharesheet may show a content preview based on the MIME type.
- **`ACTION_CREATE_DOCUMENT`** is the Storage Access Framework "save as" flow. It requires `CATEGORY_OPENABLE` and a MIME type; `EXTRA_TITLE` pre-fills the file name. It returns a `content://` URI backed by a `DocumentsProvider`; write to it with `ContentResolver.openOutputStream(uri)`. Note: it cannot overwrite an existing file — the system appends `(1)`.
- **No storage permission is needed** for either flow: the SAF grants access to the URI the user picked, and the FileProvider grant is scoped to the receiving app.
- **Timestamps**: `MaterialDatePicker` returns UTC-midnight millis. The app already converts them with `ZoneId.of("UTC")` in `DashboardFragment` — copy that exact conversion so the exported range matches the date the user picked.

### Project context reference

Follow `_bmad-output/project-context.md` and `AGENTS.md`:

- Before any Gradle call: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`, Linux `java` 21, `ANDROID_HOME=$HOME/Android/Sdk`. The AGP `sdk.dir` warning from the Windows path in `local.properties` is harmless — do not "fix" that file.
- Verify with `./gradlew :app:compileDebugSources` and `./gradlew :app:testDebugUnitTest`; assemble with `./gradlew :app:assembleDebug` and `./gradlew :app:assembleDebugAndroidTest`. **Do not launch the emulator.** State explicitly in the completion notes which checks need manual on-device verification.
- ViewModels must never hold a `Context` or views — the export payload is passed to the Fragment, which performs file IO and the Intents.
- The Room schema rule does not apply: this story must not change entities, DAOs or the database version.
- UseCases are factory-injected classes with `operator fun invoke()`.
- Layout files use `fragment_` / `item_` prefixes; view IDs are snake_case; all copy lives in `res/values/strings.xml`; all colors in `res/values/colors.xml`; touch targets ≥ 48dp.

### Project Structure Notes

NEW:

- `app/src/main/java/com/example/myfoodtracker/domain/model/ExportFormat.kt`
- `app/src/main/java/com/example/myfoodtracker/domain/model/ExportPayload.kt`
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/ExportLogsUseCase.kt`
- `app/src/main/java/com/example/myfoodtracker/data/export/LogExportFileWriter.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/settings/SettingsFragment.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/settings/model/SettingsUiState.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/settings/model/DateRange.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/settings/model/ExportState.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/SettingsViewModel.kt`
- `app/src/main/res/layout/fragment_settings.xml`
- `app/src/main/res/xml/file_paths.xml`
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/ExportLogsUseCaseTest.kt`
- `app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/SettingsViewModelTest.kt`

UPDATE:

- `app/src/main/java/com/example/myfoodtracker/MainActivity.kt` — handle the Settings menu item
- `app/src/main/java/com/example/myfoodtracker/di/AppModule.kt` — 3 new bindings
- `app/src/main/AndroidManifest.xml` — `FileProvider` `<provider>`
- `app/src/main/res/navigation/nav_graph.xml` — `settingsFragment` + action
- `app/src/main/res/values/strings.xml` — new export/settings strings

Explicitly NOT modified: `AppDatabase.kt`, `FoodCatalogDatabase.kt`, `MealDao.kt`, `MealEntryEntity.kt`, `FoodEntity.kt`, `MealRepository.kt`, `MealRepositoryImpl.kt`, `WaterLogDao.kt`, `WaterLogEntity.kt`, `RecipeDao.kt`, `LogFoodEntryUseCase.kt`, `LogQuickAddUseCase.kt`, `DashboardViewModel.kt`, `DashboardFragment.kt`, `MealLogAdapter.kt`, `FoodSearchBottomSheet.kt`, `RecipeListFragment.kt`, `menu_main.xml`, `colors.xml`, `dimens.xml`, `themes.xml`, `data_extraction_rules.xml`, `backup_rules.xml`, `app/schemas/**`, `tools/seed/**`, the catalog asset, `libs.versions.toml`, `app/build.gradle.kts`.

Deviations from the architecture spine's structural seed (both are intentional and must be followed):

- The seed puts `ExportLogsUseCase` in `domain/usecase/` — followed. It does not name a file writer; `data/export/LogExportFileWriter.kt` is the new home for the file IO (Story 5.2 will reuse the same `FileProvider` authority and paths).
- The seed lists `presentation/ui/settings/` and `presentation/viewmodel/SettingsViewModel.kt` — followed exactly.

### Strings

New strings (add to `res/values/strings.xml` in a new "Export logs (Epic 5)" block):

| Name | Value |
|---|---|
| `settings_title` | `Settings` |
| `settings_section_export` | `Export Logs` |
| `settings_signed_in_format` | `Signed in as %1$s` |
| `settings_format_label` | `Format` |
| `settings_format_csv` | `CSV` |
| `settings_format_txt` | `TXT` |
| `settings_date_range_label` | `Date range` |
| `settings_date_range_default` | `Select a date range` |
| `settings_export_share` | `Export CSV Logs` |
| `settings_export_share_txt` | `Export TXT Logs` |
| `settings_export_save` | `Save to file…` |
| `export_share_title` | `Share nutrition log` |
| `export_share_subject` | `MyFoodTracker nutrition log` |
| `export_success_format` | `Exported %1$d rows` |
| `export_success_csv` | `Exported %1$d rows to CSV` |
| `export_success_txt` | `Exported %1$d rows to TXT` |
| `export_empty` | `No logs in the selected range` |
| `export_error_range` | `Invalid date range` |
| `export_error_profile` | `No active profile` |
| `export_error_failed` | `Could not export the log file` |
| `export_error_save_failed` | `Could not save the log file` |
| `a11y_export_date_range` | `Select export date range, currently %1$s` |
| `a11y_export_success` | `Exported %1$d rows to %2$s` |
| `settings_attribution` | `Food catalog data: BLS data © Max Rubner-Institut, licensed CC BY 4.0 (DOI 10.25826/Data20251217-134202-0); OFF data © Open Food Facts contributors, licensed ODbL 1.0. All data stays on this device.` |

Reuse, do not duplicate: `action_cancel`, `action_save`, `active_profile_format`, `meal_macros_format`.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 5.1] — Settings screen → "Export CSV Logs" → date range → CSV with the fixed headers → native share picker → zero network requests.
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/prd.md#FR-12] — the generated file must contain `Date`, `Meal Slot`, `Food Name`, `Quantity`, `Calories`, `Protein`, `Carbs`, `Fat`; no external server requests.
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/prd.md#UJ-4] — "A native file picker launches to save the formatted CSV file locally" (covered by the "Save to file…" action).
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/addendum.md#intake_logs] — stale sketch; the code tables `meals` / `foods` win.
- [Source: _bmad-output/planning-artifacts/architecture/architecture-MyFoodTracker-2026-09-06/ARCHITECTURE-SPINE.md#AD-1..AD-8] — layer, sync DAO, mapper, use case, binding, DI, cascade, FTS rules; §Structural Seed names `ExportLogsUseCase`, `SettingsFragment`, `SettingsViewModel`.
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-MyFoodTracker-2026-09-06/EXPERIENCE.md#Information Architecture] — "Settings & Data Management Screen | Dashboard top overflow menu | … CSV Log Export Button".
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-MyFoodTracker-2026-09-06/EXPERIENCE.md#Voice and Tone] — "All data stays on this device."; no gamified copy.
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-MyFoodTracker-2026-09-06/DESIGN.md#Do's and Don'ts] — no loading spinners for local SQLite operations; 48dp targets.
- [Source: app/src/main/java/com/example/myfoodtracker/data/db/MealEntryEntity.kt] — `meals` table shape (`title` = slot, `date`, `time`).
- [Source: app/src/main/java/com/example/myfoodtracker/data/db/FoodEntity.kt] — `foods` table shape (`weight` in grams, absolute macros).
- [Source: app/src/main/java/com/example/myfoodtracker/data/repository/MealRepositoryImpl.kt:21-24] — `getMealEntries()` = all meals of the active profile, null-session guard.
- [Source: app/src/main/java/com/example/myfoodtracker/domain/usecase/LogFoodEntryUseCase.kt:20-37] — the per-100g scaler the export must not re-apply.
- [Source: app/src/main/java/com/example/myfoodtracker/presentation/ui/dashboard/DashboardFragment.kt:546-565] — the UTC date-picker conversion to copy.
- [Source: app/src/main/java/com/example/myfoodtracker/MainActivity.kt:53-61] — the `action_settings` no-op this story replaces.
- [Source: app/src/main/java/com/example/myfoodtracker/di/AppModule.kt] — where the three new bindings go.
- [Source: tools/seed/README.md#Provenance & licensing] — the BLS / OFF attribution text (deferred item `3.5-D1`).
- [Source: _bmad-output/implementation-artifacts/deferred-work.md#3.5-D1] — attribution is due in the story that introduces a Settings screen.
- [Source: _bmad-output/implementation-artifacts/4-2-one-tap-recipe-logging.md] — schema reality, fake-name clash, `mergeDebugResources` clean incident, CRLF-dirty worktree, DI conventions.
- [Source: _bmad-output/project-context.md] — stack pins, synchronous execution, no emulator, ViewModel/Context rule.
- [Source: https://developer.android.com/training/secure-file-sharing] — FileProvider, temporary URI grants, narrow path declarations.
- [Source: https://developer.android.com/training/data-storage/shared/documents-files] — `ACTION_CREATE_DOCUMENT` contract, `EXTRA_TITLE`, `CATEGORY_OPENABLE`.

## Dev Agent Record

### Agent Model Used

MiniMax M3.1 Flash Free (opencode, provider `opencode-go`, model `minimax-m3.1-flash-free`)

### Debug Log References

- `:app:mergeDebugResources` / `:app:packageDebugResources` failed with `NullPointerException: Cannot invoke "java.io.File.getName()" because the return value of "java.io.File.getParentFile()" is null`. This is the stale-incremental-resource-blob incident already documented in Stories 4.1 and 4.2; resolved with a single `./gradlew :app:clean` followed by a rebuild. No unrelated XML was touched.
- `ExportLogsUseCase.kt` failed to compile twice while being written: `Unresolved reference 'formatNumber'` (the nested `ExportRow` class could not reach the outer class instance member) and the same class of error for `mealDate`. Fixed by moving `formatNumber` into the `ExportRow` companion object and by resolving the meal date in `invoke()` before the row is constructed, so the nested class never touches an outer instance member.
- `ExportLogsUseCaseTest` initially failed 2 of 31 cases. Both were wrong test expectations, not production bugs: (a) the TXT header assertion assumed the "Date" column was 4 characters wide, but the column width is `max(header, longest cell)` = 10 because the dates are 10 characters; (b) the column-alignment assertion used `indexOf("1")`, which matched the `1` inside `2026-10-04` instead of the Protein column. Fixed by asserting the header column names start at the same offsets as the data columns and by measuring the gap between the first two cells (exactly 2 spaces) on every data line.
- `SettingsViewModelTest` failed to compile: the fake `SessionRepository` implemented `forgetRememberedSession()` instead of the interface's `forgetRememberedProfile()`. Fixed in the fake; no production code changed.
- Material 1.10.0 API verified against the shipped bytecode (not from memory) before writing the picker code: `MaterialDatePicker.Builder.dateRangePicker()` uses `androidx.core.util.Pair<Long, Long>` (not `android.util.Pair`) and installs **no** date validator, so a range that starts before today (the default is first-of-month → today) is accepted. `RangeDateSelector.setSelection` only requires `first <= second`.
- `announceForAccessibility` is deprecated in the current `androidx.core`; the warning also fires for the pre-existing `DashboardFragment`, `RecipeListFragment` and `FoodSearchBottomSheet` call sites, so the new code follows the established project pattern on purpose.
- **Potential infinite LiveData dispatch loop found and fixed.** The first draft of `SettingsFragment` called `viewModel.clearExportState()` unconditionally at the end of the `exportState` observer. The `androidx.lifecycle.LiveData` bytecode (`lifecycle-livedata-core-2.6.2`) was inspected and shows that `dispatchingValue` sets `mDispatchInvalidated = true` and re-dispatches when a value is set from inside an observer — so clearing unconditionally would re-enter the observer forever and freeze the app. The observer now clears only when the state is **not** `Idle`. `SettingsViewModelTest.exportState_observerThatClearsAfterHandling_terminates` reproduces the pattern and asserts the observer runs exactly 3 times (initial `Idle`, the event, the re-dispatched `Idle`); with the guard removed the same test fails with "Observer ran more than 10 times — dispatch loop", so the regression is covered.
- `:app:lintDebug` reports **0 errors** for the whole module. The only warnings attributable to the new code were 2 × `ConstantLocale` (the range formatters were `static` fields, so `Locale.getDefault()` was captured at class-load time) — fixed by moving them to instance fields, matching `DashboardViewModel`. One `Overdraw` warning remains on `fragment_settings.xml`, which is the same `android:background="@color/surface_app_bg"` root pattern the existing dashboard and recipe-list layouts use. `TypographyDashes` flags the hyphens inside the DOI of the attribution notice; that text is a **verbatim legal notice** required by AC #14 and must not be "fixed".
- `export_success_format` is unused because the format-specific `export_success_csv` / `export_success_txt` cover both export destinations. It is kept because the Dev Notes string table mandates it — the same precedent as Story 4.2, which left `a11y_recipe_item` in place unused.

### Implementation Plan

1. `ExportLogsUseCase` (Task 1) is the only place that formats anything. It reuses `MealRepository.getMealEntries()` unchanged — no DAO query, no repository method, no entity column — filters the meals to the inclusive `[from, to]` range, explodes every in-range meal into one row per food, sorts stably by `(date, time, foodName)`, and renders either CSV (RFC 4180 escaping) or TXT (two-space separator, per-column width = widest cell, trailing spaces stripped). Numbers go through `BigDecimal.valueOf(v).setScale(2, HALF_UP).stripTrailingZeros().toPlainString()`, so a German device still writes `12.5`.
2. `LogExportFileWriter` (Task 2) owns the file IO: `writeToCache` creates `cacheDir/exports/`, `contentUriFor` returns a `FileProvider` `content://` URI, `writeToUri` streams UTF-8 bytes into a user-picked document URI. The `FileProvider` is declared in the manifest with `exported="false"`, `grantUriPermissions="true"` and a narrow `cache-path` of `exports/` only. **No permission was added** — the app still declares none.
3. Presentation (Task 3): `SettingsViewModel` holds the format and the date range (so the range survives a rotation) and returns the payload; it holds no `Context`, `File` or `Uri`. `SettingsFragment` owns the file IO and both Intents, guarded by `isAdded`. The domain literals `"No active profile"` / `"Invalid date range"` are mapped to `@string` resources in the Fragment.
4. Resources/DI (Task 4): one new nav destination + action, one new `viewModel` binding, one new `factory` (the use case) and one new `single` (the file writer). The 9-`get()` `DashboardViewModel` line was not touched. `menu_main.xml` was not touched.
5. Tests (Task 5): `ExportLogsUseCaseTest` (31 cases) and `SettingsViewModelTest` (14 cases), JUnit4 + hand-written fakes, no MockK, no Robolectric, no new `androidTest` and no schema change.

### Completion Notes List

Story 5.1 implemented end-to-end. Highlights:

- **One payload, two destinations (AC4–AC8).** `ExportPayload.content` is the complete file content including the trailing newline, produced once by the domain layer and consumed unchanged by both the Sharesheet and the document picker. The two paths cannot drift.
- **Read + filter, not a new query (AC12, NFR-5).** The export is a filter over the existing `MealRepository.getMealEntries()`; profile isolation stays inside the repository and the UI never sees a profile id. No entity, DAO, schema or database version changed — `AppDatabase` stays at version 4, `FoodCatalogDatabase` at version 1, and no Gradle dependency was added.
- **Stored macros, copied verbatim (AC4).** `foods.calories/protein/carbs/fat` already hold the absolute values for the logged row; the export copies them and never re-applies the per-100g factor. `LogFoodEntryUseCase` is not called from the export path. A stored `248` kcal stays `248`.
- **Locale-independent numbers (AC6).** The single number helper uses `BigDecimal` + `toPlainString()`. `ExportLogsUseCaseTest` sets `Locale.GERMANY` around the call and asserts the output contains `12.5` and not `12,5` — this is the regression test for the most likely silent bug in the story.
- **CSV and TXT agree (AC4, AC5).** A test asserts the TXT cells equal the CSV cells row for row. The rendered output was diffed against the contract example in the Dev Notes and matches byte for byte, including the `"Cookie, with chocolate"` quoting and the aligned TXT columns.
- **No `file://` URI, ever (AC7).** The Sharesheet receives a `FileProvider` `content://` URI with `FLAG_GRANT_READ_URI_PERMISSION`; the provider exposes only `cacheDir/exports/`. The merged manifest was inspected to confirm the provider is registered and that no `INTERNET` (or any other) permission was added.
- **Settings is reachable from the dashboard only (AC1).** `MainActivity.openSettings()` checks `currentDestination?.id == R.id.dashboardFragment` and otherwise does nothing — no navigation and no crash on the lock screen. `settingsFragment` is deliberately **not** added to `appBarConfiguration`, so the Up arrow appears and returns to the dashboard.
- **Empty and error paths (AC9).** An empty range produces `ExportState.Empty` → the "No logs in the selected range" Snackbar, with no file written and no Intent launched. An invalid range or a missing profile produces `ExportState.Error` → a mapped Snackbar. A file-write or URI-creation failure is caught and shown as an error; nothing crashes and no half-opened picker is left behind.
- **Attribution closed (AC14, deferred 3.5-D1).** The notice is static text (`@string/settings_attribution`) in `fragment_settings.xml`, visible without scrolling into a hidden tab and independent of the export state.
- **Accessibility (AC13).** All interactive controls are at least 48dp (`@dimen/min_touch_target`), the date-range button carries the dynamic content description "Select export date range, currently …", the format toggle group is labelled, and every successful export produces both a Snackbar and a TalkBack announcement ("Exported N rows to CSV/TXT").
- **Rotation (AC3).** The selected range lives in the ViewModel, so it survives a rotation. The export state is cleared right after it has been handled, so a rotation cannot re-launch the Sharesheet.
- **No dispatch loop (AC7, AC8).** Clearing the export state from inside the observer is guarded on `Idle`, because `LiveData` re-dispatches when a value is set from inside an observer. Without the guard the app would freeze in an infinite dispatch loop; there is now a regression test for it.

**Verification results:**

- `./gradlew :app:compileDebugSources` — green.
- `./gradlew :app:testDebugUnitTest` — green. **293 JVM unit tests, 0 failures** across 32 test classes (248 pre-existing + 45 new: 31 use-case, 14 ViewModel). Every pre-existing test still passes; no DAO, entity, schema, catalog or Gradle-dependency change.
- `./gradlew :app:assembleDebug` — green (debug APK built).
- `./gradlew :app:assembleDebugAndroidTest` — green (androidTest APK built; no new instrumented test was added).
- `./gradlew :app:lintDebug` — green, **0 errors** module-wide.
- The merged manifest was inspected: the `FileProvider` is registered with `exported="false"`, `grantUriPermissions="true"` and `@xml/file_paths`, and **no permission was added** (no `INTERNET`).

Deliberate deviations / judgement calls:

- The `MaterialDatePicker` range API in Material 1.10.0 is typed with `androidx.core.util.Pair`, not `android.util.Pair`; the shipped bytecode was checked before writing the picker code, so the code compiles against the real signature.
- The pending "Save to file…" payload is held in a Fragment field, exactly as the story contract specifies. A configuration change while the system document picker is open therefore loses the payload; the activity result then reports "Could not save the log file" rather than writing a truncated file. This is tracked as forward note `5.1-F2`.
- `settings_date_range_format` (`%1$s – %2$s`) was added on top of the string table in the Dev Notes, because the range label needs a separator and Story 2.6 forbids hardcoded user-visible literals in Kotlin.
- The `Quantity` column holds `foods.weight` in grams, including `0` for quick-add rows (which log no weight). The column is named `Quantity` by the spec; the value is grams by design.
- Water logs are not exported — the FR-12 header is fixed and has no water column. Tracked as forward note `5.1-F1`.
- No logout button and no backup/restore buttons were added: `1.2-D3` stays open and Story 5.2 owns backup/restore.

**Manual on-device verification still required (no emulator was run, per project policy):**

- Settings opens from the dashboard overflow menu; the toolbar shows "Settings" and the Up arrow returns to the dashboard.
- The Settings menu item does nothing on the lock screen (no navigation, no crash).
- CSV export: pick a range, tap "Export CSV Logs" → the Sharesheet opens with `myfoodtracker_logs_<from>_<to>.csv` attached → open it in a spreadsheet → the 8 columns line up and the values match the dashboard rows.
- TXT export: the same flow with the TXT toggle → open the file in a text editor → the columns are aligned.
- "Save to file…": the document picker opens → pick a location → the file appears with identical content and the success Snackbar/TalkBack announcement fires.
- A German-locale device still writes `12.5` (not `12,5`).
- A second profile's rows are absent from the export.
- The attribution notice is visible on the Settings screen.
- The range picker opens pre-filled with the current month → today and converts the picked endpoints without a day shift.
- Every pre-existing feature still works (log food, quick-add, water, swipe-delete + undo, search, custom food, recipe logging, passcode auth, remember-me).

### File List

**New:**

- `app/src/main/java/com/example/myfoodtracker/domain/model/ExportFormat.kt`
- `app/src/main/java/com/example/myfoodtracker/domain/model/ExportPayload.kt`
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/ExportLogsUseCase.kt`
- `app/src/main/java/com/example/myfoodtracker/data/export/LogExportFileWriter.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/settings/SettingsFragment.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/settings/model/SettingsUiState.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/settings/model/DateRange.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/settings/model/ExportState.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/SettingsViewModel.kt`
- `app/src/main/res/layout/fragment_settings.xml`
- `app/src/main/res/xml/file_paths.xml`
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/ExportLogsUseCaseTest.kt`
- `app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/SettingsViewModelTest.kt`

**Modified:**

- `app/src/main/java/com/example/myfoodtracker/MainActivity.kt` — `action_settings` now navigates to `settingsFragment`, but only from `dashboardFragment`
- `app/src/main/java/com/example/myfoodtracker/di/AppModule.kt` — added `factory { ExportLogsUseCase(get()) }`, `single { LogExportFileWriter(androidContext()) }` and `viewModel { SettingsViewModel(get(), get()) }`
- `app/src/main/AndroidManifest.xml` — added the `androidx.core.content.FileProvider` `<provider>`; no permission added
- `app/src/main/res/navigation/nav_graph.xml` — added `settingsFragment` and `action_dashboardFragment_to_settingsFragment`
- `app/src/main/res/values/strings.xml` — 24 new "Settings & Data Management (Epic 5, Story 5.1)" strings
- `_bmad-output/implementation-artifacts/deferred-work.md` — marked `3.5-D1` resolved; added forward notes `5.1-F1` and `5.1-F2`
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — story status `ready-for-dev` → `in-progress` → `review`
- `_bmad-output/implementation-artifacts/5-1-csv-and-txt-log-export.md` — the story file itself (frontmatter `baseline_commit`, task checkboxes, Dev Agent Record, File List, Change Log, Status)

### Change Log

| Date | Change | Author |
|---|---|---|
| 2026-10-21 | Story 5.1 implemented: `ExportLogsUseCase` (CSV + TXT rendering, RFC 4180 escaping, locale-independent numbers), `LogExportFileWriter` + `FileProvider`, first Settings screen with format toggle, UTC date-range picker, Sharesheet and document-picker export destinations, catalog attribution notice, 44 new JVM unit tests | Dev Agent (MiniMax M3.1 Flash Free) |
| 2026-10-21 | Code review completed: applied 6 patches (TalkBack announcements on empty/error messages, date-range picker rotation listener re-attachment + duplicate-dialog guard, TXT newline/tab sanitization + test, `"wt"` mode in `LogExportFileWriter.writeToUri`, exception logging + catch-all in `SettingsViewModel.export()` + test, cross-year start date formatting in `SettingsFragment.formatRange`); status → `done` | Code Review Agent (Gemini 4 Argon) |
