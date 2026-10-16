# The anatomy of a screen

**Status:** Accepted, 2026-10-15. The invoice screens of F8 are the reference: what they do, every screen does. This is the
recipe; [web.md](web.md) section 8 is the rule it comes from, and this document is what a screen is checked against.

A screen is not designed twice. There are five shapes — a list, a record, a report, a console and a form — and each one
has an order that is already decided. Copy the shape, fill it with the module's data.

## 1. The frame, in every screen

```
Inicio › Sección › el registro                     [ estado ]  [ la acción que lo cambia ]
El número o el código del registro
El nombre, o la frase que dice qué es
┌──────────────────────────────────────────────────────────────────────────────┐
│ PROVEEDOR            FECHA              ALMACÉN            MATERIALES        │   ← la tira de datos
│ PR-… · Aceros        15 oct 2026        W-…                1 línea · 100 pz   │
└──────────────────────────────────────────────────────────────────────────────┘
```

| Pieza | Regla |
|---|---|
| **Migas** | `trail(home, navigate, …)`: Inicio › sección › registro. La última es texto; las anteriores navegan con el router. Nunca falta. |
| **Título** | El código o el número (`FF-187699C31A`), o el nombre del listado (`Facturas`). |
| **Descripción** | El nombre del registro, o la frase del listado. Nunca "código · nombre" en el título. |
| **Acciones** | El **estado** y la **acción que lo cambia**, juntos, arriba a la derecha (Cerrar factura, Validar orden). Los demás botones de la pantalla van dentro de su tarjeta, cerca de lo que hacen. |
| **Tira de datos** | `FactStrip`: etiqueta en mayúsculas pequeñas y valor en negrita, cuatro por fila (dos si una es larga). Sustituye a las tarjetas altas de indicadores y al "resumen" repetido sobre la tabla. |
| **Tarjetas** | `ITCard` por sección, con su título. Una sección que cabe en la tira no lleva tarjeta. |
| **Aire al final** | Lo pone el layout (`.sf:pb-12` en el contenido). Ninguna pantalla lo repite ni lo quita. |
| **Solo el kit** | `div`, `span`, `p`, `table`, `td`… no existen aquí (web.md 8.4). `ITStack`, `ITFlex`, `ITGrid`, `ITCard`, `ITText` y el resto del kit. |

## 2. Lista (Facturas, Materiales, Órdenes, Almacenes, Usuarios, Catálogos, Proveedores, Clientes, Impresoras, Bitácora, Diferencias, Plantillas de etiqueta)

1. Migas, título, descripción y **una** acción principal ("Nueva factura").
2. Los filtros que cambian la lista, en **chips** y en la URL (`/invoices?status=OPEN`): se puede compartir y recargar.
3. Una tabla, `ITDataTable` si la lista es del servidor o `PlainTable` si la pantalla ya la tiene, **siempre** con las
   props de web.md 8.7 (25 filas, 25/50/100, fijo, compacto, virtualizado a 400 px, filas de 50).
4. Las acciones de la fila son **iconos** con su nombre en `ariaLabel`/`title`.
5. Las columnas con datos llevan filtro y orden; las de acciones, ninguno.
6. `showViewToggle={false}`: la vista de tarjetas no se usa.
7. El listado que crece sin límite (impresoras, almacenes, socios) tiene columnas **buscables**.

Referencia: [`InvoicesPage.tsx`](../../web/src/pages/invoices/InvoicesPage.tsx).

## 3. Registro (Factura, Orden, Material, Socio, Almacén, Plantilla de etiqueta, Inventario inicial)

1. El marco de la sección 1, con la tira de datos.
2. **Una sola tabla** para todo lo que el registro tiene por línea. Si dos cosas describen la misma fila, son columnas de
   esa fila — no dos tablas.
3. **Una fila por cosa, y el trabajo en la fila que lo muestra**: la línea sin asignar es una fila que dice "Sin asignar",
   con las mismas acciones que las demás.
