#!/usr/bin/env python3
"""Build the pre-populated food catalog SQLite database (Story 3.1).

Source data: USDA FoodData Central (FDC), public domain under CC0 1.0.
https://fdc.nal.usda.gov/download-datasets

Pinned datasets (verify availability when running; the script fails loudly on 404):
  Foundation Foods 2026-04-30 .... ~3.7 MB zip, ~469 foods
  SR Legacy 2018-04 .............. ~6.7 MB zip, ~7,800 foods
  FNDDS 2019-2020 (2022-10-28) .... ~4.3 MB zip, ~5,400 foods
  Branded Foods 2026-04-30 ........ ~428 MB zip, filtered to fill >= 50,500 total

Regenerate:
  python3 tools/seed/build_food_catalog_db.py

Expected output: app/src/main/assets/databases/food_catalog.db with
  SELECT COUNT(*) FROM catalog_foods  >= 50,000 (target 50,500)
  PRAGMA integrity_check = ok, roughly 10-25 MB.

Algorithm (deterministic; stable IDs across runs):
  1. Preflight: Python sqlite3 must have FTS5. Download zips into
     tools/seed/.cache/ (skipped if present), record SHA-256.
  2. Generic foods (Foundation data_type == 'foundation_food', all SR Legacy,
     all FNDDS survey foods): include every row with a non-empty description;
     brand = NULL, barcode = NULL.
  3. Nutrient mapping, per 100 g, missing -> 0.0:
       kcal: 1008 (fallback 2047, then 2048), protein: 1003, carbs: 1005,
       fat: 1004, fiber: 1079, sugar: 2000, sodium: 1093.
  4. Branded filter: description 3-150 chars; gtin_upc present; all four core
     macros (kcal/protein/carbs/fat) present; deduplicate on lower(gtin_upc)
     and on (lower(trim(name)), lower(trim(brand))); ascending fdc_id order;
     take until the catalog reaches 50,500 rows.
  5. catalog_foods ids are sequential from 1 in fixed dataset order
     (Foundation -> SR Legacy -> FNDDS -> Branded). Timestamps are a fixed
     constant (the FDC release date as epoch) for reproducibility.
  6. Serving units: generic foods from food_portion.csv (gram_weight > 0,
     unit name from portion_description / modifier / measure_unit name,
     cap 8 per food, dedupe (food_id, unit_name)); branded from
     serving_size + serving_size_unit (g and ml 1:1, oz/fl-oz/kg/lb
     converted; other units skipped) plus household_serving_fulltext when
     distinct.
  7. Schema comes from the Room-exported JSON
     app/schemas/com.example.myfoodtracker.data.db.FoodCatalogDatabase/1.json:
     every entity createSql in order, then indices, then the FTS virtual
     table, then the four room_fts_content_sync_* triggers (which Room 3
     keeps in generated code, not in the JSON -- mirrored exactly from
     FoodCatalogDatabase_Impl.createAllTables), then room_master_table +
     identity hash from setupQueries.
  8. Rows are inserted with executemany (triggers populate the FTS index),
     then: PRAGMA user_version = 1; FTS rebuild safety net;
     journal_mode = DELETE (createFromAsset copies only the main DB file,
     so the asset must be self-contained); VACUUM; integrity_check.

Only the Python standard library is used (csv, sqlite3, urllib, zipfile,
hashlib, json, datetime). Raw downloads are never committed (see .gitignore);
the script, this README, and the generated .db are committed.
"""

import csv
import hashlib
import json
import os
import sqlite3
import sys
import time
import urllib.request
from datetime import datetime, timezone

# ---------------------------------------------------------------------------
# Pinned configuration
# ---------------------------------------------------------------------------

BASE_URL = "https://fdc.nal.usda.gov"

DATASETS = [
    # (key, zip name, kind)
    ("foundation", "FoodData_Central_foundation_food_csv_2026-04-30.zip", "generic"),
    ("sr_legacy", "FoodData_Central_sr_legacy_food_csv_2018-04.zip", "generic"),
    ("survey", "FoodData_Central_survey_food_csv_2022-10-28.zip", "generic"),
    ("branded", "FoodData_Central_branded_food_csv_2026-04-30.zip", "branded"),
]

TARGET_TOTAL = 50500
MIN_TOTAL = 50000

# Nutrient ids (per 100 g). Kcal falls back 1008 -> 2047 -> 2048.
NID_KCAL = (1008, 2047, 2048)
NID_PROTEIN = 1003
NID_CARBS = 1005
NID_FAT = 1004
NID_FIBER = 1079
NID_SUGAR = 2000
NID_SODIUM = 1093
WANTED_NIDS = frozenset([1008, 2047, 2048, 1003, 1005, 1004, 1079, 2000, 1093])

