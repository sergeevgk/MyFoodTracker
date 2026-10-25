#!/usr/bin/env python3
"""Build the pre-populated food catalog SQLite database (Story 3.5).

Sources (both Open Data, attribution required -- see tools/seed/README.md):
  1. Bundeslebensmittelschlüssel (BLS) 4.0, Max Rubner-Institut, CC BY 4.0.
     ~7,140 generic German staple foods with 138 nutrients per 100 g.
     DOI: 10.25826/Data20251217-134202-0, portal: https://blsdb.de/
  2. Open Food Facts (OFF) products dump, © Open Food Facts contributors, ODbL.
     Filtered to products sold in Germany (`countries_tags` contains
     `en:germany`); top ~35,000 branded items (25,000 German GS1 400-440 +
     10,000 other European items) sorted by scan popularity.
     https://world.openfoodfacts.org/data

Pinned datasets (the script fails loudly on 404 / checksum change):
  BLS_4_0_2025_DE.zip ............ ~14 MB zip, xlsx with 7,140 foods
  en.openfoodfacts.org.products.csv.gz .. ~1.3 GB gzip TSV, streamed, never
     fully loaded into RAM; only German rows passing the filters are kept.

Regenerate:
  python3 tools/seed/build_food_catalog_db.py
  python3 tools/seed/build_food_catalog_db.py --self-test   # hermetic checks
  python3 tools/seed/build_food_catalog_db.py --skip-checksum  # when refreshing

Expected output: app/src/main/assets/databases/food_catalog.db with
  35,000 <= SELECT COUNT(*) FROM catalog_foods <= 45,000 (target ~42,140)
  PRAGMA integrity_check = ok, roughly 10-20 MB.
"""

import csv
import gzip
import hashlib
import io
import json
import os
import re
import sqlite3
import sys
import time
import urllib.request
import zipfile
from datetime import datetime, timezone
from xml.etree.ElementTree import iterparse, fromstring

# OFF rows can carry >128 KB text fields (packaging/ingredient blobs);
# raise the csv module ceiling before any parsing happens.
csv.field_size_limit(sys.maxsize)

# ---------------------------------------------------------------------------
# Pinned configuration
# ---------------------------------------------------------------------------

BLS_URL = "https://blsdb.de/assets/uploads/BLS_4_0_2025_DE.zip"
BLS_ZIP_NAME = "BLS_4_0_2025_DE.zip"
# Inner xlsx member (stable across re-downloads of the same release).
BLS_XLSX_MEMBER = "BLS_4_0_2025_DE/BLS_4_0_Daten_2025_DE.xlsx"

OFF_URL = ("https://openfoodfacts-ds.s3.eu-west-3.amazonaws.com/"
           "en.openfoodfacts.org.products.csv.gz")
OFF_GZ_NAME = "en.openfoodfacts.org.products.csv.gz"

PINNED_SHA256 = {
    BLS_ZIP_NAME: "12b7a6ba62807ec9b301eb276f897dc85f99b2292311618dec3749a12d984c91",
    OFF_GZ_NAME: "f72687ee8bc6522054fe69dbfda6b91902c16af1ec2e043cde27bc6c29ad8176",
}

TARGET_BRANDED_DE = 25000
TARGET_BRANDED_EU = 10000
TARGET_BRANDED = TARGET_BRANDED_DE + TARGET_BRANDED_EU
MIN_TOTAL = 35000
MAX_TOTAL = 45000

