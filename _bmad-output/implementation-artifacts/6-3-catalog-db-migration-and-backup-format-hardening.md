---
story_id: 6.3
story_key: 6-3-catalog-db-migration-and-backup-format-hardening
epic: 6
status: backlog
created: 2026-10-25
baseline_commit: 7fee17042735dbaf77040d24d5776cb22c976e4b
---

# Story 6.3: Catalog DB Migration & Backup-Format Hardening

Status: backlog

## Story

As a user,
I want a catalog app update or backup-format change to never delete my custom foods or their serving units,
So that my food library survives upgrades and round-trips through backup/restore.

## Acceptance Criteria

1. **Given** a future `FoodCatalogDatabase` version bump with `fallbackToDestructiveMigration()`, **When** the destructive re-copy of the bundled asset runs, **Then** every `is_custom = 1` `catalog_foods` row plus its `catalog_food_nutrients` row survives (resolves **3.1-F1**, di/AppModule.kt flagged in register).
2. **Given** implementation design, **When** the migration is built, **Then** either (a) custom rows are exported before re-copy and re-imported after, or (b) custom foods move to a separate table/DB — no orphaned `catalog_food_nutrients`, no FTS index inconsistency (re-index through Room triggers, never write `catalog_foods_fts` directly).
3. **Given** the backup format, **When** a v2 backup is produced, **Then** `myfoodtracker_backup_meta.format_version = 2` and the file additionally carries the `catalog_food_serving_units` rows of custom foods (resolves **5.2-F1**, data/backup/DatabaseBackupManager.kt flagged in register).
4. **Given** restore validation (`BackupErrorReason` decision table from story 5-2), **When** a v1 backup is validated, **Then** it remains valid (accept format_version ∈ {1, 2}); a v1 file simply has no serving-unit data to restore.
5. **Given** a v2 restore on a device with custom foods, **When** restore completes, **Then** restored custom foods that gained serving units keep them (format round-trip proven by tests).

## Tasks / Subtasks

- [ ] Task 1: 3.1-F1 migration design + implementation (AC: #1, #2)
  - [ ] Decide export/re-import vs separate storage (prefer export/re-import: smaller diff, keeps `createFromAsset` flow).
  - [ ] Wire into the future version bump path — implement now, exercised by a migration unit test simulating the bump.
- [ ] Task 2: Backup format v2 (AC: #3–#5)
  - [ ] Extend `DatabaseBackupManager` section list with `catalog_food_serving_units` (custom foods only).
  - [ ] Bump `format_version` to 2; extend `BackupFacts`/validation decision table to accept {1, 2} (domain/model/BackupErrorReason.kt, BackupFacts.kt unchanged in shape).
  - [ ] Restore-side writer for serving units.
- [ ] Task 3: Tests
  - [ ] JVM unit tests: validation accepts v1 + v2; serving-unit round-trip above the JVM-testable layers (staging/validation decision table).
  - [ ] ⚠️ **Manual on-device verification required**: full backup → custom food with serving unit → restore → verify unit preserved. No emulator per project policy.

## Dev Notes

- Source register: `deferred-work.md` **3.1-F1** (forward note from story 3-1), **5.2-F1** (forward note from story 5-2).
- Both guard the same invariant: **user-added data must never be deleted by an upgrade path**.
- `format_version = 2` decision is safe only because validation currently rejects unknown versions (`UNSUPPORTED_BACKUP_VERSION`) — old app versions reading a v2 file fail cleanly with an explicit message; that is acceptable per 5-2 AC #4.3.
