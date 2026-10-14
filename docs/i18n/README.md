# i18n: translations

**Status:** Accepted, 2026-10-14. Replaces the earlier rule that display text is Spanish only. The owner asked for Spanish and English, with Spanish as the default.

## 1. Locales

| Locale | Code | Role |
|---|---|---|
| Spanish | `es` | Default. Used when the user has no preference, and as the fallback |
| English | `en` | Second locale. Every visible text has an English translation |

## 2. Default and choice

- The default locale is **`es`**.
- Each user has a locale preference (`user.locale`), set on their profile. New users start with `es`.
- Pages visible before sign-in (the login screen) use the browser's stored choice, and then `es`.
- The app uses the user's preference, not the device language, so an operator on an English phone still sees Spanish unless they choose English.
- The API picks the message language in this order: the user's preference, then the `Accept-Language` header if it is `es` or `en`, then `es`.

## 3. Keys

- Keys are in English, lowercase, dot-separated: `invoice.status.open`, `error.stock_insufficient`.
- A key is never renamed after it is released. A new meaning gets a new key.
- Every key exists in both locales. A key missing in one locale fails the build.
- Keys built at runtime (`status.${value}`) must be typed, so a missing key cannot compile on the web.

## 4. Placeholders

- Placeholders use `{{name}}`, the same in both locales, with the same names.
- The translation test checks that each placeholder in `es` appears in `en`, and the reverse.
- Numbers, dates and currency use the locale's format. Stored values are never translated.

## 5. Where the text lives

| Text | Where | Source |
|---|---|---|
| Error messages | API, `core/i18n` | [messages.md](messages.md) |
| Document labels (the comprobante of an order) | API, `messages/documents` | [documents.md](documents.md) |
| Catalog values (statuses, units, payment terms) | Database, `catalog_item_label` | [glossary.md](glossary.md) |
| Role and permission names | Seed data and i18n keys | [glossary.md](glossary.md) |
| Label template names | Database, catalog labels | [labels.md](../modules/labels.md) |
| Screens (web and app) | Web namespaces and app resources | Each phase adds its keys |

Each phase adds its keys to this folder before it closes.

## 6. Rules

1. **No text in code.** A visible string that is not a key is a defect, and the lint rule fails it.
2. **Both locales for every active catalog value.** A catalog item cannot be activated without its `es` and `en` labels (see [catalogs.md](../rules/catalogs.md)).
3. **Translations are reviewed.** The `es` text is reviewed by the owner. The `en` text is reviewed by a native English speaker before release.
4. **Dates and numbers are formatted by the locale**, with the same stored data.
5. **Printed labels** are not translated automatically. A label prints in the text of its template. See [labels.md](../modules/labels.md) section 8.

## 7. Files

- [messages.md](messages.md): error codes, Spanish and English.
- [documents.md](documents.md): the labels the API prints on its documents, Spanish and English.
- [glossary.md](glossary.md): roles, statuses, units, movement types, payment terms, label names, and domain terms.
