"""Compares a model reading with a hand-made expected reading.

Both files go through validate.normalize first, so amounts are compared as
integers, not as text ("7,20" == "7.20").

Usage: python compare.py read.json expected.json
"""
import difflib
import json
import re
import sys
import unicodedata

import validate

HEADER_FIELDS = ["storeChain", "sellerTaxId", "purchaseDate", "purchaseTime", "totalAmount"]
ITEM_FIELDS = ["quantity", "unitPrice", "grossValue", "itemDiscount", "finalValue", "vatRate", "itemType"]


def _norm_name(s):
    s = unicodedata.normalize("NFKD", s or "").encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]", "", s)


def _key(line):
    return f"{_norm_name(line['nameRaw'])}|{line['grossValue']}"


def compare(read_doc, expected_doc):
    got, got_rep = validate.normalize(read_doc)
    exp, _ = validate.normalize(expected_doc)

    header = {}
    for f in HEADER_FIELDS:
        e, g = exp.get(f), got.get(f)
        if e is None:
            continue  # field removed when anonymizing - not scored
        if f == "storeChain":
            ok = (g or "").strip().lower() == e.strip().lower()
        else:
            ok = g == e
        header[f] = {"ok": ok, "expected": e, "got": g}

    # align item lists; repeated identical lines stay separate
    eg, gg = exp["lines"], got["lines"]
    sm = difflib.SequenceMatcher(a=[_key(l) for l in eg], b=[_key(l) for l in gg], autojunk=False)
    pairs, missing, extra = [], [], []
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag == "equal" or (tag == "replace" and i2 - i1 == j2 - j1):
            pairs += list(zip(range(i1, i2), range(j1, j2)))
        else:
            missing += list(range(i1, i2))
            extra += list(range(j1, j2))

    field_ok = field_total = 0
    item_diffs = []
    name_scores = []
    for i, j in pairs:
        e, g = eg[i], gg[j]
        name_scores.append(difflib.SequenceMatcher(a=_norm_name(e["nameRaw"]), b=_norm_name(g["nameRaw"])).ratio())
        for f in ITEM_FIELDS:
            ev, gv = e[f], g[f]
            if f == "finalValue" and ev is None:
                continue
            if f == "quantity" and e["itemType"] == "DEPOSIT":
                continue  # deposit quantities are often implicit
            field_total += 1
            if ev == gv:
                field_ok += 1
            else:
                item_diffs.append({"item": i + 1, "name": e["nameRaw"], "field": f, "expected": ev, "got": gv})

    return {
        "sumCheck": got["sumCheck"],
        "modelErrors": len(got_rep.errors),
        "header": header,
        "itemsExpected": len(eg),
        "itemsRead": len(gg),
        "itemsMatched": len(pairs),
        "itemsMissing": [eg[i]["nameRaw"] for i in missing],
        "itemsExtra": [gg[j]["nameRaw"] for j in extra],
        "itemFieldAccuracy": (field_ok / field_total) if field_total else None,
        "nameSimilarity": (sum(name_scores) / len(name_scores)) if name_scores else None,
        "itemDiffs": item_diffs,
        "issues": got_rep.issues,
    }


def print_comparison(name, c, out=sys.stdout):
    h_ok = sum(1 for v in c["header"].values() if v["ok"])
    acc = c["itemFieldAccuracy"]
    out.write(f"-- {name}: header {h_ok}/{len(c['header'])}, items {c['itemsMatched']}/{c['itemsExpected']} matched "
              f"(read {c['itemsRead']}), item fields {acc:.0%}" if acc is not None else f"-- {name}: no items matched")
    out.write(f", sumCheck {c['sumCheck']}\n")
    for f, v in c["header"].items():
        if not v["ok"]:
            out.write(f"   header {f}: expected {v['expected']!r}, got {v['got']!r}\n")
    for n in c["itemsMissing"]:
        out.write(f"   missing item: {n}\n")
    for n in c["itemsExtra"]:
        out.write(f"   extra item: {n}\n")
    for d in c["itemDiffs"]:
        out.write(f"   item {d['item']} {d['name']}: {d['field']} expected {d['expected']!r}, got {d['got']!r}\n")


if __name__ == "__main__":
    with open(sys.argv[1], encoding="utf-8") as a, open(sys.argv[2], encoding="utf-8") as b:
        print_comparison(sys.argv[1], compare(json.load(a), json.load(b)))
