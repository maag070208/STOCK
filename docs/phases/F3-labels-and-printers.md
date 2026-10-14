# F3: Labels, printers and lookup

**Goal:** the system prints product and location labels on Zebra printers over the network, and the app can look up a scanned value.

**Depends on:** F2 (products and locations exist to print and to look up).

**Permissions added:** `labels.view`, `labels.manage`, `labels.print`, `printers.view`, `printers.manage`.

**Runs in parallel with:** F4.

## Specs

- [labels.md](../modules/labels.md): template catalog, printers, printing.
- [barcode-scanning.md](../modules/barcode-scanning.md): lookup order and errors.
- [validation.md](../rules/validation.md): barcode and IP address rules.

## Deliverables

### API

- **Label templates.** Catalog with `id`, `key`, `name`, `zpl`, `version`, `active`. Admin only (`labels.manage`).
- **Template validation at save.** One `^XA` … `^XZ` block. Only the allowed ZPL commands. Only the placeholders of the template's key, plus `{{copies}}`. Any other command is rejected, including `~`, `^DF`, `^XF`, `^ID`, `^JU` and `^JR`.
- **Versioning.** Each change increments `version`. Printing uses the active version.
- **Printers.** Catalog with name, warehouse, IP address and port 9100 (fixed). IPv4 validation.
- **Printing.** Fills the placeholders with sanitized values, checks the ZPL against the rules, and sends it to the printer's address over TCP port 9100. Copies from 1 to 100. A timeout of a few seconds returns `PRINTER_UNREACHABLE` and records nothing as printed. Every print is audited with template, version, values, printer, user and copies.
- **Lookup.** Resolves a scanned value: barcode, then product code, then a name search. Returns the code, name, unit and stock. The lookup never creates anything.
- **Seed templates.** `PRODUCT` and `LOCATION`, with the examples from [labels.md](../modules/labels.md). Their final layout waits for the roll size.

### Web

- Template editor (admin): the ZPL text, a validation report, and the version history.
- Printer admin (admin).
- Print dialog: template, item, printer and copies, which defaults to 1.

### App

- Camera scanner (the stub from F0 becomes the real scanner): reads the value and calls lookup.

## Acceptance criteria

1. A template with `^XZ` missing is rejected with a message in the user's locale, and no version is created.
2. A template containing `~JR` is rejected.
3. A template using `{{rack}}` in the `PRODUCT` key is rejected as an unknown placeholder for that key.
4. A print to a printer that is not in the catalog is rejected. No address typed by the user reaches the network.
5. A print to a Zebra printer on the test network returns a label with the product's barcode, which the app's scanner reads back to the same product.
6. A print to a powered-off printer returns `PRINTER_UNREACHABLE` within the timeout and records no print.
7. Printing 3 copies sends `^PQ3`.
8. A product name containing `^` or `~` prints without those characters.
9. Scanning a product code returns the product. Scanning a name with several matches returns the list, and nothing is chosen automatically.
10. `APP_USER` cannot print and cannot edit templates.

## Open questions

- Roll size and printer resolution, to replace the example layouts (see [labels.md](../modules/labels.md) section 8).
- Who is responsible for the printers' network addresses when a printer changes (see the printers table in [labels.md](../modules/labels.md)).
