"""Check the group SQL from GroupSql.kt on real SQLite against an independent reference.

Runs anywhere with Python 3 (no Android needed):
    python tools/check_group_sql.py [--rounds 300] [--seed 1]

Tables come from the Room schema export (app/schemas/.../<latest>.json) when it exists,
otherwise from FALLBACK_DDL below, which mirrors the entities in data/entity. The SQL under
test is read from GroupSql.kt, so a change there is checked without editing this script.

Random data covers: soft-deleted purchases, lines, groups, assignments and exceptions;
archived groups; statuses other than APPROVED; other currencies; dates around the period
bounds; purchases without lines (manual expenses); redundant exceptions; negative lines.
"""

import argparse
import glob
import json
import os
import random
import re
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GROUP_SQL_KT = os.path.join(ROOT, "app/src/main/java/pl/paragony/data/GroupSql.kt")
SCHEMA_GLOB = os.path.join(ROOT, "app/schemas/pl.paragony.data.AppDatabase/*.json")

FALLBACK_DDL = """
CREATE TABLE purchase (id TEXT NOT NULL PRIMARY KEY, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
  deletedAt INTEGER, kind TEXT NOT NULL, status TEXT NOT NULL, storeId TEXT, storeNameRaw TEXT, purchasedAt TEXT,
  hasTime INTEGER NOT NULL, documentNumber TEXT, sellerTaxId TEXT, codeContent TEXT, totalAmount INTEGER NOT NULL,
  currency TEXT NOT NULL, paymentMethod TEXT, title TEXT, categoryId TEXT, note TEXT, sumCheck TEXT NOT NULL,
  uncertainFields TEXT NOT NULL, readerName TEXT, readerVersion TEXT, rawReadJson TEXT);
CREATE TABLE line_item (id TEXT NOT NULL PRIMARY KEY, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
  deletedAt INTEGER, purchaseId TEXT NOT NULL, position INTEGER NOT NULL, nameRaw TEXT NOT NULL, productId TEXT,
  quantity INTEGER NOT NULL, unit TEXT NOT NULL, unitPrice INTEGER, grossValue INTEGER NOT NULL,
  itemDiscount INTEGER NOT NULL, allocatedDiscount INTEGER NOT NULL, finalValue INTEGER NOT NULL,
  itemType TEXT NOT NULL, categoryId TEXT, editedByUser INTEGER NOT NULL);
CREATE TABLE expense_group (id TEXT NOT NULL PRIMARY KEY, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
  deletedAt INTEGER, name TEXT NOT NULL, color INTEGER, description TEXT, archivedAt INTEGER, isDefault INTEGER NOT NULL);
CREATE TABLE purchase_group (id TEXT NOT NULL PRIMARY KEY, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
  deletedAt INTEGER, purchaseId TEXT NOT NULL, groupId TEXT NOT NULL);
CREATE UNIQUE INDEX pg_unique ON purchase_group (purchaseId, groupId);
CREATE TABLE line_item_group_override (id TEXT NOT NULL PRIMARY KEY, createdAt INTEGER NOT NULL,
  updatedAt INTEGER NOT NULL, deletedAt INTEGER, lineItemId TEXT NOT NULL, groupId TEXT NOT NULL, mode TEXT NOT NULL);
CREATE UNIQUE INDEX o_unique ON line_item_group_override (lineItemId, groupId);
"""

TABLES = ("purchase", "line_item", "expense_group", "purchase_group", "line_item_group_override")


def read_group_sql():
    """Constants of GroupSql.kt with `$NAME` templates resolved."""
    text = open(GROUP_SQL_KT, encoding="utf-8").read()
    consts = {}
    for m in re.finditer(r'const val (\w+) = (?:"""(.*?)"""|"([^"]*)")', text, re.S):
        consts[m.group(1)] = m.group(2) if m.group(2) is not None else m.group(3)
    for _ in range(5):
        for k, v in consts.items():
            consts[k] = re.sub(r"\$(\w+)", lambda mm: consts[mm.group(1)], v)
    return consts


def create_schema(db, sql):
    schemas = sorted(glob.glob(SCHEMA_GLOB), key=lambda p: int(os.path.basename(p).split(".")[0]))
    if schemas:
        data = json.load(open(schemas[-1], encoding="utf-8"))["database"]
        for e in data["entities"]:
            if e["tableName"] in TABLES:
                db.execute(e["createSql"].replace("${TABLE_NAME}", e["tableName"]))
        for v in data.get("views", []):
            db.execute(v["createSql"].replace("${VIEW_NAME}", v["viewName"]))
        print(f"schema: {os.path.relpath(schemas[-1], ROOT)}")
        if sql["MEMBERSHIP_VIEW"].split() != next(
            v["createSql"] for v in data["views"] if v["viewName"] == sql["MEMBERSHIP_VIEW_NAME"]
        ).split("AS", 1)[1].split():
            print("WARN: view in exported schema differs from GroupSql.MEMBERSHIP_VIEW (rebuild the app)")
    else:
        db.executescript(FALLBACK_DDL)
        db.execute(f"CREATE VIEW {sql['MEMBERSHIP_VIEW_NAME']} AS {sql['MEMBERSHIP_VIEW']}")
        print("schema: fallback DDL (no Room export yet)")


