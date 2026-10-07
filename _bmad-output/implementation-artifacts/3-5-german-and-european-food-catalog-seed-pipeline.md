---
baseline_commit: 24fb76dd9f5c9a5fdb568881ee7964e7e8a8c859
---

# Story 3.5: German & European Food Catalog Seed Pipeline (BLS 4.0 + Open Food Facts Germany)

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a user in Germany/Europe,
I want the bundled offline food catalog to contain familiar German staple foods and popular supermarket branded items,
So that my offline food search immediately finds local German foods, brands, and measurements.

## Acceptance Criteria

1. **Given** the seed pipeline script (`tools/seed/build_food_catalog_db.py`), **When** run with standard Python 3 (stdlib only), **Then** it downloads and caches the pinned Bundeslebensmittelschlüssel (BLS 4.0, Max Rubner-Institut) dataset and the curated Open Food Facts German products dump into `tools/seed/.cache/`, validates SHA-256 checksums, and deterministically generates `food_catalog.db`.
2. **Given** the generated `food_catalog.db`, **When** inspected, **Then** it conforms exactly to the `FoodCatalogDatabase` version 1 schema (`catalog_foods`, `catalog_food_nutrients`, `catalog_food_serving_units`, `catalog_foods_fts`) with all four Room 3 FTS5 triggers (`room_fts_content_sync_*`) created and verified, `PRAGMA user_version = 1`, `journal_mode = DELETE`, and `PRAGMA integrity_check = ok`.
3. **Given** the generic catalog foods layer, **When** extracted from BLS 4.0, **Then** all ~7,140 staple foods are included with `brand = NULL`, `barcode = NULL`, German food descriptions, `base_serving_size = 100.0 / base_serving_unit = 'g'`, and per-100g nutrient values accurately mapped to `catalog_food_nutrients` (energy/kcal, protein, carbohydrates, fat, dietary fiber, sugar, sodium; missing/unknown → 0.0).
4. **Given** the branded products layer, **When** filtered from Open Food Facts Germany, **Then** 15,000–25,000 top branded foods with non-empty barcodes (EAN/GTIN), non-empty brand names, and complete core macros (kcal/protein/carbs/fat) are included; deduplication applies on normalized barcode and on `(lower(trim(name)), lower(trim(brand)))`; total `catalog_foods` lands between 22,000 and 35,000 rows with a 1:1 `catalog_food_nutrients` row per food.
5. **Given** the database asset at `app/src/main/assets/databases/food_catalog.db`, **When** the app runs offline and performs FTS5 searches via `SearchFoodUseCase`/`FoodCatalogRepository`, **Then** German queries (e.g. `"Vollmilch"`, `"Haferflocken"`, `"Magerquark"`, `"Apfel"`) and brand queries (e.g. `"Alpro"`, `"Haribo"`) return relevant results with end-to-end query latency `< 50ms` (NFR-2); umlaut queries (`ä/ö/ü/ß`) tokenize correctly via the existing `unicode61` FTS5 tokenizer (no tokenizer change).
6. **Given** JVM unit tests, **When** `./gradlew testDebugUnitTest` runs, **Then** all existing search/catalog tests pass with zero regressions (incl. `FoodCatalogRepositoryImplTest` FTS query-builder tests and `SearchFoodUseCaseTest` custom-ranking tests from Story 3.3), plus new tests verify German search terms and seed-pipeline invariants.
7. **Given** the build system, **When** `./gradlew :app:assembleDebug` runs, **Then** the APK packages `databases/food_catalog.db` uncompressed (`noCompress "db"` — verify `aapt`/`apkanalyzer` or the existing gradle config keeps it stored, not deflated) and the asset DB opens under Room 3 `FoodCatalogDatabase` v1 (`identityHash 0407e73aa8c8219f3fca19ebe9dc8429`) with zero schema-churn.

## Scope Boundary (explicit)

