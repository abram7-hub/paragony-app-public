# Design notes

## Receipt readings

I keep the text read from a receipt separate from normalized amounts. The reader can flag uncertain fields. The validator checks printed totals, discounts, VAT and deposits and reports disagreements. A repeated item on the receipt counts twice. Overlap between two photos of the same printed line counts once.

## Money

I store PLN amounts in grosze and quantities in thousandths. I round half up when multiplying a quantity by a unit price. An item discount reduces its line value. A receipt-wide discount is allocated across eligible lines, with any remaining grosze assigned by the largest remainders. Deposits are accounted for separately when the fiscal total excludes them.

## Expense groups

I assign a purchase to one or more groups and can override membership for an individual item. When I select multiple groups, a matching item contributes once to the combined total, even when it belongs to several groups.

## Storage and testing

I use Room for local storage, stable UUIDs for records and soft deletion. The Android tests exercise the database on a device. The Python SQL check compares the group queries against an independent implementation. The Kotlin validator and Python prototype share the same fictional receipt fixtures.
