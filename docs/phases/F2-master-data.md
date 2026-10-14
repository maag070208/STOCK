# F2: Master data

**Goal:** the things the business is made of exist in the system: warehouses, their locations, materials, suppliers and customers, each with its validation.

**Depends on:** F1 (permissions, catalogs for payment terms, countries and states, and the unit of measure).

**Permissions added:** `locations.view`, `locations.manage`, `products.view`, `products.manage`, `suppliers.view`, `suppliers.manage`, `customers.view`, `customers.manage`.

## Specs

- [locations.md](../modules/locations.md): warehouses, locations, the temporary location, groups.
- [products.md](../modules/products.md): materials, the legacy import.
- [suppliers.md](../modules/suppliers.md) and [customers.md](../modules/customers.md).
- [validation.md](../rules/validation.md): every field.

## Deliverables

### API

- **Warehouses.** Create, read, update, deactivate. Code unique and immutable.
- **Locations.** The form creates a rack's levels in one request: warehouse, aisle, rack, number of levels. Codes are `aisle + rack + level` (for example `K05A`). All or nothing: any existing code rejects the whole request with `DUPLICATE_VALUE`, and the response lists the existing codes.
- **Temporary location.** Created once per warehouse by the system, with the key `TEMPORARY`. Cannot receive new placements.
- **Groups.** Queries by aisle and by rack. No stored group data.
- **Deactivation rules.** A location with stock cannot be deactivated. A rack deactivates only when all its levels are empty.
- **Products.** Create, read, update, deactivate. Barcode defaults to the product code, unique among active products. Unit from `UNIT_OF_MEASURE`.
- **Products import.** From the legacy file. Dry run first: reads the sheet, normalizes names (trims and collapses spaces), maps `PZA` to `PIECE`, skips rows without a code, and reports accepted and rejected rows with reasons. Then confirm: writes the products in one transaction, after checking the file's checksum matches the dry run. Idempotent by code. The import does not write stock (that is F4).
- **Suppliers and contacts.** Create, read, update, deactivate. Tax ID (RFC), structured address, payment terms, contact channel rule (at least one of email or phone).
- **Customers and contacts.** The same shape as suppliers.
- All fields validated as in [validation.md](../rules/validation.md).

### Web

- Warehouses and locations: the rack form, the list with groups by aisle and rack.
- Products: list, detail, edit. Import screen with the dry-run report before confirming.
- Suppliers and customers, each with its contacts.

### App

- None. Master data is managed on the web.

## Acceptance criteria

1. Creating rack `05` in aisle `K` with 3 levels creates `K05A`, `K05B` and `K05C`, and the warehouse gets its `TEMPORARY` location.
2. Creating rack `05` again with 3 levels is rejected in full, and no new location is created.
3. A location with stock cannot be deactivated.
4. The import dry run on the legacy file reports 1,398 accepted products and 2 rejected rows with reason "no code".
5. Confirming the import writes 1,398 products. Running it again writes nothing new.
6. A product name with doubled spaces is stored trimmed, and the case is kept as written.
7. A supplier with an invalid RFC is rejected with `TAX_ID_INVALID`. An email without `@` or without a dot is rejected with `EMAIL_INVALID`. A phone that is not 10 digits is rejected with `PHONE_INVALID`.
8. A supplier contact with neither email nor phone is rejected.
9. A customer with a 13-character individual RFC is accepted.
10. A deactivated supplier is not offered for a new invoice, and still appears on existing ones.

## Closure

**Done, 2026-10-14,** except two criteria that need records of later phases, and move there with them.

| Criterion | Proof |
|---|---|
| 1, 2 | API `LocationsEndpointTest` and `WarehousesEndpointTest`; web `warehouses.spec.ts` |
| 3 | **Moves to F4:** stock does not exist before F4. The rule is written in [locations.md](../modules/locations.md) section 8 |
| 4, 5 | API `LegacyFileAcceptanceTest`, on the real file (run with `LEGACY_FILE`); `ProductImportEndpointTest` and web `products.spec.ts` on small fixtures |
| 6 | API `ProductsEndpointTest` and `ProductRulesTest` |
| 7, 8 | API `SuppliersEndpointTest` and `RulesTest`; web `partners.spec.ts` |
| 9 | API `CustomersEndpointTest` |
| 10 | **Moves to F4:** invoices arrive in F4. A deactivated supplier stays readable, and the list filters it out (`SuppliersEndpointTest`) |

Decisions taken during F2, each recorded in its spec: the product name rule allows `#` and `¨`, measured on the legacy file; `COUNTRY` and `STATE` are seeded as proposed; `PAYMENT_TERMS` starts empty until the owner gives its values.

## Open questions

- Who creates suppliers and customers: only `ADMIN`, or also `OFFICE`? Proposed: `ADMIN` only.
- Confirm that the legacy file is the source for the first products load (see [products.md](../modules/products.md) section 9).