# BLS header names for the nutrient value columns (each nutrient also has
# "<CODE> Datenherkunft" / "<CODE> Referenz" columns which are ignored).
BLS_COL_CODE = "BLS Code"
BLS_COL_NAME = "Lebensmittelbezeichnung"
BLS_COL_KCAL = "ENERCC Energie (Kilokalorien) [kcal/100g]"
BLS_COL_KJ = "ENERCJ Energie (Kilojoule) [kJ/100g]"
BLS_COL_PROTEIN = "PROT625 Protein (Nx6,25) [g/100g]"
BLS_COL_FAT = "FAT Fett [g/100g]"
BLS_COL_CARBS = "CHO Kohlenhydrate, verfügbar [g/100g]"
BLS_COL_FIBER = "FIBT Ballaststoffe, gesamt [g/100g]"
BLS_COL_SUGAR = "SUGAR Zucker (Mono- und Disaccharide), gesamt [g/100g]"
BLS_COL_SODIUM = "NA Natrium [mg/100g]"
BLS_COL_SALT = "NACL Salz (Natriumchlorid) [g/100g]"

KJ_TO_KCAL = 1.0 / 4.184
# OFF sodium_100g is grams -> mg; salt (NaCl) g -> sodium mg (23/58.5*1000).
SALT_G_TO_SODIUM_MG = 393.15

# Fixed timestamps: the BLS 4.0 release date as epoch (reproducible builds).
FIXED_EPOCH = int(datetime(2025, 12, 17, tzinfo=timezone.utc).timestamp())

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
    "g": 1.0, "gram": 1.0, "grams": 1.0, "grm": 1.0, "g.": 1.0,
    "ml": 1.0, "milliliter": 1.0, "millilitre": 1.0, "ml.": 1.0,
    "kg": 1000.0,
    "oz": 28.3495, "ounce": 28.3495,
    "fl oz": 29.5735, "fl-oz": 29.5735, "floz": 29.5735,
    "lb": 453.592, "pound": 453.592,
    "l": 1000.0, "liter": 1000.0, "litre": 1000.0, "l.": 1000.0,
}

_SERVING_RE = re.compile(r"^\s*([\d]+(?:[.,][\d]+)?)\s*([a-zA-Zµμ°]+(?:[\s-][a-zA-Z]+)?)?")

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


def download_datasets(skip_checksum=False):
    hashes = {}
    for fname, url in ((BLS_ZIP_NAME, BLS_URL), (OFF_GZ_NAME, OFF_URL)):
        dest = os.path.join(CACHE_DIR, fname)
        if os.path.exists(dest) and os.path.getsize(dest) > 0:
            log("cache hit: %s (%.1f MB)" % (fname, os.path.getsize(dest) / 1e6))
        else:
            log("downloading %s ..." % fname)
            try:
                urllib.request.urlretrieve(url, dest)
            except Exception as exc:
                fail("download failed for %s: %s" % (url, exc))
            log("downloaded %s (%.1f MB)" % (fname, os.path.getsize(dest) / 1e6))
        sha = hashlib.sha256()
        with open(dest, "rb") as fh:
            for chunk in iter(lambda: fh.read(1 << 20), b""):
                sha.update(chunk)
        digest = sha.hexdigest()
        hashes[fname] = digest
        log("sha256 %s %s" % (digest[:16], fname))
        if not skip_checksum:
            expected = PINNED_SHA256.get(fname)
            if expected and digest != expected:
                fail("SHA-256 mismatch for %s:\n  expected: %s\n  got:      %s"
                     % (fname, expected, digest))
    with open(os.path.join(CACHE_DIR, "SHA256SUMS"), "w") as fh:
        for name, digest in hashes.items():
            fh.write("%s  %s\n" % (digest, name))


def parse_float(value, default=0.0):
    try:
        if value is None:
            return default
        s = str(value).strip().replace(",", ".")
        if not s:
            return default
        f = float(s)
        if f != f or f in (float("inf"), float("-inf")):  # NaN / Inf guard
            return default
        return f
    except (TypeError, ValueError):
        return default


def normalize_barcode(code):
    return (code or "").replace(" ", "").replace("-", "").lower()


def normalize_name_brand(name, brand):
    return ((name or "").strip().lower(), (brand or "").strip().lower())


def is_german_barcode(barcode):
    digits = re.sub(r"\D", "", barcode or "")
    if len(digits) >= 3 and 400 <= int(digits[:3]) <= 440:
        return True
    return False


