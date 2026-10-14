# Glossary

**Status:** Draft, 2026-10-14. These are the values stored in the catalogs and the terms the screens use. Keys are English. Each row has the Spanish (default) and the English text.

## Roles

| Key | `es` | `en` |
|---|---|---|
| `ADMIN` | Administrador | Administrator |
| `OFFICE` | Oficinas | Office |
| `WAREHOUSE_MANAGER` | Gerente de almacén | Warehouse manager |
| `WAREHOUSE_OPERATOR` | Almacén | Warehouse operator |
| `APP_USER` | Usuario app | App user |

`WAREHOUSE_OPERATOR` is shown as "Almacén", as the owner named it. The word also names a warehouse, which can confuse the screens. See section 9.

## Invoice status (`INVOICE_STATUS`)

| Key | `es` | `en` |
|---|---|---|
| `OPEN` | Abierta | Open |
| `PENDING` | Pendiente | Pending |
| `IN_ASSIGNMENT` | En asignación | In assignment |
| `CLOSED` | Cerrada | Closed |

## Order status (`ORDER_STATUS`)

| Key | `es` | `en` |
|---|---|---|
| `CREATED` | Creada | Created |
| `FULFILLING` | Surtiendo | Fulfilling |
| `FULFILLED` | Surtida | Fulfilled |
| `VALIDATED` | Validada | Validated |
| `CANCELLED` | Cancelada | Cancelled |

## Units of measure (`UNIT_OF_MEASURE`)

| Key | `es` | `en` |
|---|---|---|
| `PIECE` | Pieza | Piece |
| `METER` | Metro | Meter |
| `KILOGRAM` | Kilogramo | Kilogram |

## Payment terms (`PAYMENT_TERMS`)

| Key | `es` | `en` |
|---|---|---|
| `CASH` | Contado | Cash |
| `NET_15` | 15 días | 15 days |
| `NET_30` | 30 días | 30 days |

## Stock movement types

| Key | `es` | `en` |
|---|---|---|
| `OPENING` | Inventario inicial | Opening inventory |
| `RECEIPT` | Entrada | Receipt |
| `OUT` | Salida | Out |
| `IN_RETURN` | Devolución a stock | Return to stock |

## Label templates

| Key | `es` | `en` |
|---|---|---|
| `PRODUCT` | Etiqueta de producto | Product label |
| `LOCATION` | Etiqueta de ubicación | Location label |

## Domain terms

| Term | `es` | `en` |
|---|---|---|
| Warehouse | Almacén | Warehouse |
| Location | Ubicación | Location |
| Temporary location | Ubicación temporal | Temporary location |
| Aisle | Pasillo | Aisle |
| Rack | Rack | Rack |
| Level | Nivel | Level |
| Material (product) | Material | Material |
| Supplier | Proveedor | Supplier |
| Customer | Cliente | Customer |
| Invoice | Factura | Invoice |
| Order | Orden | Order |
| Due date | Fecha de entrega | Due date |
| Assignment | Asignación | Assignment |
| Count | Conteo | Count |
| Discrepancy | Discrepancia | Discrepancy |
| Placement | Colocación | Placement |
| Reservation | Reserva | Reservation |
| Pick | Surtido | Pick |
| Reassignment | Reasignación | Reassignment |
| Staff template | Plantilla de personal | Staff template |
| Opening inventory | Inventario inicial | Opening inventory |
| Delivery receipt (PDF) | Comprobante | Delivery receipt |
| Printer | Impresora | Printer |
| Kardex | Kardex | Kardex |

## Rules for this glossary

1. A new catalog value is added here in both locales before it is released.
2. The English term is a translation, not a rename. The key stays as it is.
3. Terms with two meanings are written out in full on screen, for example "Almacén (rol)" in the access console. See section 9.

## 9. Open points

- **"Almacén"** is both a role and a warehouse. The owner's word for the role is kept. Confirm whether the role should read "Operador de almacén" on screen.