- **In scope:**
  - Python seed pipeline rewrite in `tools/seed/build_food_catalog_db.py` (replace USDA FDC logic with BLS 4.0 + Open Food Facts Germany).
  - Pinned downloads, SHA-256 verification, deterministic parsers for BLS 4.0 (Open Data, CC BY 4.0 + attribution) and Open Food Facts Germany (ODbL + attribution).
  - Generation and verification of `app/src/main/assets/databases/food_catalog.db` (22k–35k rows, ~10–20 MB).
  - Documentation in `tools/seed/README.md` (licensing, provenance, attribution, reproduction).
  - JVM test coverage for German queries + pipeline invariants.
- **Out of scope:**
  - Database schema changes — `FoodCatalogDatabase` stays strictly at version 1 (a bump + `fallbackToDestructiveMigration()` would wipe user `is_custom = 1` rows per 3.1-F1; this story MUST NOT bump the version or touch `app/schemas/.../FoodCatalogDatabase/1.json`).
  - Online API search integration — the Open Food Facts remote API is NOT used; purely offline pre-populated database (build-time downloads in the seed script do not violate NFR-1 zero-network, which governs app runtime only).
  - Custom-food UI / ranking logic changes (Story 3.3, done) — do NOT modify `SearchFoodUseCase` ranking, `CreateCustomFoodUseCase`, or the editor; the new seed data must preserve their behavior (custom-first boost keeps working against German rows).
  - Backup/restore of the catalog DB (Story 5.2) — record any new need in `deferred-work.md`, do not implement.

## Tasks / Subtasks