# ---------------------------------------------------------------------------
# BLS 4.0 xlsx parsing (stdlib only: zipfile + xml.etree)
# ---------------------------------------------------------------------------

_SS_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
_CELL_RE = re.compile(r"^([A-Z]+)(\d+)$")


def _col_letters(ref):
    m = _CELL_RE.match(ref or "")
    return m.group(1) if m else ""


def load_bls():
    """Return (foods, nutrients) from the BLS xlsx inside the cached zip.

    foods: list of (bls_code, german_name). nutrients: dict bls_code ->
    (kcal, protein, carbs, fat, fiber, sugar, sodium).
    """
    zip_path = os.path.join(CACHE_DIR, BLS_ZIP_NAME)
    try:
        outer = zipfile.ZipFile(zip_path)
    except zipfile.BadZipFile:
        fail("BLS archive is not a valid zip: %s" % zip_path)
    try:
        names = outer.namelist()
    except Exception as exc:
        fail("cannot list BLS archive members: %s" % exc)
    member = BLS_XLSX_MEMBER if BLS_XLSX_MEMBER in names else None
    if member is None:
        xlsx = [n for n in names if n.lower().endswith(".xlsx")]
        if len(xlsx) != 1:
            fail("BLS archive layout changed: expected one xlsx, members=%r"
                 % names[:20])
        member = xlsx[0]
        log("BLS inner xlsx member renamed, using: %s" % member)
    raw = outer.read(member)
    outer.close()

    inner = zipfile.ZipFile(io.BytesIO(raw))
    try:
        sheet_names = [n for n in inner.namelist()
                       if n.startswith("xl/worksheets/sheet")]
    except Exception as exc:
        fail("cannot list xlsx sheets: %s" % exc)
    if not sheet_names:
        fail("no worksheets found in BLS xlsx")
    sheet = sheet_names[0]

    # Shared strings table (small: ~24k entries).
    try:
        ss_root = fromstring(inner.read("xl/sharedStrings.xml"))
    except KeyError:
        fail("xl/sharedStrings.xml missing in BLS xlsx")
    strs = []
    for si in ss_root.iter("{%s}si" % _SS_NS):
        strs.append("".join(t.text or ""
                            for t in si.iter("{%s}t" % _SS_NS)))

    def cell_text(cell):
        v = cell.find("{%s}v" % _SS_NS)
        val = v.text if v is not None and v.text is not None else ""
        if cell.get("t") == "s":
            try:
                return strs[int(val)] if val else ""
            except (ValueError, IndexError):
                return ""
        if cell.get("t") == "inlineStr":
            t = cell.find("{%s}is/{%s}t" % (_SS_NS, _SS_NS))
            return t.text or "" if t is not None else ""
        return val

    header = None
    col_index = {}
    foods = []
    nutrients = {}
    wanted = {BLS_COL_KCAL, BLS_COL_KJ, BLS_COL_PROTEIN, BLS_COL_FAT,
              BLS_COL_CARBS, BLS_COL_FIBER, BLS_COL_SUGAR, BLS_COL_SODIUM,
              BLS_COL_SALT}
    for _ev, row in iterparse(inner.open(sheet), events=("end",)):
        if not row.tag.endswith("}row"):
            continue
        cells = {}
        for c in row.iter("{%s}c" % _SS_NS):
            ref = c.get("r")
            if ref:
                cells[_col_letters(ref)] = cell_text(c)
        row.clear()
        if header is None:
            # Header row: map column letter -> header name.
            header = cells
            inv = {v: k for k, v in cells.items()}
            for name in ([BLS_COL_CODE, BLS_COL_NAME] + sorted(wanted)):
                if name not in inv:
                    fail("BLS header %r not found; archive layout changed. "
                         "Headers seen: %r" % (name, sorted(cells.values())[:10]))
                col_index[name] = inv[name]
            continue
        code = cells.get(col_index[BLS_COL_CODE], "").strip()
        name = " ".join(cells.get(col_index[BLS_COL_NAME], "").split())
        if not code or not name:
            continue
        foods.append((code, name))
        kcal = parse_float(cells.get(col_index[BLS_COL_KCAL]), None)
        if kcal is None:
            kj = parse_float(cells.get(col_index[BLS_COL_KJ]), None)
            kcal = kj * KJ_TO_KCAL if kj is not None else 0.0
        sodium = parse_float(cells.get(col_index[BLS_COL_SODIUM]), None)
        if sodium is None:
            salt = parse_float(cells.get(col_index[BLS_COL_SALT]), None)
            sodium = salt * SALT_G_TO_SODIUM_MG if salt is not None else 0.0
        nutrients[code] = (
            kcal,
            parse_float(cells.get(col_index[BLS_COL_PROTEIN]), 0.0),
            parse_float(cells.get(col_index[BLS_COL_CARBS]), 0.0),
            parse_float(cells.get(col_index[BLS_COL_FAT]), 0.0),
            parse_float(cells.get(col_index[BLS_COL_FIBER]), 0.0),
            parse_float(cells.get(col_index[BLS_COL_SUGAR]), 0.0),
            sodium,
        )
    inner.close()
    log("BLS: %d foods, %d with parsed nutrients" % (len(foods), len(nutrients)))
    if len(foods) < 7000:
        fail("BLS food count %d below the expected ~7,140" % len(foods))
    foods.sort(key=lambda kv: kv[0])
    return foods, nutrients


