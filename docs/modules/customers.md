# Customers

**Status:** Proposed, mirroring [suppliers.md](suppliers.md) at the owner's request, 2026-10-13. Every field is validated as defined in [validation.md](../rules/validation.md). The API mirrors suppliers: see [suppliers.md](suppliers.md) section 7. Open questions are in section 6.

## 1. Purpose

Customers are the buyers that orders are sent to. Each order names one customer and, optionally, one of its contacts. Customers are an entity catalog in the sense of [catalogs.md](../rules/catalogs.md): records in the database, administered through the API.

## 2. Customer

| Field | Rule |
|---|---|
| `code` | Required. Unique. Immutable. Code rule in [validation.md](../rules/validation.md) |
| `name` | Required. Name rule in [validation.md](../rules/validation.md) |
| `tax_id` | Required. The fiscal identifier (RFC), validated by the RFC rule |
| `address` | Required. Structured, with the same fields as a supplier address |
| `payment_terms` | Required. Reference to the `PAYMENT_TERMS` catalog, as for suppliers |
| `active` | Required. Default `true` |

Only active customers can be chosen for a new order. Deactivating a customer keeps it on existing orders.

## 3. Customer contact

Each customer can have several contacts.

| Field | Rule |
|---|---|
| `customer` | Required. Belongs to one customer |
| `name` | Required. Name rule in [validation.md](../rules/validation.md) |
| `email` | Optional. If given, must pass the email rule (`EMAIL_INVALID` otherwise) |
| `phone` | Optional. If given, must be 10 digits (`PHONE_INVALID` otherwise) |
| `active` | Required. Default `true` |

A contact must have at least one of `email` or `phone`, as for supplier contacts (proposed).

Only active contacts of the chosen customer appear on an order.

## 4. Permissions

| Permission | Who |
|---|---|
| `customers.view` | `ADMIN`, `OFFICE`, `WAREHOUSE_MANAGER` |
| `customers.manage` | `ADMIN` |

`OFFICE` chooses customers for orders but does not create them, the same as suppliers.

## 5. Use in orders

The order header gets a **customer** field, required, chosen from the active customers. It also gets an optional **customer contact**, chosen from that customer's contacts. Both appear on the order's PDF (see [order-fulfillment.md](order-fulfillment.md)).

## 6. Open questions

1. **Creating customers.** Only `ADMIN`, as proposed, or also `OFFICE` when creating an order?
2. **Payment terms.** Customers may pay on other terms than suppliers. Same `PAYMENT_TERMS` catalog, or a separate one for customers? *Proposed:* the same catalog for now.
3. **Address and tax ID.** Same rules as suppliers. Confirm that individual customers, with a 13-character RFC, are allowed.
