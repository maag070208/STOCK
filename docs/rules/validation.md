# Validation

**Status:** Accepted, 2026-10-11, as stated by the business owner: every field is validated. Field rules below are proposed and get confirmed with the owner.

## 1. Principles

1. **Every field has an explicit rule:** required or optional, type, length, format, range, uniqueness, and normalization.
2. **The API validates every write**, whatever the client. The apps repeat the same rules so the user sees errors early. The API is the authority.
3. **Normalize, then validate.** Trim spaces, lowercase emails, uppercase tax IDs and codes. Nothing else is changed silently.
4. **Reject, never guess.** A value that fails a rule is rejected with an error. The system does not correct it.
5. **Unknown fields are rejected.** A request with a field that is not in the spec fails with `UNKNOWN_FIELD`.
6. **Errors carry a code and a field.** Codes are English (`EMAIL_INVALID`). The message is in the user's locale, Spanish by default, from [i18n/messages.md](../i18n/messages.md).

## 2. Error codes

| Code | When |
|---|---|
| `FIELD_REQUIRED` | A required field is missing or empty after trimming |
| `FIELD_TOO_LONG` | Longer than the maximum length |
| `FORMAT_INVALID` | Does not match the field's format (generic) |
| `EMAIL_INVALID` | Email does not match the email rule |
| `PHONE_INVALID` | Phone is not 10 digits |
| `TAX_ID_INVALID` | Tax ID (RFC) does not match its format or date |
| `POSTAL_CODE_INVALID` | Postal code is not 5 digits |
| `QUANTITY_INVALID` | Quantity is not positive, or has too many decimals for its unit |
| `DATE_IN_FUTURE` | A date that cannot be in the future is later than today |
| `DUPLICATE_VALUE` | A unique field already exists in its scope |
| `CATALOG_VALUE_INVALID` | Catalog value does not exist or is inactive |
| `UNKNOWN_FIELD` | Request contains a field outside the spec |
| `BARCODE_INVALID` | Barcode does not match the barcode rule |
| `IP_ADDRESS_INVALID` | Not a valid IPv4 address |

## 3. Rules by field type

### Text

| Rule | Value |
|---|---|
| Names (people, suppliers, warehouses, products) | Required. 2 to 120 characters. Trimmed. No control characters |
| Notes | Optional, except where a module says required. 1 to 500 characters |

### Codes

Used by products, suppliers, warehouses, and invoices where the owner gives a code.

| Rule | Value |
|---|---|
| Characters | Uppercase letters, digits, and hyphen only |
| Length | 2 to 20 characters |
| Uniqueness | Unique across its table |
| Changes | Immutable after creation |

### Barcode

Used by products. Scanned by the operator app, so it must be printable in Code 128.

| Rule | Value |
|---|---|
| Characters | Uppercase letters, digits, and hyphen only |
| Length | 6 to 20 characters |
| Numeric EAN-13 or UPC-A | If the value is 13 or 12 digits, its check digit must be valid |
| Uniqueness | Unique among active products |
| Normalization | Trimmed and uppercased |

### IP address

Used by printers.

| Rule | Value |
|---|---|
| Shape | Four numbers separated by dots |
| Each number | 0 to 255, no leading zeros |
| Spaces | Not allowed |

### Email

| Rule | Value |
|---|---|
| Shape | Exactly one `@`. A local part and a domain |
| Local part | 1 to 64 characters: letters, digits, `.`, `_`, `%`, `+`, `-`. Cannot start or end with `.` |
| Domain | Letters, digits, and hyphens in each label. At least one `.` |
| Top-level domain | Letters only, at least 2 characters |
| Total length | Up to 254 characters |
| Spaces | Not allowed |
| Normalization | Trimmed and lowercased |

Examples: `ana@empresa.com.mx` is valid. `ana@empresa` (no dot), `ana.empresa.com` (no `@`) and `ana@@empresa.com` are rejected with `EMAIL_INVALID`.

### Phone

| Rule | Value |
|---|---|
| Input | Digits, with optional spaces, dashes, and parentheses. An optional leading `+52` is accepted |
| Stored | Exactly 10 digits, with the `+52` and separators removed |
| Rejected | Anything that is not exactly 10 digits after cleaning, with `PHONE_INVALID` |

Examples: `(55) 1234-5678` and `+52 55 1234 5678` are stored as `5512345678`. `12345678` (8 digits) is rejected.

### Tax ID (RFC)

The owner's fiscal identifier is the Mexican RFC. This rule assumes the business operates in Mexico.

| Rule | Value |
|---|---|
| Normalization | Trimmed and uppercased |
| Length | 12 characters for companies, 13 for individuals |
| Shape | Letters (including `Ñ` and `&`), then 6 digits, then 3 letters or digits |
| Date | The 6 digits are a valid date in `YYMMDD` |

### Postal code

Mexican postal code: exactly 5 digits.

### Address

| Field | Rule |
|---|---|
| `street` | Required. 1 to 120 characters |
| `exterior_number` | Required. 1 to 10 characters: letters, digits, `/`, and `-`. `S/N` is allowed |
| `interior_number` | Optional. Same rule as the exterior number |
| `neighborhood` | Required. 1 to 80 characters |
| `city` | Required. 1 to 80 characters |
| `state` | Required. A value from the `STATE` catalog for the chosen country |
| `postal_code` | Required. See above |
| `country` | Required. A value from the `COUNTRY` catalog (ISO 3166-1 alpha-2, such as `MX`) |

### Catalog references

A field that points to a catalog must hold an active catalog value. An inactive value is rejected for new records (`CATALOG_VALUE_INVALID`) but still shows on history.

### Dates

- Sent in the API as ISO 8601 (`YYYY-MM-DD`).
- Must be a real calendar date.
- A date the business does not allow in the future fails with `DATE_IN_FUTURE`. No module refuses a date for being in the
  future today: an invoice date was the example until F8-01 allowed it.

### Quantities

| Rule | Value |
|---|---|
| Sign | Positive for expected and received quantities. Zero is allowed for counted quantities |
| Decimals | Limited by the unit. The `UNIT_OF_MEASURE` catalog holds `decimal_places`. Piece: 0. Kilogram: 3 (proposed) |
| Maximum | 999999 per field (proposed) |
| Format | Dot as decimal separator in the API. Display follows the locale |

### Invoice fields

| Field | Rule |
|---|---|
| Invoice number | Required. 1 to 30 characters: letters, digits, `-`, and `/`. Uppercased. Unique per supplier |
| Invoice date | Required. Not in the future |
| Supplier | Required. An active supplier |
| Warehouse | Required. An active warehouse the user can access |

### Location fields

| Field | Rule |
|---|---|
| Aisle | One uppercase letter, `A` to `Z` |
| Rack | Two digits, `00` to `99` |
| Levels to create | Integer, 1 to 26. The system letters them `A` onward |
| Full code | `aisle + rack + level`, such as `K05A`. Unique within its warehouse. The system location `TEMPORARY` is the only exception |

## 4. Enforcement

- **API:** every write runs the rules above before it touches the database.
- **Database:** critical rules are mirrored with constraints (unique keys, length, non-negative quantities), so a bug in the API cannot store bad data.
- **Apps:** show the same rules as they type. They are for feedback only.

## 5. Open questions

1. **Rack 00.** Does rack `00` exist, or do racks start at `01`? The owner's list said `0` to `5`.
2. **State catalog.** Do we load the 32 Mexican states as `STATE` values? *Proposed:* yes. Applied in F2 as proposed: `MX_AGU` to `MX_ZAC`, each with its country.
3. **Product code.** What format do product codes have? The rule above is the default until the owner gives one.