# Fixed timestamps: the FDC release date as epoch (reproducible builds).
FIXED_EPOCH = int(datetime(2026, 4, 30, tzinfo=timezone.utc).timestamp())

# Room 3 FTS sync triggers, mirrored exactly from the generated
# FoodCatalogDatabase_Impl.createAllTables (Room 3 keeps these in code,
# not in the exported schema JSON).
FTS_TRIGGERS = [
    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_catalog_foods_fts_BEFORE_UPDATE"
    " BEFORE UPDATE ON `catalog_foods` BEGIN DELETE FROM `catalog_foods_fts`"
    " WHERE `rowid`=OLD.`rowid`; END",
    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_catalog_foods_fts_BEFORE_DELETE"
    " BEFORE DELETE ON `catalog_foods` BEGIN DELETE FROM `catalog_foods_fts`"
    " WHERE `rowid`=OLD.`rowid`; END",
    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_catalog_foods_fts_AFTER_UPDATE"
    " AFTER UPDATE ON `catalog_foods` BEGIN INSERT INTO `catalog_foods_fts`"
    "(`rowid`, `name`, `brand`) VALUES (NEW.`rowid`, NEW.`name`, NEW.`brand`); END",
    "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_catalog_foods_fts_AFTER_INSERT"
    " AFTER INSERT ON `catalog_foods` BEGIN INSERT INTO `catalog_foods_fts`"
    "(`rowid`, `name`, `brand`) VALUES (NEW.`rowid`, NEW.`name`, NEW.`brand`); END",
]

# Branded serving-size unit -> grams multiplier (ml treated 1:1 by density).
UNIT_TO_GRAMS = {
    "g": 1.0, "gram": 1.0, "grams": 1.0, "grm": 1.0,
    "ml": 1.0, "milliliter": 1.0, "millilitre": 1.0,
    "kg": 1000.0,
    "oz": 28.3495, "ounce": 28.3495,
    "fl oz": 29.5735, "fl-oz": 29.5735, "floz": 29.5735,
    "lb": 453.592, "pound": 453.592,
    "l": 1000.0, "liter": 1000.0, "litre": 1000.0,
}

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SEED_DIR = os.path.join(REPO_ROOT, "tools", "seed")
CACHE_DIR = os.path.join(SEED_DIR, ".cache")
SCHEMA_JSON = os.path.join(
    REPO_ROOT, "app", "schemas",
    "com.example.myfoodtracker.data.db.FoodCatalogDatabase", "1.json")
ASSET_DB = os.path.join(
    REPO_ROOT, "app", "src", "main", "assets", "databases", "food_catalog.db")


def log(msg):
    print(msg, flush=True)


def fail(msg):
    print("FATAL: " + msg, file=sys.stderr)
    sys.exit(1)


def preflight():
    con = sqlite3.connect(":memory:")
    try:
        row = con.execute("SELECT sqlite_compileoption_used('ENABLE_FTS5')").fetchone()
    finally:
        con.close()
    if not row or not row[0]:
        fail("Python sqlite3 lacks FTS5 (ENABLE_FTS5); cannot build the catalog.")
    log("preflight: python sqlite %s with FTS5" % sqlite3.sqlite_version)
    os.makedirs(CACHE_DIR, exist_ok=True)


def download_datasets():
    hashes = {}
    for key, zip_name, _kind in DATASETS:
        dest = os.path.join(CACHE_DIR, zip_name)
        url = BASE_URL + "/fdc-datasets/" + zip_name
        if os.path.exists(dest):
            log("cache hit: %s" % zip_name)
        else:
            log("downloading %s (%.1f MB expected) ..." % (zip_name, 0.0))
            try:
                urllib.request.urlretrieve(url, dest)
            except Exception as exc:
                fail("download failed for %s: %s" % (url, exc))
            log("downloaded %s (%.1f MB)" % (zip_name, os.path.getsize(dest) / 1e6))
        sha = hashlib.sha256()
        with open(dest, "rb") as fh:
            for chunk in iter(lambda: fh.read(1 << 20), b""):
                sha.update(chunk)
        hashes[zip_name] = sha.hexdigest()
        log("sha256 %s %s" % (sha.hexdigest()[:16], zip_name))
    with open(os.path.join(CACHE_DIR, "SHA256SUMS"), "w") as fh:
        for name, digest in hashes.items():
            fh.write("%s  %s\n" % (digest, name))