4. Los totales y el resumen van en la tira, no repetidos encima de la tabla.
5. Lo que se captura (la barra de material, la línea de una orden) va en una tarjeta **antes** de la tabla.
6. Un registro que fluye (factura → asignar → contar → colocar) muestra el siguiente paso en el mismo sitio donde está el
   estado: nunca manda a otra pantalla para lo que cabe aquí.
7. Los datos que no cambian (fechas, quién creó) van en la tira, no en tarjetas propias.

Referencia: [`InvoicePage.tsx`](../../web/src/pages/invoices/InvoicePage.tsx).

## 4. Reporte (Existencias, Kardex, Movimientos, Órdenes, Facturas)

1. Migas `Inicio › Reportes › <reporte>` y el título del reporte con su descripción.
2. Los filtros del reporte, en una tarjeta (`ITCard`), y **los del último pedido** guardados para la exportación.
3. La exportación (Excel y PDF) vive en las acciones de la cabecera y manda los mismos filtros.
4. La tabla, con las props de web.md 8.7, y los totales del reporte en la tira o al pie.
5. Nada de gráficas que no se hayan pedido: primero el número.

Referencia: [`StockReportPage.tsx`](../../web/src/pages/reports/StockReportPage.tsx).

## 5. Consola (Acceso: matriz, roles, políticas, pantallas)

1. Migas y título; las pestañas (`ITTabs`) son las secciones de la consola, y cada una es una pantalla dentro de la misma.
2. Una tabla por sección, con las props de web.md 8.7. La tabla que se lee entera (qué ve cada rol) no se esconde tras la
   altura virtualizada sin decirlo.
3. Los cambios sin guardar se ven ("3 cambios sin guardar"): una consola no guarda sola lo que no dijo que guardaría.

Referencia: [`AccessPage.tsx`](../../web/src/pages/access/AccessPage.tsx).

## 6. Formulario y modal (Nueva factura, Asignar, Ajustar, Nuevo material, Nuevo socio, Imprimir etiqueta…)

1. Un modal es `FormDialog`: `ITDialog` con el `<form role="dialog">` de la app dentro, **una** acción primaria y
   `Cancelar`.
2. Etiquetas cortas: el sustantivo ("Número", "Proveedor"), no una frase.
3. Cuatro campos como mucho en el alta rápida; lo demás se captura en el registro, en su tarjeta.
4. Guardar que continúa el flujo lleva al siguiente paso (la factura nueva abre su captura de materiales).
5. Lo que el formulario necesita se dice **mientras se llena** ("Faltan 50 por asignar"), y su guardar sigue cerrado hasta
   que cuadre; un campo obligatorio vacío toma el foco en lugar de mostrar un error que nadie pidió.
6. El teclado no sale del modal: Tab da la vuelta dentro del diálogo del kit, Esc cierra y el foco vuelve al botón que lo
   abrió.

Referencia: [`NewInvoiceDialog.tsx`](../../web/src/features/invoice/NewInvoiceDialog.tsx) y
[`SharesEditor.tsx`](../../web/src/features/invoice/SharesEditor.tsx).

## 7. La lista de comprobación, pantalla por pantalla

Antes de dar una pantalla por terminada:

- [ ] Miga en `ITPage`, que empieza en Inicio, sin repetir la sección cuando la sección es la página.
- [ ] Título y descripción: el código arriba, el nombre abajo.
- [ ] La tira de datos con lo que la persona necesita saber de un vistazo (registros); nada de tarjetas altas.
- [ ] El estado y su acción, juntos, en la cabecera.
- [ ] Una tabla por pantalla, con las props de 8.7, acciones de icono y columnas con filtro y orden.
- [ ] Los filtros de la lista, en la URL.
- [ ] Las tarjetas, una por sección, con su título; sin tarjetas para lo que cabe en la tira.
- [ ] Ningún elemento HTML crudo (lo dice el lint).
- [ ] Los textos, en `es` y `en`, sin nada escrito en el código.
- [ ] La prueba e2e del flujo pasa, y no dejó datos en la base.
- [ ] Aire al final: el layout lo pone; la pantalla no lo quita.
