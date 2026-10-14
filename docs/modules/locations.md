# Warehouses and locations

**Status:** Captured from the business owner, 2026-10-11. Field rules are in [validation.md](../rules/validation.md). The API is in section 8. Open questions are in section 9.

## 1. Concepts

- A **warehouse** is a physical building or site, such as `CENTRAL`.
- A **location** is one place where material is stored. Its code is the aisle, rack and level: `K05A`.
- A **group** is a set of locations that share a part of their code. Groups are for finding and reporting. They do not hold stock and do not add rules.

Locations are **independent**. Each location holds any number of products, and each product can have any quantity in it. A group never limits what a location can hold.

## 2. Creating locations

The form creates a whole rack at once:

| Field | Input | Example |
|---|---|---|
| Warehouse | Select | `CENTRAL` |
| Aisle | Text (one letter) | `K` |
| Rack | Text (two digits) | `05` |
| Number of levels | Integer, 1 to 26 | `3` |

The system creates one location per level. Each level becomes the next letter, starting at `A`:

| Input | Locations created |
|---|---|
| Aisle `K`, rack `05`, 3 levels | `K05A`, `K05B`, `K05C` |
| Aisle `K`, rack `05`, 1 level | `K05A` |

Creation is all or nothing. If any of the codes already exists in the warehouse, the request is rejected with `DUPLICATE_VALUE`, and the response lists the existing codes. Nothing is created in part.

## 3. Groups

Two groups come from the code, with no extra data entry:

| Group | Contains | Example |
|---|---|---|
| Aisle | Every location with the same warehouse and aisle | `CENTRAL` / `K`: all `K..` locations |
| Rack | Every location with the same warehouse, aisle and rack | `CENTRAL` / `K` / `05`: `K05A`, `K05B`, `K05C` |

Groups are used for filters, reports, and listing. Stock is never stored at group level.

## 4. Fields

**Warehouse**

| Field | Rule |
|---|---|
| `code` | Required. Unique. Immutable. Example: `CENTRAL` |
| `name` | Required. Example: "Almacén central" |
| `active` | Required. Default `true` |

**Location**

| Field | Rule |
|---|---|
| `warehouse` | Required. Each location belongs to exactly one warehouse |
| `aisle` | Required. One letter |
| `rack` | Required. Two digits |
| `level` | Required. One letter, assigned by the system |
| `code` | Generated as `aisle + rack + level`. Example: `K05A`. Unique within its warehouse |
| `active` | Required. Default `true` |

## 5. Stock in a location

A location's stock is a balance per product:

| Field | Rule |
|---|---|
| `location` | Required |
| `product` | Required |
| `quantity` | Required. Zero or more. Unit is the product's unit |

- A location can hold any number of products.
- A product can be in any number of locations.
- There is no capacity limit. Not in the owner's description.
- Balances are derived from movements. A placement adds to the balance. A movement out subtracts from it, and a balance can never go below zero.

## 6. Rules

1. **Only active locations** can receive stock.
2. **The temporary location** (`TEMPORARY`) exists once per warehouse. The system creates it, not the form. It holds the opening inventory and nothing new. Its balances move out as products are placed into real locations, and it cannot receive new placements.
3. **Deactivate, never delete.** A location with any positive balance cannot be deactivated (proposed).
4. **Placement is restricted to the invoice's warehouse.** See [material-receiving.md](material-receiving.md).
5. **Scope.** A location's warehouse decides who can see and place into it, through the `AREA` scope. See [access-control.md](../rules/access-control.md).
6. **Groups are read-only views** of the codes. Changing a group's contents means changing the locations' codes, which is not allowed, so groups cannot be edited.

## 7. Permissions

| Permission | Who |
|---|---|
| `locations.view` | `ADMIN`, `WAREHOUSE_MANAGER` and `WAREHOUSE_OPERATOR`, within their warehouses |
| `locations.manage` | `ADMIN`. Creates, deactivates and reactivates locations |

## 8. API

All under `/api/v1`, with the headers and checks of [identity.md](identity.md) section 1. Reads need `locations.view`, changes `locations.manage`. With `AREA`, a list holds only the person's warehouses (and their locations), its total counts only those, and a read by id of another warehouse answers `404 NOT_FOUND`.