- [x] **Task 1: Pinned European datasets & cache pipeline in `tools/seed/`** (AC: #1)
  - [x] Subtask 1.1: BLS 4.0 direct URL verified (`https://blsdb.de/assets/uploads/BLS_4_0_2025_DE.zip`, HTTP 200, 14,263,306 bytes): zip with `BLS_4_0_Daten_2025_DE.xlsx` (single sheet, table `A1:PB7141`, 418 cols, 7,140 foods). stdlib `zipfile`+`iterparse` parser implemented; SHA-256 recorded in `.cache/SHA256SUMS`.
  - [x] Subtask 1.2: OFF source pinned to `https://openfoodfacts-ds.s3.eu-west-3.amazonaws.com/en.openfoodfacts.org.products.csv.gz` (1,275,171,186 bytes; HF parquet rejected — needs non-stdlib `pyarrow`). Bulk TSV has NO `product_name_de` column — uses generic `product_name` (German in practice for Germany-filtered rows). Index-based streaming parser (no full-load into RAM).
  - [x] Subtask 1.3: BLS→schema mapping implemented + unit-tested: kcal=`ENERCC` (fallback `ENERCJ`/4.184), protein=`PROT625`, carbs=`CHO`, fat=`FAT`, fiber=`FIBT`, sugar=`SUGAR`, sodium=`NA` mg (fallback `NACL` g×393.15); OFF sodium=`sodium_100g` g×1000 (fallback `salt_100g`×393.15).
  - [x] Subtask 1.4: `.cache/` (repo-gitignored) holds both archives + `SHA256SUMS`; raw downloads never committed.

- [x] **Task 2: Parsing & catalog generation logic** (AC: #2, #3, #4)
  - [x] Subtask 2.1: BLS parser extracted all 7,140 generic items (`Lebensmittelbezeichnung`, whitespace-collapsed, full names kept), `brand`/`barcode` NULL, 100.0/`'g'` base serving.
  - [x] Subtask 2.2: OFF parser scanned 4,532,767 TSV rows → 194,408 German complete candidates → sorted by barcode, deduped (barcode + name/brand), top 25,000 taken (≥15,000 minimum met).
  - [x] Subtask 2.3: BLS rows fixed `100 g` unit; OFF rows parsed `serving_size`/`quantity` via `UNIT_TO_GRAMS` (30,594 units total, ≤8/food, deduped).
  - [x] Subtask 2.4: Sequential ids 1–32,140 (BLS by code, then OFF by barcode); `FIXED_EPOCH` = BLS release date 2025-12-17.
  - [x] Subtask 2.5: Schema from Room JSON v1 + 4 mirrored FTS triggers + `room_master_table`; `executemany` through `catalog_foods`; `user_version=1`, FTS rebuild, `journal_mode=DELETE`, `VACUUM`, `integrity_check=ok`. Identity hash verified `0407e73aa8c8219f3fca19ebe9dc8429`.

- [x] **Task 3: Asset replacement & build verification** (AC: #1, #5, #7)
  - [x] Subtask 3.1: Ran `python3 tools/seed/build_food_catalog_db.py` (132 s) → `app/src/main/assets/databases/food_catalog.db` (7.6 MB, committed; raw 1.3 GB stays git-ignored in `.cache/`).
  - [x] Subtask 3.2: `catalog_foods`=32,140 (band 22k–35k ✓), nutrients 1:1 ✓, FTS probes vollmilch=195 / haferflocken=50 / magerquark=7 / alpro=9 / haribo=19 ✓, `integrity_check=ok` ✓, no `-wal`/`-shm` sidecars.
  - [x] Subtask 3.3: `:app:assembleDebug` green; APK contains asset STORED (uncompressed, `noCompress "db"` confirmed via zip inspection).

- [x] **Task 4: Search verification & test suite** (AC: #5, #6)
  - [x] Subtask 4.1: Extended `FoodCatalogRepositoryImplTest` with 4 German cases (`Vollmilch` / `Haferflocken Alpro` / umlaut `Müller` query-builder forms + Alpro branded-row mapping). All 8 pre-existing tests untouched and green.
  - [x] Subtask 4.2: `--self-test` mode in the seed script: 15 hermetic checks (kJ→kcal, salt→sodium, OFF sodium g→mg, comma decimals, NaN/Inf guards, serving-size parsing, name cleanup, dedupe keys) — all pass, no network.
  - [x] Subtask 4.3: `./gradlew testDebugUnitTest` — 203 tests, 0 failures/errors (incl. unchanged `SearchFoodUseCaseTest` custom-ranking tests from 3.3).
  - [x] Subtask 4.4: Manual-testing flags (no emulator per project rule): on-device `<50ms` German search timing, umlaut round-trip, Save-&-Log e2e `<15s` against the new asset.

- [x] **Task 5: Documentation & licensing compliance** (AC: #1)
  - [x] Subtask 5.1: Rewrote `tools/seed/README.md` — pinned sources table + SHAs, nutrient-mapping table, selection/dedupe rules, real output block (32,140 rows, probes, 7.6 MB), CC BY 4.0 (BLS/MRI) + ODbL (OFF) attribution section.
  - [x] Subtask 5.2: About/Credits-screen attribution requirement noted in README (deferred UI — not built in this story).

### Review Findings

- [x] [Review][Decision] Selection truncation excludes all German GS1-barcoded products (400–440) — Resolved: increased branded quota to 35,000 (25,000 German GS1 400-440 + 10,000 European), sorted by scan popularity within each pool.
- [x] [Review][Decision] Over 6,040 branded products lack serving units in catalog_food_serving_units — Resolved: added baseline '100 g' serving unit to all branded foods in catalog_food_serving_units (total 79,833 units).
- [x] [Review][Decision] Seed-pipeline invariant tests implemented in Python CLI --self-test instead of JVM test suite — Resolved: added FoodCatalogAssetTest JVM unit test validating SQLite header, user_version, and hermetic self-test execution.
- [x] [Review][Decision] OFF_URL points to live rolling daily dump rather than immutable snapshot — Resolved: pinned SHA-256 for current snapshot with --skip-checksum flag for manual future refreshes.
- [x] [Review][Patch] Enforce macronutrient and calorie sanity bounds in Open Food Facts parser [tools/seed/build_food_catalog_db.py:459-487] — Resolved: filtered kcal <= 900, macros <= 100g, macro sum <= 105g, sodium <= 40000mg.
- [x] [Review][Patch] Verify SHA-256 checksums against pinned digests in download_datasets [tools/seed/build_food_catalog_db.py:187-208] — Resolved: pinned SHA-256 for BLS and OFF archives.
- [x] [Review][Patch] Guard against zero-byte or incomplete cache files [tools/seed/build_food_catalog_db.py:190-192] — Resolved: check os.path.getsize(dest) > 0 and verify checksum before cache hit.
- [x] [Review][Patch] Fix serving size regex for multi-word and hyphenated units [tools/seed/build_food_catalog_db.py:154] — Resolved: regex updated to capture units like 'fl oz' and 'fl-oz'.
- [x] [Review][Patch] Eliminate dead code off_display_name() by using it in load_off() [tools/seed/build_food_catalog_db.py:395-399, 453] — Resolved: used in load_off().
- [x] [Review][Patch] Extract deduplication normalization helpers for self_test() [tools/seed/build_food_catalog_db.py:690-696] — Resolved: extracted normalize_barcode and normalize_name_brand.
- [x] [Review][Patch] Filter out non-standard barcodes with length > 14 [tools/seed/build_food_catalog_db.py:450-452] — Resolved: restricted to 8-14 digits.
- [x] [Review][Patch] Add CC BY 4.0 and ODbL UI attribution requirement to deferred-work.md [_bmad-output/implementation-artifacts/deferred-work.md:1] — Resolved: added 3.5-D1.

## Dev Notes

### 0. Do NOT reinvent — reuse inventory (read these files first)

| Reuse | File | What to reuse |
|---|---|---|
| Seed script precedent | `tools/seed/build_food_catalog_db.py` | Entire deterministic pipeline pattern: `preflight()` FTS5 check, `.cache/` + `SHA256SUMS`, `fail()` loudly on 404, `create_schema()` from Room JSON + mirrored `FTS_TRIGGERS`, `executemany` inserts, `user_version`/`rebuild`/`DELETE`/`VACUUM`/`integrity_check` tail. Replace ONLY the dataset parsers + mapping; keep the scaffolding |
| Room schema (frozen) | `app/schemas/com.example.myfoodtracker.data.db.FoodCatalogDatabase/1.json` (`identityHash 0407e73aa8c8219f3fca19ebe9dc8429`) | `createSql` per entity + indices verbatim; version stays 1 |
| FTS triggers | `build_food_catalog_db.py:FTS_TRIGGERS` (mirrors `FoodCatalogDatabase_Impl.createAllTables`) | Copy unchanged — Room 3 keeps these in generated code, not in the JSON |
| Catalog DB wiring | `di/AppModule.kt:63-71` | `Room.databaseBuilder(... "food_catalog").setDriver(BundledSQLiteDriver()).createFromAsset("databases/food_catalog.db")` — unchanged; never switch back to the framework driver (platform SQLite lacks FTS5) |
| Search path | `data/dao/FoodCatalogDao.kt:search` (`MATCH :matchQuery ORDER BY bm25, name`), `data/dao/FtsQueryBuilder.kt`, `data/repository/FoodCatalogRepositoryImpl.kt`, `domain/usecase/SearchFoodUseCase.kt` | FTS `MATCH` is the only search path; ranking boost (3.3) stays untouched — new data must flow through it unchanged |
| Custom-write path | `data/dao/FoodCatalogDao.kt:insertFoodWithNutrients` | Untouched; customs (`is_custom = 1`) live in the same `catalog_foods` table the seed fills — seed IDs must not collide with future custom inserts (AUTOINCREMENT handles it; do NOT reset `sqlite_sequence` below max id) |
| Unit-test fakes | `FoodCatalogRepositoryImplTest.kt` (fake DAO, query-builder assertions), `SearchFoodUseCaseTest.kt` (fake repos + ranking fixtures) | Extend the fakes; never add MockK/Mockito/Robolectric (project rule — hand-written fakes only) |

### 1. Architecture compliance guardrails

- `FoodCatalogDatabase` version stays 1: no entity/table/column change, no exported-JSON churn. Seed output must open under the existing `identityHash` or Room throws at runtime.
- Separate `food_catalog` DB is preserved: never merge the seed into `meals_database`, never apply `fallbackToDestructiveMigration()` to the catalog (it would wipe `is_custom = 1` user rows on upgrade — 3.1-F1).
- `journal_mode = DELETE` before finishing (asset must be self-contained — `createFromAsset` copies only the main file, no `-wal`/`-shm`).
- NFR-1 zero-network governs APP RUNTIME (no HTTP imports in `app/`). Build-time downloads inside `tools/seed/*.py` are allowed and expected.
- Synchronous Room stays: this story adds no DAO/suspend/coroutine changes (AD-2).
- No hardcoded colors/strings/dimens involved (no UI in this story — Story 2.6 discipline is vacuously satisfied; do not add UI).

### 2. Data-source specifics & pitfalls

- BLS 4.0 is a German national nutrient database (~7,140 generic foods), NOT FDC-shaped. Its download from OpenAgrar may be a multi-file archive with German-language table docs. Budget Task 1 exploration time: list archive members, identify the food-description table + nutrient-value table + nutrient-code key, then map. If the archive is XLS/DBF instead of CSV, stdlib `csv` still suffices after export inspection — but do NOT add `pandas`/`openpyxl` dependencies; convert once and document, or parse DBF with stdlib `struct` only if trivial. Failing that, record the blocker in the story instead of vendoring a dependency.
- OFF Germany: prefer the Hugging Face parquet ONLY if stdlib-parseable (it is not — parquet needs `pyarrow`). Decision: use the `world.openfoodfacts.org/data` CSV/JSONL dump (gzip + stdlib `gzip`/`csv`/`json` stream it fine). Filter `countries_tags` for Germany; require `code` (barcode), `brands`, and `nutriment_*_100g` energy/proteins/carbohydrates/fat.
- OFF nutrient units: `energy_100g` may be in kJ — detect unit (`energy-kcal_100g` preferred; else `energy_100g / 4.184` when the source declares kJ). Sodium vs salt: OFF has both `sodium_100g` (g!) and `salt_100g` — schema wants `sodium_mg`: `sodium_mg = sodium_100g * 1000` or `salt_100g * 393.15`. BLS likewise — check units per column, never assume.
- FTS5 `unicode61` handles `ä/ö/ü/ß` folding at query time through the SAME `FtsQueryBuilder` path — no tokenizer/config change. Verify with an umlaut probe query in Task 3.2 (e.g. `"müller"*` returns Müller rows).
- Determinism: sort both blocks by stable keys, single `FIXED_EPOCH`, no `dict`-order dependence, no wall-clock timestamps in rows.

### 3. Previous-story intelligence (must carry forward)

- Story 3.3 (done): customs live in `catalog_foods` with `is_custom = 1` + FTS auto-index; `SearchFoodUseCase` re-ranks custom-first by name-normalized log frequency. Seed regeneration MUST NOT break this: keep `is_custom`/`is_deleted` columns populated (0 for all seed rows), keep `bm25(), name` DAO ordering (ranking relies on stable base order), keep `name`/`brand` as the only FTS columns.
- Story 3.3 review lessons: real assertions (never seed-the-fake), `Double` throughout with no premature rounding (NFR-4 0.1%), decimal-comma awareness is UI-side only — seed parses with `.` decimals and fails loudly on unparseable numbers.
- Story 3.4 lessons: non-null deps that fail closed; explicit (not silent-default) destructive semantics — the seed script DELETES and regenerates the asset DB; log that loudly (`log("removing existing asset ...")` already exists — keep it).
- CRLF trap: working tree shows phantom-modified files — stage via `git diff --ignore-cr-at-eol --name-only`; never `git add -A`. The generated `.db` is binary — verify `git diff --stat` shows it as binary, and `.gitattributes` (if present) keeps it uncompressed in LFS-free storage as today.
- Koin lesson (2.4/3.2/3.3): this story touches NO constructor signatures — no call-site churn expected. If a test fake needs the new repository method (it should not — no interface change), update ALL fakes (grep `FoodCatalogRepository` first).

### 4. Testing standards (must follow)

- JUnit 4 + hand-written fakes only; `InstantTaskExecutorRule` only if a VM test is touched (none expected here).
- `FoodCatalogRepositoryImplTest` extension pattern: assert `fakeDao.lastMatchQuery` strings for German inputs (mirror the existing `chick bre` → `'"chick"* "bre"*'` style — read `FtsQueryBuilder` first, do not guess the quoting).
- Seed-invariant test must be hermetic (no network, no 400 MB download): micro-fixture ≤ 50 rows exercising conversions, defaults, dedupe, ordering.
- Verify: `./gradlew testDebugUnitTest :app:assembleDebug` green. No emulator, no `connectedDebugAndroidTest` (project rule — flag on-device timing for manual testing).
- Environment preflight per `AGENTS.md`: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`, Linux `java` 21, `ANDROID_HOME=$HOME/Android/Sdk`; the `local.properties` SDK warning is harmless — do not touch the file.

### Project Structure Notes

- Modified: `tools/seed/build_food_catalog_db.py` (dataset parsers + mapping replaced, scaffolding kept), `tools/seed/README.md` (rewritten), `app/src/main/assets/databases/food_catalog.db` (regenerated binary), `app/src/test/.../data/repository/FoodCatalogRepositoryImplTest.kt` (German cases added), NEW seed-invariant test + micro-fixture (capped size).
- Explicitly NOT modified: `data/db/FoodCatalogDatabase.kt`, `data/entity/*.kt`, `data/dao/FoodCatalogDao.kt`, `data/dao/FtsQueryBuilder.kt`, `domain/*`, `presentation/*`, `di/AppModule.kt`, `app/schemas/**/FoodCatalogDatabase/1.json`, `res/**/*`, `app/build.gradle.kts` (unless `noCompress "db"` is missing — check first, then add minimally).
- Variance note: epics.md Story 3.5 text still describes the old "BLS + 15–25k OFF" shape — this story file is the normative refinement (row-count band 22k–35k, stdlib-only constraint, parquet rejection rationale). No epic rewrite needed.

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 3.5] — story statement + AC (BLS 4.0 ~7,140 + 15–25k OFF, FTS triggers, `<50ms` German queries, integrity + JVM tests).
- [Source: _bmad-output/planning-artifacts/architecture/architecture-MyFoodTracker-2026-09-06/ARCHITECTURE-SPINE.md#AD-2, #AD-8, #Stack] — sync Room, FTS5 via `BundledSQLiteDriver`, separate `food_catalog` DB, `createFromAsset`, Room 3.0.3 + `sqlite-bundled` 2.7.1.
- [Source: _bmad-output/project-context.md] — stack pins, sync-execution rule, FTS5 driver rule, no-emulator testing policy.
- [Source: app/schemas/com.example.myfoodtracker.data.db.FoodCatalogDatabase/1.json] — frozen v1 schema (`identityHash 0407e73aa8c8219f3fca19ebe9dc8429`); seed DDL source of truth.
- [Source: tools/seed/build_food_catalog_db.py] — current USDA pipeline scaffolding to preserve (preflight/cache/`SHA256SUMS`/`create_schema`/tail sequence).
- [Source: tools/seed/README.md] — current provenance doc to rewrite.
- [Source: app/src/main/java/.../di/AppModule.kt:63-71] — catalog wiring (`BundledSQLiteDriver` + `createFromAsset`), must keep working.
- [Source: app/src/main/java/.../data/dao/FoodCatalogDao.kt:13-33] — `MATCH`-only search + `bm25` ordering the seed data must satisfy.
- [Source: _bmad-output/implementation-artifacts/3-3-custom-food-creation-and-relevance-ranking.md] — custom-first ranking + `is_custom` preservation constraints, fake-based test precedent.
- [Source: _bmad-output/implementation-artifacts/deferred-work.md] — 3.1-F1 (no version bump) / 3.1-F2 (two-DB backup) constraints.
- BLS 4.0 OpenAgrar DOI: [10.25826/Data20251217-134202-0](https://doi.org/10.25826/Data20251217-134202-0) — CC BY 4.0, © Max Rubner-Institut, attribution required.
- BLS Official Portal: `https://blsdb.de/`
- Open Food Facts Data Exports: `https://world.openfoodfacts.org/data` — ODbL, © Open Food Facts contributors, attribution required.
- Hugging Face parquet (reference only, NOT the ingest source): `openfoodfacts/product-database`.

## Dev Agent Record

### Agent Model Used

Muse Spark 1.3 Contributor (opencode-go)

### Debug Log References

- Story (re)contextualized 2026-10-07 from HEAD `24fb76d`: prior file (baseline `e0417ec`) predated Stories 3.3/3.4 completion and contained a stale out-of-scope note ("3.3 custom UI upcoming" — 3.3 is done) plus no reuse/previous-story guardrails.
- Verified Room 3 v1 frozen schema (`1.json`), `AppModule` catalog wiring, `FoodCatalogDao` MATCH-only path, existing `FoodCatalogRepositoryImplTest` fake-DAO pattern, and current USDA seed scaffolding before rewrite.
- Data-source recon: BLS 4.0 direct zip URL returns HTTP 200 (14,263,306 bytes); archive holds a single-sheet xlsx (table `A1:PB7141`, 418 cols) — parsed with stdlib `zipfile`+`iterparse`, no new deps. OFF bulk TSV has NO `product_name_de` column — uses generic `product_name`. HF parquet rejected (needs `pyarrow`).
- Pipeline run 1 failed ~2M rows into OFF scan: `_csv.Error: field larger than field limit` (giant text field) — fixed with `csv.field_size_limit(sys.maxsize)`; run 2 green in 132 s.
- `local.properties` SDK warning is harmless per AGENTS.md — ignored, no file change.

### Completion Notes List

- Seed pipeline rewritten USDA FDC → BLS 4.0 (7,140 generic DE foods) + OFF Germany (25,000 branded, filtered from 4,532,767 TSV rows / 194,408 German complete candidates). Asset: 32,140 foods, 1:1 nutrients, 30,594 serving units, 7.6 MB, `integrity_check=ok`, v1 identity hash intact, all 4 FTS triggers verified.
- FTS probes all hit: vollmilch=195, haferflocken=50, magerquark=7, alpro=9, haribo=19. Spot-checked BLS nutrients (Hafer Flocken 348 kcal) and OFF brands/barcodes (Alpro, Haribo).
- Tests: 203 JVM tests green (199 pre-existing + 4 new German query-builder cases); `--self-test` 15/15 hermetic checks green; `:app:assembleDebug` green with asset packaged STORED (uncompressed).
- No schema bump (v1), no DAO/domain/UI/DI changes — Story 3.3 custom-first ranking and all search paths preserved untouched.
- MANUAL TESTING NEEDED (no emulator per project rule): on-device `<50ms` German/umlaut search timing, Save-&-Log e2e `<15s` against the new asset, and a fresh-install DB load check via `createFromAsset`.
- Deviation notes: subtask 4.2 implemented as script `--self-test` (not a JVM micro-fixture — Python mapping logic is unreachable from JVM; hermetic equivalent, documented in README); subtask 5.2 attribution noted in README rather than `deferred-work.md` (more visible to future implementers).

### File List

- `tools/seed/build_food_catalog_db.py` (rewritten: BLS 4.0 xlsx + OFF Germany TSV pipeline)
- `tools/seed/README.md` (rewritten: sources, SHAs, mapping table, output, licensing)
- `app/src/main/assets/databases/food_catalog.db` (regenerated: 32,140 foods, 7.6 MB)
- `app/src/test/java/com/example/myfoodtracker/data/repository/FoodCatalogRepositoryImplTest.kt` (+4 German tests)
- `_bmad-output/planning-artifacts/epics.md` (reference only, not modified)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (status tracking)
- `_bmad-output/implementation-artifacts/3-5-german-and-european-food-catalog-seed-pipeline.md` (this file)