def bind(query, groups=None):
    """Room expands `:groupIds` into a placeholder list; sqlite3 needs it done by hand."""
    if groups is not None:
        query = query.replace(":groupIds", ",".join(f":g{i}" for i in range(len(groups))))
    return query


# ---------- random data ----------

PERIOD = ("2026-10-01T00:00:00", "2026-11-01T00:00:00")
DATES = ["2026-09-30T23:59:59", "2026-10-01T00:00:00", "2026-10-15T12:30:00", "2026-10-31T23:59:59",
         "2026-11-01T00:00:00", None]


def make_data(rnd):
    n = 0

    def nid(prefix):
        nonlocal n
        n += 1
        return f"{prefix}-{n:05d}"

    groups = [dict(id=nid("g"), deletedAt=(1 if rnd.random() < 0.15 else None),
                   archivedAt=(1 if rnd.random() < 0.2 else None)) for _ in range(rnd.randint(1, 5))]
    purchases, lines, pgs, overrides = [], [], [], []
    for _ in range(rnd.randint(0, 8)):
        p = dict(id=nid("p"), deletedAt=(1 if rnd.random() < 0.1 else None),
                 status=rnd.choice(["APPROVED"] * 4 + ["TO_REVIEW", "PENDING_READ", "READ_ERROR"]),
                 currency=rnd.choice(["PLN"] * 5 + ["EUR"]), purchasedAt=rnd.choice(DATES),
                 totalAmount=rnd.randint(-500, 20000))
        purchases.append(p)
        for g in groups:
            if rnd.random() < 0.5:
                pgs.append(dict(id=nid("pg"), purchaseId=p["id"], groupId=g["id"],
                                deletedAt=(1 if rnd.random() < 0.2 else None)))
        for pos in range(rnd.choice([0, 0, 1, 2, 3, 5])):
            li = dict(id=nid("li"), purchaseId=p["id"], position=pos,
                      deletedAt=(1 if rnd.random() < 0.1 else None), finalValue=rnd.randint(-300, 5000))
            lines.append(li)
            for g in groups:
                if rnd.random() < 0.3:
                    overrides.append(dict(id=nid("o"), lineItemId=li["id"], groupId=g["id"],
                                          mode=rnd.choice(["ADD", "REMOVE"]),
                                          deletedAt=(1 if rnd.random() < 0.2 else None)))
    return groups, purchases, lines, pgs, overrides


def insert(db, data):
    groups, purchases, lines, pgs, overrides = data
    for g in groups:
        db.execute("INSERT INTO expense_group (id, createdAt, updatedAt, deletedAt, name, archivedAt, isDefault) "
                   "VALUES (?, 0, 0, ?, ?, ?, 0)", (g["id"], g["deletedAt"], g["id"], g["archivedAt"]))
    for p in purchases:
        db.execute("INSERT INTO purchase (id, createdAt, updatedAt, deletedAt, kind, status, purchasedAt, hasTime, "
                   "totalAmount, currency, sumCheck, uncertainFields) VALUES (?, 0, 0, ?, 'RECEIPT', ?, ?, 0, ?, ?, "
                   "'NOT_CHECKED', '[]')",
                   (p["id"], p["deletedAt"], p["status"], p["purchasedAt"], p["totalAmount"], p["currency"]))
    for li in lines:
        db.execute("INSERT INTO line_item (id, createdAt, updatedAt, deletedAt, purchaseId, position, nameRaw, "
                   "quantity, unit, grossValue, itemDiscount, allocatedDiscount, finalValue, itemType, editedByUser) "
                   "VALUES (?, 0, 0, ?, ?, ?, 'X', 1000, 'PCS', ?, 0, 0, ?, 'PRODUCT', 0)",
                   (li["id"], li["deletedAt"], li["purchaseId"], li["position"], li["finalValue"], li["finalValue"]))
    for r in pgs:
        db.execute("INSERT INTO purchase_group (id, createdAt, updatedAt, deletedAt, purchaseId, groupId) "
                   "VALUES (?, 0, 0, ?, ?, ?)", (r["id"], r["deletedAt"], r["purchaseId"], r["groupId"]))
    for o in overrides:
        db.execute("INSERT INTO line_item_group_override (id, createdAt, updatedAt, deletedAt, lineItemId, groupId, "
                   "mode) VALUES (?, 0, 0, ?, ?, ?, ?)",
                   (o["id"], o["deletedAt"], o["lineItemId"], o["groupId"], o["mode"]))


