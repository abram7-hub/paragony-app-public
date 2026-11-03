package pl.paragony.data

/**
 * SQL for group membership and sums (D12, D18). Kept as constants so the same text is used by
 * Room (`@DatabaseView`, `@Query`) and by `tools/check_group_sql.py`, which runs it on SQLite
 * against an independent reference on random data.
 *
 * The rule (data model): a line item belongs to group G when
 * (its purchase is in G and the line has no REMOVE for G) or the line has an ADD for G.
 * A purchase without live line items (manual expense) belongs to its purchase groups with
 * its total amount. Soft-deleted rows (`deletedAt`) never count; archived groups do.
 *
 * Sums count each entry once even when several picked groups contain it (DISTINCT entryId).
 * Only APPROVED, not deleted purchases, in one currency, with `from <= purchasedAt < to`.
 */
object GroupSql {
    const val MEMBERSHIP_VIEW_NAME = "spending_group_membership"

    const val MEMBERSHIP_VIEW = """
SELECT li.id AS entryId, li.purchaseId AS purchaseId, pg.groupId AS groupId,
       li.finalValue AS amount, 1 AS isLine
FROM line_item li
JOIN purchase_group pg ON pg.purchaseId = li.purchaseId AND pg.deletedAt IS NULL
JOIN expense_group g ON g.id = pg.groupId AND g.deletedAt IS NULL
WHERE li.deletedAt IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM line_item_group_override o
    WHERE o.lineItemId = li.id AND o.groupId = pg.groupId
      AND o.mode = 'REMOVE' AND o.deletedAt IS NULL)
UNION
SELECT li.id, li.purchaseId, o.groupId, li.finalValue, 1
FROM line_item_group_override o
JOIN line_item li ON li.id = o.lineItemId AND li.deletedAt IS NULL
JOIN expense_group g ON g.id = o.groupId AND g.deletedAt IS NULL
WHERE o.mode = 'ADD' AND o.deletedAt IS NULL
UNION
SELECT p.id, p.id, pg.groupId, p.totalAmount, 0
FROM purchase p
JOIN purchase_group pg ON pg.purchaseId = p.id AND pg.deletedAt IS NULL
JOIN expense_group g ON g.id = pg.groupId AND g.deletedAt IS NULL
WHERE NOT EXISTS (
    SELECT 1 FROM line_item li WHERE li.purchaseId = p.id AND li.deletedAt IS NULL)
"""

    /** Common filter on `purchase p`. */
    private const val COUNTED_PURCHASE = """
p.deletedAt IS NULL AND p.status = 'APPROVED' AND p.currency = :currency
AND p.purchasedAt >= :from AND p.purchasedAt < :to"""

    /** Total for a set of groups, each entry once. Params: groupIds, currency, from, to. */
    const val SUM_FOR_GROUPS = """
SELECT COALESCE(SUM(amount), 0) FROM (
  SELECT DISTINCT m.entryId AS entryId, m.amount AS amount
  FROM $MEMBERSHIP_VIEW_NAME m
  JOIN purchase p ON p.id = m.purchaseId
  WHERE m.groupId IN (:groupIds) AND $COUNTED_PURCHASE
)"""

    /** Total per group (an entry counts in every group it belongs to). Params: currency, from, to. */
    const val SUM_PER_GROUP = """
SELECT m.groupId AS groupId, SUM(m.amount) AS total
FROM $MEMBERSHIP_VIEW_NAME m
JOIN purchase p ON p.id = m.purchaseId
WHERE $COUNTED_PURCHASE
GROUP BY m.groupId"""

    /** Total of everything, regardless of groups. Params: currency, from, to. */
    const val SUM_ALL = """
SELECT COALESCE(SUM(amount), 0) FROM (
  SELECT li.finalValue AS amount
  FROM line_item li JOIN purchase p ON p.id = li.purchaseId
  WHERE li.deletedAt IS NULL AND $COUNTED_PURCHASE
  UNION ALL
  SELECT p.totalAmount
  FROM purchase p
  WHERE $COUNTED_PURCHASE
    AND NOT EXISTS (
      SELECT 1 FROM line_item li WHERE li.purchaseId = p.id AND li.deletedAt IS NULL)
)"""
}
