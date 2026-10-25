---
story_id: 5.2
story_key: 5-2-offline-full-database-backup-and-restore
epic: 5
status: review
created: 2026-10-21
baseline_commit: c8eb9d45952f1ebf6bcc486c58b733552fe6bba7
---

# Story 5.2: Offline Full Database Backup & Restore

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user,
I want to back up my entire SQLite database to a local file and restore it on another device,
so that my data is portable without cloud sync.

## Acceptance Criteria

1. **Given** the Settings screen, **When** it is rendered, **Then** a second card **"Backup & Restore"** is visible below the existing **Export Logs** card (and above the attribution footer), containing a short explanatory line plus exactly two buttons: **Backup Database** and **Restore Database**.
   **And** the existing Export Logs section, the format toggle, the date-range picker, the attribution notice and the profile header are **unchanged** and still fully functional.
   **And** both buttons have a touch target of at least `48dp` and a content description; every success and every failure is announced to TalkBack.
2. **Given** the user taps **Backup Database**, **When** the backup is prepared, **Then** a single backup artifact is produced with the file name **`myfoodtracker_backup_<yyyy-MM-dd>.db`** (e.g. `myfoodtracker_backup_2026-10-21.db`, `yyyy-MM-dd` = the **device-local** current date) and the system **create-document** picker opens (`ActivityResultContracts.CreateDocument("application/vnd.sqlite3")`, which sets `ACTION_CREATE_DOCUMENT` + `EXTRA_TITLE` = that file name).
   **And** **nothing** is written to the user-chosen destination until the user confirms the destination; the backup is first materialised in the app cache at the deterministic path `cacheDir/backups/<fileName>`.
   **And** after the user confirms, the **exact same bytes** are written to the returned `content://` URI via `ContentResolver.openOutputStream(uri, "wt")`, and the app shows a success message ("Database backed up to …") plus a TalkBack announcement.
   **And** a cancelled picker writes nothing and shows **no** error.