# ---------------------------------------------------------------------------
# Open Food Facts parsing (streamed gzip TSV)
# ---------------------------------------------------------------------------

OFF_NEEDED = ("code", "product_name", "brands",
              "countries_tags", "serving_size", "quantity",
              "energy-kcal_100g", "energy_100g",
              "proteins_100g", "carbohydrates_100g", "fat_100g",
              "fiber_100g", "sugars_100g", "sodium_100g", "salt_100g",
              "unique_scans_n", "completeness")


def parse_serving_size(text):
    """Parse '30 g' / '250ml' style serving info -> (label, grams) or None."""
    if not text:
        return None
    m = _SERVING_RE.match(str(text))
    if not m:
        return None
    try:
        amount = float(m.group(1).replace(",", "."))
    except ValueError:
        return None
    if not amount > 0:
        return None
    unit = (m.group(2) or "g").strip().lower()
    mult = UNIT_TO_GRAMS.get(unit)
    if mult is None:
        return None
    label = ("%g %s" % (amount, (m.group(2) or "g").strip()))[:60]
    return (label, amount * mult)


def branded_serving_units(serving_size, quantity):
    units = []
    for text in (serving_size, quantity):
        parsed = parse_serving_size(text)
        if parsed and all(parsed[0].lower() != u[0].lower() for u in units):
            units.append(parsed)
    return units[:8]


def off_display_name(raw_name_or_row):
    # NOTE: the bulk TSV export carries only the generic `product_name`
    # (no per-language columns). Rows are pre-filtered to products sold in
    # Germany, whose names are German in practice.
    if isinstance(raw_name_or_row, dict):
        text = raw_name_or_row.get("product_name") or ""
    elif isinstance(raw_name_or_row, str):
        text = raw_name_or_row
    else:
        text = str(raw_name_or_row or "")
    return " ".join(text.split())