| Method and path | Permission | Notes |
|---|---|---|
| `POST /warehouses/query` | `locations.view` | Table contract. Filters: `code`, `name`, `active`. Sort: `code` (default), `name`, `createdAt` |
| `GET /warehouses/{id}` | `locations.view` | |
| `GET /warehouses/{id}/location-groups` | `locations.view` | The aisles, each with its racks: `[{ aisle, racks: [{ rack, locations, active }] }]`. The temporary location is not in a group |
| `POST /warehouses` | `locations.manage` | `code`, `name`. Creates the warehouse and its `TEMPORARY` location, in one transaction |
| `PATCH /warehouses/{id}` | `locations.manage` | `name`. The code cannot change: sending it is `UNKNOWN_FIELD` |
| `DELETE /warehouses/{id}` | `locations.manage` | Deactivates. A warehouse is never deleted |
| `POST /warehouses/{id}/reactivate` | `locations.manage` | |
| `POST /locations/query` | `locations.view` | Table contract. Filters: `warehouseId`, `code`, `aisle`, `rack`, `active`, `temporary`. Sort: `warehouse` and `code` (default), `aisle`, `rack`. The groups of section 3 are the filters `aisle`, and `aisle` with `rack` |
| `GET /locations/{id}` | `locations.view` | |
| `POST /locations/racks` | `locations.manage` | The form of section 2: `warehouseId`, `aisle`, `rack`, `levels`. Response: `locations`, the levels created |
| `DELETE /locations/{id}` | `locations.manage` | Deactivates. The temporary location answers `409 SYSTEM_LOCATION_PROTECTED` |
| `POST /locations/{id}/reactivate` | `locations.manage` | Not while its warehouse is deactivated (`CATALOG_VALUE_INVALID` on `warehouseId`) |

A warehouse is `{ id, code, name, active, temporaryLocation: { id, code }, createdAt }`. A location is `{ id, warehouseId, warehouseCode, code, aisle, rack, level, temporary, active }`.

**Errors of the rack form.** Each field that fails is in `details` with its name: `FIELD_REQUIRED`, or `FORMAT_INVALID` for an aisle that is not one letter, a rack that is not two digits, or a level count outside 1 to 26. A warehouse that does not exist or is deactivated is `CATALOG_VALUE_INVALID` on `warehouseId`. When codes exist, the answer is `409 DUPLICATE_VALUE` with one detail per existing code, as the field `codes.<code>` (for example `codes.K05A`), and nothing is created.

**Warehouses of a person.** `PUT /users/{id}/warehouses` ([identity.md](identity.md)) takes only warehouses that exist and are active; any other id is `CATALOG_VALUE_INVALID` on `warehouseIds`.

**Audit actions:** `WAREHOUSE_CREATED`, `WAREHOUSE_UPDATED`, `WAREHOUSE_DEACTIVATED`, `WAREHOUSE_REACTIVATED`, `RACK_CREATED` (on the warehouse, with the codes created), `LOCATION_DEACTIVATED`, `LOCATION_REACTIVATED`.

**Arrives with stock (F4):** a location with stock cannot be deactivated; deactivating a whole rack when every level is empty (question 2); the temporary location refuses new placements.

## 9. Open questions

1. **Adding levels to an existing rack.** If `K05` already has `A`, `B`, `C`, should a new request with 5 levels create `D` and `E` (the next letters), or be rejected? *Proposed:* rejected. Adding levels is a separate action, to be specified.
2. **Deactivating a rack.** Does deactivating a rack deactivate all its levels, only when they are empty? *Proposed:* yes, only when every level is empty.
3. **Custom groups.** Do you need groups other than aisle and rack, such as "cold zone" or "high rotation"? If yes, a location can belong to several groups. *Proposed:* not in v1.
4. **Location code display.** Do we show the code as `K05A`, or with the warehouse, as `CENTRAL / K05A`? The code is unique per warehouse, so the warehouse is needed when two warehouses have the same code. *Proposed:* show both.
5. **Aisle letters.** Is one letter enough for aisles? If the warehouse has more than 26 aisles, two letters are needed. *Proposed:* one letter for now.