def parse_float(value, default=0.0):
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def load_generic(key, zip_name):
    """Return (foods, nutrients, portions).

    foods: dict fdc_id -> dict(name). nutrients: dict fdc_id -> dict nid->amount.
    portions: dict fdc_id -> list of (unit_name, grams).
    """
    import zipfile
    import io as _io

    def io_wrap(fh):
        return _io.TextIOWrapper(fh, encoding="utf-8-sig", errors="replace")

    zip_path = os.path.join(CACHE_DIR, zip_name)
    prefix = zip_name.replace(".zip", "") + "/"
    zf = zipfile.ZipFile(zip_path)

    foods = {}
    with zf.open(prefix + "food.csv") as fh:
        for row in csv.DictReader(io_wrap(fh)):
            if key == "foundation" and row.get("data_type") != "foundation_food":
                continue
            desc = (row.get("description") or "").strip()
            if not desc:
                continue
            foods[int(row["fdc_id"])] = desc

    nutrients = {}
    with zf.open(prefix + "food_nutrient.csv") as fh:
        for row in csv.DictReader(io_wrap(fh)):
            try:
                nid = int(row["nutrient_id"])
            except (TypeError, ValueError):
                continue
            if nid not in WANTED_NIDS:
                continue
            try:
                fdc = int(row["fdc_id"])
            except (TypeError, ValueError):
                continue
            if fdc not in foods:
                continue
            nutrients.setdefault(fdc, {})[nid] = parse_float(row.get("amount"), 0.0)

    measure_units = {}
    with zf.open(prefix + "measure_unit.csv") as fh:
        for row in csv.DictReader(io_wrap(fh)):
            measure_units[row["id"]] = (row.get("name") or "").strip()

    portions = {}
    with zf.open(prefix + "food_portion.csv") as fh:
        for row in csv.DictReader(io_wrap(fh)):
            try:
                fdc = int(row["fdc_id"])
            except (TypeError, ValueError):
                continue
            if fdc not in foods:
                continue
            grams = parse_float(row.get("gram_weight"), 0.0)
            if grams <= 0:
                continue
            name = ((row.get("portion_description") or "").strip()
                    or (row.get("modifier") or "").strip()
                    or measure_units.get(row.get("measure_unit_id", ""), ""))
            name = " ".join(name.split())
            if not name:
                continue
            portions.setdefault(fdc, []).append((name[:60], grams))

    log("generic %s: %d foods, %d with nutrients, %d with portions"
        % (key, len(foods), len(nutrients), len(portions)))
    return foods, nutrients, portions


def branded_serving_units(serving_size, serving_unit, household):
    """Derive up to 2 (unit_name, grams) pairs from branded serving info."""
    units = []
    try:
        size = float(serving_size)
    except (TypeError, ValueError):
        return units
    if size <= 0:
        return units
    mult = UNIT_TO_GRAMS.get((serving_unit or "").strip().lower())
    if mult is None:
        return units
    grams = size * mult
    label = ("%g %s" % (size, (serving_unit or "").strip()))[:60]
    units.append((label, grams))
    house = " ".join((household or "").split())[:60]
    if house and house.lower() != label.lower():
        units.append((house, grams))
    return units