# ---------- reference (written from data model / D12 / D18, not from the SQL) ----------

def reference(data, currency="PLN"):
    groups, purchases, lines, pgs, overrides = data
    live_groups = {g["id"] for g in groups if g["deletedAt"] is None}  # archived still count
    entries = []  # (entryId, amount, set of groups, counted?)
    for p in purchases:
        counted = (p["deletedAt"] is None and p["status"] == "APPROVED" and p["currency"] == currency
                   and p["purchasedAt"] is not None and PERIOD[0] <= p["purchasedAt"] < PERIOD[1])
        pgroups = {r["groupId"] for r in pgs if r["purchaseId"] == p["id"] and r["deletedAt"] is None} & live_groups
        plines = [li for li in lines if li["purchaseId"] == p["id"] and li["deletedAt"] is None]
        if not plines:
            entries.append((p["id"], p["totalAmount"], pgroups, counted, False))
        for li in plines:
            ov = {o["groupId"]: o["mode"] for o in overrides
                  if o["lineItemId"] == li["id"] and o["deletedAt"] is None and o["groupId"] in live_groups}
            gs = {g for g in pgroups if ov.get(g) != "REMOVE"} | {g for g, m in ov.items() if m == "ADD"}
            entries.append((li["id"], li["finalValue"], gs, counted, True))
    return entries


def check_round(db, sql, rnd):
    data = make_data(rnd)
    for t in TABLES:
        db.execute(f"DELETE FROM {t}")
    insert(db, data)
    entries = reference(data)
    errors = []
    params = {"currency": "PLN", "from": PERIOD[0], "to": PERIOD[1]}

    # membership of every entry (view), independent of period/status
    view = {}
    for entry_id, group_id, is_line in db.execute(
            f"SELECT entryId, groupId, isLine FROM {sql['MEMBERSHIP_VIEW_NAME']}"):
        view.setdefault(entry_id, set()).add(group_id)
    for entry_id, _, gs, _, _ in entries:
        if view.get(entry_id, set()) != gs:
            errors.append(f"membership {entry_id}: sql={sorted(view.get(entry_id, set()))} ref={sorted(gs)}")

    counted = [e for e in entries if e[3]]
    all_groups = [g["id"] for g in data[0]]
    subsets = [[]] + [[g] for g in all_groups] + [all_groups]
    subsets += [rnd.sample(all_groups, rnd.randint(1, len(all_groups))) for _ in range(3)]
    for subset in subsets:
        got = db.execute(bind(sql["SUM_FOR_GROUPS"], subset),
                         {**params, **{f"g{i}": g for i, g in enumerate(subset)}}).fetchone()[0]
        want = sum(a for _, a, gs, _, _ in counted if gs & set(subset))
        if got != want:
            errors.append(f"SUM_FOR_GROUPS {subset}: sql={got} ref={want}")

    got = dict(db.execute(sql["SUM_PER_GROUP"], params).fetchall())
    want = {}
    for _, a, gs, _, _ in counted:
        for g in gs:
            want[g] = want.get(g, 0) + a
    if got != want:
        errors.append(f"SUM_PER_GROUP: sql={got} ref={want}")

    got = db.execute(sql["SUM_ALL"], params).fetchone()[0]
    want = sum(a for _, a, _, _, _ in counted)
    if got != want:
        errors.append(f"SUM_ALL: sql={got} ref={want}")
    return errors, data


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--rounds", type=int, default=300)
    ap.add_argument("--seed", type=int, default=1)
    args = ap.parse_args()
    sql = read_group_sql()
    for name in ("MEMBERSHIP_VIEW_NAME", "MEMBERSHIP_VIEW", "SUM_FOR_GROUPS", "SUM_PER_GROUP", "SUM_ALL"):
        if name not in sql:
            sys.exit(f"missing {name} in GroupSql.kt")
    db = sqlite3.connect(":memory:")
    create_schema(db, sql)
    rnd = random.Random(args.seed)
    for i in range(args.rounds):
        errors, data = check_round(db, sql, rnd)
        if errors:
            print(f"FAIL round {i}:")
            for e in errors[:20]:
                print("  " + e)
            print(json.dumps(data, indent=1)[:4000])
            sys.exit(1)
    print(f"OK: {args.rounds} random rounds, SQL = reference")


if __name__ == "__main__":
    main()
