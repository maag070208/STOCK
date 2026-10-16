# Labels and printers

**Status:** Captured from the business owner, 2026-10-13. Printers are Zebra, connected by IP. Label size is a working assumption (section 8), to be confirmed against the roll.

## 1. Purpose

The system prints labels: product labels, location labels, and later others. A label carries the barcode the operator app scans (see [barcode-scanning.md](barcode-scanning.md)). Each label type has a ZPL template, stored in the database and administered from the system, so a change to a label never needs a deploy.

## 2. Label templates

One row per label template:

| Field | Rule |
|---|---|
| `id` | Required. Unique. Assigned by the system |
| `key` | Required. Unique. UPPER_SNAKE_CASE, such as `PRODUCT` or `LOCATION`. Immutable |
| `name` | Required. Display name in `es` and `en`, from the catalog labels |
| `zpl` | Required. The template. See section 4 |
| `version` | Set by the system. Starts at 1 and increases with each change |
| `active` | Required. Default `true` |

Initial rows:

| id | key | name (es) |
|---:|---|---|
| 1 | `PRODUCT` | Etiqueta de producto |
| 2 | `LOCATION` | Etiqueta de ubicación |

The catalog is open: more label types can be added later, such as orders or invoices, without a deploy.

## 3. Placeholders

A template contains placeholders, written `{{name}}`. The system fills them when printing. Each key allows only its own placeholders, plus `{{copies}}`, which every template has. Saving a template with any other placeholder is rejected.

| Key | Placeholders |
|---|---|
| `PRODUCT` | `{{code}}`, `{{name}}`, `{{barcode}}`, `{{unit}}`, `{{copies}}` |
| `LOCATION` | `{{code}}`, `{{warehouse_code}}`, `{{aisle}}`, `{{rack}}`, `{{level}}`, `{{copies}}` |

`{{copies}}` is a whole number from 1 to 100, and it is the number of labels printed. It defaults to 1 and is asked for on each print.

A location's barcode is its code (for example `K05A`), which is what the operator scans.

## 4. Template rules

A template is ZPL, the printer's language. It is data that reaches a printer, so it is restricted:

