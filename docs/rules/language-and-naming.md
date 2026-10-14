# Language and naming

**Status:** Accepted

## 1. Rule

Everything in the repository is written in **English**:

- File and folder names: `invoice-status.md`, `phases/`. Not `estado-factura.md`, not `fases/`.
- Source code: types, functions, variables, packages.
- Comments, KDoc, and JSDoc.
- Database: schemas, tables, columns, constraints, seed codes.
- API: endpoints, path and query parameters, JSON fields, error codes.
- Documentation prose, commit messages, and branch names.

All code and documentation is English. **User-visible display text has two locales:** Spanish (the default) and English. See [i18n/README.md](../i18n/README.md).

## 2. Display text: Spanish by default, English too

- Every text the user sees has a Spanish (`es`) and an English (`en`) translation.
- Spanish is the default. Each user can choose English on their profile.
- Display text never lives in code. It lives in translation data: catalog labels in the database, and the message and screen catalogs described in [i18n/](../i18n/README.md).
- Keys and codes are English. The values are translated. A new locale can be added later without renaming any key or code.

## 3. Naming conventions

| Element | Convention | Example |
|---|---|---|
| Doc and source file names | kebab-case | `invoice-status.md`, `stock-movement.kt` |
| Kotlin types | PascalCase | `StockMovement` |
| Kotlin functions and properties | camelCase | `applyMovement` |
| Catalog codes | UPPER_SNAKE_CASE | `INVOICE_STATUS`, `OPEN` |
| Permission keys | lowercase `module.action` | `invoices.close` |
| Database tables and columns | snake_case, singular table names | `catalog_item`, `created_at` |
| API endpoints | kebab-case, plural nouns, versioned | `/api/v1/catalog-items` |
| API JSON fields | camelCase | `createdAt` |
| Error codes | UPPER_SNAKE_CASE | `STOCK_INSUFFICIENT` |
| Translation keys | lowercase, dot-separated | `invoice.status.open` |

## 4. Examples

| Element | Correct | Wrong |
|---|---|---|
| Endpoint | `GET /api/v1/invoices` | `GET /api/v1/facturas` |
| Table | `catalog_item` | `articulo_catalogo` |
| Catalog code | `INVOICE_STATUS` / `OPEN` | `ESTADO_FACTURA` / `ABIERTA` |
| Display label | stored as `es` label "Abierta" | shown as "Open" |
| Comment | `// Reserved stock is not available` | `// El stock reservado no cuenta` |