def load_off():
    """Stream the OFF dump; return (selected, nutrients, units).

    selected: list of dicts with target 35,000 branded items (25,000 German GS1
    400-440 + 10,000 other European items), sorted by scan popularity within
    each group, and deduplicated.
    """
    gz_path = os.path.join(CACHE_DIR, OFF_GZ_NAME)
    candidates_de = []
    candidates_eu = []
    seen_raw_barcode = set()
    total_rows = 0
    german_rows = 0
    try:
        fh = gzip.open(gz_path, "rt", encoding="utf-8", errors="replace")
    except OSError as exc:
        fail("cannot open OFF dump %s: %s" % (gz_path, exc))
    with fh:
        reader = csv.reader(fh, delimiter="\t")
        try:
            header = next(reader)
        except StopIteration:
            fail("OFF dump is empty")
        idx = {c: i for i, c in enumerate(header)}
        missing = [c for c in OFF_NEEDED if c not in idx]
        if missing:
            fail("OFF header missing columns %r (dump layout changed)" % missing)
        i_code, i_name, i_brands, i_tags = (idx["code"], idx["product_name"],
                                           idx["brands"], idx["countries_tags"])
        i_serv, i_qty = idx["serving_size"], idx["quantity"]
        i_kcal, i_kj = idx["energy-kcal_100g"], idx["energy_100g"]
        i_prot, i_carb, i_fat = (idx["proteins_100g"],
                                 idx["carbohydrates_100g"], idx["fat_100g"])
        i_fib, i_sug = idx["fiber_100g"], idx["sugars_100g"]
        i_sod, i_salt = idx["sodium_100g"], idx["salt_100g"]
        i_scans, i_comp = idx["unique_scans_n"], idx["completeness"]
        ncol = len(header)
        for row in reader:
            total_rows += 1
            if total_rows % 500000 == 0:
                log("  ... OFF scan: %d rows, %d valid candidates (DE: %d, EU: %d)"
                    % (total_rows, german_rows, len(candidates_de), len(candidates_eu)))
            if len(row) < ncol:
                continue
            tags = (row[i_tags] or "").lower()
            if "germany" not in tags:
                continue
            barcode = (row[i_code] or "").strip()
            digits = re.sub(r"\D", "", barcode)
            if not (8 <= len(digits) <= 14):
                continue
            b_norm = normalize_barcode(barcode)
            if b_norm in seen_raw_barcode:
                continue
            name = off_display_name(row[i_name])
            if not (3 <= len(name) <= 150):
                continue
            brand = " ".join((row[i_brands] or "").split())
            if not brand:
                continue
            kcal = parse_float(row[i_kcal], None)
            if kcal is None:
                kj = parse_float(row[i_kj], None)
                kcal = kj * KJ_TO_KCAL if kj is not None else None
            protein = parse_float(row[i_prot], None)
            carbs = parse_float(row[i_carb], None)
            fat = parse_float(row[i_fat], None)
            if kcal is None or protein is None or carbs is None or fat is None:
                continue
            # Physiological bounds per 100g
            if kcal < 0.0 or kcal > 900.0:
                continue
            if protein < 0.0 or protein > 100.0:
                continue
            if carbs < 0.0 or carbs > 100.0:
                continue
            if fat < 0.0 or fat > 100.0:
                continue
            if (protein + carbs + fat) > 105.0:
                continue
            fiber = max(parse_float(row[i_fib], 0.0), 0.0)
            sugar = max(parse_float(row[i_sug], 0.0), 0.0)
            sodium = parse_float(row[i_sod], None)
            if sodium is not None:
                sodium_mg = sodium * 1000.0
            else:
                salt = parse_float(row[i_salt], None)
                sodium_mg = salt * SALT_G_TO_SODIUM_MG if salt is not None else 0.0
            if sodium_mg < 0.0 or sodium_mg > 40000.0:
                continue

            seen_raw_barcode.add(b_norm)
            scans = parse_float(row[i_scans], 0.0)
            comp = parse_float(row[i_comp], 0.0)
            item = {
                "barcode": barcode,
                "name": name,
                "brand": brand,
                "nutrients": (kcal, protein, carbs, fat, fiber, sugar, sodium_mg),
                "serving_size": row[i_serv],
                "quantity": row[i_qty],
                "scans": scans,
                "comp": comp,
            }
            german_rows += 1
            if is_german_barcode(digits):
                candidates_de.append(item)
            else:
                candidates_eu.append(item)

    log("OFF scan: %d rows total, %d valid candidates (DE GS1: %d, EU: %d)"
        % (total_rows, german_rows, len(candidates_de), len(candidates_eu)))
    if not candidates_de and not candidates_eu:
        fail("no OFF German candidates after filters")

    # Sort each group by popularity descending (scans, completeness, stable barcode tie-break)
    candidates_de.sort(key=lambda x: (x["scans"], x["comp"], x["barcode"]), reverse=True)
    candidates_eu.sort(key=lambda x: (x["scans"], x["comp"], x["barcode"]), reverse=True)

    seen_barcode = set()
    seen_name_brand = set()
    selected_nutrients = {}
    selected_units = {}

    def pick_items(source_list, quota):
        picked = []
        for item in source_list:
            if len(picked) >= quota:
                break
            bkey = normalize_barcode(item["barcode"])
            if bkey in seen_barcode:
                continue
            nb_key = normalize_name_brand(item["name"], item["brand"])
            if nb_key in seen_name_brand:
                continue
            seen_barcode.add(bkey)
            seen_name_brand.add(nb_key)
            picked.append(item)
            selected_nutrients[item["barcode"]] = item["nutrients"]
            units = branded_serving_units(item["serving_size"], item["quantity"])
            if units:
                selected_units[item["barcode"]] = units
        return picked

    final_de = pick_items(candidates_de, TARGET_BRANDED_DE)
    final_eu = pick_items(candidates_eu, TARGET_BRANDED_EU)
    log("OFF selection: %d German GS1 foods (target %d), %d European foods (target %d)"
        % (len(final_de), TARGET_BRANDED_DE, len(final_eu), TARGET_BRANDED_EU))

    final = final_de + final_eu
    # Deterministic order by barcode for reproducible IDs
    final.sort(key=lambda item: item["barcode"])

    if len(final) < 25000:
        fail("OFF total selection %d below 25,000" % len(final))
    return final, selected_nutrients, selected_units


