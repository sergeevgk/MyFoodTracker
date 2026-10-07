# Food catalog seed pipeline (Story 3.5)

Builds the pre-populated, offline German/European food catalog used by the
FTS5 search engine: `app/src/main/assets/databases/food_catalog.db`
(42,140 foods: 7,140 BLS + 35,000 Open Food Facts, ~10 MB).

## Data sources

**1. Bundeslebensmittelschlüssel (BLS) 4.0** — Max Rubner-Institut (MRI),
CC BY 4.0 (attribution required, see below):
https://blsdb.de/download — DOI
[10.25826/Data20251217-134202-0](https://doi.org/10.25826/Data20251217-134202-0)

**2. Open Food Facts (OFF) products dump** — © Open Food Facts contributors,
ODbL (attribution required, see below):
https://world.openfoodfacts.org/data

Pinned datasets:

| Dataset | File | Size | Foods used |
| --- | --- | --- | --- |
| BLS 4.0 (2025, xlsx in zip) | `BLS_4_0_2025_DE.zip` | ~14 MB | 7,140 (all; `brand`/`barcode` NULL) |
| OFF products dump (2026-10-07 snapshot) | `en.openfoodfacts.org.products.csv.gz` | ~1,275 MB | 35,000 (25k German GS1 + 10k European) |

SHA-256 digests (validated at startup, recorded in `tools/seed/.cache/SHA256SUMS`):

- `BLS_4_0_2025_DE.zip`: `12b7a6ba62807ec9b301eb276f897dc85f99b2292311618dec3749a12d984c91`
- `en.openfoodfacts.org.products.csv.gz`: `f72687ee8bc6522054fe69dbfda6b91902c16af1ec2e043cde27bc6c29ad8176`

## Regenerate

```sh
python3 tools/seed/build_food_catalog_db.py
python3 tools/seed/build_food_catalog_db.py --self-test       # hermetic checks, no downloads
python3 tools/seed/build_food_catalog_db.py --skip-checksum  # when refreshing upstream dumps
```

Stdlib only (`csv`, `gzip`, `sqlite3`, `urllib`, `zipfile`, `xml.etree`,
`hashlib`, `json`, `re`). Python's bundled SQLite must have FTS5 (checked
at startup). First run downloads ~1.3 GB into `tools/seed/.cache/`
(git-ignored); re-runs reuse the cache. Full build takes ~2–5 minutes on a
typical machine (BLS xlsx parse + streamed OFF scan of ~4.5 M rows).

BLS implementation note: the release ships a single-sheet `.xlsx`
(`BLS_4_0_Daten_2025_DE.xlsx`, table `A1:PB7141`), not CSV. The script
parses it with stdlib `zipfile` + streaming `iterparse` — no
pandas/openpyxl dependency. Column lookup is header-driven (names below),
so minor column moves do not break the build; a missing header fails
loudly. The Hugging Face OFF parquet was deliberately NOT used (it needs
non-stdlib `pyarrow`); the bulk TSV export has no per-language
`product_name_de` column, so Germany-filtered rows use `product_name`
(German in practice).

## Nutrient mapping (per 100 g, missing/unknown → 0.0)

| Schema column | BLS 4.0 field | OFF field |
| --- | --- | --- |
| `calories` (kcal) | `ENERCC` (fallback `ENERCJ` kJ / 4.184) | `energy-kcal_100g` (fallback `energy_100g` kJ / 4.184) |
| `protein_g` | `PROT625` Protein (Nx6,25) | `proteins_100g` |
| `carbs_g` | `CHO` Kohlenhydrate, verfügbar | `carbohydrates_100g` |
| `fat_g` | `FAT` Fett | `fat_100g` |
| `fiber_g` | `FIBT` Ballaststoffe, gesamt | `fiber_100g` |
| `sugar_g` | `SUGAR` Zucker, gesamt | `sugars_100g` |
| `sodium_mg` | `NA` Natrium [mg] (fallback `NACL` Salz g × 393.15) | `sodium_100g` [g] × 1000 (fallback `salt_100g` × 393.15) |

Branded selection: `countries_tags` contains Germany, valid 8–14 digit EAN/UPC
barcode, name 3–150 chars, non-empty brand, complete finite core macros
(kcal/protein/carbs/fat) with physiological bounds checks (kcal ≤ 900, macros ≤ 100g,
sum ≤ 105g, sodium ≤ 40,000mg). Candidates are separated into German GS1 (prefixes
400–440) and general European products, sorted by scan popularity (`unique_scans_n`,
tie-breaker `completeness`), deduplicated on normalized barcode and on
`(lower(name), lower(brand))`, taking 25,000 German GS1 items and 10,000 European items.

Serving units: All foods (BLS and branded) receive a baseline `100 g` serving unit.
Branded rows additionally parse `serving_size`/`quantity` (`g`/`ml` 1:1,
`oz`/`fl-oz`/`kg`/`lb`/`l` converted; unparseable skipped; max 8 total per food).

Determinism: BLS block sorted by BLS code, OFF block by barcode;
sequential ids from 1; fixed `created_at`/`updated_at` (BLS release-date
epoch) — rebuilds are byte-identical given identical inputs.

## Expected output

- `SELECT COUNT(*) FROM catalog_foods` = 42,140 (requirement: 35,000–45,000)
- `catalog_food_nutrients` = 42,140 rows (one per food)
- `catalog_food_serving_units` ≈ 80,000 rows (baseline 100g on all foods)
- All four `room_fts_content_sync_*` triggers present, `user_version = 1`
- German domestic GS1 barcodes (prefix 400–440) = 25,000
- FTS probes: `vollmilch`, `haferflocken`, `magerquark`, `alpro`, `haribo` (all > 0)
- `PRAGMA integrity_check` = `ok`
- DB size ≈ 10 MB, `journal_mode = DELETE` (self-contained for
  `createFromAsset`), packaged STORED (uncompressed) via `noCompress "db"`

## Provenance & licensing

- BLS data © Max Rubner-Institut, licensed **CC BY 4.0**. Cite as: Max
  Rubner-Institut (2025): Bundeslebensmittelschlüssel (BLS), Version 4.0 —
  Deutsche Nährstoffdatenbank. Karlsruhe. DOI:
  10.25826/Data20251217-134202-0. If the app ever gains an About/Credits
  screen, this attribution must appear there (tracked in `deferred-work.md` under 3.5-D1).
- OFF data © Open Food Facts contributors, licensed **ODbL 1.0**
  (https://opendatacommons.org/licenses/odbl/). Same attribution note as
  above.
- `catalog_foods.id` is sequential from 1 (BLS block, then OFF block);
  schema is generated from the Room-exported
  `app/schemas/com.example.myfoodtracker.data.db.FoodCatalogDatabase/1.json`
  plus the four `room_fts_content_sync_*` triggers mirrored from
  `FoodCatalogDatabase_Impl.createAllTables`. Schema stays at version 1 —
  never bump it for seed changes (a bump + destructive migration would wipe
  user `is_custom = 1` rows).

## What is committed

- `tools/seed/build_food_catalog_db.py`, this README — committed.
- `app/src/main/assets/databases/food_catalog.db` — committed (the asset).
- `tools/seed/.cache/` (raw downloads, hashes) — git-ignored, never committed.
