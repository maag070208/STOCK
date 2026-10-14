# Access control: RBAC and ABAC

**Status:** Accepted. Adopts the access model of the house standard (CYC, [D-009](../../../CYC/DECISIONES.md) and [D-018](../../../CYC/DECISIONES.md)), adjusted to warehouses.

## 1. Model

- **RBAC (dynamic):** roles, permissions, and the role × permission matrix live in the database. Users can hold several roles. The matrix is cached in memory and reloaded after every write.
- **Scopes:** each grant carries a scope that limits how far the permission reaches.
- **Per-user exceptions:** an individual grant with a reason and an optional expiry.
- **ABAC (policies):** conditions evaluated after RBAC has allowed the action. They can deny or allow based on the record and the user.
- **Fail-closed:** if the access data cannot be loaded, every scope is `NONE`.
- **Server-side only.** The JWT identifies the user. Permissions are resolved from the database on every request.

## 2. Scopes

`NONE < OWN < AREA < ALL`. When a user has several roles, the highest scope wins (`maxScope`). Roles add permissions and never subtract them. A valid user exception overrides the role.

| Scope | Meaning in Stock |
|---|---|
| `NONE` | No access |
| `OWN` | Records the user created, or records assigned to the user |
| `AREA` | Records in the user's assigned warehouses |
| `ALL` | Every record |

**AREA** comes from `user_warehouses`, which assigns warehouses to users. Each module registers how its records map to a warehouse, as CYC does with its `groups` resolver (D-028). Catalogs and products are shared across warehouses, so they only use `ALL` or `NONE`.

Out-of-scope access: reads by id respond `404`, writes respond `403`.

## 3. Tables

| Table | Content |
|---|---|
| `permissions` | Catalog: `key` (`module.action`), `module`, `name_key`, `scopes[]`, `sensitive`, `active`, `sort_order` |
| `roles` | `key`, `name` (custom roles only), `system`, `active`, `sort_order` |
| `role_permissions` | Role → permission → scope. Absence means `NONE` |
| `user_roles` | Roles held by a user (multi-role) |
| `user_permissions` | Exceptions per person: `scope`, `reason`, `expires_at`, `granted_by` |
| `user_warehouses` | Warehouses in the user's `AREA` |
| `policies` | `key`, `priority`, `effect` (`ALLOW` / `DENY`), `action`, `active` |
| `policy_conditions` | `policy_key`, `field`, `operator`, `value` (JSON) |
| `policy_roles` | Roles the policy applies to. No rows means every role |

Roles, permissions and policies use their `key` as natural key. The other tables use a UUID `id`, except the link tables, whose key is the pair they link.

## 4. Permission catalog

Permission keys are `module.action`, in lowercase. Developers define them, because each one guards code. The migrations seed them into `permissions`. Granting them to roles and users is data.

This catalog lists the permissions that the seed roles use. Stock, movements, counts, adjustments, transfers and reports get their permissions when their modules are specified. Until then, only `ADMIN` can reach them.

**Sensitive** marks permissions that move money or change access. The access console highlights them.

| Permission | Scopes | Sensitive | Guards |
|---|---|:---:|---|
| `users.view` / `users.create` / `users.edit` / `users.delete` | ALL | | Users |
| `users.permissions` | ALL | ✓ | Per-user exceptions |
| `roles.manage` | ALL | ✓ | Roles, matrix, policies, access console |
| `audit.view` | ALL | | Audit log |
| `config.view` / `config.manage` | ALL | | Settings |
| `catalogs.view` / `catalogs.manage` | ALL | | Catalogs and their items |
| `products.view` / `products.manage` | ALL | | Products |
| `suppliers.view` / `suppliers.manage` | ALL | | Suppliers and their contacts |
| `customers.view` / `customers.manage` | ALL | | Customers and their contacts |
| `locations.view` | AREA, ALL | | Warehouses, zones, locations (read) |
| `locations.manage` | ALL | | Warehouses, zones, locations (change) |
| `invoices.view` | AREA, ALL | | Invoices (read) |
| `invoices.create` | ALL | | Creating invoices |
| `invoices.manage` | ALL | ✓ | Editing invoices and changing their status |
| `receipts.view` | OWN, AREA, ALL | | Material entries (read) |
| `receipts.assign` | AREA, ALL | | Assigning entries to staff |
| `receipts.receive` | OWN, AREA, ALL | | Counting the materials assigned to the user |
| `receipts.place` | OWN, AREA, ALL | | Placing counted materials into locations |
| `discrepancies.resolve` | AREA, ALL | ✓ | Resolving a difference between expected and counted quantities |
| `orders.create` | ALL | | Creating orders |
| `orders.view` | AREA, ALL | | Orders (read) |
| `orders.assign` | AREA, ALL | | Assigning an order to an operator |
| `orders.accept_difference` | AREA, ALL | ✓ | Accepting a difference between picks and the order line |
| `orders.fulfill` | AREA, ALL | | Picking materials for an order |
| `orders.validate` | AREA, ALL | ✓ | Validating a fulfilled order |
| `orders.cancel` | AREA, ALL | | Cancelling an order with no picks yet |
| `orders.reassign` | AREA, ALL | ✓ | Reassigning an order, or its picked quantities, after picking has started |
| `labels.view` / `labels.manage` | ALL | ✓ | Label templates (ZPL) |
| `labels.print` | AREA, ALL | | Printing labels on the printers of the user's warehouse |
| `printers.view` / `printers.manage` | ALL | ✓ | Printers and their IP addresses |
| `staff_templates.manage` | AREA, ALL | | Staff templates: who works in each warehouse |
| `web.access` | ALL | | Signing in through the web |
| `app.access` | ALL | | Signing in through the mobile app |

