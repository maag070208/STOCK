# Catalogs

**Status:** Accepted

## 1. Rule

Every list the business manages (statuses, units of measure, suppliers, products, warehouses) is **data in the database**, not code. An administrator creates, renames, reorders, and deactivates entries through the API, without a deploy.

Example, `INVOICE_STATUS`:

| Code | Label (es) | Attributes |
|---|---|---|
| `OPEN` | Abierta | none |
| `PENDING` | Pendiente | none |
| `CLOSED` | Cerrada | `terminal: true` |

## 1.1 Two shapes

- **Value catalogs** hold a fixed list of values with a label per locale, such as `INVOICE_STATUS` or `UNIT_OF_MEASURE`. They use the `catalog`, `catalog_item` and `catalog_item_label` tables described below.
- **Entity catalogs** hold records with their own fields and relations, such as suppliers, supplier contacts, products and warehouses. Each one has its own table, with `active` and the audit columns. Their specs live in [modules/](../modules/).

Both shapes are administered in the database through the API. Neither is hard-coded. Both follow rule 2 of section 3: deactivate, never delete.

## 2. Data model

**`catalog`**: one row per catalog.
- `id`
- `code`: unique, UPPER_SNAKE_CASE. Immutable.
- `is_system`: true for catalogs that code depends on. Cannot be deleted.
- `active`
- `created_at`, `updated_at`, `created_by`

**`catalog_item`**: one row per value.
- `id`
- `catalog_id` → `catalog.id`
- `code`: unique within its catalog, UPPER_SNAKE_CASE. Immutable.
- `sort_order`
- `active`
- `attributes`: JSONB of typed flags (see section 4).
- `created_at`, `updated_at`, `created_by`

**`catalog_item_label`**: translations.
- `catalog_item_id` → `catalog_item.id`
- `locale`: BCP 47 tag (`es` default, `en` required for active items)
- `label`: short display text
- `description`: optional
- Primary key: (`catalog_item_id`, `locale`)

## 3. Rules

1. **Codes are immutable.** Labels, descriptions, and order can change. The code is the stable identifier that other tables and the code use.
2. **Deactivate, never delete.** An item referenced by any record is set to `active = false`. Inactive items are hidden from new records but still appear in history.
3. **Every active item needs an `es` and an `en` label.** The API rejects activation without both.
4. **Referencing columns are foreign keys** to `catalog_item.id`. Free-text status columns are not allowed. The service also checks that the item belongs to the catalog that column expects.
5. **Code never branches on labels.** It branches on codes, and only for catalogs marked `is_system`. Behavior that depends on a value reads its `attributes` (for example `terminal`), not its name.
6. **Every change is audited:** who, when, and before/after values.
7. **Access is controlled by permissions:** `catalogs.view` to read, `catalogs.manage` to create or change catalogs and items. See [roles.md](roles.md) and [access-control.md](access-control.md).

## 4. Attributes

Attributes are typed flags on an item. Developers define the vocabulary of keys, because the code interprets them. Administrators set the values.

| Key | Type | Meaning |
|---|---|---|
| `terminal` | boolean | No transition is allowed out of this value |
| `decimal_places` | integer, 0 to 6 | How many decimals a quantity in this unit of measure may have (`UNIT_OF_MEASURE`) |
| `country` | text, a `COUNTRY` code | The country a `STATE` value belongs to. An address takes a state of its own country |
| `direction` | `in` or `out` | Whether a `MOVEMENT_TYPE` adds to a balance or takes from it |

Adding a key is a code change. Setting a key on an item is data.

## 5. Transitions (open)

Which values can move to which is also data: a `catalog_transition` table with `from_item_id` and `to_item_id`. The `INVOICE_STATUS` transitions will be defined from the business flows.

## 6. Seeding

Initial catalogs are seed data in the migrations, so a fresh database works. After that, the database is the source of truth. Re-running seeds never overwrites administrator changes.
