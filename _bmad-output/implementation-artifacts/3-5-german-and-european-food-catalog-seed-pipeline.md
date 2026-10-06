---
baseline_commit: e0417eccac9cb4054d087b2c3d18ef5d226d9651
---

# Story 3.5: German & European Food Catalog Seed Pipeline (BLS 4.0 + Open Food Facts Germany)

Status: ready-for-dev

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user in Germany/Europe,
I want the bundled offline food catalog to contain familiar German staple foods and popular supermarket branded items,
So that my offline food search immediately finds local German foods, brands, and measurements.

## Acceptance Criteria

1. **Given** the seed pipeline script (`tools/seed/build_food_catalog_db.py`), **When** run with standard Python 3, **Then** it downloads and caches the pinned Bundeslebensmittelschlüssel (BLS 4.0, Max Rubner-Institut) dataset and the curated Open Food Facts German products dump into `tools/seed/.cache/`, validates SHA-256 checksums, and deterministically generates `food_catalog.db`.
2. **Given** the generated `food_catalog.db`, **When** inspected, **Then** it conforms exactly to the `FoodCatalogDatabase` version 1 schema (`catalog_foods`, `catalog_food_nutrients`, `catalog_food_serving_units`, `catalog_foods_fts`) with all four Room 3 FTS5 triggers (`room_fts_content_sync_*`) created and verified.
3. **Given** the generic catalog foods layer, **When** extracted from BLS 4.0, **Then** all ~7,140 staple foods are included with `brand = NULL`, `barcode = NULL`, German food descriptions, and per-100g nutrient values accurately mapped to `catalog_food_nutrients` (energy/kcal, protein, carbohydrates, fat, dietary fiber, sugar, sodium).
4. **Given** the branded products layer, **When** filtered from Open Food Facts Germany (`en:germany`), **Then** 15,000–25,000 top branded foods with non-empty barcodes (EAN/GTIN), non-empty brand names, and complete macronutrient values are included. Deduplication applies to barcode and `(lower(name), lower(brand))`.
5. **Given** the database asset at `app/src/main/assets/databases/food_catalog.db`, **When** the app runs offline and performs FTS5 searches via `SearchFoodUseCase`, **Then** German search queries (e.g. `"Vollmilch"`, `"Haferflocken"`, `"Magerquark"`) and brand searches (e.g. `"Alpro"`, `"Haribo"`) return relevant results with query latency `< 50ms`.
6. **Given** JVM unit tests, **When** `./gradlew testDebugUnitTest` runs, **Then** existing search and catalog tests pass with no regressions, and new unit tests verify German search terms against the repository.
7. **Given** the build system, **When** `./gradlew :app:assembleDebug` runs, **Then** the APK is generated packaging `databases/food_catalog.db` uncompressed (`noCompress.add("db")`) with `PRAGMA integrity_check` = `ok`.

## Scope Boundary (explicit)

- **In scope:**
  - Python seed pipeline update in `tools/seed/build_food_catalog_db.py`.
  - Pinned downloads, checksums, and parser for BLS 4.0 (Open Data CC BY 4.0).
  - Pinned download / streaming parser for Open Food Facts Germany (ODbL).
  - Generation and verification of `app/src/main/assets/databases/food_catalog.db`.
  - Documentation in `tools/seed/README.md` (licensing, provenance, attribution).
  - JVM test coverage for German queries.
- **Out of scope:**
  - Database schema changes (remains strictly schema version 1 to preserve Room 3 compatibility).
  - Online API search integration (Open Food Facts remote API is not used; purely offline pre-populated database).
  - Story 3.3 custom food user UI (remains a separate upcoming story).

## Tasks / Subtasks

