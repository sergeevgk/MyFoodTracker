# Food catalog seed pipeline (Story 3.1)

Builds the pre-populated, offline food catalog used by the FTS5 search engine:
`app/src/main/assets/databases/food_catalog.db` (50,500 foods, ~16 MB).

## Data source

USDA FoodData Central (FDC), public domain under **CC0 1.0**:
https://fdc.nal.usda.gov/download-datasets

Pinned datasets:

| Dataset | Zip | Zip size | Foods used |
| --- | --- | --- | --- |
| Foundation Foods 2026-04-30 | `FoodData_Central_foundation_food_csv_2026-04-30.zip` | ~3.7 MB | 469 (`foundation_food` rows only) |
| SR Legacy 2018-04 | `FoodData_Central_sr_legacy_food_csv_2018-04.zip` | ~6.7 MB | 7,793 (all) |
| FNDDS 2019-2020 (2022-10-28) | `FoodData_Central_survey_food_csv_2022-10-28.zip` | ~4.3 MB | 5,624 (all) |
| Branded Foods 2026-04-30 | `FoodData_Central_branded_food_csv_2026-04-30.zip` | ~428 MB | 36,614 (filtered, fills to 50,500) |

## Regenerate

```sh
python3 tools/seed/build_food_catalog_db.py
```

Stdlib only (`csv`, `sqlite3`, `urllib`, `zipfile`, `hashlib`, `json`,
`datetime`). Python's bundled SQLite must have FTS5 (checked at startup).
First run downloads ~450 MB into `tools/seed/.cache/` (git-ignored);
re-runs reuse the cache. SHA-256 digests are recorded in
`tools/seed/.cache/SHA256SUMS`. Takes ~2 minutes, prints row counts, the
FTS sanity probe, file size, and duration.

## Expected output

- `SELECT COUNT(*) FROM catalog_foods` = 50,500 (requirement: ≥ 50,000)
- `catalog_food_nutrients` = 50,500 rows (one per food)
- `catalog_food_serving_units` ≈ 109,000 rows
- `PRAGMA integrity_check` = `ok`
- DB size ≈ 16 MB, `journal_mode = DELETE` (self-contained for `createFromAsset`)

## Provenance

- Nutrient values are per 100 g as published in FDC `food_nutrient.csv`
  (kcal 1008, fallback 2047/2048; protein 1003; carbs 1005; fat 1004;
  fiber 1079; sugar 2000; sodium 1093; missing → 0.0).
- `catalog_foods.id` is sequential from 1 in dataset order
  (Foundation → SR Legacy → FNDDS → Branded); `created_at`/`updated_at`
  are the fixed FDC release-date epoch (reproducible builds).
- Schema is generated from the Room-exported
  `app/schemas/com.example.myfoodtracker.data.db.FoodCatalogDatabase/1.json`
  plus the four `room_fts_content_sync_*` triggers mirrored from
  `FoodCatalogDatabase_Impl.createAllTables`.

## What is committed

- `tools/seed/build_food_catalog_db.py`, this README — committed.
- `app/src/main/assets/databases/food_catalog.db` — committed (the asset).
- `tools/seed/.cache/` (raw downloads, hashes) — git-ignored, never committed.