# ---------------------------------------------------------------------------
# Catalog assembly + schema
# ---------------------------------------------------------------------------

def build_rows(bls_data, off_data):
    """Assemble final catalog rows in BLS -> OFF order."""
    rows = []       # (name, brand, barcode)
    nut_rows = []   # (calories, protein, carbs, fat, fiber, sugar, sodium)
    unit_rows = []  # (food_seq_id, unit_name, grams)
    foods, nutrients = bls_data
    for code, name in foods:
        rows.append((name, None, None))
        nut_rows.append(nutrients.get(code, (0.0,) * 7))
        seq_id = len(rows)
        unit_rows.append((seq_id, "100 g", 100.0))
    selected, sel_nuts, sel_units = off_data
    for item in selected:
        rows.append((item["name"], item["brand"], item["barcode"]))
        nut_rows.append(sel_nuts[item["barcode"]])
        seq_id = len(rows)
        # Decision 2: All branded foods receive a baseline "100 g" serving unit
        unit_rows.append((seq_id, "100 g", 100.0))
        seen = {"100 g", "100g"}
        for uname, grams in sel_units.get(item["barcode"], [])[:7]:
            k = uname.lower()
            if k in seen:
                continue
            seen.add(k)
            unit_rows.append((seq_id, uname, grams))
    return rows, nut_rows, unit_rows


def create_schema(con):
    with open(SCHEMA_JSON, "r", encoding="utf-8") as fh:
        db = json.load(fh)["database"]
    cur = con.cursor()
    for entity in db["entities"]:
        table = entity["tableName"]
        cur.execute(entity["createSql"].replace("${TABLE_NAME}", table))
        for idx in entity.get("indices", []):
            cur.execute(idx["createSql"].replace("${TABLE_NAME}", table))
    for trigger_sql in FTS_TRIGGERS:
        cur.execute(trigger_sql)
    for q in db.get("setupQueries", []):
        cur.execute(q)
    log("schema created from Room JSON (%d entities, %d FTS triggers)"
        % (len(db["entities"]), len(FTS_TRIGGERS)))


