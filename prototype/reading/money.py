"""Money arithmetic for the reading prototype.

Amounts are integers in grosze, quantities are integers in thousandths (D5).
Rounding happens only here, HALF_UP. The Kotlin `money` package in S2 should
mirror these rules and reuse the same test cases.
"""
import re

_SPACES = re.compile(r"[\s\u00a0\u202f]")
_CURRENCY = re.compile(r"(zł|zl|pln)$", re.IGNORECASE)
_AMOUNT = re.compile(r"^(-)?(\d+)(?:[.,](\d{1,2}))?$")
_QTY = re.compile(r"^(\d+)(?:[.,](\d{1,3}))?$")


class ParseError(ValueError):
    pass


def _clean(text):
    if text is None:
        raise ParseError("empty value")
    t = _SPACES.sub("", str(text))
    t = t.replace("−", "-").replace("\u2013", "-")  # unicode minus / en dash
    t = _CURRENCY.sub("", t)
    return t


def parse_amount(text):
    """'1 234,56' -> 123456, '-7,20' -> -720, '30' -> 3000, '0,5' -> 50."""
    t = _clean(text)
    m = _AMOUNT.match(t)
    if not m:
        raise ParseError(f"not an amount: {text!r}")
    sign, whole, frac = m.groups()
    value = int(whole) * 100 + int((frac or "0").ljust(2, "0"))
    return -value if sign else value


def parse_quantity(text):
    """'2' -> 2000, '2,590' -> 2590, '0,11' -> 110 (thousandths)."""
    t = _clean(text)
    m = _QTY.match(t)
    if not m:
        raise ParseError(f"not a quantity: {text!r}")
    whole, frac = m.groups()
    return int(whole) * 1000 + int((frac or "0").ljust(3, "0"))


def round_half_up_div(numerator, denominator):
    """Integer division rounded half away from zero (HALF_UP for both signs)."""
    if denominator <= 0:
        raise ValueError("denominator must be positive")
    q, r = divmod(abs(numerator), denominator)
    if 2 * r >= denominator:
        q += 1
    return -q if numerator < 0 else q


def line_value(quantity_thousandths, unit_price_grosze):
    """quantity x unit price, rounded HALF_UP to grosze."""
    return round_half_up_div(quantity_thousandths * unit_price_grosze, 1000)


def vat_from_gross(gross_grosze, percent_hundredths):
    """Tax contained in a gross amount: gross * p / (100 + p). Percent in hundredths (23% = 2300)."""
    return round_half_up_div(gross_grosze * percent_hundredths, 10000 + percent_hundredths)


def parse_percent(text):
    """'23%' -> 2300, '05,00%' -> 500, '8.00%' -> 800."""
    t = _clean(text).rstrip("%")
    m = re.match(r"^(\d+)(?:[.,](\d{1,2}))?$", t)
    if not m:
        raise ParseError(f"not a percent: {text!r}")
    whole, frac = m.groups()
    return int(whole) * 100 + int((frac or "0").ljust(2, "0"))


def format_amount(grosze):
    sign = "-" if grosze < 0 else ""
    g = abs(grosze)
    return f"{sign}{g // 100},{g % 100:02d}"