## 5. Evaluation

For each request:

1. Authenticate and load the user.
2. **Channel:** `web.access` for web requests, `app.access` for app requests. Without it, respond `403 INSUFFICIENT_PERMISSIONS`.
3. **RBAC:** effective scope = the highest scope from the user's roles, or the user's valid exception if one exists. If the scope is `NONE`, respond `403 INSUFFICIENT_PERMISSIONS`.
4. **Record scope:** the service checks that the record is inside the scope (`OWN` or `AREA`), not the client.
5. **ABAC:** take the active policies for the action whose roles apply to the actor (no roles means everyone), sorted by `priority` ascending, then `key`. The **first** policy whose conditions all hold decides with its effect. If none matches, the action is allowed, because RBAC already granted it.
6. If the decision is `DENY`, respond `403 POLICY_DENIED` and write `ACCESS_DENIED` to the audit log with the policy key.

## 6. Policies

A policy restricts an action under a condition. It is administered in the access console.

### Conditions

A condition is `field operator value`:

| Operator | Meaning |
|---|---|
| `eq`, `neq` | Equal, not equal |
| `in`, `not_in` | Value is (or is not) in a list. If the field is an array, it matches when any element matches |
| `contains`, `not_contains` | Text or array contains (or not) the value |
| `gt`, `gte`, `lt`, `lte` | Comparison |
| `exists` | Field is present |

`value` is JSON. It accepts the references `"@user.id"`, `"@user.username"` and `"@user.roles"`.

### Action registry

Only actions registered in the API can have policies. Each action declares which fields a condition may read. An action is registered when the code that enforces it exists, so a policy never names an action that nothing checks. In F1 the registry holds `users.permissions.set`; each module adds its actions with its own code. This is the security boundary: a policy cannot read data the action did not expose, and it cannot restrict the access-control permissions, because `roles.manage` is not a registered action.

Saving a policy with an unknown action or field is rejected (`POLICY_ACTION_UNKNOWN`, `POLICY_FIELD_UNKNOWN`).

| Action | Fields | Where it applies |
|---|---|---|
| `invoices.create` | `supplierId`, `invoiceDate` | Creating an invoice |
| `receipts.assign` | `quantity`, `warehouseId` | Assigning an entry to staff |
| `receipts.receive` | `expectedQuantity`, `countedQuantity`, `difference` | Counting an assignment |
| `receipts.place` | `quantity`, `locationId` | Placing counted materials into a location |
| `discrepancies.resolve` | `difference`, `expectedQuantity` | Adjusting an expected quantity to the counted one |
| `orders.create` | `lineCount`, `warehouseId`, `customerId` | Creating an order |
| `orders.fulfill` | `quantity`, `warehouseId` | Picking materials for an order |
| `orders.cancel` | `reservedQuantity`, `status` | Cancelling an order |
| `users.permissions.set` | `target.id`, `target.roles`, `roles`, `permission`, `scope` | Granting roles (`roles` is the new set) or exceptions (`permission`, `scope`) |

### Examples

These are examples of how the mechanism works. The values are not decided.

| Policy | Action | Effect | Condition | Roles | Priority |
|---|---|---|---|---|---|
| `RECEIPT_QUANTITY_LIMIT` | `receipts.receive` | `DENY` | `countedQuantity gt 100` | `WAREHOUSE_OPERATOR` | 10 |

`RECEIPT_QUANTITY_LIMIT` blocks an operator from counting more than 100 units on one assignment. `ADMIN` is not listed, so it is not affected.

## 7. Adopted from CYC, not yet decided for Stock

- **Policies that grant access** are not part of the model. Policies only deny or allow within what RBAC already granted, as in CYC.
- **No match means allow.** This is CYC's rule for policies. RBAC still denies by default.
- **AREA by assigned warehouses** is the proposed definition for Stock. Confirm it with the flows.
- **Channel permissions** (`web.access`, `app.access`) are a Stock addition. Section 6 of [roles.md](roles.md) asks to confirm them.
