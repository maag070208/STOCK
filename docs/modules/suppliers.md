# Suppliers

**Status:** Captured from the business owner, 2026-10-11. Every field is validated as defined in [validation.md](../rules/validation.md). The API is in section 7. Open questions are in section 8.

## 1. Purpose

Suppliers are the companies that send invoices. Invoices reference a supplier and, optionally, one of its contacts. Suppliers are an entity catalog in the sense of [catalogs.md](../rules/catalogs.md): records in the database, administered through the API.

## 2. Supplier

| Field | Rule |
|---|---|
| `code` | Required. Unique. Immutable. Code rule in [validation.md](../rules/validation.md) |
| `name` | Required. Name rule in [validation.md](../rules/validation.md) |
| `tax_id` | Required. The fiscal identifier (RFC). Validated by the RFC rule. Display label: "RFC" |
| `address` | Required. Structured. See section 3 |
| `payment_terms` | Required. Reference to the `PAYMENT_TERMS` catalog. See section 4 |
| `active` | Required. Default `true` |

Only active suppliers can be chosen for a new invoice. Deactivating a supplier keeps it on existing invoices.

## 3. Address

| Field | Rule |
|---|---|
| `street` | Required |
| `exterior_number` | Required |
| `interior_number` | Optional |
| `neighborhood` | Required |
| `city` | Required |
| `state` | Required. A value from the `STATE` catalog for the country |
| `postal_code` | Required. Exactly 5 digits |
| `country` | Required. A value from the `COUNTRY` catalog |

The field rules for each part of the address are in [validation.md](../rules/validation.md).

## 4. Payment terms

`PAYMENT_TERMS` is a value catalog with a label per locale, as described in [catalogs.md](../rules/catalogs.md). The owner has not listed the values. Examples of the shape:

| Key | Label (es) |
|---|---|
| `CASH` | Contado |
| `NET_15` | 15 días |
| `NET_30` | 30 días |

## 5. Supplier contact

Each supplier can have several contacts.

| Field | Rule |
|---|---|
| `supplier` | Required. Belongs to one supplier |
| `name` | Required. Name rule in [validation.md](../rules/validation.md) |
| `email` | Optional. If given, must pass the email rule (`EMAIL_INVALID` otherwise) |
| `phone` | Optional. If given, must be 10 digits (`PHONE_INVALID` otherwise) |
| `active` | Required. Default `true` |

A contact must have at least one of `email` or `phone`, so the business can reach the supplier (proposed).

Only active contacts of the chosen supplier appear on an invoice.

## 6. Permissions

| Permission | Who |
|---|---|
| `suppliers.view` | `ADMIN`, `OFFICE`, `WAREHOUSE_MANAGER` |
| `suppliers.manage` | `ADMIN` |

`OFFICE` chooses suppliers but does not create them. The owner has not said who creates suppliers, so this follows the catalog rule that only administrators manage catalogs.

## 7. API

All under `/api/v1`, with the headers and checks of [identity.md](identity.md) section 1. Reads need `suppliers.view`, changes `suppliers.manage`. Customers have the same endpoints under `/customers`, with `customers.view` and `customers.manage` ([customers.md](customers.md)).

| Method and path | Notes |
|---|---|
| `POST /suppliers/query` | Table contract. Filters: `code`, `name`, `taxId`, `active`. Sort: `code` (default), `name`, `taxId`, `createdAt` |
| `GET /suppliers/{id}` | The supplier with its contacts, active and inactive |
| `POST /suppliers` | `code`, `name`, `taxId`, `address`, `paymentTerms` |
| `PATCH /suppliers/{id}` | `name`, `taxId`, `address` (sent whole), `paymentTerms`. The code cannot change: sending it is `UNKNOWN_FIELD` |
| `DELETE /suppliers/{id}`, `POST /suppliers/{id}/reactivate` | Deactivates and reactivates. A supplier is never deleted |
| `POST /suppliers/{id}/contacts` | `name`, `email`, `phone` |
| `PATCH /suppliers/{id}/contacts/{contactId}` | The fields that are sent change; an empty `email` or `phone` removes it |
| `DELETE /suppliers/{id}/contacts/{contactId}`, `POST …/reactivate` | Deactivates and reactivates a contact |

`address` is `{ street, exteriorNumber, interiorNumber, neighborhood, city, state, postalCode, country }`. `state`, `country` and `paymentTerms` are catalog codes, such as `MX_JAL`, `MX` and the payment term's code. A supplier comes back with each of them as `{ code, label }`, labeled in the language of the request, and with `contacts` when it is read by id.

**Rules as the API applies them.** Codes, names, the RFC, the postal code and the street numbers follow [validation.md](../rules/validation.md) section 3, and each error names its field (`address.postalCode`, for example). The RFC and codes are uppercased; an email is lowercased; a phone is stored as its 10 digits. The state must be a `STATE` value of the address's country (its attribute `country`), or it is `CATALOG_VALUE_INVALID`. A contact with neither an email nor a phone is refused with `CONTACT_CHANNEL_REQUIRED` on both fields.

**Catalogs.** `COUNTRY` holds `MX`, and `STATE` the 32 Mexican states (`MX_AGU` to `MX_ZAC`, ISO 3166-2), as validation.md section 5 proposes. `PAYMENT_TERMS` starts empty: until the owner gives its values (question 3), an administrator adds them in the catalogs screen. The RFC and postal code rules apply to every supplier, since only Mexico exists (question 4).

**Audit actions:** `SUPPLIER_CREATED`, `SUPPLIER_UPDATED`, `SUPPLIER_DEACTIVATED`, `SUPPLIER_REACTIVATED`, and `SUPPLIER_CONTACT_CREATED`, `_UPDATED`, `_DEACTIVATED`, `_REACTIVATED`. Customers: the same with `CUSTOMER_`.

**Arrives with invoices (F4):** a deactivated supplier, or contact, is not offered for a new invoice, and still shows on the invoices that name it.

## 8. Open questions

1. **Contact channel.** Must every contact have an email, a phone, or both? *Proposed:* at least one.
2. **Who creates suppliers.** Only `ADMIN`, as proposed, or also `OFFICE` when creating an invoice?
3. **Payment terms.** Which values does the owner want in `PAYMENT_TERMS`?
4. **Country.** Are suppliers always Mexican, or can they be abroad? *Proposed:* the country is chosen from the `COUNTRY` catalog, and the RFC and postal code rules apply to Mexican suppliers.
