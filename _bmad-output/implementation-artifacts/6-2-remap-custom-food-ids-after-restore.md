---
story_id: 6.2
story_key: 6-2-remap-custom-food-ids-after-restore
epic: 6
status: backlog
created: 2026-10-25
baseline_commit: 7fee17042735dbaf77040d24d5776cb22c976e4b
---

# Story 6.2: Remap Custom-Food IDs in recipe_ingredients After Restore

Status: backlog

## Story

As a user restoring my database backup,
I want my recipes to still reference the correct custom foods,
So that restoring does not silently corrupt recipe macro data.

## Acceptance Criteria

1. **Given** `PendingRestoreApplier` re-inserts the backup's custom foods with **freshly allocated IDs** through `FoodCatalogDao` (to avoid collision with the seeded catalog asset), **When** each row is inserted, **Then** an old-ID → new-ID mapping is captured for every re-inserted `is_custom = 1` food (resolves **5.2-D1**, RecipeIngredientEntity.kt flagged in register).
2. **Given** the mapping, **When** custom foods are re-inserted and before the first recipe read, **Then** `recipe_ingredients.catalog_food_id` rows referencing old custom-food IDs are `UPDATE`d to the new IDs inside the live `meals_database` (recipe entity DAO UPDATE — no direct SQL outside DAOs).
3. **Given** a recipe ingredient whose old catalog food ID is not in the mapping (custom food deleted before backup), **When** remapping runs, **Then** the ingredient row is removed explicitly and its calorie/macro total is recomputed on next display — never left dangling against a nonexistent ID.
4. **Given** the restore flow end, **When** remapping is complete, **Then** the same on-device path verifies: search a restored recipe, log it, macro totals equal the pre-backup values.
5. **Given** `res/xml/backup_rules.xml` / `data_extraction_rules.xml` unchanged since 5.2, **When** the platform auto-backup config runs, **Then** `filesDir/pending_restore/` is **excluded** from cloud upload (resolves **5.2-F4** in this story; MainApplication.kt / res/xml flagged in register).

## Tasks / Subtasks

- [ ] Task 1: Capture mapping during re-insert (AC: #1)
  - [ ] `PendingRestoreApplier` / `DatabaseBackupManager`: return/create `Map<Long, Long>` old custom-food ID → fresh ID from the `FoodCatalogDao` insert loop.
- [ ] Task 2: Remap `recipe_ingredients` (AC: #2, #3)
  - [ ] Add DAO update method(s): batch remap + explicit remove of unmatched custom-ID ingredient rows.
  - [ ] Run only when mapping is non-empty; no-op preserves today's seeded-ID behavior.
- [ ] Task 3: Backup-rules exclusion (AC: #5)
  - [ ] Add `<exclude ... path="pending_restore/" />` (or domain-tag equivalent) to `backup_rules.xml` / `data_extraction_rules.xml`.
- [ ] Task 4: Tests (AC: #1–#3)
  - [ ] JVM unit test: mapping logic + remap decision table (matched / unmatched / empty map).
  - [ ] `testDebugUnitTest`, `compileDebugSources` per project rules.
  - [ ] ⚠️ **Manual on-device verification required (AC: #4)**: backup → log recipe with custom food → modify → restore → log recipe → compare macros. No emulator per project policy.

## Dev Notes

- Source register: `deferred-work.md` **5.2-D1** and **5.2-F4**.
- Restore path context: story 5-2 AC #7 (restart-time apply, custom foods via existing `FoodCatalogDao` so FTS5 triggers index them — never write `catalog_foods_fts` directly).
- The `meals_database` file itself is replaced wholesale from the backup — that is why the remap must run **after** Room opens the file, inside DAOs, not on the staged copy.
- Room schema of `recipe_ingredients` is unchanged → no `AppDatabase` version bump, no schema export regeneration.
