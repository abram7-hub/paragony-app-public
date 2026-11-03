"""Run: python -m unittest discover -s prototype/reading -v"""
import copy
import glob
import json
import os
import shutil
import sys
import tempfile
import unittest
from unittest import mock

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import compare  # noqa: E402
import money  # noqa: E402
import read_receipt  # noqa: E402
import validate  # noqa: E402

EXPECTED = os.path.join(HERE, "..", "..", "testdata", "reading", "expected")


def load(name):
    with open(os.path.join(EXPECTED, name + ".json"), encoding="utf-8") as f:
        return json.load(f)


class MoneyTest(unittest.TestCase):
    def test_parse_amount(self):
        cases = {"14,59": 1459, "-7,20": -720, "1 234,56": 123456, "0,5": 50, "30": 3000,
                 "167,71 zł": 16771, "7.20": 720, "−6,30": -630, "0,79A": None}
        for text, want in cases.items():
            if want is None:
                self.assertRaises(money.ParseError, money.parse_amount, text)
            else:
                self.assertEqual(money.parse_amount(text), want, text)

    def test_parse_quantity(self):
        self.assertEqual(money.parse_quantity("2"), 2000)
        self.assertEqual(money.parse_quantity("2,590"), 2590)
        self.assertEqual(money.parse_quantity("0,11"), 110)
        self.assertEqual(money.parse_quantity("3,000"), 3000)
        self.assertRaises(money.ParseError, money.parse_quantity, "1,2345")

    def test_line_value_half_up(self):
        self.assertEqual(money.line_value(2590, 199), 515)    # 5,1541 -> 5,15
        self.assertEqual(money.line_value(105, 1199), 126)    # 1,25895 -> 1,26
        self.assertEqual(money.line_value(560, 3690), 2066)   # 20,664 -> 20,66
        self.assertEqual(money.line_value(74, 5740), 425)     # 4,2476 -> 4,25
        self.assertEqual(money.line_value(110, 2490), 274)    # 2,739 -> 2,74
        self.assertEqual(money.line_value(500, 1), 1)         # 0,005 -> 0,01 (half up)
        self.assertEqual(money.round_half_up_div(-5, 10), -1)  # symmetric for negatives

    def test_vat(self):
        self.assertEqual(money.vat_from_gross(7483, 500), 356)
        self.assertEqual(money.vat_from_gross(1047, 800), 78)
        self.assertEqual(money.parse_percent("05,00%"), 500)
        self.assertEqual(money.parse_percent("23.00%"), 2300)


class ValidateTest(unittest.TestCase):
    def test_all_expected_files_add_up(self):
        files = glob.glob(os.path.join(EXPECTED, "*.json"))
        self.assertGreaterEqual(len(files), 9)
        for f in files:
            with open(f, encoding="utf-8") as fh:
                p, r = validate.normalize(json.load(fh))
            self.assertEqual(p["sumCheck"], "OK", f"{f}: {r.issues}")
            self.assertEqual(r.errors, [], f)

    def test_deposit_after_sum(self):
        p, _ = validate.normalize(load("biedronka_2000-01-03"))
        self.assertEqual(p["totalAmount"], 1060)
        self.assertEqual(p["fiscalTotal"], 960)

    def test_wrong_amount_is_reported_not_fixed(self):
        doc = load("biedronka_2000-01-01")
        doc["items"][1]["grossValueText"] = "29,78"  # misread digit
        p, r = validate.normalize(doc)
        self.assertEqual(p["sumCheck"], "MISMATCH")
        self.assertEqual(p["lines"][1]["grossValue"], 2978)  # kept as read
        paths = [i["path"] for i in r.issues]
        self.assertIn("items[1].grossValueText", paths)   # qty x price
        self.assertIn("items[1].finalValueText", paths)   # inconsistent printed total
        self.assertIn("amountDueText", paths)

    def test_missing_item_breaks_sum(self):
        doc = load("carrefour_2000-01-04")
        del doc["items"][8]  # model merged a real repeated line
        p, r = validate.normalize(doc)
        self.assertEqual(p["sumCheck"], "MISMATCH")
        self.assertTrue(any("difference 5,00" in i["message"] for i in r.errors))

    def test_voucher_taken_as_discount_breaks_sum(self):
        doc = load("biedronka_2000-01-03")
        doc["documentDiscounts"] = [{"label": "VOUCHER", "amountText": "30,00"}]
        p, _ = validate.normalize(doc)
        self.assertEqual(p["sumCheck"], "MISMATCH")

    def test_uncertain_fields_are_surfaced(self):
        doc = load("biedronka_2000-01-02")
        doc["uncertainFields"] = [{"path": "items[2].unitPriceText", "reason": "smudged"}]
        _, r = validate.normalize(doc)
        self.assertTrue(any(i["path"] == "items[2].unitPriceText" for i in r.issues))

    def test_missing_date_is_error(self):
        doc = load("lidl_2000-01-09")
        doc["purchaseDate"] = None
        _, r = validate.normalize(doc)
        self.assertTrue(any(i["path"] == "purchaseDate" for i in r.errors))