def verify_db(con):
    cur = con.cursor()
    count = cur.execute("SELECT COUNT(*) FROM catalog_foods").fetchone()[0]
    nut_count = cur.execute(
        "SELECT COUNT(*) FROM catalog_food_nutrients").fetchone()[0]
    unit_count = cur.execute(
        "SELECT COUNT(*) FROM catalog_food_serving_units").fetchone()[0]
    log("rows: catalog_foods=%d catalog_food_nutrients=%d serving_units=%d"
        % (count, nut_count, unit_count))
    if nut_count != count:
        fail("nutrient rows %d != food rows %d" % (nut_count, count))
    if count < MIN_TOTAL or count > MAX_TOTAL:
        fail("catalog row count %d outside %d-%d band" % (count, MIN_TOTAL, MAX_TOTAL))
    # Verify German GS1 barcodes exist in large numbers (prefix 400-440)
    de_barcodes = cur.execute(
        "SELECT COUNT(*) FROM catalog_foods WHERE barcode IS NOT NULL "
        "AND CAST(SUBSTR(barcode, 1, 3) AS INTEGER) BETWEEN 400 AND 440"
    ).fetchone()[0]
    log("German GS1 barcodes (400-440): %d" % de_barcodes)
    if de_barcodes < 20000:
        fail("German GS1 barcodes %d below expected 20,000" % de_barcodes)
    # Check no negative or corrupt nutrients
    bad_nuts = cur.execute(
        "SELECT COUNT(*) FROM catalog_food_nutrients "
        "WHERE calories < 0 OR calories > 900 OR protein_g < 0 OR protein_g > 100 "
        "OR carbs_g < 0 OR carbs_g > 100 OR fat_g < 0 OR fat_g > 100 OR sodium_mg < 0 "
        "OR sodium_mg > 40000"
    ).fetchone()[0]
    if bad_nuts > 0:
        fail("Found %d corrupt nutrient rows" % bad_nuts)
    for probe in ("vollmilch", "haferflocken", "magerquark", "alpro", "haribo"):
        hits = cur.execute(
            "SELECT COUNT(*) FROM catalog_foods_fts"
            " WHERE catalog_foods_fts MATCH '\"%s\"*'" % probe).fetchone()[0]
        log("fts probe(%s)=%d" % (probe, hits))
        if hits == 0:
            fail("FTS probe %r returned zero rows" % probe)
    triggers = {r[0] for r in cur.execute(
        "SELECT name FROM sqlite_master WHERE type = 'trigger'").fetchall()}
    for name in ("room_fts_content_sync_catalog_foods_fts_BEFORE_UPDATE",
                 "room_fts_content_sync_catalog_foods_fts_BEFORE_DELETE",
                 "room_fts_content_sync_catalog_foods_fts_AFTER_UPDATE",
                 "room_fts_content_sync_catalog_foods_fts_AFTER_INSERT"):
        if name not in triggers:
            fail("Room FTS trigger missing: %s" % name)
    return count