1. **One label per template.** Exactly one `^XA` … `^XZ` block.
2. **Allowed commands only.** A fixed list covering layout (`^PW`, `^LL`, `^LH`, `^FO`, `^GB`), text (`^A`, `^CF`, `^FB`, `^FD`, `^FS`), barcodes (`^BY`, `^BC`, `^BQ`), and print quantity (`^PQ`). Anything else is rejected at save. Commands that configure, store, delete, or reset the printer are never allowed (`~` control commands, `^DF`, `^XF`, `^ID`, `^JU`, `^JR`).
3. **Data is sanitized.** At print time, every placeholder value has the characters `^`, `~` and `\` removed, and is cut to 60 characters. Product names with inch marks and slashes are allowed.
4. **Only admins edit.** `labels.manage` is `ADMIN`.
5. **Versioned.** Every change creates a new version. The active version is used for printing.
6. **The editor draws the label.** The screen of a template shows it as the printer would print it (the width and the
   length of the template, the texts at their position and height, the bars of the Code 128 barcode, and the values with a
   sample in place of each placeholder), so a change is checked before a roll of labels is spent. It is a drawing and not
   an interpreter: it understands the commands of rule 2 and nothing else, and a `^BQ` is shown as the square it takes.

### Example: product label

Size 4 × 2 inches at 203 dpi (812 × 406 dots). The name wraps to two lines, the barcode is printed with its text, and the code and unit are below it.

```
^XA
^PW812
^LL406
^LH0,0
^CF0,40
^FO30,30^FB752,2,0,L,0^FD{{name}}^FS
^FO30,150^BY3,2,110^BCN,110,Y,N,N^FD{{barcode}}^FS
^FO30,320^A0N,40,40^FD{{code}}  {{unit}}^FS
^PQ{{copies}}
^XZ
```

### Example: location label

The location code is printed large, with its barcode below it, and the warehouse, aisle, rack and level on the right.

```
^XA
^PW812
^LL406
^LH0,0
^FO30,20^A0N,200,180^FD{{code}}^FS
^FO30,250^BY3,2,100^BCN,100,Y,N,N^FD{{code}}^FS
^FO430,40^A0N,40,40^FD{{warehouse_code}}^FS
^FO430,110^A0N,36,36^FDPasillo {{aisle}}^FS
^FO430,160^A0N,36,36^FDRack {{rack}}^FS
^FO430,210^A0N,36,36^FDNivel {{level}}^FS
^PQ{{copies}}
^XZ
```

## 5. Printers

Printers are Zebra printers that connect by IP. They are an entity catalog, administered in the database.

| Field | Rule |
|---|---|
| `name` | Required. 2 to 60 characters. Example: "Zebra recepción" |
| `warehouse` | Required. Each printer belongs to one warehouse |
| `ip_address` | Required. A valid IPv4 address. See [validation.md](../rules/validation.md) |
| `port` | Fixed at 9100, the raw printing port of Zebra printers. Not editable |
| `active` | Required. Default `true` |

The API only sends labels to printers in this catalog. It never sends to an address typed by the user, so a label cannot reach an arbitrary host on the network.

## 6. Printing

1. The user chooses a template, the item to print (a product or a location), a printer of their warehouse, and the number of copies. The number defaults to 1.
2. The API fills the placeholders from the item's stored data, using the active version, and checks the result against the rules in section 4.
3. The API opens a connection to the printer's IP on port 9100, sends the ZPL, and closes it. If the printer does not answer within a few seconds, the print fails with `PRINTER_UNREACHABLE`, and nothing is recorded as printed.
4. The print is audited with the template `id` and `version`, the values printed, the printer, the user, and the number of copies.

Products can be printed on any printer the user can access. Locations, on printers of their warehouse.

Printing never changes stock, products or locations.

## 7. Permissions

| Permission | Who |
|---|---|
| `labels.view` | `ADMIN` |
| `labels.manage` | `ADMIN`: templates |
| `printers.view`, `printers.manage` | `ADMIN`: printers and their addresses |
| `labels.print` | `ADMIN`, `OFFICE` (all warehouses); `WAREHOUSE_MANAGER` and `WAREHOUSE_OPERATOR` (their warehouses) |

The five are in [access-control.md](../rules/access-control.md) section 4. All but `labels.print` are sensitive.

Everyone prints except `APP_USER`, confirmed by the owner, 2026-10-13. That role only signs in to the app.

## 8. Open questions

1. **Roll size.** The examples assume a 4 × 2 inch roll at 203 dpi. Confirm it, or send the real size and resolution. The two `^PW` and `^LL` values and the positions change with it.

## 9. API

All under `/api/v1`, with the headers and checks of [identity.md](identity.md) section 1.

### Templates

| Method and path | Permission | Notes |
|---|---|---|
| `GET /label-templates` | `labels.view` | Every template, with `name` in the request's language, `nameEs`, `nameEn`, the active `version` and its `zpl`, and the `placeholders` its key allows |
| `GET /label-templates/{id}` | `labels.view` | One template |
| `GET /label-templates/{id}/versions` | `labels.view` | Every version, newest first: `version`, `zpl`, `createdAt` |
| `POST /label-templates/validate` | `labels.manage` | `key`, `zpl`. Writes nothing. Answers `{ problems: [{ code, token, message }] }`, the report the editor shows as the person types |
| `POST /label-templates` | `labels.manage` | `key`, `nameEs`, `nameEn`, `zpl`. Starts at version 1 |
| `PATCH /label-templates/{id}` | `labels.manage` | `nameEs`, `nameEn`, `zpl`, `active`. A different `zpl` is a new version. The key is not a field (`UNKNOWN_FIELD`) |

A template that breaks section 4 is refused with `400` on `zpl`, and no version is created:

| Code | When | `token` in the report |
|---|---|---|
| `ZPL_BLOCK_INVALID` | Not exactly one `^XA` … `^XZ` block | — |
| `ZPL_COMMAND_NOT_ALLOWED` | A command outside the list, or any `~` command | The command, such as `~JR` |
| `ZPL_PLACEHOLDER_UNKNOWN` | A placeholder its key does not have | The placeholder's name |

A new key has only `{{copies}}` until its data is defined, so it prints fixed text.

### Printers

| Method and path | Permission | Notes |
|---|---|---|
| `GET /printers`, `GET /printers/{id}` | `printers.view` | `name`, `warehouseId`, `warehouseCode`, `ipAddress`, `port` (always 9100), `active` |
| `POST /printers` | `printers.manage` | `name`, `warehouseId` (an active warehouse), `ipAddress` (`IP_ADDRESS_INVALID`). There is no `port` field |
| `PATCH /printers/{id}` | `printers.manage` | `name`, `warehouseId`, `ipAddress`, `active` |

### Printing

| Method and path | Permission | Notes |
|---|---|---|
| `GET /labels/printers` | `labels.print` | The active printers of the warehouses the person prints in (every one with `ALL`). Never their addresses |
| `POST /labels/print` | `labels.print` | `templateKey`, `printerId`, `copies` (1 to 100), and `productId` for `PRODUCT` or `locationId` for `LOCATION`. There is no address field. Answers `{ printerId, templateKey, version, copies }` |

- The printer must be in the person's warehouses (`403` otherwise). A location prints only on a printer of its own warehouse (`LOCATION_NOT_IN_WAREHOUSE`). An unknown or inactive template, printer, product or location is `CATALOG_VALUE_INVALID` on its field.
- The filled label is checked again against section 4 before it is sent. Copies without `{{copies}}` in the template are added as `^PQ` before `^XZ`.
- A printer that does not answer within the timeout (`stockflow.printing.timeout-ms`, 3 seconds) is `PRINTER_UNREACHABLE` (`503`), and nothing is audited.
- **Audit actions:** `LABEL_TEMPLATE_CREATED`, `LABEL_TEMPLATE_UPDATED`, `PRINTER_CREATED`, `PRINTER_UPDATED`, and `LABEL_PRINTED`, on the product, the location, or the template for a label of another key, with the template id, key and version, the values printed, the printer and the copies.