def load_branded(needed):
    """Stream the branded dataset; return (selected, nutrients, units).

    selected: list of dicts in ascending fdc_id order, at most `needed`
    entries passing the deterministic filter. nutrients: fdc -> nid->amount
    for selected rows only. units: fdc -> list of (unit_name, grams).
    """
    import zipfile
    zip_name = DATASETS[3][1]
    zip_path = os.path.join(CACHE_DIR, zip_name)
    prefix = zip_name.replace(".zip", "") + "/"
    zf = zipfile.ZipFile(zip_path)
    import io as _io

    def io_wrap(fh):
        return _io.TextIOWrapper(fh, encoding="utf-8-sig", errors="replace")

    # Pass 1: cheap filters (description length, gtin present). Row-aligned
    # food.csv + branded_food.csv are read in lockstep.
    candidates = {}  # fdc -> (name, brand, barcode, serving_size, serving_unit, household)
    with zf.open(prefix + "food.csv") as fa, zf.open(prefix + "branded_food.csv") as fb:
        ra = csv.DictReader(io_wrap(fa))
        rb = csv.DictReader(io_wrap(fb))
        for a, b in zip(ra, rb):
            try:
                fdc = int(a["fdc_id"])
            except (TypeError, ValueError):
                continue
            if b["fdc_id"] != a["fdc_id"]:
                fail("branded food.csv / branded_food.csv row misalignment at fdc %s"
                     % a["fdc_id"])
            desc = (a.get("description") or "").strip()
            if not (3 <= len(desc) <= 150):
                continue
            gtin = (b.get("gtin_upc") or "").strip()
            if not gtin:
                continue
            brand = (b.get("brand_owner") or "").strip() or (b.get("brand_name") or "").strip()
            candidates[fdc] = (desc, brand,
                               gtin,  # barcode preserves leading zeros
                               b.get("serving_size"), b.get("serving_size_unit"),
                               b.get("household_serving_fulltext"))
    log("branded pass 1: %d cheap-filter candidates" % len(candidates))
    if not candidates:
        fail("no branded candidates after cheap filters")

    # Pass 2: nutrients for candidates only (single streaming pass).
    nutrients = {}
    milestone = 0
    with zf.open(prefix + "food_nutrient.csv") as fh:
        get = candidates.get
        for row in csv.DictReader(io_wrap(fh)):
            try:
                fdc = int(row["fdc_id"])
            except (TypeError, ValueError):
                continue
            if get(fdc) is None:
                continue
            try:
                nid = int(row["nutrient_id"])
            except (TypeError, ValueError):
                continue
            if nid not in WANTED_NIDS:
                continue
            nutrients.setdefault(fdc, {})[nid] = parse_float(row.get("amount"), 0.0)
            if len(nutrients) - milestone >= 500000:
                milestone = len(nutrients)
                log("  ... nutrient pass: %d foods seen" % milestone)
    log("branded pass 2: nutrients for %d candidates" % len(nutrients))

    # Pass 3: ascending fdc_id, macros present + dedupe, take until needed.
    selected = []
    selected_nutrients = {}
    selected_units = {}
    seen_gtin = set()
    seen_name_brand = set()
    for fdc in sorted(candidates):
        if len(selected) >= needed:
            break
        nuts = nutrients.get(fdc)
        if not nuts:
            continue
        kcal = next((nuts[n] for n in NID_KCAL if n in nuts), None)
        if kcal is None or NID_PROTEIN not in nuts or NID_CARBS not in nuts \
                or NID_FAT not in nuts:
            continue
        name, brand, barcode, ssize, sunit, house = candidates[fdc]
        gtin_key = barcode.replace(" ", "").replace("-", "").lower()
        if gtin_key in seen_gtin:
            continue
        nb_key = (name.strip().lower(), brand.strip().lower())
        if nb_key in seen_name_brand:
            continue
        seen_gtin.add(gtin_key)
        seen_name_brand.add(nb_key)
        selected.append({"fdc": fdc, "name": name, "brand": brand or None,
                         "barcode": barcode})
        selected_nutrients[fdc] = nuts
        units = branded_serving_units(ssize, sunit, house)
        if units:
            selected_units[fdc] = units
    log("branded pass 3: selected %d (needed %d)" % (len(selected), needed))
    return selected, selected_nutrients, selected_units


def macro_row(nuts):
    kcal = next((nuts[n] for n in NID_KCAL if n in nuts), 0.0)
    return (kcal, nuts.get(NID_PROTEIN, 0.0), nuts.get(NID_CARBS, 0.0),
            nuts.get(NID_FAT, 0.0), nuts.get(NID_FIBER, 0.0),
            nuts.get(NID_SUGAR, 0.0), nuts.get(NID_SODIUM, 0.0))


def build_rows(generic_data, branded_data):
    """Assemble final catalog rows in Foundation -> SR -> FNDDS -> Branded order."""
    rows = []       # (name, brand, barcode)
    nut_rows = []   # (calories, protein, carbs, fat, fiber, sugar, sodium)
    unit_rows = []  # (food_seq_id, unit_name, grams)
    for key in ("foundation", "sr_legacy", "survey"):
        foods, nutrients, portions = generic_data[key]
        for fdc in sorted(foods):
            rows.append((foods[fdc], None, None))
            nut_rows.append(macro_row(nutrients.get(fdc, {})))
            seq_id = len(rows)
            seen = set()
            for uname, grams in portions.get(fdc, [])[:8]:
                k = uname.lower()
                if k in seen:
                    continue
                seen.add(k)
                unit_rows.append((seq_id, uname, grams))
    selected, sel_nuts, sel_units = branded_data
    for item in selected:
        rows.append((item["name"], item["brand"], item["barcode"]))
        nut_rows.append(macro_row(sel_nuts[item["fdc"]]))
        seq_id = len(rows)
        seen = set()
        for uname, grams in sel_units.get(item["fdc"], [])[:8]:
            k = uname.lower()
            if k in seen:
                continue
            seen.add(k)
            unit_rows.append((seq_id, uname, grams))
    return rows, nut_rows, unit_rows


