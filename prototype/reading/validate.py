"""Checks a read document (JSON from the model) with plain integer arithmetic.

The model only proposes data. This module parses every amount, recomputes
line values and totals, and reports what does not add up. It never changes
amounts to make totals match.

Usage: python validate.py read.json
"""
import datetime as dt
import json
import re
import sys

import money

ERROR, WARN = "ERROR", "WARN"


class Report:
    def __init__(self):
        self.issues = []

    def add(self, level, path, message):
        self.issues.append({"level": level, "path": path, "message": message})

    @property
    def errors(self):
        return [i for i in self.issues if i["level"] == ERROR]


def _amount(report, text, path, required=True):
    if text is None or str(text).strip() == "":
        if required:
            report.add(ERROR, path, "missing amount")
        return None
    try:
        return money.parse_amount(text)
    except money.ParseError as e:
        report.add(ERROR, path, str(e))
        return None


def normalize(doc, today=None):
    """Returns (normalized purchase dict with integer amounts, Report)."""
    today = today or dt.date.today()
    r = Report()
    fmt = money.format_amount

    # --- header ---
    date = doc.get("purchaseDate")
    if not date:
        r.add(ERROR, "purchaseDate", "missing date (required to approve)")
    else:
        try:
            d = dt.date.fromisoformat(date)
            if d > today:
                r.add(WARN, "purchaseDate", f"date {date} is in the future")
        except ValueError:
            r.add(ERROR, "purchaseDate", f"not YYYY-MM-DD: {date!r}")
    time = doc.get("purchaseTime")
    if time and not re.fullmatch(r"([01]\d|2[0-3]):[0-5]\d", time):
        r.add(WARN, "purchaseTime", f"not HH:MM: {time!r}")
    tax_id = (doc.get("store") or {}).get("taxId")
    if tax_id and not re.fullmatch(r"\d{10}", re.sub(r"\D", "", tax_id)):
        r.add(WARN, "store.taxId", f"NIP should have 10 digits: {tax_id!r}")

    # --- items ---
    lines = []
    for i, item in enumerate(doc.get("items") or []):
        p = f"items[{i}]"
        qty = None
        try:
            qty = money.parse_quantity(item.get("quantityText") or "1")
        except money.ParseError as e:
            r.add(ERROR, f"{p}.quantityText", str(e))
        price = _amount(r, item.get("unitPriceText"), f"{p}.unitPriceText", required=False)
        gross = _amount(r, item.get("grossValueText"), f"{p}.grossValueText")
        discount = 0
        for j, d in enumerate(item.get("discounts") or []):
            a = _amount(r, d.get("amountText"), f"{p}.discounts[{j}].amountText")
            if a is not None:
                if a < 0:
                    r.add(WARN, f"{p}.discounts[{j}].amountText", "discount given with minus sign; using absolute value")
                    a = -a
                discount += a
        final = None
        if gross is not None:
            final = gross - discount
            if qty is not None and price is not None:
                expected = money.line_value(qty, price)
                if expected != abs(gross):
                    r.add(WARN, f"{p}.grossValueText",
                          f"quantity x price = {fmt(expected)}, printed {fmt(gross)}")
            if gross > 0 and discount > gross:
                r.add(ERROR, f"{p}.discounts", f"discount {fmt(discount)} is larger than value {fmt(gross)}")
            printed_final = item.get("finalValueText")
            if printed_final not in (None, ""):
                pf = _amount(r, printed_final, f"{p}.finalValueText")
                if pf is not None and pf != final:
                    r.add(ERROR, f"{p}.finalValueText",
                          f"value {fmt(gross)} - discount {fmt(discount)} = {fmt(final)}, printed {fmt(pf)}")
        lines.append({
            "position": item.get("position", i + 1),
            "nameRaw": item.get("nameRaw"),
            "quantity": qty,
            "unit": item.get("unit"),
            "unitPrice": price,
            "grossValue": gross,
            "itemDiscount": discount,
            "finalValue": final,
            "vatRate": (item.get("vatRate") or None),
            "itemType": item.get("itemType"),
        })

    doc_discount = 0
    for j, d in enumerate(doc.get("documentDiscounts") or []):
        a = _amount(r, d.get("amountText"), f"documentDiscounts[{j}].amountText")
        if a is not None:
            doc_discount += abs(a)

    # --- totals ---
    amount_due = _amount(r, doc.get("amountDueText"), "amountDueText")
    fiscal_total = _amount(r, doc.get("fiscalTotalText"), "fiscalTotalText", required=False)
    complete = all(l["finalValue"] is not None for l in lines)
    items_total = sum(l["finalValue"] for l in lines if l["finalValue"] is not None) - doc_discount
    if not lines:
        r.add(ERROR, "items", "no items read")
    if amount_due is not None and complete and items_total != amount_due:
        r.add(ERROR, "amountDueText",
              f"items sum to {fmt(items_total)}, document says {fmt(amount_due)} "
              f"(difference {fmt(amount_due - items_total)})")

    untaxed_deposits = sum(l["finalValue"] or 0 for l in lines
                           if l["itemType"] == "DEPOSIT" and not l["vatRate"])
    if fiscal_total is not None and amount_due is not None:
        if fiscal_total not in (amount_due, amount_due - untaxed_deposits):
            r.add(WARN, "fiscalTotalText",
                  f"total {fmt(fiscal_total)} matches neither amount due {fmt(amount_due)} "
                  f"nor amount due without deposits {fmt(amount_due - untaxed_deposits)}")

    printed_disc = _amount(r, doc.get("totalDiscountText"), "totalDiscountText", required=False)
    item_disc = sum(l["itemDiscount"] for l in lines)
    if printed_disc is not None and abs(printed_disc) != item_disc + doc_discount:
        r.add(WARN, "totalDiscountText",
              f"discounts read: {fmt(item_disc + doc_discount)}, printed total {fmt(abs(printed_disc))}")

    # --- VAT summary ---
    vat = doc.get("vatSummary") or []
    if vat:
        vat_total = 0
        all_rated = all(l["vatRate"] for l in lines if not (l["itemType"] == "DEPOSIT" and not l["vatRate"]))
        for k, v in enumerate(vat):
            p = f"vatSummary[{k}]"
            taxable = _amount(r, v.get("taxableText"), f"{p}.taxableText")
            if taxable is None:
                continue
            vat_total += taxable
            rate = v.get("vatRate")
            if all_rated and complete:
                s = sum(l["finalValue"] for l in lines if l["vatRate"] == rate)
                if s != taxable:
                    r.add(WARN, f"{p}.taxableText",
                          f"items with rate {rate} sum to {fmt(s)}, summary says {fmt(taxable)}")
            if v.get("percentText") and v.get("taxText"):
                try:
                    pct = money.parse_percent(v["percentText"])
                    tax = money.parse_amount(v["taxText"])
                    exp = money.vat_from_gross(taxable, pct)
                    if abs(exp - tax) > 1:
                        r.add(WARN, f"{p}.taxText", f"expected tax about {fmt(exp)}, printed {fmt(tax)}")
                except money.ParseError as e:
                    r.add(WARN, p, str(e))
        if amount_due is not None and vat_total + untaxed_deposits != amount_due:
            r.add(ERROR, "vatSummary",
                  f"taxable sales {fmt(vat_total)} + deposits {fmt(untaxed_deposits)} "
                  f"!= amount due {fmt(amount_due)}")

    pays = [_amount(r, p.get("amountText"), f"payments[{k}].amountText")
            for k, p in enumerate(doc.get("payments") or [])]
    if pays and None not in pays and amount_due is not None and sum(pays) != amount_due:
        r.add(WARN, "payments", f"payments {fmt(sum(pays))} != amount due {fmt(amount_due)}")

    for u in doc.get("uncertainFields") or []:
        r.add(WARN, u.get("path", "?"), f"model unsure: {u.get('reason', '')}")

    sum_ok = amount_due is not None and complete and items_total == amount_due
    purchase = {
        "storeChain": (doc.get("store") or {}).get("chain"),
        "storeNameRaw": (doc.get("store") or {}).get("name"),
        "sellerTaxId": re.sub(r"\D", "", tax_id) if tax_id else None,
        "purchaseDate": date,
        "purchaseTime": time,
        "totalAmount": amount_due,
        "fiscalTotal": fiscal_total,
        "itemsTotal": items_total if complete else None,
        "totalItemDiscount": item_disc,
        "documentDiscount": doc_discount,
        "currency": doc.get("currency") or "PLN",
        "sumCheck": "OK" if sum_ok and not r.errors else "MISMATCH",
        "lines": lines,
    }
    return purchase, r


def print_report(name, purchase, report, out=sys.stdout):
    fmt = money.format_amount
    total = purchase["totalAmount"]
    out.write(f"== {name}: {purchase['storeChain']} {purchase['purchaseDate']} "
              f"{fmt(total) if total is not None else '?'} {purchase['currency']} "
              f"| {len(purchase['lines'])} items | sumCheck {purchase['sumCheck']}\n")
    for i in report.issues:
        out.write(f"   {i['level']:5} {i['path']}: {i['message']}\n")


if __name__ == "__main__":
    for path in sys.argv[1:]:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        purchase, rep = normalize(data)
        print_report(path, purchase, rep)
