# F3 tasks: Labels, printers and lookup

Phase: [F3-labels-and-printers.md](../F3-labels-and-printers.md).

**Progress, 2026-10-14:** F3-01 to F3-10 are done in the API (contracts in [labels.md](../../modules/labels.md) section 9 and [barcode-scanning.md](../../modules/barcode-scanning.md) section 3; tests `LabelRulesTest`, `LabelsEndpointTest`, `LookupEndpointTest`). Two changes to the tasks as written: a refused template answers its own code on `zpl` (`ZPL_BLOCK_INVALID`, `ZPL_COMMAND_NOT_ALLOWED`, `ZPL_PLACEHOLDER_UNKNOWN`), not `FORMAT_INVALID`, so the editor can say what is wrong; and the E2E of F3-06 cannot create `TUBO ^~ 1-7/8" X 6 MT`, since a product name refuses `^` and `~` ([products.md](../../modules/products.md)), so the removal is a unit test and the E2E checks the cut to 60 characters. Printers have their own `printers.view` and `printers.manage`, as access-control.md lists them.

F3-11 and F3-12 are done on the web: **Etiquetas** (`/labels`, the template list, and `/labels/{id}`, the editor with the report next to the ZPL, each problem with its lines, and the versions), **Impresoras** (`/printers`), and the print dialog on a material's page and on each location of a warehouse. The dialog shows only the printers of the person's warehouses (a location's, only those of its warehouse) and picks the printer when there is one. The copies field has no browser minimum or maximum, since the kit's field would change 101 into 100 without saying so; the form shows the rule instead. The web suite prints to a fake printer on port 19100, the printing port of the profile `e2e`. F3-13 is done on the app: the scanner (CameraX with ML Kit on Android, AVFoundation on iOS), the lookup screen with the typed fallback, and `GET /products/lookup` through a session that refreshes an expired token. Tests: `LookupViewModelTest`, `SessionViewModelTest` (refresh), `QuantityFormatTest` on both platforms; `android-lookup.test.mjs` on the emulator, where the camera reads a product's Code 128 from the virtual scene and shows the product; `LookupUITests` on the iOS simulator, which has no camera and types the value. There was no scanner stub from F0 to remove. Pending by hand: the Android suite on the phone (it was locked), and the camera of a real phone reading a label printed on a Zebra, which is also acceptance criterion 5.

| ID | Task | Area | After | Owner |
|---|---|---|---|---|
| F3-01 | Label template catalog | API | F1-15 | |
| F3-02 | Template validation: one label, allowed commands | API | F3-01 | |
| F3-03 | Template placeholders per key | API | F3-02 | |
| F3-04 | Template versioning | API | F3-01 | |
| F3-05 | Printer catalog | API | F2-01 | |
| F3-06 | Placeholder sanitizing | API | F3-03 | |
| F3-07 | Print: fill, send over TCP, copies | API | F3-04, F3-05, F3-06 | |
| F3-08 | Print audit | API | F3-07, F1-17 | |
| F3-09 | Barcode lookup | API | F2-06 | |
| F3-10 | Seed label templates | API | F3-03 | |
| F3-11 | Template editor and printers screens | Web | F3-01, F3-05 | |
| F3-12 | Print dialog | Web | F3-07 | |
| F3-13 | App scanner and lookup | App | F3-09 | |

## F3-01 · Label template catalog · API · after F1-15
- Unit (red): a template needs a key in UPPER_SNAKE_CASE; the key cannot change after creation.
- E2E (red): an administrator creates, reads and deactivates a template; a user without `labels.manage` gets `403`.
- Accept: `labels.view` reads and `labels.manage` changes.

## F3-02 · Template validation: one label, allowed commands · API · after F3-01
- Unit (red): a template without `^XZ` is rejected; a template containing `~JR`, `^DF`, `^XF`, `^ID`, `^JU` or `^JR` is rejected; a command outside the allowlist is rejected.
- E2E (red): saving such a template returns `400` with `FORMAT_INVALID` and no version is created.
- Accept: the allowlist is the one in [labels.md](../../modules/labels.md) section 4.

## F3-03 · Template placeholders per key · API · after F3-02
- Unit (red): `PRODUCT` accepts `{{code}}`, `{{name}}`, `{{barcode}}`, `{{unit}}`, `{{copies}}`; `{{rack}}` in `PRODUCT` is rejected.
- E2E (red): a template with `{{rack}}` under `PRODUCT` returns the unknown-placeholder error.
- Accept: `{{copies}}` is allowed in every key.

## F3-04 · Template versioning · API · after F3-01
- Unit (red): each change increments `version` by one; the active version is the one printed.
- E2E (red): two saves produce versions 2 and 3; a print after the second save uses version 3.
- Accept: old versions remain readable.

## F3-05 · Printer catalog · API · after F2-01
- Unit (red): an IP address that is not four octets from 0 to 255 is rejected with `IP_ADDRESS_INVALID`; the port is fixed at 9100.
- E2E (red): an administrator creates a printer; a user without `printers.manage` gets `403`.
- Accept: a printer belongs to one warehouse.

## F3-06 · Placeholder sanitizing · API · after F3-03
- Unit (red): `^`, `~` and `\` are removed from every value; a value is cut to 60 characters; an inch mark and a slash are kept.
- E2E (red): a product named `TUBO ^~ 1-7/8" X 6 MT` prints without the `^` and the `~`.
- Accept: the sanitizer is the only path from data to ZPL.

## F3-07 · Print: fill, send over TCP, copies · API · after F3-04, F3-05, F3-06
- Unit (red): copies from 1 to 100 are accepted, and produce `^PQn`; 0 and 101 are rejected; the sender returns `PRINTER_UNREACHABLE` after the timeout.
- E2E (red): a print to the fake printer on port 9100 receives the ZPL with `^PQ3` for three copies; a print to an address not in the catalog is rejected; a print to a stopped fake printer returns `PRINTER_UNREACHABLE` and records nothing.
- Accept: the API never sends to an address typed by the user.

## F3-08 · Print audit · API · after F3-07, F1-17
- Unit (red): the audit entry carries template id, version, values, printer, user and copies.
- E2E (red): a successful print writes one audit entry; a failed one writes none.
- Accept: printing changes no stock, product or location.

## F3-09 · Barcode lookup · API · after F2-06
- Unit (red): the lookup order is barcode, then code, then name; several name matches return a list and no single product.
- E2E (red): a barcode returns its product; a code returns it too; a name with three matches returns three results; a value with no match returns `BARCODE_NOT_FOUND`.
- Accept: the lookup creates nothing.

## F3-10 · Seed label templates · API · after F3-03
- Unit (red): the seed contains `PRODUCT` and `LOCATION`, each with its placeholders, and passes the validation of F3-02.
- E2E (red): `GET` lists the two templates with their names in the user's locale.
- Accept: the seed layouts are the examples from [labels.md](../../modules/labels.md), marked as examples until the roll size is confirmed.

## F3-11 · Template editor and printers screens · Web · after F3-01, F3-05
- Unit (red): the editor shows the validation report next to the text as the user types.
- E2E (red): Playwright saves a valid template and sees its new version; an invalid one shows the message in the user's locale.
- Accept: only `ADMIN` sees these screens.

## F3-12 · Print dialog · Web · after F3-07
- Unit (red): the copies field defaults to 1 and rejects values outside 1 to 100.
- E2E (red): Playwright prints a product label to the fake printer with 3 copies.
- Accept: the dialog lists only the printers of the user's warehouse.

## F3-13 · App scanner and lookup · App · after F3-09
- Unit (red): the view model turns a scanned value into a lookup request, and shows the candidates when there are several.
- E2E (red): on Android and on iOS, the scanner reads a printed product label and shows the product.
- Accept: a scan never adds a line; the scanner stub from F0 is removed.