- [ ] **Task 1: Pinned European Datasets & Cache Pipeline in `tools/seed/`** (AC: #1, #3, #4)
  - [ ] Subtask 1.1: Pin BLS 4.0 download URLs from OpenAgrar / official MRI repository and define nutrient code mappings to schema nutrients (`kcal`, `protein`, `carbohydrates`, `fat`, `fiber`, `sugar`, `sodium`).
  - [ ] Subtask 1.2: Pin Open Food Facts Germany dataset (Hugging Face parquet or compressed JSONL/CSV dump) with SHA-256 verification.
  - [ ] Subtask 1.3: Update cache mechanism in `tools/seed/.cache/` and `.gitignore` to support the new source archives.

- [ ] **Task 2: Parsing & Catalog Generation Logic** (AC: #2, #3, #4)
  - [ ] Subtask 2.1: Implement BLS 4.0 parser: extract ~7,140 generic items, clean names (German primary description), map nutrients, base serving size (100g/100ml).
  - [ ] Subtask 2.2: Implement Open Food Facts Germany parser: filter products with `countries_tags` containing Germany, valid EAN barcode, complete macros, deduplicate by barcode and `(name, brand)`, select top 15,000–25,000 products.
  - [ ] Subtask 2.3: Generate serving units (`catalog_food_serving_units`) for metric portions (100g, 100ml, standard package/serving sizes).
  - [ ] Subtask 2.4: Deterministically assign incremental IDs starting from 1 (BLS first, followed by branded products).
  - [ ] Subtask 2.5: Write rows into SQLite tables using Room-exported schema from `app/schemas/com.example.myfoodtracker.data.db.FoodCatalogDatabase/1.json`, execute Room FTS5 triggers, rebuild FTS index, set `journal_mode = DELETE`, and run `VACUUM`.

- [ ] **Task 3: Asset Replacement & Build Verification** (AC: #1, #5, #7)
  - [ ] Subtask 3.1: Execute `python3 tools/seed/build_food_catalog_db.py` to produce `app/src/main/assets/databases/food_catalog.db`.
  - [ ] Subtask 3.2: Verify row counts (`catalog_foods` between 22,000 and 35,000 rows, `catalog_food_nutrients` matching 1:1), and verify `PRAGMA integrity_check = ok`.
  - [ ] Subtask 3.3: Verify DB asset size is reasonable (~10–20 MB) and APK packaging uncompressed via `./gradlew :app:assembleDebug`.

- [ ] **Task 4: Search Verification & Test Suite** (AC: #5, #6)
  - [ ] Subtask 4.1: Update unit tests in `FoodCatalogRepositoryImplTest.kt` or add dedicated catalog tests verifying queries for common German food staples (e.g. `"Vollmilch"`, `"Haferflocken"`, `"Apfel"`) and branded entries.
  - [ ] Subtask 4.2: Run `./gradlew testDebugUnitTest` and ensure all JVM tests pass.

- [ ] **Task 5: Documentation & Licensing Compliance** (AC: #1)
  - [ ] Subtask 5.1: Update `tools/seed/README.md` to document the new data sources, license requirements (Max Rubner-Institut CC BY 4.0 attribution, Open Food Facts ODbL), and reproduction instructions.

## Dev Notes

### Architecture & Constraints
- **Room 3 Compatibility:** Schema version remains `1`. The table structures, column types, FTS5 virtual table definition, and trigger names MUST remain identical to `app/schemas/com.example.myfoodtracker.data.db.FoodCatalogDatabase/1.json` and Room's `FoodCatalogDatabase_Impl`.
- **Database Driver:** The app uses `androidx.sqlite:sqlite-bundled` (`BundledSQLiteDriver`) configured in `AppModule.kt` with `createFromAsset("databases/food_catalog.db")`.
- **Self-Contained DB File:** `journal_mode = DELETE` must be set before finishing the SQLite build so no `-wal` or `-shm` companion files are required.
- **Testing Policy:** Follow `AGENTS.md`: Rely on JVM unit tests (`./gradlew testDebugUnitTest`) and compile checks (`:app:assembleDebug`). Do NOT attempt to run the Android emulator.

### Source Files to Touch
- `tools/seed/build_food_catalog_db.py` (UPDATE - replace US FDC logic with BLS 4.0 + Open Food Facts Germany)
- `tools/seed/README.md` (UPDATE - documentation, provenance, licenses)
- `app/src/main/assets/databases/food_catalog.db` (UPDATE - generated binary asset)
- `app/src/test/java/com/example/myfoodtracker/data/repository/FoodCatalogRepositoryImplTest.kt` (UPDATE/VERIFY)

### References
- BLS 4.0 OpenAgrar DOI: [10.25826/Data20251217-134202-0](https://doi.org/10.25826/Data20251217-134202-0)
- BLS Official Portal: `https://blsdb.de/`
- Open Food Facts Data Exports: `https://world.openfoodfacts.org/data`
- Hugging Face Open Food Facts Parquet: `openfoodfacts/product-database`
- Schema Definition: `app/schemas/com.example.myfoodtracker.data.db.FoodCatalogDatabase/1.json`

## Dev Agent Record

### Agent Model Used

Gemini 3.8 Flash (Medium)

### Debug Log References

- Verified existing Room 3 FTS5 architecture from Story 3.1.
- Validated current seed script layout and exported Room schema.

### Completion Notes List

- Story context initialized with exhaustive technical and data requirements.

### File List

- `tools/seed/build_food_catalog_db.py`
- `tools/seed/README.md`
- `app/src/main/assets/databases/food_catalog.db`
- `app/src/test/java/com/example/myfoodtracker/data/repository/FoodCatalogRepositoryImplTest.kt`
- `_bmad-output/planning-artifacts/epics.md`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`
- `_bmad-output/implementation-artifacts/3-5-german-and-european-food-catalog-seed-pipeline.md`