def main():
    t0 = time.time()
    skip_checksum = "--skip-checksum" in sys.argv
    preflight()
    download_datasets(skip_checksum=skip_checksum)

    bls_data = load_bls()
    off_data = load_off()

    rows, nut_rows, unit_rows = build_rows(bls_data, off_data)
    total = len(rows)
    log("assembled: %d foods, %d nutrient rows, %d serving units"
        % (total, len(nut_rows), len(unit_rows)))
    if total < MIN_TOTAL or total > MAX_TOTAL:
        fail("catalog has %d foods, outside the %d-%d band"
             % (total, MIN_TOTAL, MAX_TOTAL))

    if os.path.exists(ASSET_DB):
        log("removing existing asset DB: %s" % ASSET_DB)
        os.remove(ASSET_DB)
    os.makedirs(os.path.dirname(ASSET_DB), exist_ok=True)
    con = sqlite3.connect(ASSET_DB)
    try:
        cur = con.cursor()
        create_schema(con)

        cur.executemany(
            "INSERT INTO catalog_foods"
            "(name, brand, barcode, base_serving_size, base_serving_unit,"
            " is_custom, is_deleted, created_at, updated_at)"
            " VALUES (?, ?, ?, 100.0, 'g', 0, 0, ?, ?)",
            ((r[0], r[1], r[2], FIXED_EPOCH, FIXED_EPOCH) for r in rows),
        )
        log("inserted %d catalog_foods rows" % len(rows))

        cur.executemany(
            "INSERT INTO catalog_food_nutrients"
            "(food_id, calories, protein_g, carbs_g, fat_g, fiber_g, sugar_g, sodium_mg)"
            " VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            ((i + 1, *nut_rows[i]) for i in range(len(nut_rows))),
        )
        log("inserted %d catalog_food_nutrients rows" % len(nut_rows))

        cur.executemany(
            "INSERT INTO catalog_food_serving_units(food_id, unit_name, grams_per_unit)"
            " VALUES (?, ?, ?)",
            unit_rows,
        )
        log("inserted %d catalog_food_serving_units rows" % len(unit_rows))

        con.commit()
        cur.execute("PRAGMA user_version = 1")
        # Safety net: triggers already populated the FTS index on insert.
        cur.execute("INSERT INTO catalog_foods_fts(catalog_foods_fts) VALUES('rebuild')")
        con.commit()
        verify_db(con)
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


# ---------------------------------------------------------------------------
# Hermetic self-test (no network, no downloads)
# ---------------------------------------------------------------------------

def self_test():
    failures = []

    def check(label, actual, expected):
        ok = actual == expected
        if not ok:
            failures.append("%s: got %r, want %r" % (label, actual, expected))
        print(("PASS " if ok else "FAIL ") + label, flush=True)

    # Unit conversions.
    check("kj->kcal", round(1443 * KJ_TO_KCAL, 2), 344.89)
    check("salt->sodium", round(1.0 * SALT_G_TO_SODIUM_MG, 2), 393.15)
    check("off sodium g->mg", 0.5 * 1000.0, 500.0)
    # parse_float guards.
    check("float comma", parse_float("12,5", 0.0), 12.5)
    check("float empty->default", parse_float("", 7.0), 7.0)
    check("float none->default", parse_float(None, 7.0), 7.0)
    check("float nan->default", parse_float("nan", 7.0), 7.0)
    check("float inf->default", parse_float("inf", 7.0), 7.0)
    # Serving-size parsing.
    check("serving 30 g", parse_serving_size("30 g"), ("30 g", 30.0))
    check("serving 250ml", parse_serving_size("250ml"), ("250 ml", 250.0))
    check("serving fl oz", parse_serving_size("2 fl oz"), ("2 fl oz", 2.0 * 29.5735))
    check("serving fl-oz", parse_serving_size("1 fl-oz"), ("1 fl-oz", 29.5735))
    check("serving unknown unit", parse_serving_size("1 portion"), None)
    check("serving empty", parse_serving_size(""), None)
    # OFF display-name cleanup.
    check("off name de",
          off_display_name({"product_name": "  Vollmilch  3,5% "}),
          "Vollmilch 3,5%")
    check("off name blank",
          off_display_name({"product_name": "  "}),
          "")
    # Dedupe-key normalization using production functions.
    check("normalize_barcode",
          normalize_barcode("EAN 123-456"), "ean123456")
    check("normalize_name_brand",
          normalize_name_brand(" VollMilch ", " Alpro "),
          ("vollmilch", "alpro"))
    check("is_german_barcode de", is_german_barcode("4001234567890"), True)
    check("is_german_barcode eu", is_german_barcode("3017620422003"), False)
    if failures:
        print("%d FAILURES" % len(failures), file=sys.stderr)
        for f in failures:
            print("  " + f, file=sys.stderr)
        sys.exit(1)
    print("self-test: all checks passed", flush=True)


if __name__ == "__main__":
    if "--self-test" in sys.argv:
        self_test()
    else:
        main()