def create_schema(con):
    """Execute the Room-exported schema: entities, indices, FTS, triggers."""
    with open(SCHEMA_JSON, encoding="utf-8") as fh:
        schema = json.load(fh)
    db = schema["database"]
    if db["version"] != 1:
        fail("unexpected schema version %r in %s" % (db.get("version"), SCHEMA_JSON))
    cur = con.cursor()
    for entity in db["entities"]:
        table = entity["tableName"]
        create_sql = entity["createSql"].replace("${TABLE_NAME}", table)
        log("schema: %s" % create_sql[:110])
        cur.execute(create_sql)
        for index in entity.get("indices", []):
            cur.execute(index["createSql"].replace("${TABLE_NAME}", table))
    for trigger in FTS_TRIGGERS:
        cur.execute(trigger)
    for stmt in db.get("setupQueries", []):
        cur.execute(stmt)
    con.commit()
    log("schema: %d entities + %d FTS triggers + room_master_table"
        % (len(db["entities"]), len(FTS_TRIGGERS)))


def main():
    t0 = time.time()
    preflight()
    download_datasets()

    generic_data = {}
    for key, zip_name, _kind in DATASETS[:3]:
        generic_data[key] = load_generic(key, zip_name)
    generic_total = sum(len(generic_data[k][0]) for k in generic_data)
    log("generic total: %d" % generic_total)

    needed = max(TARGET_TOTAL - generic_total, 0)
    branded_data = load_branded(needed)

    rows, nut_rows, unit_rows = build_rows(generic_data, branded_data)
    total = len(rows)
    log("assembled: %d foods, %d nutrient rows, %d serving units"
        % (total, len(nut_rows), len(unit_rows)))
    if total < MIN_TOTAL:
        fail("catalog has %d foods, below the %d minimum" % (total, MIN_TOTAL))

    if os.path.exists(ASSET_DB):
        os.remove(ASSET_DB)
    os.makedirs(os.path.dirname(ASSET_DB), exist_ok=True)
    con = sqlite3.connect(ASSET_DB)
    try:
        create_schema(con)
        cur = con.cursor()
        cur.execute("PRAGMA foreign_keys = OFF")
        food_ids = list(range(1, total + 1))
        cur.executemany(
            "INSERT INTO catalog_foods (id, name, brand, barcode, base_serving_size,"
            " base_serving_unit, is_custom, is_deleted, created_at, updated_at)"
            " VALUES (?, ?, ?, ?, 100.0, 'g', 0, 0, %d, %d)"
            % (FIXED_EPOCH, FIXED_EPOCH),
            [(fid, r[0], r[1], r[2]) for fid, r in zip(food_ids, rows)])
        cur.executemany(
            "INSERT INTO catalog_food_nutrients (food_id, calories, protein_g,"
            " carbs_g, fat_g, fiber_g, sugar_g, sodium_mg)"
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            [(fid, n[0], n[1], n[2], n[3], n[4], n[5], n[6])
             for fid, n in zip(food_ids, nut_rows)])
        cur.executemany(
            "INSERT INTO catalog_food_serving_units (food_id, unit_name, grams_per_unit)"
            " VALUES (?, ?, ?)",
            unit_rows)
        con.commit()
        cur.execute("PRAGMA user_version = 1")
        # Safety net: triggers already populated the FTS index on insert.
        cur.execute("INSERT INTO catalog_foods_fts(catalog_foods_fts) VALUES('rebuild')")
        con.commit()
        count = cur.execute("SELECT COUNT(*) FROM catalog_foods").fetchone()[0]
        fts_probe = cur.execute(
            "SELECT COUNT(*) FROM catalog_foods_fts WHERE catalog_foods_fts MATCH '\"chick\"*'").fetchone()[0]
        log("rows: catalog_foods=%d fts_prefix_probe(chick)=%d" % (count, fts_probe))
        cur.execute("PRAGMA journal_mode = DELETE")
        cur.execute("VACUUM")
        integrity = cur.execute("PRAGMA integrity_check").fetchone()[0]
        log("integrity_check: %s" % integrity)
        if integrity != "ok":
            fail("integrity_check failed: %s" % integrity)
    finally:
        con.close()

    size_mb = os.path.getsize(ASSET_DB) / 1e6
    log("wrote %s (%.1f MB) in %.0fs" % (ASSET_DB, size_mb, time.time() - t0))


if __name__ == "__main__":
    main()
