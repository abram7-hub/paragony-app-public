package pl.paragony.data

// Stored as TEXT by name (Room's built-in enum handling). Never rename a constant
// without a migration that rewrites the stored values.

enum class PurchaseKind { RECEIPT, MANUAL }

enum class PurchaseStatus { PENDING_READ, TO_REVIEW, APPROVED, READ_ERROR }

/** Sum check of a stored purchase. [NOT_CHECKED] for manual expenses and unread documents. */
enum class StoredSumCheck { OK, MISMATCH, NOT_CHECKED }

/** Unit of a line quantity or a product package. Named to avoid a clash with Kotlin's Unit. */
enum class QuantityUnit { PCS, KG, L, OTHER }

enum class LineItemType { PRODUCT, BAG, DEPOSIT, FEE, OTHER }

enum class DiscountScope { WHOLE, ITEMS }

enum class DiscountSource { READ, ALLOCATED, USER }

enum class AttachmentSource { CAMERA, GALLERY, SHARE, SCREENSHOT }

/** D12: a line item only stores exceptions to its purchase's groups. */
enum class OverrideMode { ADD, REMOVE }
