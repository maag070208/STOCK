# F2 tasks: Master data

Phase: [F2-master-data.md](../F2-master-data.md).

| ID | Task | Area | After | Owner |
|---|---|---|---|---|
| F2-01 | Warehouses | API | F1 | |
| F2-02 | Location codes | API | F2-01 | |
| F2-03 | Rack form, all or nothing | API | F2-02 | |
| F2-04 | Temporary location per warehouse | API | F2-01 | |
| F2-05 | Location groups by aisle and rack | API | F2-03 | |
| F2-06 | Products | API | F1-15 | |
| F2-07 | Products import: dry run | API | F2-06 | |
| F2-08 | Products import: confirm and idempotency | API | F2-07 | |
| F2-09 | Suppliers and contacts | API | F1-15 | |
| F2-10 | Customers and contacts | API | F2-09 | |
| F2-11 | Warehouses and locations screens | Web | F2-03, F2-05 | |
| F2-12 | Products screens and import | Web | F2-06, F2-08 | |
| F2-13 | Suppliers and customers screens | Web | F2-09, F2-10 | |

**Progress, 2026-10-14:**
- F2-01 to F2-05 are done in the API (contract in [locations.md](../../modules/locations.md) section 8). What depends on stock waits for F4: deactivating a location with stock, deactivating a rack, and refusing placements into `TEMPORARY`.
- F2-11 is done on the web: the warehouses list, and each warehouse with its aisles, racks and locations, and the rack form.
- F2-06 to F2-08 are done in the API (contract in [products.md](../../modules/products.md) section 8). Acceptance criteria 4 and 5 were checked on the real file. The name rule gained `#` and `¨`, measured on the file.
- F2-12 is done on the web: the materials list and detail, and the import screen with the dry run's report before the confirm.
- F2-09 and F2-10 are done in the API (contract in [suppliers.md](../../modules/suppliers.md) section 7). `COUNTRY` and `STATE` are seeded as proposed; `PAYMENT_TERMS` starts empty until the owner gives its values.
- F2-13 is done on the web: suppliers and customers, each with its detail, its contacts, and the form as its own screen (`/new`, `/:id/edit`), which answers with the `403` page without the manage permission.
- **F2 is closed.** Criteria 3 and 10 move to F4 with stock and invoices.

## F2-01 · Warehouses · API · after F1
- Unit (red): a warehouse code is unique, uppercase and immutable; a deactivated warehouse is not offered for new records.
- E2E (red): create, read, update, deactivate a warehouse; a user without `locations.manage` gets `403`.
- Accept:
  - A warehouse is never deleted.
  - `user_warehouses.warehouse_id` gets its foreign key to the warehouses table.
  - The `AREA` checks deferred from F1-11 run here over HTTP: a user with `locations.view` at `AREA` reads a warehouse by id outside their warehouses as `404`, and a list returns only their warehouses, with a total that counts only those.

## F2-02 · Location codes · API · after F2-01
- Unit (red): `aisle + rack + level` gives `K05A`; an aisle that is not one letter, a rack that is not two digits, or a level count outside 1 to 26 is rejected.
- E2E (red): the validation errors come back with `FORMAT_INVALID` and the field name.
- Accept: `TEMPORARY` is the only code outside the pattern.

## F2-03 · Rack form, all or nothing · API · after F2-02
- Unit (red): the generator yields letters `A` onward for the level count.
- E2E (red): aisle `K`, rack `05`, three levels creates `K05A`, `K05B`, `K05C`. Creating again rejects the whole request with `DUPLICATE_VALUE` and lists the existing codes; no location is created.
- Accept: the creation runs in one transaction.

## F2-04 · Temporary location per warehouse · API · after F2-01
- Unit (red): the warehouse creation adds one `TEMPORARY` location; a second one is not created.
- E2E (red): creating a warehouse returns it with its `TEMPORARY` location; the form cannot create a location with that key.
- Accept: `TEMPORARY` cannot receive new placements (checked in F4).

## F2-05 · Location groups by aisle and rack · API · after F2-03
- Unit (red): the group query filters on warehouse and aisle, or on warehouse, aisle and rack.
- E2E (red): the rack group `K05` returns `K05A`, `K05B`, `K05C` only.
- Accept: groups store no data of their own.

## F2-06 · Products · API · after F1-15
- Unit (red): the barcode defaults to the product code; the barcode must be unique among active products; the name is trimmed and its doubled spaces collapsed; the unit must be an active `UNIT_OF_MEASURE` value.
- E2E (red): create a product; a second product with the same barcode returns `DUPLICATE_VALUE`; a product name with a `"` is accepted.
- Accept: a deactivated product stays in history.

## F2-07 · Products import: dry run · API · after F2-06
- Unit (red): the parser maps `PZA` to `PIECE`, skips rows without a code, and reports each rejected row with its reason.
- E2E (red): the dry run on the small fixture returns the accepted count, the rejected rows with reasons, and the control result. It writes nothing.
- Accept: the fixture is anonymized and small. The legacy file is not in the repository.

## F2-08 · Products import: confirm and idempotency · API · after F2-07
- Unit (red): the confirmation rejects a file whose checksum differs from the reviewed dry run.
- E2E (red): confirming writes the products in one transaction; running it again writes nothing; a changed file is rejected.
- Accept: the import creates no stock. Stock is loaded in F4.

## F2-09 · Suppliers and contacts · API · after F1-15
- Unit (red): RFC rule (12 or 13 characters, valid date); email rule (`@`, a dot, a top-level domain of two letters or more); phone rule (exactly 10 digits after cleaning); postal code (5 digits); a contact with neither email nor phone is rejected.
- E2E (red): a supplier with an invalid RFC returns `TAX_ID_INVALID`; an email without `@` returns `EMAIL_INVALID`; a phone of 8 digits returns `PHONE_INVALID`.
- Accept: a valid supplier with a valid contact is created, and the contact appears in its list.

## F2-10 · Customers and contacts · API · after F2-09
- Unit (red): the same rules as suppliers, reusing the same validators; an individual RFC of 13 characters is accepted.
- E2E (red): a customer with a 13-character RFC is created; a customer with a 12-character RFC is created too.
- Accept: an order cannot name a customer that is inactive (tested in F5).

## F2-11 · Warehouses and locations screens · Web · after F2-03, F2-05
- Unit (red): the rack form shows the codes it will create before submitting.
- E2E (red): Playwright creates a rack from the form, and the list shows the groups by aisle and rack.
- Accept: a duplicate rack shows the server's error in the user's locale, next to the form.

## F2-12 · Products screens and import · Web · after F2-06, F2-08
- Unit (red): the import screen shows the dry-run counts before the confirm button is enabled.
- E2E (red): Playwright runs the dry run and the confirm on the fixture, and the second confirm reports nothing new.
- Accept: the list uses the server-side table contract, with a filter and a sort on each data column.

## F2-13 · Suppliers and customers screens · Web · after F2-09, F2-10
- Unit (red): the contact form requires email or phone before enabling save.
- E2E (red): Playwright creates a supplier with a contact, and an invalid RFC shows the error next to the field.
- Accept: `OFFICE` sees the lists and cannot open the create form (`403` page).