2b. **Given** the app has been used (a profile exists, meals/foods/water/recipes exist) and the user backs it up, **Then** the backup file is a **self-contained copy of the live `meals_database`** — every row of `meals`, `foods`, `users`, `user_daily_goals`, `water_logs`, `recipes` and `recipe_ingredients` is preserved verbatim, together with the `room_master_table` identity hash and `PRAGMA user_version` of the live database.
   **And** the copy is taken **after a WAL checkpoint** so that no committed data lives only in the `-wal` sidecar (Dev Notes §"Backup file integrity"), and the produced file is **completely self-contained**: no `-wal`/`-shm` sidecar exists next to it and no data is hidden in one.
   **And** in addition to the app tables, the backup file carries exactly two extra tables (Dev Notes §"Backup file format"): `myfoodtracker_backup_meta` (key/value metadata) and `myfoodtracker_custom_foods` (the user's own custom foods, i.e. every `catalog_foods` row with `is_custom = 1` plus its `catalog_food_nutrients` row).
   **And** the seeded part of the food catalog is **not** copied (the bundled asset is reinstallable); only the user's custom foods travel with the backup. This is the resolution of deferred item **3.1-F2**.
3. **Given** the user taps **Restore Database**, **When** the system **open-document** picker opens (`ActivityResultContracts.OpenDocument(arrayOf("application/vnd.sqlite3", "application/x-sqlite3", "application/octet-stream"))` — it sets `ACTION_OPEN_DOCUMENT` + `CATEGORY_OPENABLE` + `EXTRA_MIME_TYPES` and `type = "*/*"`) and the user picks a file, **Then** the app copies the picked content into the app cache and **validates** it before anything on the device is touched (AC #4).
   **And** a cancelled picker writes nothing, changes nothing and shows **no** error.
   **And** the restore is scoped to the **whole local database** — profiles (including passcode hashes), daily goals, meals, foods, water logs, recipes and custom foods — and it is **not** scoped to the active profile: restoring replaces every profile's data.
4. **Given** a picked candidate file, **When** it is validated, **Then** the following checks run **in this order** and the **first** failure is reported to the user (exact reason literals in Dev Notes §"Validation decision table"):
   1. the file opens as a SQLite database **and** contains the table `myfoodtracker_backup_meta` (`NOT_A_DATABASE` otherwise — a random `.db`, a photo or a text file is rejected here, **not** as a version problem);
   2. `PRAGMA integrity_check` returns `ok` (`INTEGRITY_CHECK_FAILED` otherwise);
   3. `myfoodtracker_backup_meta.format_version` exists and equals `1` (`UNSUPPORTED_BACKUP_VERSION` otherwise — only reachable when the meta table *is* present but carries a different version);
   4. the declared `main_user_version` equals the file's actual `PRAGMA user_version` (`INCOMPLETE_BACKUP` otherwise — the file is self-inconsistent);
   5. `PRAGMA user_version` equals the running app's schema version `4` (`SCHEMA_VERSION_MISMATCH` otherwise);
   6. all required tables are present — `meals`, `foods`, `users`, `user_daily_goals`, `water_logs`, `recipes`, `recipe_ingredients` (`MISSING_TABLES` otherwise);
   7. the candidate's `room_master_table.identity_hash` (row `id = 42`) equals the **live** database's identity hash (`SCHEMA_MISMATCH` otherwise) — this is what stops Room from throwing `IllegalStateException("Room cannot verify the data integrity")` on the next open;
   8. the declared `custom_foods_count` equals the actual row count of `myfoodtracker_custom_foods` (`INCOMPLETE_BACKUP` otherwise).
   **And** the validation never writes to the live database and never launches a destructive action on its own.
5. **Given** a candidate file that passes validation, **When** the user is shown the overwrite warning, **Then** a **modal alert** is displayed with the message *"Restoring a database backup will overwrite your current local logs. Proceed?"* and two explicit buttons — **Cancel** and **Overwrite & Restore** (UX: `EXPERIENCE.md` §State Patterns, "Database Restore Warning").
   **And** **Cancel** aborts the restore completely: the staged file is deleted, the live database is untouched, and no error is shown.
   **And** **Overwrite & Restore** is the **commit point**: it stages the validated file in `filesDir/pending_restore/` (app-private, **not** the cache — the cache may be cleared by the system) and then shows the restart prompt.
6. **Given** the restore is staged, **When** the restart prompt is shown, **Then** a modal alert explains that the app must restart to apply the restored data and offers **Restart now** and **Cancel**.
   **And** **Restart now** terminates the app process (`Process.killProcess(Process.myPid())`) — the restored data is applied on the next launch.
   **And** **Cancel** at this point **discards** the staged restore (the staging directory is deleted) so the current data is never silently overwritten later.
   **And** if the process dies between staging and the restart prompt (e.g. the OS kills the app), the staged restore is still applied on the next launch — the staging directory is the commit point, and **Cancel** is the only way to abort it.
   **And** the prompt wording makes clear that the current local data (profiles, logs, custom foods) will be replaced.
7. **Given** the app is (re)launched after a staged restore, **When** `MainApplication.onCreate()` runs, **Then** the pending restore is applied **before Koin starts and therefore before Room opens any database**:
   the staged file's `myfoodtracker_custom_foods` rows are read, the two `myfoodtracker_*` backup tables are dropped from the staged copy, the live database file is replaced (`Context.deleteDatabase("meals_database")` first, so the `-wal`/`-shm`/`-journal` sidecars of the old database are removed as well), and the staged copy is written to `context.getDatabasePath("meals_database")`.
   **And** after Koin has started, the restored custom foods are inserted into the catalog database through the **existing Room DAO** (writes go to `catalog_foods`, so Room's `room_fts_content_sync_*` triggers index them for FTS5 search automatically — never write to `catalog_foods_fts` directly).
   **And** the previous custom foods of this device are removed first (the restore replaces state, it does not merge).
   **And** the "remember this device" profile id is cleared from the session preferences, so after the restart the app shows the passcode screen and the user logs in with the passcode of the **restored** profile.
   **And** the pending-restore staging directory is deleted after the restore has been applied, so a restore is never applied twice.
8. **Given** a restored database, **When** the app starts, **Then** Room opens the restored database **without** a destructive migration: `PRAGMA user_version` is `4` (equal to `AppDatabase.version`) and the identity hash matches, so `fallbackToDestructiveMigration()` is never triggered and the restored rows are visible immediately.
   **And** every pre-existing feature keeps working against the restored data: dashboard logging, quick-add, water logging, swipe-to-delete + undo, food search (including the restored custom foods), custom foods, recipe logging, passcode auth and remember-me.
9. **Given** any backup or restore, **When** it runs, **Then** **zero network requests** are made. The manifest declares **no permissions at all** (there is no `INTERNET` permission) — do not add one, and do not add any network dependency.
   **And** no backup file is exposed through the `FileProvider`: the backup lives in `cacheDir/backups/`, which is **not** covered by `res/xml/file_paths.xml` (only `cache-path/exports/` is) — do not widen the provider paths.
   **And** the app's own cloud-backup configuration (`res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`) is **not** changed by this story.
10. **Given** a file-write, URI-creation or validation failure, **When** it happens, **Then** the app shows an explicit error message, does **not** crash (no unhandled exception) and does **not** leave a half-written backup in the user's chosen location.
    **And** a failed backup leaves the live database completely untouched.
    **And** a failed restore leaves the live database and the staging directory untouched (no partially applied restore).
11. **Given** this story's scope, **When** implementing, **Then** the **database schema, DAOs, entities and the database versions are unchanged** — `AppDatabase` stays at `version = 4`, `FoodCatalogDatabase` stays at `version = 1`, **no new columns, no migration, no new table in the live schema**. The two `myfoodtracker_*` tables exist **only inside the backup file** and are dropped from the staged copy before it replaces the live database.
    **And** **no new Gradle dependency** is added and no library is upgraded.
    **And** `app/schemas/**` is not regenerated.
12. **Given** the Settings screen, **When** the backup/restore section is rendered, **Then** every interactive control has a touch target of at least `48dp`, every icon-only/compact control has a content description, and both success and failure messages are announced to TalkBack.
    **And** the existing attribution notice (`@string/settings_attribution`) is still visible and unchanged.

## Tasks / Subtasks

- [x] Task 1: Domain — backup models + pure use case (AC: #2, #4)
  - [x] Subtask 1.1: NEW `domain/model/BackupErrorReason.kt` — `enum class BackupErrorReason { NOT_A_DATABASE, INTEGRITY_CHECK_FAILED, UNSUPPORTED_BACKUP_VERSION, SCHEMA_VERSION_MISMATCH, SCHEMA_MISMATCH, MISSING_TABLES, INCOMPLETE_BACKUP, IO_ERROR }`. Pure Kotlin, no Android imports (AD-1).
  - [x] Subtask 1.2: NEW `domain/model/BackupFacts.kt` — `data class BackupFacts(val fileReadable: Boolean, val metaTablePresent: Boolean, val integrityOk: Boolean, val formatVersion: Int?, val declaredUserVersion: Int?, val actualUserVersion: Int?, val expectedUserVersion: Int, val missingTables: List<String>, val candidateIdentityHash: String?, val liveIdentityHash: String?, val declaredCustomFoodCount: Int?, val actualCustomFoodCount: Int)`. Pure.
  - [x] Subtask 1.3: NEW `domain/model/BackupValidation.kt` — `sealed class BackupValidation { object Valid : BackupValidation(); data class Invalid(val reason: BackupErrorReason) : BackupValidation() }`. Pure.
  - [x] Subtask 1.4: NEW `domain/model/CustomFoodBackup.kt` — `data class CustomFoodBackup(val name: String, val brand: String?, val barcode: String?, val baseServingSize: Double, val baseServingUnit: String, val isDeleted: Int, val calories: Double, val proteinG: Double, val carbsG: Double, val fatG: Double, val fiberG: Double, val sugarG: Double, val sodiumMg: Double, val createdAt: Long, val updatedAt: Long)`. Pure. (`isDeleted` mirrors `catalog_foods.is_deleted` so a soft-deleted custom food stays soft-deleted after a restore.)
  - [x] Subtask 1.5: NEW `domain/usecase/BackupDatabaseUseCase.kt` — `class BackupDatabaseUseCase` with:
    - `fun backupFileName(date: LocalDate): String` → `"myfoodtracker_backup_${date}.db"` (`LocalDate.toString()` = `yyyy-MM-dd`).
    - `operator fun invoke(facts: BackupFacts): BackupValidation` — implements the decision table of AC #4 **exactly in that order**, first failure wins. Pure Kotlin/JVM: no Android imports, no `suspend`, no coroutines (AD-1, AD-2). Registered in Koin as `factory` (AD-4).
  - [x] Subtask 1.6: The **file format constants** (table names, meta keys, `format_version = 1`, the required app table names) live in **one** place — a `companion object` in `BackupDatabaseUseCase` (e.g. `META_TABLE`, `CUSTOM_FOODS_TABLE`, `FORMAT_VERSION`, `META_KEY_*`, `REQUIRED_TABLES`) — and are referenced by the data layer. Do not duplicate string literals in Kotlin and SQL strings.
- [x] Task 2: Data layer — backup creation (AC: #2, #2b, #3, #10)
  - [x] Subtask 2.1: NEW `data/backup/DatabaseBackupManager.kt` — `class DatabaseBackupManager(private val context: Context, private val foodCatalogDao: FoodCatalogDao, private val backupUseCase: BackupDatabaseUseCase)`. It owns every file/SQLite operation:
    - `fun createBackupFile(fileName: String): File` — runs the WAL checkpoint, copies the live DB into `cacheDir/backups/<fileName>`, appends the two `myfoodtracker_*` tables to the copy and returns the file. The returned path is **deterministic** (`cacheDir/backups/<fileName>`) — the caller never has to keep a `File` in a Fragment field.
    - `fun writeBackupTo(uri: Uri, fileName: String)` — opens the deterministic cache file and streams it into the user-picked `content://` URI with mode `"wt"` (truncating).
    - `fun validateCandidate(uri: Uri): BackupValidation` — copies the picked content into `cacheDir/restore/candidate.db`, collects the facts and returns `backupUseCase(facts)`.
    - `fun stageRestore(candidate: File): File` — copies the validated candidate into `filesDir/pending_restore/meals_database` and returns the staged path. The write must be **durable** (`FileOutputStream(...).fd.sync()`) so a process kill right after cannot leave a truncated staged file.
    - `fun discardStagedRestore()` — deletes `filesDir/pending_restore/` recursively.
    - `fun deleteCachedCandidate()` — deletes `cacheDir/restore/candidate.db` (called after staging, after a cancelled restore and after a failed validation).
    - All methods throw on failure; the caller catches and maps the exception to a `BackupErrorReason`.
  - [x] Subtask 2.2: **All file-level SQL uses the platform SQLite** — `android.database.sqlite.SQLiteDatabase.openDatabase(path, null, OPEN_READWRITE)` / `OPEN_READONLY` — **not** Room.
    - **Room 3.0.3 has no `openHelper` and no public `query()`/`execSQL()`** (verified by decompiling `room3-runtime.aar`: `androidx.room3.RoomDatabase` exposes only `getPath()`, `getInvalidationTracker()`, `close()`, …). `appDatabase.openHelper.writableDatabase` **does not compile** — it is Room 2.x API.
    - The main database is created with the **framework** driver, so the platform SQLite can open its file safely. The **catalog** database must never be opened this way (it needs FTS5 → platform SQLite would fail with "no such module: fts5").
    - The live database's file path is `context.getDatabasePath("meals_database")` (identical to `AppDatabase.getPath()`); the manager does **not** depend on the `AppDatabase` singleton, so it does not force the Room instance to be created.
  - [x] Subtask 2.3: **WAL checkpoint before copying** (Dev Notes §"Backup file integrity"): open the live file with the platform SQLite, read `PRAGMA journal_mode`; if it returns `wal`, run `PRAGMA wal_checkpoint(TRUNCATE)` and check the returned `busy` column (`rawQuery(...).moveToFirst(); getInt(0)`) is `0`. If it is not `0` → retry once → still busy → fail with `IO_ERROR`. **Never** copy a database whose WAL is busy (the copy would silently miss recent writes).
    - Room 3 puts the database in WAL by default (verified: `BaseRoomConnectionManager` issues `PRAGMA journal_mode = WAL`), so this branch is the normal path, not an edge case.
  - [x] Subtask 2.4: **Copy + sidecar discipline** — the copy is a WAL-mode file (the header flag is copied), so writing to it would put the new tables into a `-wal` sidecar that never travels with the file. Therefore:
    1. copy `context.getDatabasePath("meals_database")` byte-for-byte into `cacheDir/backups/<fileName>`;
    2. open the **copy** with the platform SQLite and run `PRAGMA journal_mode = DELETE` on it (the copy has no other connection, so this succeeds and converts it to a rollback-journal database whose writes land in the main file);
    3. `CREATE TABLE IF NOT EXISTS myfoodtracker_backup_meta …` and `CREATE TABLE IF NOT EXISTS myfoodtracker_custom_foods …` (columns in Dev Notes §"Backup file format"), then insert the meta rows and the custom-food rows;
    4. `close()` the copy, then **explicitly delete `<copy>-wal` and `<copy>-shm`** if they exist;
    5. the resulting file is self-contained — it must open standalone in any SQLite tool.
    - **Never** change the journal mode of the **live** database — only of the copies.
  - [x] Subtask 2.5: **Custom foods source**: read them from the catalog database via the **Room DAO** (never from the raw file — the catalog needs FTS5):
    - **UPDATE** `data/dao/FoodCatalogDao.kt` with **three** methods:
      ```kotlin
      @Query("SELECT * FROM catalog_foods WHERE is_custom = 1")
      fun getCustomFoods(): List<CatalogFoodEntity>

      @Query("SELECT * FROM catalog_food_nutrients WHERE food_id IN (SELECT id FROM catalog_foods WHERE is_custom = 1)")
      fun getCustomFoodNutrients(): List<CatalogFoodNutrientEntity>

      @Query("DELETE FROM catalog_foods WHERE is_custom = 1")
      fun deleteCustomFoods()
      ```
    - The `catalog_food_nutrients` children of a deleted custom food are removed by the foreign key `CASCADE` (the entity declares `ForeignKey(..., onDelete = CASCADE)`) — **never** delete the nutrient rows by hand (project rule "let SQLite handle cascade deletion").
    - **UPDATE** the fake DAO in `app/src/test/java/com/example/myfoodtracker/data/repository/FoodCatalogRepositoryImplTest.kt` (`FakeFoodCatalogDao`) with the **three** new methods, otherwise the test source set will not compile.
    - Do **not** add a method to the `FoodCatalogRepository` **interface** (that would force every fake to change and buys nothing — the backup manager is a data-layer class and may use the DAO directly).
  - [x] Subtask 2.6: **Meta values** written into `myfoodtracker_backup_meta`: `format_version` = `1`; `app_version` = the app version name from `PackageManager` (fall back to a constant); `created_at` = epoch millis; `main_user_version` = `PRAGMA user_version` of the copied database; `identity_hash` = the copied database's `room_master_table.identity_hash` (row `id = 42`) — **informational only**, the validator compares the *live* hash with the candidate's `room_master_table`, not with this row; `custom_foods_count` = the number of custom food rows written; `main_db_name` = `meals_database`.
  - [x] Subtask 2.7: **Reading the identity hash** — `SELECT identity_hash FROM room_master_table WHERE id = 42` on the respective file. The live hash is read from the live `meals_database`, the candidate's from the candidate file. **Both are read at runtime and neither may ever be hard-coded** (the compiled hash of the *catalog* — `0407e73aa8c8219f3fca19ebe9dc8429` — is a different database and is only quoted in this story to show that the mechanism exists). If the live database file does not exist yet, pass `liveIdentityHash = null` and the validator skips check #7 (the `user_version` check still applies).
- [x] Task 3: Data layer — applying the restore at cold start (AC: #6, #7, #8)
  - [x] Subtask 3.1: NEW `data/backup/PendingRestoreApplier.kt` — `class PendingRestoreApplier(private val context: Context)`:
    - `fun applyPendingDatabaseSwap(): List<CustomFoodBackup>` — **must run before Koin starts**. If `filesDir/pending_restore/meals_database` does not exist → return an empty list. Otherwise:
      1. open the **staged file** with the platform SQLite (read-write), run `PRAGMA journal_mode = DELETE` on it (same reason as Subtask 2.4 — the staged copy is a WAL-mode file and the DROP statements would otherwise land in a sidecar that never travels);
      2. read the `myfoodtracker_custom_foods` rows into `CustomFoodBackup` objects;
      3. `DROP TABLE IF EXISTS myfoodtracker_backup_meta` and `DROP TABLE IF EXISTS myfoodtracker_custom_foods`;
      4. `close()` and delete `<staged>-wal` / `<staged>-shm` if they exist;
      5. `context.deleteDatabase("meals_database")` (removes the old file **and** its `-wal`/`-shm`/`-journal` sidecars);
      6. copy the staged file to `context.getDatabasePath("meals_database")` (create the parent directory first);
      7. clear the remembered-session preferences;
      8. delete the staging directory; return the custom foods.
      - **Defensive**: if the staged file cannot be opened or fails `PRAGMA integrity_check`, delete the staging directory and return an empty list — the app must start with the current database instead of crashing.
    - `fun applyCustomFoods(foodCatalogDao: FoodCatalogDao, foods: List<CustomFoodBackup>)` — **runs after Koin starts**: `foodCatalogDao.deleteCustomFoods()` (the nutrient children are removed by the FK cascade), then insert the restored rows through the DAO (`insertFoodWithNutrients` with `id = 0` → Room binds `NULL` for the auto-generated primary key → SQLite allocates a fresh id). Writes go to `catalog_foods`, so the FTS triggers index them. **Never** reuse the backed-up ids — they may collide with the seeded catalog ids of a different asset version, and `OnConflictStrategy.REPLACE` would overwrite a seeded food.
  - [x] Subtask 3.2: **UPDATE** `MainApplication.kt` — in `onCreate()`:
    ```kotlin
    val restoreApplier = PendingRestoreApplier(this)   // constructed directly, NOT resolved from Koin
    val restoredCustomFoods = restoreApplier.applyPendingDatabaseSwap()   // BEFORE startKoin
    val koinApp = startKoin { androidLogger(); androidContext(this@MainApplication); modules(appModule) }
    if (restoredCustomFoods.isNotEmpty()) {
        koinApp.koin.get<FoodCatalogDao>().let { dao -> restoreApplier.applyCustomFoods(dao, restoredCustomFoods) }
    }
    ```
    - `PendingRestoreApplier` is **constructed directly** — it must not be a Koin binding, because the database swap has to happen before Koin exists (a Koin `single` could not be resolved at that point).
    - The `FoodCatalogDao` is resolved **only when there are custom foods to restore** — resolving it unconditionally would open the catalog database (and copy the ~10–20 MB asset on the first run) on every cold start.
    - Wrap both steps in try/catch: a failing restore application must **never** crash the app on startup — log the error and continue with the (unchanged) current database.
  - [x] Subtask 3.3: The remembered-session clearing uses the existing preference file name `SharedPrefsSessionStorage.PREFS_NAME` (`auth_session_prefs`) and clears it with `edit().clear().commit()` — `commit()` (not `apply()`), because the process may be killed right after.
  - [x] Subtask 3.4: **Do not** change `di/AppModule.kt`'s database builder, the database names (`meals_database`, `food_catalog`) or the `fallbackToDestructiveMigration()` setting. The restore relies on `user_version = 4` matching `AppDatabase.version` so that no destructive migration runs.
- [x] Task 4: Presentation — ViewModel state + Settings UI (AC: #1, #3, #5, #6, #10, #12)
  - [x] Subtask 4.1: NEW `presentation/ui/settings/model/BackupState.kt` — `sealed class BackupState { object Idle; object Preparing; data class Ready(val fileName: String); data class Saved(val fileName: String); data class Error(val reason: BackupErrorReason) }`. Plain `object`, not `data object` (same convention as `ExportState`).
  - [x] Subtask 4.2: NEW `presentation/ui/settings/model/RestoreState.kt` — `sealed class RestoreState { object Idle; object Validating; data class ConfirmRequired(val candidateName: String); data class Invalid(val reason: BackupErrorReason); object Staged; data class Error(val reason: BackupErrorReason) }`.
  - [x] Subtask 4.3: UPDATE `presentation/viewmodel/SettingsViewModel.kt` — add a third constructor dependency `backupDatabaseUseCase: BackupDatabaseUseCase` and expose `backupState: LiveData<BackupState>` and `restoreState: LiveData<RestoreState>` plus the state-transition methods `onBackupPreparing()`, `onBackupReady(fileName)`, `onBackupSaved(fileName)`, `onBackupFailed(reason)`, `onRestoreValidating()`, `onRestoreCandidateReady(candidateName)`, `onRestoreInvalid(reason)`, `onRestoreStaged()`, `onRestoreFailed(reason)`, `confirmRestore()`, `cancelRestore()` and `fun backupFileName(date: LocalDate = LocalDate.now()): String`.
    - The restore **dialog state** (`ConfirmRequired`, `Staged`) lives in the ViewModel and is **only** cleared when the user acts (confirm / cancel). It is **not** cleared after "handling" like the export state — otherwise a rotation would drop the pending dialog and the user would be stuck with a staged restore and no prompt. The Fragment re-shows the dialog from the retained state in `onViewCreated`.
    - The ViewModel still holds **no** `Context`, `File` or `Uri` (project rule) — the file IO stays in the Fragment/`DatabaseBackupManager`.
    - Do **not** change the existing export behaviour, the existing `LiveData` names or the existing error literals (`ERROR_NO_ACTIVE_PROFILE`, `ERROR_INVALID_DATE_RANGE`, `ERROR_EXPORT_FAILED`).
  - [x] Subtask 4.4: UPDATE `res/layout/fragment_settings.xml` — add the **Backup & Restore** `MaterialCardView` between the Export Logs card and the attribution footer, with the section title, a description `TextView`, `btn_backup_database` (filled button) and `btn_restore_database` (outlined button). Reuse the existing card/colour/dimen resources (`@color/surface_card_bg`, `@color/primary_forest`, `@dimen/margin_section`, `@dimen/padding_card`, `@dimen/min_touch_target`). No hardcoded colours or strings (Story 2.6 rule).
  - [x] Subtask 4.5: UPDATE `presentation/ui/settings/SettingsFragment.kt`:
    - inject `DatabaseBackupManager` with `by inject()` (the Fragment — not the ViewModel — performs the file IO and the Intents, exactly like the export flow);
    - **Backup**: `btn_backup_database` → `viewModel.onBackupPreparing()` → `manager.createBackupFile(viewModel.backupFileName())` → on success `viewModel.onBackupReady(fileName)` and launch `createDocumentLauncher.launch(fileName)`; on `RESULT_OK` with a non-null URI → `manager.writeBackupTo(uri, fileName)` → `viewModel.onBackupSaved(fileName)` + success Snackbar + TalkBack announcement; on cancel → delete the cached backup and show **no** error; on any exception → `viewModel.onBackupFailed(IO_ERROR)` + error Snackbar.
      - **Who launches the picker:** the **click handler** launches it. The `BackupState.Ready(fileName)` state is informational (it carries the file name so the result callback can recompute the deterministic cache path `cacheDir/backups/<fileName>` — **no `File` is kept in a Fragment field**, so a rotation cannot lose the pending backup).
      - `createDocumentLauncher` is `ActivityResultContracts.CreateDocument("application/vnd.sqlite3")` — it sets `ACTION_CREATE_DOCUMENT` **and** `EXTRA_TITLE` from the launched argument (verified in `activity-1.8.0.aar`), so the picker pre-fills `myfoodtracker_backup_<date>.db`.
    - **Restore**: `btn_restore_database` → launch `openDocumentLauncher.launch(arrayOf("application/vnd.sqlite3", "application/x-sqlite3", "application/octet-stream"))` → on `RESULT_OK` → `viewModel.onRestoreValidating()` → `manager.validateCandidate(uri)` → `Valid` → `viewModel.onRestoreCandidateReady(candidateName)` → the state observer shows the overwrite warning dialog (AC #5) → **Overwrite & Restore** → `viewModel.confirmRestore()` → `manager.stageRestore(candidate)` → `viewModel.onRestoreStaged()` → the state observer shows the restart dialog (AC #6) → **Restart now** → `Process.killProcess(Process.myPid())`; **Cancel** → `viewModel.cancelRestore()` + `manager.discardStagedRestore()` + `manager.deleteCachedCandidate()`; `Invalid(reason)` → `viewModel.onRestoreInvalid(reason)` → show the reason message and change nothing.
      - `openDocumentLauncher` is `ActivityResultContracts.OpenDocument(arrayOf(...))` — it sets `ACTION_OPEN_DOCUMENT` + `CATEGORY_OPENABLE` + `EXTRA_MIME_TYPES` and `type = "*/*"` (verified in `activity-1.8.0.aar`). A raw Intent with `type = "application/vnd.sqlite3"` + `EXTRA_MIME_TYPES` filters inconsistently across providers and hides `.db` files.
    - The dialogs are built with `MaterialAlertDialogBuilder` (Material 1.10.0). `MaterialAlertDialogBuilder.show()` takes **no** `FragmentManager` — a plain `AlertDialog` does **not** survive a rotation. The rotation-safe pattern is the **state-driven** one: the dialog is shown from the `RestoreState` observer in `onViewCreated`; because `ConfirmRequired`/`Staged` stay in the ViewModel until the user acts, the dialog is shown again after a rotation. Guard against showing it twice (track the currently shown dialog in a Fragment field and dismiss it in `onDestroyView`).
    - Observe the new `LiveData` with `viewLifecycleOwner`; never clear `ConfirmRequired`/`Staged` from inside the observer (that is what loses the prompt). The export state keeps its existing "clear after handling" behaviour unchanged.
    - Guard `isAdded` before every Snackbar / `announceForAccessibility` (AD-5). `onDestroyView()` still nulls `_binding`.
  - [x] Subtask 4.6: The candidate file name shown in the overwrite warning is the **picked file's** display name when available (`ContentResolver.query` on `OpenableColumns.DISPLAY_NAME`), otherwise `"backup"`. Never show a raw path.
  - [x] Subtask 4.7: The staged restore lives **on disk** in `filesDir/pending_restore/`, so a rotation or a process death cannot lose it — this story deliberately does **not** repeat the pattern of deferred item 5.1-F2 (a pending payload kept in a Fragment field). The Fragment may keep the *candidate* cache path in a field, but the **decision** to restore is only taken after the user confirms the dialog.
  - [x] Subtask 4.8: **Execution model**: the whole backup/restore flow runs **synchronously on the main thread** (AD-2: no coroutines, no `Flow`, no `suspend`). The main database holds only the user's own logs (typically well under 1 MB), so the copy and the `integrity_check` are fast; the ~10–20 MB catalog is never copied and never integrity-checked. `Preparing`/`Validating` are transient state-machine values, not spinners — DESIGN.md forbids loading spinners for local operations. If an on-device measurement ever exceeds ~1 s, only the file copy may be moved to a background `java.util.concurrent.Executor` (no coroutines) — but then the checkpoint + copy must not race with Room writes, which is only safe today because everything runs on the main thread.
  - [x] Subtask 4.9: **No logout button, no profile-goal editor** — deferred item `1.2-D3` stays open and the UX's "Profile Goal Editor" remains out of scope.
- [x] Task 5: DI (AC: all)
  - [x] Subtask 5.1: UPDATE `di/AppModule.kt` — add `factory { BackupDatabaseUseCase() }`, `single { DatabaseBackupManager(androidContext(), get(), get()) }` and change the ViewModel binding to `viewModel { SettingsViewModel(get(), get(), get()) }`.
    - **Do not** touch the existing `DashboardViewModel` binding (9 `get()`s), the database builders or any other binding (AD-6).
    - `PendingRestoreApplier` is **not** a Koin binding — it is constructed directly in `MainApplication` because it must run before Koin starts (Subtask 3.2).
- [x] Task 6: Tests (AC: #4, #2b, #6)
  - [x] Subtask 6.1: NEW `app/src/test/java/com/example/myfoodtracker/domain/usecase/BackupDatabaseUseCaseTest.kt` — JUnit4, no MockK, no Robolectric. Covers the whole decision table of AC #4 (every failure reason, the order of the checks, and the `Valid` case).
  - [x] Subtask 6.2: UPDATE `app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/SettingsViewModelTest.kt` — add the third constructor argument (`BackupDatabaseUseCase()`) and cover the new state transitions.
  - [x] Subtask 6.3: UPDATE the fake DAO in `app/src/test/java/com/example/myfoodtracker/data/repository/FoodCatalogRepositoryImplTest.kt` for the **three** new `FoodCatalogDao` methods (compile requirement).
  - [x] Subtask 6.4: **No emulator.** The SQLite-level behaviour (WAL checkpoint, `integrity_check`, the identity hash, the cold-start swap) can only be verified on a device — list these as manual checks in the completion notes. An `androidTest` for the restore swap is optional; if added, it must not be executed automatically.
  - [x] Subtask 6.5: `./gradlew :app:compileDebugSources`, `./gradlew :app:testDebugUnitTest`, `./gradlew :app:assembleDebug` and `./gradlew :app:assembleDebugAndroidTest` must be green and the whole existing JVM suite must keep passing.
- [x] Task 7: Documentation + verification notes (AC: #12)
  - [x] Subtask 7.1: UPDATE `_bmad-output/implementation-artifacts/deferred-work.md` — mark **`3.1-F2` resolved** by this story (the backup now carries the `is_custom = 1` rows). Do **not** close `3.1-F1` (a catalog version bump still wipes custom foods), `1.2-D3` (Settings logout), `5.1-F1`/`5.1-F2` or any other open item. Add forward notes for anything this story deliberately leaves open (e.g. the manual on-device verification of the restore swap).
  - [x] Subtask 7.2: Fill the **Dev Agent Record** sections (model used, debug log, completion notes, file list) and explicitly list the manual on-device checks that could not be run (no emulator, per project policy).

### Review Findings

- [x] [Review][Patch] Target device custom foods are not deleted when restoring a backup with 0 custom foods [MainApplication.kt:34, PendingRestoreApplier.kt:81]
- [x] [Review][Patch] Catastrophic data loss risk in replaceLiveDatabase if file copy fails after deleteDatabase [PendingRestoreApplier.kt:208]
- [x] [Review][Patch] Cancelled backup document picker leaves ViewModel backupState in Ready state [SettingsFragment.kt:313, SettingsViewModel.kt:135]
- [x] [Review][Patch] Verify return value of PRAGMA journal_mode = DELETE to prevent silent WAL fallback [DatabaseBackupManager.kt:259, PendingRestoreApplier.kt:132]
- [x] [Review][Patch] Wrap custom food deletion and restoration in a single atomic transaction [FoodCatalogDao.kt:65, PendingRestoreApplier.kt:81]
- [x] [Review][Patch] Call confirmRestore() in SettingsFragment when user accepts restore dialog [SettingsFragment.kt:377]
- [x] [Review][Patch] Clean up orphaned -wal and -shm sidecar files on backup creation failure [DatabaseBackupManager.kt:74]
- [x] [Review][Defer] Discarded custom food IDs in recipe_ingredients after restore [RecipeIngredientEntity.kt:24] — deferred, architectural limitation
- [x] [Review][Defer] Synchronous main-thread execution of backup and restore operations [SettingsFragment.kt:292] — deferred, pre-existing architectural rule (AD-2, 5.2-F3)

## Dev Notes

### Locked design — read this before writing code

This story adds the **second** Settings card and the first flow that **replaces the live database file**. Everything below is a decision, not a suggestion.

**The two databases.** The app has **two** Room databases (see `di/AppModule.kt`):

| Database | File name | Driver | Holds | Backup strategy |
|---|---|---|---|---|
| `AppDatabase` | `meals_database` | platform `AndroidSQLiteDriver` | `meals`, `foods`, `users`, `user_daily_goals`, `water_logs`, `recipes`, `recipe_ingredients` | **fully backed up** (the whole file) |
| `FoodCatalogDatabase` | `food_catalog` | `BundledSQLiteDriver` (FTS5) | the 50k seeded foods **and** the user's custom foods (`is_custom = 1`) | **only the custom foods** are backed up; the seeded part is reinstalled from the bundled asset |

**The backup file is the main database, plus two extra tables.** This is the central design decision:

- The backup is a **self-contained copy of `meals_database`** (after a WAL checkpoint). Restoring it is therefore a plain file replacement — the restored rows, the `room_master_table` identity hash and `PRAGMA user_version` are all preserved exactly, and Room opens it without a migration.
- Two **extra** tables are appended to that copy inside the backup file only:
  - `myfoodtracker_backup_meta` — key/value metadata (format version, app version, creation time, schema version, identity hash, custom-food count);
  - `myfoodtracker_custom_foods` — the user's own foods (this is how deferred item **3.1-F2** is closed without copying the ~10–20 MB seeded catalog).
- The extra tables are **dropped from the staged copy before it replaces the live database**, so the live schema is unchanged (AC #11). Room ignores unknown tables: its integrity check compares the **compiled** identity hash with the one stored in `room_master_table`, and that hash is unaffected by extra tables.
- **Rejected alternative:** a container format with the main DB as a BLOB. It would need the blob to be extracted and re-validated, and it would break the "the backup file *is* the database" property that makes the restore a single file copy.

**Why the restore needs a restart, and why the swap happens at cold start.** The live database file is open by SQLite while the app runs. Overwriting it under an open connection is unsafe (stale `-wal`/`-shm` sidecars can replay frames against a different "lineage" and silently corrupt the data). Therefore:

1. the validated file is **staged** in `filesDir/pending_restore/` while the app is running;
2. the app is killed (`Process.killProcess(Process.myPid())`);
3. on the next launch, `MainApplication.onCreate()` applies the swap **before Koin starts** — i.e. before Room has opened anything — and only then starts Koin.

This is also why the swap must not run while the app is alive: at cold start the old `-wal`/`-shm`/`-journal` sidecars are removed with `Context.deleteDatabase("meals_database")` **before** the new file is written.

**The catalog database must never be touched by the platform SQLite.** `food_catalog` was created with the **bundled** SQLite driver because the platform SQLite is not compiled with FTS5 (`project-context.md` §FTS5 Availability). Reading or writing it with `android.database.sqlite.SQLiteDatabase` would fail on `catalog_foods_fts` ("no such module: fts5") as soon as the FTS sync trigger fires. **All catalog access goes through Room** (`FoodCatalogDao`), writes go to `catalog_foods` and the Room-generated `room_fts_content_sync_*` triggers index them (AD-8). The main database has no FTS table and may be opened with the platform SQLite — that is what the backup/staging code does.

**Room 3.0.3 has no `openHelper`.** Verified by decompiling `room3-runtime.aar`: `androidx.room3.RoomDatabase` exposes `getPath()`, `getInvalidationTracker()`, `getConfiguration()`, `close()`, … — **no** `openHelper`, **no** `query()`, **no** `execSQL()`. `appDatabase.openHelper.writableDatabase` is Room 2.x API and **does not compile** here. Every file-level operation of this story therefore uses the **platform** `android.database.sqlite.SQLiteDatabase` on the database file path (`context.getDatabasePath("meals_database")`, identical to `AppDatabase.getPath()`), and the manager does not depend on the `AppDatabase` singleton at all.

**`fallbackToDestructiveMigration()` is a trap.** `AppModule` builds the main database with `.fallbackToDestructiveMigration()`. If the restored file had a different `user_version`, Room would silently **wipe** the restored data on the next open. That is exactly why the validation enforces `user_version == 4` and the identity hash (AC #4, checks 5 and 7) — the validation is not cosmetic, it prevents data loss.

**The identity hash is the schema fingerprint.** Room stores the compiled schema hash in `room_master_table` (row `id = 42`, column `identity_hash`). On open, Room compares that stored hash with the compiled one; a mismatch throws `IllegalStateException("Room cannot verify the data integrity…")` and the app crashes at startup. Comparing the candidate's hash with the **live** database's hash before the swap is what prevents that. Both hashes are read **at runtime** from the respective files — neither may be hard-coded.

**The backup contains passcode hashes.** `users.passcode_hash` is part of the restored database, so the backup file is sensitive. The user chooses where it goes; the app must not share it through the `FileProvider` and must not upload it.

### Backup file format

```sql
-- Table 1 (app tables + data come from the copied meals_database)
CREATE TABLE IF NOT EXISTS myfoodtracker_backup_meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

-- Table 2
CREATE TABLE IF NOT EXISTS myfoodtracker_custom_foods (
    name              TEXT    NOT NULL,
    brand             TEXT,
    barcode           TEXT,
    base_serving_size REAL    NOT NULL,
    base_serving_unit TEXT    NOT NULL,
    is_deleted        INTEGER NOT NULL,
    calories          REAL    NOT NULL,
    protein_g         REAL    NOT NULL,
    carbs_g           REAL    NOT NULL,
    fat_g             REAL    NOT NULL,
    fiber_g           REAL    NOT NULL,
    sugar_g           REAL    NOT NULL,
    sodium_mg         REAL    NOT NULL,
    created_at        INTEGER NOT NULL,
    updated_at        INTEGER NOT NULL
);
```

`myfoodtracker_backup_meta` rows:

| Key | Value |
|---|---|
| `format_version` | `1` (integer as text) — bump this if the format ever changes |
| `app_version` | the app version name (e.g. `1.0`) |
| `created_at` | epoch millis of the backup |
| `main_user_version` | `PRAGMA user_version` of the copied database (must be `4`) |
| `identity_hash` | `room_master_table.identity_hash` of the copied database — **informational**, not used by the validator |
| `custom_foods_count` | number of rows in `myfoodtracker_custom_foods` |
| `main_db_name` | `meals_database` |

**File name:** `myfoodtracker_backup_<yyyy-MM-dd>.db` (device-local date), e.g. `myfoodtracker_backup_2026-10-21.db`. Matches the UX flow (`EXPERIENCE.md` §Flow 4, step 3).

**MIME type:** `application/vnd.sqlite3` for creating and for the restore filter (`application/x-sqlite3` and `application/octet-stream` are accepted as well, because many file providers report a generic type for `.db` files).

**Known limitation (state it, do not hide it):** `catalog_food_serving_units` rows of custom foods are **not** backed up (custom foods are currently created without serving units, so there is nothing to lose). If serving units are ever attached to custom foods, this format must be extended.

### Backup file integrity (the WAL trap)

Room 3 opens the main database in **WAL** mode by default (verified: `BaseRoomConnectionManager` issues `PRAGMA journal_mode = WAL`). In WAL mode, committed transactions may live only in the `meals_database-wal` sidecar until a checkpoint happens. **Copying `meals_database` without checkpointing first produces a backup that is missing recent writes** — the most likely silent bug in this story.

Procedure (mandatory):

1. Open the live file with the platform SQLite; read `PRAGMA journal_mode`.
2. If it is `wal` → `PRAGMA wal_checkpoint(TRUNCATE)`; the pragma returns one row `(busy, log, checkpointed)`. If `busy != 0` → retry once, then fail with `IO_ERROR`. **Never** copy a database whose WAL is busy.
3. If the journal mode is not `wal` → copy the file directly.
4. Copy `context.getDatabasePath("meals_database")` byte-for-byte into `cacheDir/backups/<fileName>`.
5. On the **copy** (never on the live file): `PRAGMA journal_mode = DELETE`, then create + fill the two backup tables, `close()`, then delete `<copy>-wal` / `<copy>-shm` if they exist.

**Why step 5 is not optional:** a copy of a WAL database keeps the WAL flag in its header (bytes 18/19 = 2). Opening it therefore puts it back into WAL mode, and the `CREATE TABLE` / `INSERT` statements would be written into a `<copy>-wal` sidecar that never travels with the file — the backup would then fail its own `format_version` check on restore (or, worse, appear valid and lose the custom foods). Switching the copy to `journal_mode = DELETE` makes every write land in the main file.

`VACUUM INTO` is the documented alternative (SQLite ≥ 3.27, available on API 35) and is *not* required here — it would also work, but it needs the destination path as a SQL literal and it produces a vacuumed file; the checkpoint + copy path keeps the file byte-faithful, which the restore relies on.

### Validation decision table

Implemented **only** in `BackupDatabaseUseCase` (pure). The data layer collects the facts; the use case decides. First failure wins:

| # | Fact | Failure reason | User-visible message |
|---|---|---|---|
| 1 | the file opens as SQLite **and** contains `myfoodtracker_backup_meta` | `NOT_A_DATABASE` | "The selected file is not a MyFoodTracker backup" |
| 2 | `PRAGMA integrity_check` = `ok` | `INTEGRITY_CHECK_FAILED` | "The selected backup is damaged and cannot be restored" |
| 3 | `format_version` exists and `== 1` | `UNSUPPORTED_BACKUP_VERSION` | "This backup was created by an incompatible app version" |
| 4 | declared `main_user_version` `==` actual `PRAGMA user_version` | `INCOMPLETE_BACKUP` | "This backup is incomplete and cannot be restored" |
| 5 | actual `user_version` `== 4` (the running app's `AppDatabase.version`) | `SCHEMA_VERSION_MISMATCH` | "This backup was created with a different database schema version" |
| 6 | no missing table out of `meals`, `foods`, `users`, `user_daily_goals`, `water_logs`, `recipes`, `recipe_ingredients` | `MISSING_TABLES` | "This backup is missing required tables" |
| 7 | candidate identity hash `==` live identity hash (skipped when the live DB does not exist yet) | `SCHEMA_MISMATCH` | "This backup was created with a different database schema" |
| 8 | declared `custom_foods_count` `==` actual row count | `INCOMPLETE_BACKUP` | "This backup is incomplete and cannot be restored" |

`IO_ERROR` is **not** part of the table — it is the reason for an I/O failure (checkpoint busy, copy failed, URI write failed), reported by the data layer.

**Fact-collection rules:** querying `myfoodtracker_backup_meta` on a file that does not contain it raises a `SQLiteException` — catch it and report `metaTablePresent = false` (→ `NOT_A_DATABASE`), never let it escape as a crash. The same applies to every other optional query: an unreadable table must degrade into a *fact*, not into an exception.

### Restore mechanics (end to end)

```
[ running app ]
  btn_restore_database
    → OpenDocument picker
    → manager.validateCandidate(uri)
        → copy the picked bytes to cacheDir/restore/candidate.db
        → collect BackupFacts (meta table, integrity_check, user_version, tables, identity hash, counts)
        → backupUseCase(facts) → Valid | Invalid(reason)
    → Valid  → MaterialAlertDialogBuilder: "Restoring a database backup will overwrite your current local logs. Proceed?"
                  [Cancel] → discardStagedRestore() + deleteCachedCandidate(); nothing happens
                  [Overwrite & Restore]  ← COMMIT POINT
    → manager.stageRestore(candidate)   → filesDir/pending_restore/meals_database (durable write)
    → MaterialAlertDialogBuilder: "Restart required"
                  [Cancel]          → manager.discardStagedRestore() → nothing pending
                  [Restart now]     → Process.killProcess(Process.myPid())

[ next launch ]
  MainApplication.onCreate()
    → PendingRestoreApplier(this).applyPendingDatabaseSwap()      // BEFORE startKoin
        · open the staged file, PRAGMA journal_mode = DELETE
        · read myfoodtracker_custom_foods
        · DROP the two myfoodtracker_* tables from the staged copy
        · close, delete <staged>-wal / <staged>-shm
        · context.deleteDatabase("meals_database")                // removes -wal / -shm / -journal
        · copy the staged file → context.getDatabasePath("meals_database")
        · clear the remembered-session preferences
        · delete filesDir/pending_restore/
    → startKoin { … }
    → if (customFoods.isNotEmpty()) restoreApplier.applyCustomFoods(koin.get<FoodCatalogDao>(), customFoods)
        · DAO: deleteCustomFoods()   (nutrient children removed by the FK cascade)
        · DAO: insertFoodWithNutrients(..., id = 0) → FTS triggers index them
```

**Why the custom foods are inserted with new ids.** The backed-up `catalog_foods.id` values come from the catalog asset of the *source* device. If the target device ships a different asset version (Story 3.5 rebuilt the catalog), those ids can collide with seeded rows — and `OnConflictStrategy.REPLACE` would **overwrite a seeded food**. `id = 0` makes Room bind `NULL` for the auto-generated primary key, so SQLite allocates a fresh id.

**The remembered session is cleared.** The remembered profile id lives in `SharedPreferences` (`auth_session_prefs`), not in the database. After a restore the profile set may differ from the current device, so the stale id is cleared and the user logs in with the passcode of the restored profile (the passcode hashes are part of the restored `users` table).

**The staging directory must be in `filesDir`, not `cacheDir`.** The system may clear the cache at any time; a staged restore that silently disappears would strand the user. `filesDir/pending_restore/` is app-private. Note that with the current, unmodified `backup_rules.xml` / `data_extraction_rules.xml` the platform auto-backup includes `files/`, so a staged restore file may be included in the system's cloud backup — this is the user's own data and acceptable, but it is a conscious decision, not an oversight.

### Current state of files this story touches

| File | Today | This story changes | Must preserve |
|---|---|---|---|
| `presentation/ui/settings/SettingsFragment.kt` | 349 lines; export flow (format toggle, date-range picker, Sharesheet, `ACTION_CREATE_DOCUMENT` for "Save to file…"), `createDocumentLauncher` | add the backup/restore section, the two pickers, the two dialogs, the state observers | the whole export flow, the rotation-safe date-range picker pattern, `isAdded` guards, `onDestroyView` nulling `_binding` |
| `presentation/viewmodel/SettingsViewModel.kt` | 2 deps (`ExportLogsUseCase`, `SessionRepository`); `uiState`, `exportState`, `format`, `dateRange` | +1 dep (`BackupDatabaseUseCase`), +2 `LiveData`, +state methods | the export state machine and its exact error literals; the ViewModel stays `Context`-free |
| `res/layout/fragment_settings.xml` | profile header card, Export Logs card, attribution footer | insert a third card (Backup & Restore) between the Export card and the footer | the attribution footer stays the **last** element and stays visible without scrolling into a hidden tab |
| `di/AppModule.kt` | 5 DAOs, 2 DBs, 6 repositories, 19 use-case factories, 7 ViewModels | +1 use-case factory, +1 `single`, ViewModel binding gains a third `get()` | the 9-`get()` `DashboardViewModel` line; both database builders; every existing binding |
| `MainApplication.kt` | `super.onCreate()` → `startKoin { androidLogger(); androidContext(); modules(appModule) }` | apply the pending restore **before** `startKoin`, apply the custom foods **after** it | the Koin setup itself (logger, context, module) |
| `data/dao/FoodCatalogDao.kt` | `search`, `countFoods`, `insertFood`, `insertNutrients`, `insertFoodWithNutrients`, `deleteFoodById` | +3 queries for the custom foods (2 read, 1 delete) | the FTS search query and the write path (`catalog_foods` only, never the FTS table) |
| `data/db/AppDatabase.kt` | `version = 4`, `exportSchema = false`, 7 entities | **nothing** | do not bump the version, do not add entities |
| `data/db/FoodCatalogDatabase.kt` | `version = 1`, `BundledSQLiteDriver`, `createFromAsset`, `exportSchema = true` | **nothing** | a version bump wipes the custom foods (3.1-F1); the asset is not regenerated |
| `res/values/strings.xml` | 221 lines, all copy centralised | + the backup/restore strings (see §Strings) | never hardcode copy in Kotlin or XML |
| `res/xml/file_paths.xml` | `<cache-path name="exports" path="exports/"/>` | **nothing** | do **not** add the backup directory — a database backup must not be shareable through the provider |
| `app/src/main/AndroidManifest.xml` | no permissions, one `<activity>`, one `FileProvider` | **nothing** | do not add a permission; do not add a second provider |
| `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml` | empty templates, `allowBackup="true"` | **nothing** | out of scope |
| `MainActivity.kt`, `res/navigation/nav_graph.xml`, `res/menu/menu_main.xml` | Settings reachable from the dashboard only | **nothing** | the Settings entry point is unchanged |
| `domain/usecase/ExportLogsUseCase.kt`, `domain/model/Export*.kt` | Story 5.1 export | **nothing** | do not modify the export contract |
| `data/export/LogExportFileWriter.kt` | cache writer + FileProvider URIs | **nothing** | the export file writer is not reused for backups (a backup must not be shareable) |
| `app/src/test/.../FoodCatalogRepositoryImplTest.kt` | `FakeFoodCatalogDao` implements the DAO interface | +3 fake methods | the fake must stay in sync with the interface |

### Architecture compliance

- **AD-1 (layer boundaries):** `BackupFacts`, `BackupValidation`, `BackupErrorReason`, `CustomFoodBackup` and `BackupDatabaseUseCase` are pure Kotlin/JVM — no `android.*`, no Room imports. `DatabaseBackupManager` and `PendingRestoreApplier` live in `data/backup/` and may use `android.*` and Room. The `presentation` layer only talks to the domain use case and to the data-layer manager (the same pattern the export flow uses: the Fragment performs the IO because it needs a `Context`).
- **AD-2 (synchronous Room):** every database operation is synchronous on the main thread with `.allowMainThreadQueries()` retained — no `suspend`, no coroutines, no `Flow`. The backup touches a local file only; no spinner (DESIGN.md: no loading spinners for local operations).
- **AD-3 (domain decoupling):** the use case sees only plain data (`BackupFacts`, `CustomFoodBackup`); Room entities never leave `data/`.
- **AD-4 (use case pattern):** `BackupDatabaseUseCase` exposes `operator fun invoke()`; Koin `factory`.
- **AD-5 (view binding lifecycle):** `SettingsFragment` nulls `_binding` in `onDestroyView()`; all `LiveData` observed with `viewLifecycleOwner`; `isAdded` guarded before Snackbar / `announceForAccessibility` / `startActivity`.
- **AD-6 (central DI):** every injected dependency is declared in `AppModule.kt` only. `PendingRestoreApplier` is the single exception and it is **not injected** — it is constructed by `MainApplication` because it must run before Koin exists.
- **AD-7 (cascade deletes):** the device's custom foods are removed with `deleteCustomFoods()`; the `catalog_food_nutrients` children are removed by the FK `CASCADE` — never by hand-written child deletes. No app-table rows are deleted by this story.
- **AD-8 (FTS5):** the catalog keeps the `BundledSQLiteDriver`; the restored custom foods are written to `catalog_foods` only, so Room's FTS sync triggers index them; the FTS table is never written directly, and the catalog file is never opened with the platform SQLite.
- **NFR-1 (no network):** no HTTP client, no `INTERNET` permission, no analytics. The manifest keeps zero permissions.
- **NFR-5 (data isolation / privacy):** the backup contains **all** profiles (that is the point of a full backup) and stays in app-private storage until the user explicitly hands it to a document provider. The restore replaces the whole database — it is not profile-scoped, and the UI says so. The backup file contains passcode hashes and is never shared through the `FileProvider`.
- **NFR-6 (accessibility):** ≥ 48dp targets, content descriptions on both new buttons, TalkBack announcements for success and failure.
- **Consistency conventions:** dates are `YYYY-MM-DD` ISO strings; the backup file name uses `LocalDate.toString()`; macro values are copied verbatim (never re-scaled).
- **Documented deviation — "DAO SQL isolation":** `project-context.md` says "keep all SQLite queries isolated to DAO interfaces". This story issues raw SQL (`PRAGMA …`, `CREATE/DROP TABLE`, `SELECT identity_hash`) from `data/backup/`. This is deliberate and unavoidable: the backup, the candidate and the staged file are **not Room-managed databases**, so no DAO can serve them. The rule is still honoured for the **catalog** database, where every statement goes through `FoodCatalogDao`.

### Library and framework requirements

Pinned stack — **do not upgrade and do not add a dependency**:

- Room 3.0.3, Koin 3.5.6, Navigation 2.6.0, Material 1.10.0, AppCompat 1.6.1, AGP 9.2.1, SDK 36/35, `androidx.activity:activity-ktx` 1.8.0, `androidx.core:core-ktx` 1.10.1.
- `androidx.activity` 1.8.0 already provides the two contracts this story needs (verified by decompiling `activity-1.8.0.aar`):
  - `ActivityResultContracts.CreateDocument("application/vnd.sqlite3")` → `ACTION_CREATE_DOCUMENT` + `setType(mimeType)` + **`putExtra(EXTRA_TITLE, input)`** — the launched argument becomes the proposed file name;
  - `ActivityResultContracts.OpenDocument(arrayOf(...))` → `ACTION_OPEN_DOCUMENT` + `CATEGORY_OPENABLE` + `putExtra(EXTRA_MIME_TYPES, input)` + **`setType("*/*")`**.
- `MaterialAlertDialogBuilder` (Material 1.10.0) for the two modal alerts — already a project dependency. `show()` takes **no** `FragmentManager`; the rotation-safe pattern is the state-driven one (Dev Notes §"Restore mechanics").
- `android.database.sqlite.SQLiteDatabase` (platform) for all file-level SQL on the **main** database and its copies. It must **never** be used for `food_catalog` (FTS5).
- Room 3.0.3 exposes **no** `openHelper`/`query()`/`execSQL()` on `RoomDatabase` — do not write Room 2.x code.
- No MockK, no Robolectric, no SQLite-JDBC, no new JSON library (the backup tables are plain SQL tables, not JSON — `org.json` is not usable in JVM unit tests).

### Testing requirements

`BackupDatabaseUseCaseTest` (pure, JUnit4):

- `backupFileName` → `myfoodtracker_backup_2026-10-21.db` for `LocalDate.of(2026, 10, 21)`; no spaces, lowercase, `.db` suffix.
- every failure reason of the decision table is produced by the matching facts;
- the **order** of the checks: a file that is not a database **and** has a wrong format version reports `NOT_A_DATABASE`; a file with a wrong `user_version` **and** a wrong identity hash reports `SCHEMA_VERSION_MISMATCH`;
- `metaTablePresent = false` → `NOT_A_DATABASE` (not `UNSUPPORTED_BACKUP_VERSION`);
- `liveIdentityHash == null` (the live DB does not exist yet) **skips** check #7 but still applies check #5;
- `declaredCustomFoodCount != actualCustomFoodCount` → `INCOMPLETE_BACKUP`;
- all-good facts → `BackupValidation.Valid`.

`SettingsViewModelTest` (extended):

- the new constructor argument is `BackupDatabaseUseCase()`;
- `onBackupPreparing()` → `BackupState.Preparing`; `onBackupReady(name)` → `BackupState.Ready(name)`; `onBackupSaved(name)` → `BackupState.Saved(name)`; `onBackupFailed(reason)` → `BackupState.Error(reason)`;
- `onRestoreValidating()` → `RestoreState.Validating`; `onRestoreCandidateReady(name)` → `RestoreState.ConfirmRequired(name)`; `onRestoreInvalid(reason)` → `RestoreState.Invalid(reason)`; `onRestoreStaged()` → `RestoreState.Staged`; `onRestoreFailed(reason)` → `RestoreState.Error(reason)`;
- `confirmRestore()` advances `ConfirmRequired` → `Staged`; `cancelRestore()` resets to `Idle`;
- **`ConfirmRequired` and `Staged` survive a ViewModel re-creation** — the state is only cleared by an explicit user action (this is the regression test for the "dialog lost on rotation" disaster);
- the export behaviour of Story 5.1 is unchanged (all existing tests keep passing unchanged);
- the observer pattern terminates: clearing the **export** state from inside an observer must not loop (the existing `exportState_observerThatClearsAfterHandling_terminates` test is the model — the **new** restore states must **not** copy that clearing pattern).

`FoodCatalogRepositoryImplTest` — the fake DAO gains the three new methods; the existing tests must pass unchanged.

**No emulator.** After the JVM tests pass, flag these manual on-device checks in the completion notes:

- Backup: Settings → Backup Database → the picker opens with `myfoodtracker_backup_<date>.db` → pick a location → the file exists and its size is plausible.
- Open the backup file with a SQLite tool → it contains `meals`, `foods`, `users`, `water_logs`, `recipes`, `recipe_ingredients` with the expected row counts, plus `myfoodtracker_backup_meta` and `myfoodtracker_custom_foods` — and **no** `-wal`/`-shm` file is needed to read it.
- Backup → restore the same file → the app restarts → the data is identical (profiles, logs, water, recipes, custom foods).
- Restore a **non-backup** `.db` file → an explicit "not a MyFoodTracker backup" message and nothing changes.
- Restore a backup created by an older schema → the version-mismatch message and nothing changes.
- The restore warning dialog shows the overwrite text with **Cancel** and **Overwrite & Restore**; **Cancel** changes nothing.
- After **Overwrite & Restore** → the restart prompt → **Restart now** → the app closes → reopening it shows the restored data and the passcode screen.
- A custom food created before the backup is searchable (FTS) after the restore; a custom food created on the target device before the restore is **gone** after the restore.
- The remembered session is cleared after a restore (the passcode screen is shown, not the dashboard).
- Every pre-existing feature still works (log food, quick-add, water, swipe-delete + undo, search, custom food, recipe logging, passcode auth).

### Previous story intelligence

Story 5.1 (`5-1-csv-and-txt-log-export.md`, done, commit `c8eb9d4`) and Story 4.2 (`4-2-one-tap-recipe-logging.md`, commit `4940143`):

- **The package root is `com/example/myfoodtracker`** — not `sergeevgk/myfoodtracker` (the architecture spine's structural seed is stale on this point).
- **The real schema is `meals` + `foods`** — not the PRD addendum's `intake_logs`. The table names this story must know: `meals`, `foods`, `users`, `user_daily_goals`, `water_logs`, `recipes`, `recipe_ingredients` (main DB) and `catalog_foods`, `catalog_food_nutrients`, `catalog_food_serving_units`, `catalog_foods_fts` (catalog).
- Story 5.1's established patterns to **copy**: the Fragment performs the file IO and the Intents while the ViewModel holds only the state; `LogExportFileWriter` lives in `data/` because it needs a `Context`; the `StartActivityForResult` + `ContentResolver.openOutputStream(uri, "wt")` pattern; the `Snackbar` + `announceForAccessibility` success pattern; the "clear the state after handling" pattern **for the export state only** (do not copy it into the restore state — see Subtask 4.5).
- Review patches already applied in 5.1 — **do not revert** them: the TalkBack announcements on empty/error, the rotation-safe date-range picker listener, the whitespace sanitization in the TXT renderer, the `"wt"` mode, the broad exception catch in `SettingsViewModel.export()`, and the year-aware range formatting.
- Deferred, leave them open: `5.1-F1` (water logs are not part of the CSV export), `5.1-F2` (the pending payload is lost on rotation — **this story must not repeat the pattern**; the staged restore lives on disk), `4.2-D1`, `4.1-D1..D4`, `3.3-D1`, `3.2-D1/D2`, `3.1-F1`, `2.4-D1`, `2.5-D1`, `1.2-D1..D5`.
- Test incident: a fake whose backing-property name matched an overridden function caused a JVM signature clash — name fake backing fields differently from the function.
- Build incident: `:app:mergeDebugResources` NPE from a stale incremental blob → run `:app:clean` once and rebuild. Do not rewrite unrelated XML.
- The worktree is dirty with CRLF-only diffs on many files. Do **not** `git add -A`. Stage only the files this story creates or edits.

### Git intelligence

Recent commits: `c8eb9d4` Story 5.1, `4940143` Story 4.2, `974cf2d` Story 4.1, `69de783` Story 3.5, `24fb76d` Story 3.3, `5ecb1af` Story 3.2.

Conventions to copy from `c8eb9d4` (21 files, +2659 lines): one new domain model + use case + its JVM test, one new ViewModel + its test, the Fragment/layout extension, the `AppModule` additions, the `strings.xml` additions, and the story file itself. That is exactly the shape this story should have (plus the two data-layer classes, the `MainApplication` cold-start hook and the DAO additions).

`local.properties` `sdk.dir` points at the Windows SDK; the AGP warning on Linux is harmless — do not edit that file.

### Latest technical information

Verified against the current Android/SQLite platform documentation and the decompiled artifacts of the pinned versions (checked 2026-10):

- **WAL and file copies.** In WAL mode the committed data can live in the `-wal` sidecar until a checkpoint. SQLite's own documentation recommends the **Online Backup API** or `VACUUM INTO` for backups of a live database; the Room-idiomatic equivalent is `PRAGMA wal_checkpoint(TRUNCATE)` followed by a file copy. The pragma returns `(busy, log, checkpointed)` — a non-zero `busy` means a reader blocked the checkpoint and the copy would be incomplete, so it must be treated as an error, not ignored. Room 3.0.3 does set `PRAGMA journal_mode = WAL` (`BaseRoomConnectionManager`), so this is the normal path.
- **A copy of a WAL database is still a WAL database.** The WAL flag lives in the file header (bytes 18/19 = 2), so a byte copy opens in WAL mode and writes to a new `-wal` sidecar. Convert the **copy** to `journal_mode = DELETE` before writing to it, then delete any `-wal`/`-shm` sidecar. The same applies to the staged restore file.
- **Sidecars are part of the "database".** A SQLite database in WAL mode is three files: `<db>`, `<db>-wal`, `<db>-shm` (the suffix is **appended**, so `meals_database-wal`, not `meals_database.wal`). Restoring only the main file while stale sidecars survive is the classic way to get silent corruption: SQLite would replay the old WAL frames against the new file. `Context.deleteDatabase(name)` removes the database **and** its sidecars in one call — use it before writing the restored file.
- **Room's integrity check.** Room stores the compiled schema's identity hash in `room_master_table` (`id = 42`, column `identity_hash`) and verifies it on every open; a mismatch throws `IllegalStateException("Room cannot verify the data integrity…")`. Comparing the candidate's hash with the live database's hash before the swap is the only reliable way to avoid a startup crash.
- **`fallbackToDestructiveMigration()`** deletes the data instead of migrating when the `user_version` differs. Because the app uses it, a restored file with the wrong `user_version` would be wiped on the next open — hence the mandatory version check before the swap.
- **Room 3.0.3 removed the `openHelper` accessor.** `androidx.room3.RoomDatabase` exposes `getPath()` and no raw-SQL API; `openHelper.writableDatabase` is Room 2.x. Use the platform SQLite on the file path (the main DB is a framework-driver database) or, if a DAO is unavoidable, Room 3's `androidx.room3.RawQuery` annotation (which lives in `room3-common`) — but a DAO cannot serve the non-Room backup/candidate/staged files anyway.
- **`VACUUM INTO`** (SQLite ≥ 3.27; the platform SQLite on API 35 is ≥ 3.39) creates a consistent single-file snapshot of a live database and is an alternative to the checkpoint + copy. It requires the destination file to **not** exist, and the target is an expression (a bound parameter is accepted by SQLite, but a quoted literal is the portable form). It is **not** required here — the checkpoint + copy path keeps the file byte-faithful, which the restore relies on.
- **SAF.** `ACTION_CREATE_DOCUMENT` and `ACTION_OPEN_DOCUMENT` need `CATEGORY_OPENABLE`; `EXTRA_TITLE` pre-fills the new file's name (only `ACTION_CREATE_DOCUMENT`); `EXTRA_MIME_TYPES` filters the picker and requires `type = "*/*"` to behave consistently across providers. The returned URI is a `content://` URI backed by a `DocumentsProvider` and is written with `ContentResolver.openOutputStream(uri, "wt")` — `"wt"` truncates, which matters when the provider reuses an existing document. **No storage permission is needed** for either flow.
- **Killing the process for a restart.** There is no supported "restart the app" API. The standard approach is `Process.killProcess(Process.myPid())` after the user confirms; the app is then relaunched by the user. The work that must survive the kill has to be **on disk** (this story stages the restore in `filesDir`), never in memory.
- **`android:allowBackup="true"` with empty rules** means the platform's auto-backup currently includes `databases/` and `files/`. The seeded catalog is an **asset**, so it is not part of the auto-backup; the staged restore file in `filesDir` is. The backup rules are **not** changed by this story — noted so it is a conscious decision.

### Project context reference

Follow `_bmad-output/project-context.md` and `AGENTS.md`:

- Before any Gradle call: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`, Linux `java` 21, `ANDROID_HOME=$HOME/Android/Sdk`. The AGP `sdk.dir` warning from the Windows path in `local.properties` is harmless — do not "fix" that file.
- Verify with `./gradlew :app:compileDebugSources` and `./gradlew :app:testDebugUnitTest`; assemble with `./gradlew :app:assembleDebug` and `./gradlew :app:assembleDebugAndroidTest`. **Do not launch the emulator.** State explicitly in the completion notes which checks need manual on-device verification.
- ViewModels must never hold a `Context` or views — the Fragment performs the file IO and the Intents.
- **This story must not change the Room schema.** No entities, no columns, no version bump, no migration, no regenerated `app/schemas/**`.
- UseCases are factory-injected classes with `operator fun invoke()`.
- Layout files use `fragment_` / `item_` prefixes; view IDs are snake_case; all copy lives in `res/values/strings.xml`; all colors in `res/values/colors.xml`; touch targets ≥ 48dp.
- Data entities are mapped to domain models inside the repository layer; entities never leak past `data/`.

### Project Structure Notes

NEW:

- `app/src/main/java/com/example/myfoodtracker/domain/model/BackupErrorReason.kt`
- `app/src/main/java/com/example/myfoodtracker/domain/model/BackupFacts.kt`
- `app/src/main/java/com/example/myfoodtracker/domain/model/BackupValidation.kt`
- `app/src/main/java/com/example/myfoodtracker/domain/model/CustomFoodBackup.kt`
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/BackupDatabaseUseCase.kt`
- `app/src/main/java/com/example/myfoodtracker/data/backup/DatabaseBackupManager.kt`
- `app/src/main/java/com/example/myfoodtracker/data/backup/PendingRestoreApplier.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/settings/model/BackupState.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/settings/model/RestoreState.kt`
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/BackupDatabaseUseCaseTest.kt`

UPDATE:

- `app/src/main/java/com/example/myfoodtracker/presentation/ui/settings/SettingsFragment.kt` — the backup/restore section
- `app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/SettingsViewModel.kt` — the two new states and the third dependency
- `app/src/main/java/com/example/myfoodtracker/di/AppModule.kt` — 2 new bindings + the ViewModel binding
- `app/src/main/java/com/example/myfoodtracker/MainApplication.kt` — the cold-start restore hook
- `app/src/main/java/com/example/myfoodtracker/data/dao/FoodCatalogDao.kt` — 3 custom-food queries
- `app/src/main/res/layout/fragment_settings.xml` — the Backup & Restore card
- `app/src/main/res/values/strings.xml` — the new strings
- `app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/SettingsViewModelTest.kt` — the new constructor argument + the new state tests
- `app/src/test/java/com/example/myfoodtracker/data/repository/FoodCatalogRepositoryImplTest.kt` — the fake DAO
- `_bmad-output/implementation-artifacts/deferred-work.md` — close `3.1-F2`

Explicitly NOT modified: `AppDatabase.kt`, `FoodCatalogDatabase.kt`, `MealDao.kt`, `MealEntryEntity.kt`, `FoodEntity.kt`, `RecipeDao.kt`, `RecipeEntity.kt`, `RecipeIngredientEntity.kt`, `WaterLogDao.kt`, `WaterLogEntity.kt`, `UserProfileDao.kt`, `UserProfileEntity.kt`, `UserDailyGoalEntity.kt`, `CatalogFood*.kt`, `MealRepository.kt`, `MealRepositoryImpl.kt`, `FoodCatalogRepository.kt`, `FoodCatalogRepositoryImpl.kt`, `SessionRepository.kt`, `SessionRepositoryImpl.kt`, `SharedPrefsSessionStorage.kt`, `LogExportFileWriter.kt`, `ExportLogsUseCase.kt`, `ExportFormat.kt`, `ExportPayload.kt`, `DashboardViewModel.kt`, `DashboardFragment.kt`, `MainActivity.kt`, `nav_graph.xml`, `menu_main.xml`, `colors.xml`, `dimens.xml`, `themes.xml`, `file_paths.xml`, `backup_rules.xml`, `data_extraction_rules.xml`, `AndroidManifest.xml`, `app/build.gradle.kts`, `libs.versions.toml`, `app/schemas/**`, `tools/seed/**`, the catalog asset.

Deviations from the architecture spine's structural seed (intentional, must be followed):

- The seed puts `BackupDatabaseUseCase` in `domain/usecase/` — followed. It does not name a file/DB helper class; `data/backup/` is the new home for the Android-dependent database IO (mirroring `data/export/LogExportFileWriter.kt` from Story 5.1).
- The seed's structural tree lists `data/db/AppDatabase.kt` as "pre-seeded asset loader" — stale (the asset loader is `FoodCatalogDatabase`); follow the code.

### Strings

New strings (add to `res/values/strings.xml` in a new "Backup & Restore (Epic 5, Story 5.2)" block):

| Name | Value |
|---|---|
| `settings_section_backup` | `Backup &amp; Restore` |
| `settings_backup_description` | `Create a full local copy of your database, or restore one from a backup file. Everything stays on this device.` |
| `settings_backup_button` | `Backup Database` |
| `settings_restore_button` | `Restore Database` |
| `backup_success_format` | `Database backed up to %1$s` |
| `backup_error_prepare` | `Could not prepare the database backup` |
| `backup_error_write` | `Could not save the database backup` |
| `backup_error_read` | `Could not read the selected file` |
| `restore_confirm_title` | `Restore Database?` |
| `restore_confirm_message` | `Restoring a database backup will overwrite your current local logs. Proceed?` |
| `restore_confirm_positive` | `Overwrite &amp; Restore` |
| `restore_restart_title` | `Restart required` |
| `restore_restart_message` | `MyFoodTracker must restart to apply the restored data. Restart now?` |
| `restore_restart_positive` | `Restart now` |
| `restore_error_not_a_database` | `The selected file is not a MyFoodTracker backup` |
| `restore_error_integrity` | `The selected backup is damaged and cannot be restored` |
| `restore_error_version` | `This backup was created by an incompatible app version` |
| `restore_error_schema_version` | `This backup was created with a different database schema version` |
| `restore_error_schema` | `This backup was created with a different database schema` |
| `restore_error_missing_tables` | `This backup is missing required tables` |
| `restore_error_incomplete` | `This backup is incomplete and cannot be restored` |
| `restore_error_failed` | `Could not restore the database` |
| `a11y_backup_button` | `Back up the local database to a file` |
| `a11y_restore_button` | `Restore the local database from a backup file` |
| `a11y_backup_success` | `Database backup saved as %1$s` |
| `a11y_restore_invalid` | `This file cannot be restored: %1$s` |

**`BackupErrorReason` → string mapping** (implemented in the Fragment, the same pattern as `SettingsViewModel.mapErrorMessage` from Story 5.1):

| Reason | String |
|---|---|
| `NOT_A_DATABASE` | `restore_error_not_a_database` |
| `INTEGRITY_CHECK_FAILED` | `restore_error_integrity` |
| `UNSUPPORTED_BACKUP_VERSION` | `restore_error_version` |
| `SCHEMA_VERSION_MISMATCH` | `restore_error_schema_version` |
| `SCHEMA_MISMATCH` | `restore_error_schema` |
| `MISSING_TABLES` | `restore_error_missing_tables` |
| `INCOMPLETE_BACKUP` | `restore_error_incomplete` |
| `IO_ERROR` (backup path) | `backup_error_prepare` (preparation) / `backup_error_write` (writing to the chosen URI) |
| `IO_ERROR` (restore path) | `restore_error_failed` |

Reuse, do not duplicate: `action_cancel`, `settings_title`, `settings_attribution`.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 5.2] — Settings screen → "Backup Database" → the entire SQLite database file is saved via the system file picker; "Restore Database" → a `.db` file → a modal alert warns of the overwrite, validates the schema integrity, overwrites the active DB and prompts an application restart.
- [Source: _bmad-output/planning-artifacts/prds/prd-MyFoodTracker-2026-08-08/prd.md#FR-13] — "Exporting creates a single DB backup file saved via native system sharing. Restoring validates database integrity and schemas before overwriting the active DB, and prompts for an application restart." (Note: the epic's AC says "via system file picker" — this story uses the SAF **create-document** picker, not the Sharesheet, because a database backup must not be handed to arbitrary apps by default; the file is still a normal local file whose location the user chooses.)
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-MyFoodTracker-2026-09-06/EXPERIENCE.md#Flow-4] — "User taps `[Backup Database]` → native system file picker opens; user saves `myfoodtracker_backup_2026-09-06.db` → on a new device the user taps `[Restore Database]` → the app validates the schema, restores the database and reloads the active profile → the user sees the full historical logs restored completely offline."
- [Source: _bmad-output/planning-artifacts/ux-designs/ux-MyFoodTracker-2026-09-06/EXPERIENCE.md#State-Patterns] — "Database Restore Warning: Modal alert: 'Restoring a database backup will overwrite current local logs. Proceed?' with explicit `[Cancel]` and `[Overwrite & Restore]` buttons."
- [Source: _bmad-output/planning-artifacts/architecture/architecture-MyFoodTracker-2026-09-06/ARCHITECTURE-SPINE.md#Capability-Map] — "FR-13: Local DB Backup and Restore → `domain/usecase/BackupDatabaseUseCase.kt` → governed by AD-2, AD-4."
- [Source: _bmad-output/implementation-artifacts/deferred-work.md#3.1-F2] — "Story 5.2 backup/restore must cover two databases … it must later include the `food_catalog` file (at minimum the `is_custom = 1` rows, since the bundled asset itself is reinstallable)."
- [Source: _bmad-output/project-context.md] — FTS5 availability, synchronous Room access, entity/domain decoupling, ViewModel rules, no-emulator policy, centralised resources.
- [Source: _bmad-output/implementation-artifacts/5-1-csv-and-txt-log-export.md] — the established Settings/export patterns this story builds on.

## Dev Agent Record

### Agent Model Used

DeepSeek V4.1 Flash Free (`opencode` / `deepseek-v4.1-flash-free`), running the `bmad-dev-story` workflow.

### Debug Log References

- Environment checked before every Gradle call per `AGENTS.md`: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`, Linux `java 21.0.12.1`, `ANDROID_HOME=$HOME/Android/Sdk`. The AGP `sdk.dir` warning from the Windows path in `local.properties` was left untouched.
- One Kotlin incremental-compilation cache exception (`IncrementalJvmCompilerRunner` / `AppendableSetBasicMap`) occurred during the first `:app:testDebugUnitTest` compile — the same class of stale-blob incident recorded in Story 5.1. Gradle recovered within the same run; a second `:app:testDebugUnitTest --rerun` executed the suite cleanly with no source changes.
- `:app:compileDebugSources`, `:app:testDebugUnitTest`, `:app:assembleDebug` and `:app:assembleDebugAndroidTest` all report `BUILD SUCCESSFUL`. Unit suite: **334 tests, 0 failures / 0 errors** (new `BackupDatabaseUseCaseTest`: 22 tests; `SettingsViewModelTest`: 32 tests, previously 19).
- No emulator was launched (project policy). All SQLite-level behaviour is covered by the manual on-device checklist below.

### Completion Notes List

**What was implemented**

- Domain (pure Kotlin): `BackupErrorReason`, `BackupFacts`, `BackupValidation`, `CustomFoodBackup` and `BackupDatabaseUseCase` (`backupFileName()` + the eight-step validation decision table, first failure wins). The backup file-format constants (table names, meta keys, `format_version = 1`, required tables, `EXPECTED_USER_VERSION = 4`) live only in `BackupDatabaseUseCase.companion object` and are referenced by the data layer — no duplicated SQL/Kotlin literals.
- Data: `DatabaseBackupManager` (WAL checkpoint with one retry → byte copy → `journal_mode = DELETE` on the copy → create/fill `myfoodtracker_backup_meta` + `myfoodtracker_custom_foods` → close → delete sidecars; `writeBackupTo` with `"wt"`; candidate copy + fact collection; durable `filesDir/pending_restore/` staging via `.tmp` + rename + `fd.sync()`; cache/staging cleanup) and `PendingRestoreApplier` (cold-start swap: journal conversion, integrity check, read custom foods, drop the two backup tables, `Context.deleteDatabase("meals_database")`, write the staged file, clear `auth_session_prefs` with `commit()`, delete staging; then DAO-based custom-food replacement after Koin starts).
- Cold start: `MainApplication.onCreate()` applies the pending swap **before** `startKoin`, resolves `FoodCatalogDao` **only** when custom foods were carried, and both steps are wrapped in try/catch so a bad staged file can never crash startup.
- Settings: third card "Backup & Restore" between Export Logs and the attribution footer, both buttons with 48dp targets + content descriptions, `CreateDocument("application/vnd.sqlite3")` / `OpenDocument(arrayOf(...))` pickers, the overwrite warning and restart prompt built with `MaterialAlertDialogBuilder`, success/failure Snackbar + TalkBack announcements.
- State: `BackupState` / `RestoreState`; `ConfirmRequired` and `Staged` are never cleared by an observer, so a rotation re-shows the prompt (covered by `restoreDialogStates_areNotClearedByObservation`).
- DAO: three custom-food queries; `deleteCustomFoods()` relies on the nutrient FK `CASCADE`.
- Deferred item **3.1-F2 is resolved**; forward notes `5.2-F1..F4` added to `deferred-work.md`.

**Deliberate decisions / small additions (all inside the story's design)**

- `stageRestore` writes to `meals_database.tmp` and renames, so a failure cannot leave a partially staged restore (AC #10).
- The Fragment stages the file **before** flipping the state (`viewModel.onRestoreStaged()`), so the restart prompt is only shown once the restore is durably staged. `confirmRestore()` is implemented as the `ConfirmRequired → Staged` state transition and covered by its own tests.
- `writeBackupTo` best-effort deletes the user document through `DocumentsContract.deleteDocument` when streaming fails, so a failed write does not leave a half-written backup in the chosen location.
- One extra string, `restore_restart_message_replace_notice`, was added because AC #6 requires the restart prompt to state that the current local data is replaced, while the prescribed `restore_restart_message` literal is kept intact.
- A restore candidate that cannot be read at all reports `backup_error_read` ("Could not read the selected file") instead of the generic `restore_error_failed`; every `BackupErrorReason` still maps through `restoreErrorString()`.
- Added two small `DatabaseBackupManager` helpers not named in Subtask 2.1: `cachedCandidateFile()` (hands the validated candidate to `stageRestore`) and `deleteCachedBackup(fileName)` (deletes a cancelled backup without a `File` in a Fragment field).

**Scope preserved**

- `AppDatabase` stays at `version = 4`, `FoodCatalogDatabase` at `version = 1`; no entity, column, migration or `app/schemas/**` regeneration (the only schema-file diff in the worktree is the pre-existing CRLF-only diff from an earlier session).
- No new Gradle dependency, no manifest permission, no `file_paths.xml`/backup-rule change, no network code.
- The Story 5.1 export flow, its rotation-safe date-range picker and its "clear the state after handling" behaviour are untouched.

**Manual on-device verification required (not run — no emulator per project policy)**

1. Settings → **Backup Database** → the picker opens pre-filled with `myfoodtracker_backup_<yyyy-MM-dd>.db`; save it and confirm the file exists with a plausible size.
2. Open the saved file with a SQLite tool: it contains `meals`, `foods`, `users`, `water_logs`, `recipes`, `recipe_ingredients` with the expected row counts plus `myfoodtracker_backup_meta` and `myfoodtracker_custom_foods`, and it reads without any `-wal`/`-shm` file next to it.
3. Log a meal and back up immediately: the newest row is present (WAL checkpoint works).
4. Backup → restore the same file → **Restart now** → reopen: data is identical (profiles, daily goals, logs, water, recipes, custom foods) and the passcode screen is shown.
5. Restore a non-backup `.db` file → "The selected file is not a MyFoodTracker backup" and nothing changes.
6. Restore a backup with a different schema version → the version-mismatch message and nothing changes.
7. The overwrite warning shows the required text with **Cancel** / **Overwrite & Restore**; cancel changes nothing and shows no error.
8. Cancel either system picker → no file is written and no error is shown.
9. Rotate the device while the overwrite warning or the restart prompt is visible → the dialog is shown again.
10. A custom food created before the backup is FTS-searchable after the restore; a custom food created on the target device before the restore is gone.
11. The remembered session is cleared after a restore (passcode screen, not the dashboard).
12. Every pre-existing feature still works against restored data: dashboard logging, quick-add, water, swipe-delete + undo, search, custom food creation, recipe logging, passcode auth and remember-me.

### File List

NEW:

- `app/src/main/java/com/example/myfoodtracker/domain/model/BackupErrorReason.kt`
- `app/src/main/java/com/example/myfoodtracker/domain/model/BackupFacts.kt`
- `app/src/main/java/com/example/myfoodtracker/domain/model/BackupValidation.kt`
- `app/src/main/java/com/example/myfoodtracker/domain/model/CustomFoodBackup.kt`
- `app/src/main/java/com/example/myfoodtracker/domain/usecase/BackupDatabaseUseCase.kt`
- `app/src/main/java/com/example/myfoodtracker/data/backup/DatabaseBackupManager.kt`
- `app/src/main/java/com/example/myfoodtracker/data/backup/PendingRestoreApplier.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/settings/model/BackupState.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/settings/model/RestoreState.kt`
- `app/src/test/java/com/example/myfoodtracker/domain/usecase/BackupDatabaseUseCaseTest.kt`

UPDATED:

- `app/src/main/java/com/example/myfoodtracker/MainApplication.kt`
- `app/src/main/java/com/example/myfoodtracker/data/dao/FoodCatalogDao.kt`
- `app/src/main/java/com/example/myfoodtracker/di/AppModule.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/ui/settings/SettingsFragment.kt`
- `app/src/main/java/com/example/myfoodtracker/presentation/viewmodel/SettingsViewModel.kt`
- `app/src/main/res/layout/fragment_settings.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/example/myfoodtracker/data/repository/FoodCatalogRepositoryImplTest.kt`
- `app/src/test/java/com/example/myfoodtracker/presentation/viewmodel/SettingsViewModelTest.kt`
- `_bmad-output/implementation-artifacts/deferred-work.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
- `_bmad-output/implementation-artifacts/5-2-offline-full-database-backup-and-restore.md`

### Change Log

- 2026-10-22: Implemented Story 5.2 — backup format + validation use case, `DatabaseBackupManager`, cold-start `PendingRestoreApplier`, Backup & Restore Settings card with two system pickers and two modal dialogs, three `FoodCatalogDao` custom-food queries, DI bindings, and the JVM test suites (use case + ViewModel). Resolved deferred item 3.1-F2. Status: review.