class CompareTest(unittest.TestCase):
    def test_identical(self):
        e = load("carrefour_2000-01-06")
        c = compare.compare(e, e)
        self.assertEqual(c["itemFieldAccuracy"], 1.0)
        self.assertEqual(c["itemsMatched"], c["itemsExpected"])

    def test_dropped_repeated_line_and_wrong_price(self):
        e = load("carrefour_2000-01-06")
        g = copy.deepcopy(e)
        del g["items"][2]  # one of many identical "Produkt powtarzany" lines
        g["items"][0]["unitPriceText"] = "21,90"
        c = compare.compare(g, e)
        self.assertEqual(c["itemsMissing"], ["Produkt powtarzany"])
        self.assertTrue(any(d["field"] == "unitPrice" for d in c["itemDiffs"]))
        self.assertEqual(c["sumCheck"], "MISMATCH")


class PipelineTest(unittest.TestCase):
    """Runs read_receipt.main with a fake API that returns the expected reading."""

    def test_end_to_end_with_fake_api(self):
        tmp = tempfile.mkdtemp()
        try:
            doc_dir = os.path.join(tmp, "receipts", "lidl_2000-01-09")
            os.makedirs(doc_dir)
            for n in ("1.jpg", "2.jpg"):
                open(os.path.join(doc_dir, n), "wb").write(b"\xff\xd8\xff\xd9")
            answer = load("lidl_2000-01-09")
            calls = []

            def fake_http(method, url, key, body=None, timeout=180):
                calls.append(body)
                return {"candidates": [{"content": {"parts": [{"text": json.dumps(answer)}]}}],
                        "usageMetadata": {"promptTokenCount": 1, "candidatesTokenCount": 1}}

            argv = ["x", os.path.join(tmp, "receipts"), "--out", os.path.join(tmp, "out"),
                    "--expected", EXPECTED, "--delay", "0", "--model", "fake"]
            with mock.patch.object(read_receipt, "http", fake_http), mock.patch.object(sys, "argv", argv), \
                    mock.patch.dict(os.environ, {"GEMINI_API_KEY": "test"}), mock.patch("builtins.print"):
                read_receipt.main()
            self.assertEqual(len(calls), 1)  # one request per document
            self.assertEqual(len([p for p in calls[0]["contents"][0]["parts"] if "inline_data" in p]), 2)
            report = open(os.path.join(tmp, "out", "fake", "report.md"), encoding="utf-8").read()
            self.assertIn("| lidl_2000-01-09 | OK | 4/4 | 100% | 0 |", report)
        finally:
            shutil.rmtree(tmp)


if __name__ == "__main__":
    unittest.main()
