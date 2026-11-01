# Plan de mejora de StockFlow: recepción ágil con escaneo

## 1. Resumen ejecutivo

StockFlow ya es un sistema de inventario con una base considerable: administración web, app Android/iOS, API, facturas operativas de proveedor, asignación a operarios, conteo, resolución de diferencias, ubicación, existencias, movimientos inmutables, etiquetas ZPL, órdenes de salida, permisos, auditoría y reportes.

El problema principal no es la falta de módulos ni únicamente el estilo visual. Es que el flujo de entrada está organizado alrededor de **pantallas y estados del sistema**, no alrededor del trabajo físico del operario. La experiencia actual exige cambiar de módulo, esperar a que toda la factura avance y, en algunos casos, volver a la web para desbloquear el trabajo.

La propuesta es **no rehacer la plataforma**, sino rediseñar primero el flujo vertical:

> **Alta de factura → asignación simple → escaneo de material → conteo → escaneo de ubicación → avance parcial y trazable → siguiente material.**

Prioridades:

1. **P0 — Integridad y continuidad:** evitar cierres ambiguos sin inventario, permitir avance por línea, proteger reintentos y conservar las cantidades originales.
2. **P1 — Velocidad operativa:** unir conteo y ubicación en la app, simplificar asignación, hacer que el escaneo entienda el contexto y convertir los listados en bandejas de trabajo.
3. **P2 — Optimización:** códigos alternos, sugerencia de ubicaciones, modo sin conexión más amplio, evidencia documental y otras mejoras posteriores al piloto.

La revisión se basó en el código y la documentación del repositorio. Antes de cerrar el diseño definitivo se recomienda observar el proceso real en piso y probar un prototipo con las personas que capturan y surten.

## 1.1 Estado de implementación (2026-10-31)

La primera tanda ya está en el código. Lo que sigue es el estado de cada punto, con la especificación que quedó actualizada:

| Punto | Estado | Dónde quedó |
|---|---|---|
| Cerrar factura sin inventario | Resuelto: `CLOSED` solo se alcanza ubicando todo; cerrar a mano ya no existe | `InvoiceService.close`, `ReceivingService.advance`; [material-receiving.md](modules/material-receiving.md) sección 8 |
| Una diferencia bloquea toda la factura | Resuelto: cada línea avanza sola; ubicar se permite en `PENDING` y `IN_ASSIGNMENT` para asignaciones resueltas | `ReceivingService.place`, `advance`; `PlacementViewModel` |
| Reintentos duplican movimientos | Resuelto: `Idempotency-Key` obligatorio en conteo y ubicación, con bloqueo por clave antes de leer | `ReceivingController`, `IdempotencyService`; [material-entry.md](modules/material-entry.md) |
| El ajuste reescribe la cantidad facturada | Resuelto: se conserva lo facturado, lo esperado, lo contado y lo aceptado por separado | `V22__receiving_integrity.sql`, `AcceptedQuantity`; [material-receiving.md](modules/material-receiving.md) sección 5 |
| Contar y ubicar separados en la app | Resuelto: la captura termina en “Ubicar ahora” y la ubicación en “Siguiente material” | `CountScreen`, `PlacementScreen`, `App.kt` |
| Ubicar todo el remanente de una vez | Resuelto: se ubica en partes y se continúa con el resto | `PlacementViewModel.continuePlacement` |
| Asignación manual línea por línea | Resuelto en web y API: “Asignar toda la factura” reparte todas las líneas en una acción | `InvoiceWorkDialogs.tsx`, `PUT /invoices/{id}/assignments` |
| Escaneo web que no resuelve códigos de barras | Resuelto: el buscador de materiales usa `GET /products/lookup` (código de barras, código, nombre) | `ProductPicker.tsx`, `productApi.lookup` |
| Listados como estados y no como bandejas | Resuelto en web: chips En recepción / Por ubicar / Completadas / Canceladas, avance por línea y ubicaciones | `InvoicesPage.tsx`, `InvoicePage.tsx` |
| Inicio móvil como menú de módulos | Resuelto: el inicio abre con Contar y Ubicar y cuánto falta en cada uno | `HomeScreen.kt` |
| Facturas que nunca llegan | Resuelto: cancelación con motivo, auditada, antes de contar | `POST /invoices/{id}/cancellation` |

Pendiente de la lista original (fase P2): códigos alternos por proveedor, sugerencia de ubicación por histórico, modo sin conexión más amplio y evidencia documental de la recepción. El resto del documento describe el diagnóstico y el diseño que originó estos cambios.

---

## 2. Lo que conviene conservar

No se propone cambiar de tecnología ni reescribir módulos que ya aportan valor:

- **API en Kotlin/Spring Boot y PostgreSQL:** las reglas de negocio están centralizadas.
- **Web en React y app Kotlin Multiplatform:** cada canal tiene una responsabilidad clara.
- **Movimientos de inventario inmutables y saldos transaccionales:** son una buena base de trazabilidad.
- **Permisos por alcance y auditoría:** necesarios para recuentos, ajustes y ubicaciones.
- **Escáner de cámara en Android/iOS:** ya reconoce Code 128, EAN/UPC y QR.
- **Etiquetas ZPL, diseño por tokens y pruebas automatizadas:** permiten evolucionar el sistema sin improvisar estilos por pantalla.

La inversión debe ir a simplificar el proceso y a corregir reglas que hoy frenan la operación, no a una migración tecnológica.

---

## 3. Cómo funciona hoy y dónde se pierde agilidad

### Flujo actual

1. Oficina crea la factura con número, proveedor, fecha y almacén.
2. En el detalle captura materiales y cantidades.
3. Puede asignar cada línea, abriendo un diálogo y repartiendo cantidades entre operarios.
4. El operario entra a `Entradas → Contar`, escanea el producto, selecciona la asignación si hay varias y captura el conteo.
5. Toda diferencia debe ser ajustada por otra persona.
6. Solo cuando **todas las líneas están asignadas, contadas y sin diferencias pendientes**, la factura pasa a `IN_ASSIGNMENT`.
7. El operario sale de conteo, entra a `Ubicar`, elige nuevamente el material, escanea una o varias ubicaciones y captura cantidades.
8. La app exige que las filas sumen todo el remanente antes de guardar. Al ubicar todas las asignaciones, la factura se cierra.

### Fricción principal

- El trabajo se detiene por factura completa, aunque una línea ya esté lista para ubicarse.
- Contar y ubicar son dos acciones separadas dentro del menú, no un recorrido continuo.
- La asignación fina por línea es obligatoria incluso cuando toda la factura iría a una sola persona.
- El escaneo identifica productos, pero no guía por sí solo el siguiente paso del proceso.
- La web prioriza una tabla uniforme, pero no muestra bien las bandejas de trabajo, bloqueos y ubicaciones.
- La app dedica mucho espacio a la cabecera y a la navegación por módulos, en lugar de mostrar el trabajo pendiente y la acción principal.

---

## 4. Hallazgos y puntos de mejora

### P0 — Corregir antes del piloto

#### 4.1 “Cerrar factura” no significa necesariamente que el material ingresó

La acción manual permite cerrar una factura con líneas y sin conteos. Esa operación cambia el estado a `CLOSED`, pero no crea movimientos de entrada; incluso puede cerrar trabajo asignado que todavía no se cuenta.

- Evidencia: `api/src/main/kotlin/com/axzydev/stock/api/modules/entries/InvoiceService.kt:116`.
- Evidencia de la acción visible: `web/src/pages/invoices/InvoicePage.tsx:155`.
- Riesgo: una factura puede verse terminada sin existencias ni ubicación asociada.

**Propuesta:** separar claramente estos resultados:

- **Completada:** toda cantidad aceptada quedó contada y ubicada.
- **Cancelada / no recibida:** requiere motivo y permiso; no genera inventario.
- **Solo documentada**, si el negocio realmente la necesita: debe tener un nombre y métricas distintos de “surtida” o “completada”.

No debe existir un cierre genérico que mezcle esos significados.

#### 4.2 Una línea bloquea la ubicación de toda la factura

La API exige el estado global `IN_ASSIGNMENT` para ubicar. Ese estado solo se alcanza cuando todas las líneas están asignadas y todos los conteos están resueltos.

- Evidencia: `api/src/main/kotlin/com/axzydev/stock/api/modules/entries/ReceivingService.kt:142` y `:226`.
- Riesgo: material ya contado permanece en el piso por una diferencia o por el trabajo pendiente de otra persona.

**Propuesta:** mover el control de avance a la **línea/asignación**. Una asignación contada y liquidada puede ubicarse aunque otras líneas sigan pendientes. La factura mostrará progreso agregado y se completará cuando todas sus líneas lleguen a un resultado terminal.

#### 4.3 Los reintentos de ubicación pueden duplicar inventario

La creación de facturas sí usa `Idempotency-Key`, pero conteo y ubicación no la reciben en sus endpoints ni en el cliente móvil. Una colocación crea movimientos nuevos en cada llamada; no hay una restricción que haga única una operación repetida.

- Evidencia: `api/src/main/kotlin/com/axzydev/stock/api/modules/entries/ReceivingController.kt:25`.
- Evidencia del movimiento: `api/src/main/kotlin/com/axzydev/stock/api/modules/entries/ReceivingService.kt:166`.
- Evidencia del cliente: `app/shared/src/commonMain/kotlin/com/axzydev/stock/data/receipts/ReceivingApi.kt:127`.
- Riesgo: si se pierde la respuesta y el operario reintenta, puede duplicarse el movimiento y el saldo.

**Propuesta:** exigir una clave por operación de conteo/ubicación, reutilizar el mecanismo de idempotencia existente y reforzarlo con una restricción única en base de datos. La misma clave y el mismo contenido deben devolver la primera respuesta; la misma clave con otro contenido debe rechazarse.

#### 4.4 El ajuste modifica la cantidad original de la factura

Al resolver una diferencia, la API cambia tanto la cantidad esperada de la asignación como la cantidad de la línea de factura.

- Evidencia: `api/src/main/kotlin/com/axzydev/stock/api/modules/entries/ReceivingService.kt:125`.
- Riesgo: la factura deja de representar lo que originalmente decía el documento del proveedor. La auditoría conserva el cambio, pero la consulta operativa pierde esa referencia directa.

**Propuesta:** conservar por separado:

- `invoicedQuantity`: lo facturado por el proveedor, inmutable después de confirmar la captura.
- `expectedQuantity`: lo asignado para recibir.
- `countedQuantity`: lo físicamente contado.
- `acceptedQuantity`: lo aceptado después de resolver la diferencia.
- `placedQuantity`: lo ya ubicado.

El ajuste debe registrar una resolución y una razón, no reescribir el dato original del proveedor.

#### 4.5 La app no aprovecha la ubicación parcial que la API ya permite

La API acepta varias llamadas y valida únicamente que no se exceda lo contado. Sin embargo, el `PlacementViewModel` exige que el total de las filas sea exactamente todo el remanente antes de guardar.

- Evidencia: `app/shared/src/commonMain/kotlin/com/axzydev/stock/feature/receipts/PlacementViewModel.kt:139`.
- Riesgo: se obliga a conocer todas las ubicaciones antes de confirmar cualquiera, justo lo contrario de un proceso ágil por bultos o viajes.

**Propuesta:** guardar cada ubicación o grupo de ubicaciones confirmado, mostrar `ubicado / pendiente` y permitir terminar después en otra llamada, siempre con idempotencia.

#### 4.6 Falta una prueba móvil completa del proceso crítico

Existen pruebas compartidas de ViewModels y pruebas API de recepción, pero las suites móviles visibles no incluyen un recorrido Android/iOS de factura → conteo → diferencia → ubicación parcial → cierre. Las pruebas Android se concentran en acceso, diseño, consulta y órdenes; en iOS tampoco hay pruebas UI de conteo/ubicación.

**Propuesta:** hacer del flujo completo de entradas una prueba de liberación en ambas plataformas, incluyendo reintentos, códigos incorrectos, concurrencia y reanudación del trabajo.

---

### P1 — Mejorar velocidad y claridad

#### 4.7 Asignación demasiado detallada para el caso común

Cada línea se asigna desde un diálogo y las partes deben sumar exactamente la cantidad de la línea.

- Evidencia: `web/src/features/invoice/InvoiceDialogs.tsx:11`.
- Mejora: ofrecer primero **“Asignar toda la factura a…”** o “Asignar líneas seleccionadas”. Mantener el reparto por cantidades como opción avanzada, no como paso obligatorio para todas las facturas.

Acciones recomendadas:

- Asignación masiva por factura y por selección de líneas.
- Reasignación de trabajo todavía no contado.
- Recordar almacén y último criterio de asignación por persona, no solo durante una sesión si el negocio lo confirma.
- Mostrar carga pendiente por operario para evitar repartos a ciegas.

#### 4.8 El alta rápida web no resuelve realmente códigos de barras

`ProductPicker` busca por código y nombre mediante dos consultas; no consulta el `barcode` ni el endpoint de lookup. Un lector USB/Bluetooth que escribe un EAN de proveedor no encontrará el producto salvo que el código coincida con el SKU.

- Evidencia: `web/src/entities/product/ui/ProductPicker.tsx:14`.
- Mejora: un campo de captura que acepte escáner de teclado, cámara y captura manual mediante la misma resolución contextual de códigos que usa la app.

#### 4.9 Contar y ubicar están separados

Después de guardar un conteo, la app muestra el resultado y “contar otro”. Para ubicar hay que volver al menú y abrir otra página.

- Evidencia: `app/shared/src/commonMain/kotlin/com/axzydev/stock/ui/receipts/CountScreen.kt:141`.
- Evidencia de módulos separados: `app/shared/src/commonMain/kotlin/com/axzydev/stock/ui/nav/Modules.kt:45`.
- Mejora: después del conteo liquidado, mostrar **“Ubicar ahora”** y **“Siguiente material”**. El operario no debe regresar al inicio para continuar la misma tarea.

#### 4.10 Las diferencias detienen el proceso y su resolución queda fuera del recorrido móvil

La pantalla móvil de diferencias solo muestra filas; no contiene la acción de ajuste que sí existe en la web.

- Evidencia: `app/shared/src/commonMain/kotlin/com/axzydev/stock/ui/receipts/ReceivingScreens.kt:82`.
- Evidencia del ajuste web: `web/src/pages/invoices/DiscrepanciesPage.tsx:33`.
- Mejora: registrar la diferencia sin bloquear las demás líneas, notificar al responsable y permitir resolver desde el canal que su rol realmente utilice. Hay que decidir si el gerente tendrá acceso móvil; hoy la documentación de roles lo define principalmente como usuario web.

#### 4.11 El detalle web no responde bien “¿dónde quedó?”

La tabla contiene esperado, contado y ubicado, pero no las ubicaciones registradas aunque la API ya las devuelve. Al dividir una línea entre operarios también se repiten datos y se mezcla la lectura de producto y responsable.

- Evidencia: `web/src/pages/invoices/InvoicePage.tsx:94`.
- Mejora: una fila principal por línea con progreso y responsables; expansión para ver asignaciones, diferencias y `ubicación + cantidad + fecha + persona`.

#### 4.12 Los listados no funcionan como bandejas de trabajo

La lista de facturas filtra por todas, abiertas y cerradas. “Abiertas” agrupa situaciones operativas muy diferentes: por asignar, por contar, por resolver y por ubicar.

- Evidencia: `web/src/pages/invoices/InvoicesPage.tsx:101`.
- La página de inicio web solo muestra saludo y descripción: `web/src/pages/home/HomePage.tsx:5`.

**Propuesta:** reemplazar el inicio pasivo por accesos según rol:

- Por asignar.
- En conteo.
- Por ubicar.
- Con diferencias.
- Completadas hoy.

Cada fila debe mostrar avance, antigüedad, almacén, responsable y bloqueo actual, no solamente el estado global.

#### 4.13 El diseño móvil prioriza marca y módulos sobre la tarea

La app utiliza una cabecera tipo hero en todas las páginas, una cuadrícula de módulos de 150 dp y una cámara de 240 dp. En una tarea frecuente esto consume espacio que debería dedicarse al producto, la cantidad y la siguiente acción.

- Evidencia: `app/shared/src/commonMain/kotlin/com/axzydev/stock/ui/components/Hero.kt:62`.
- Evidencia: `app/shared/src/commonMain/kotlin/com/axzydev/stock/ui/components/Modules.kt:34`.
- Evidencia: `app/shared/src/commonMain/kotlin/com/axzydev/stock/ui/components/Scanner.kt:21`.

**Propuesta:** conservar la identidad en acceso e inicio, pero usar una cabecera compacta en las pantallas operativas. El inicio del operario debe ser una cola de trabajo con conteos y una acción dominante **“Escanear / Continuar recepción”**, no una cuadrícula de catálogos.

#### 4.14 Cada escaneo vuelve a descargar demasiado trabajo

Para localizar una asignación, el conteo consulta nuevamente `/receipts/mine`. En el servidor ese método obtiene todas las asignaciones históricas del operario antes de filtrar las facturas activas.

- Evidencia: `app/shared/src/commonMain/kotlin/com/axzydev/stock/feature/receipts/CountViewModel.kt:152`.
- Evidencia: `api/src/main/kotlin/com/axzydev/stock/api/modules/entries/ReceivingService.kt:71`.
- Riesgo: mayor latencia y consumo de datos conforme crece el historial.

**Propuesta:** consulta del lado del servidor solo para trabajo vigente, filtrada por almacén, estado y producto, con paginación y una respuesta resumida para la app.

#### 4.15 La sincronización por factura puede serializar a los operarios

Cada conteo, ajuste y ubicación bloquea la fila de la factura completa.

- Evidencia: `api/src/main/kotlin/com/axzydev/stock/api/modules/entries/ReceivingService.kt:216`.

La protección es comprensible, pero al permitir avance independiente por línea conviene revisar el alcance del bloqueo. La solución debe conservar atomicidad y pruebas de concurrencia; no se trata simplemente de quitar el candado.

---

### P2 — Incorporar después de estabilizar el flujo

- **Varios códigos por producto:** SKU interno, EAN/UPC del proveedor y códigos por empaque; hoy existe un único campo de barcode.
- **Sugerencia de ubicación:** ubicación fija del producto, ubicación donde ya existe o última utilizada. La sugerencia nunca debe sustituir la confirmación por escaneo.
- **Operación con conectividad intermitente:** cola local de operaciones descargadas previamente, estado visible `pendiente de sincronizar` y resolución explícita de conflictos. No mostrar una operación como aplicada hasta que la API la confirme.
- **Accesorios de escaneo:** lectores HID/Bluetooth, linterna, vibración/sonido configurable y escaneo continuo con pausa entre lecturas.
- **Adjuntos de factura y evidencia de diferencias:** foto o PDF, sin convertir el sistema en facturación fiscal.
- **Etiquetas de recepción y, si el proceso lo necesita, tarimas/bultos:** para abrir directamente el contexto de trabajo.
- **Edición controlada de encabezado:** antes del primer conteo y con auditoría.
- **Reglas avanzadas de almacén:** capacidad, cuarentena, lotes o series solo cuando exista una necesidad operativa confirmada.

---

## 5. Flujo objetivo propuesto

### 5.1 Web: capturar y poner trabajo en la bandeja

1. **Crear recepción:** los cuatro datos actuales son suficientes para el caso común.
2. **Capturar líneas:** lector de código o búsqueda manual; cantidad con foco continuo y sin abrir diálogos por producto.
3. **Asignar:** por defecto, toda la factura a un operario. Se puede cambiar a varias personas o repartir líneas seleccionadas.
4. **Acción principal:** `Guardar y enviar a recepción`.
5. **Seguimiento:** avance por línea, diferencias, responsables y ubicaciones en el mismo detalle.

El cierre manual desaparece del camino normal. Las excepciones se expresan como `cancelar línea`, `marcar no recibida` o `resolver diferencia`, siempre con motivo.

### 5.2 App: una recepción, no varios módulos

1. El inicio muestra **Mis tareas** y el botón **Escanear**.
2. Se escanea la recepción o se selecciona una asignada.
3. Se escanea el material:
   - se valida que pertenezca a la tarea;
   - se muestran producto, unidad, esperado, contado y pendiente;
   - la cantidad esperada queda precargada para confirmar o corregir.
4. Si el conteo coincide, se habilita **Ubicar ahora**.
5. Si hay diferencia:
   - se registra el conteo real y el motivo disponible;
   - se notifica al responsable;
   - las demás líneas pueden continuar;
   - solo esa línea espera resolución antes de ubicarse.
6. Se sugiere una ubicación cuando exista una regla confiable y se **escanea la ubicación física** para confirmar.
7. La cantidad a ubicar precarga el remanente, pero permite una entrega parcial.
8. La app confirma visualmente y con vibración/sonido opcional, actualiza `ubicado / pendiente` y ofrece:
   - `Ubicar el resto`;
   - `Siguiente material`;
   - `Terminar por ahora`.
9. Al terminar todas las líneas, la recepción queda completada y su inventario ya está disponible en las ubicaciones escaneadas.

En el caso normal, una línea requiere **dos escaneos —material y ubicación— y una confirmación**, sin regresar al inicio ni buscar dos veces el mismo producto.

### 5.3 Escaneo contextual

Un mismo lector debe interpretar qué se escaneó y qué se espera en ese momento:

- Recepción.
- Producto.
- Ubicación.
- En una fase posterior, tarima o bulto.

Se recomienda un endpoint de resolución con contexto, por ejemplo `POST /scan/resolve`, que reciba valor, almacén, recepción y tipo esperado. Debe devolver el objeto, su descripción, la acción siguiente y un error recuperable. Los códigos actuales deben seguir funcionando; las nuevas etiquetas pueden usar un prefijo o payload tipado para evitar ambigüedades.

### 5.4 Estados que representen trabajo paralelo

El estado único de la factura no debe ocultar que unas líneas se están contando y otras ya se están ubicando.

Estado operativo por línea/asignación:

- `Por asignar`.
- `Por contar`.
- `Por ubicar`.
- `Completada`.
- `Cancelada / no recibida`.

La diferencia es un **bloqueo o incidencia asociada**, no un callejón sin salida para toda la factura.

La cabecera puede mostrar un resumen derivado:

> `En recepción · 8/12 líneas contadas · 5/12 ubicadas · 1 diferencia`

`IN_ASSIGNMENT` debe desaparecer como etiqueta visible: actualmente significa que la asignación terminó y que empieza la ubicación, lo cual resulta confuso.

---

## 6. Propuesta de diseño

### Web: centro de control de recepciones

Conservar los tokens, colores y componentes, pero cambiar la jerarquía de información:

- **Inicio útil por rol:** bandejas y trabajo atrasado; no tarjetas decorativas ni gráficas sin acción.
- **Lista de recepciones:** filtros por siguiente acción, almacén, responsable y antigüedad; contadores reales en cada bandeja.
- **Detalle:** encabezado compacto con proveedor, almacén, recepción, responsable y avance.
- **Barra de captura única:** `Escanear o buscar material → cantidad → agregar`, con foco devuelto al lector.
- **Tabla por línea:** material, facturado, asignado, contado, diferencia, ubicado, pendiente, estado y acciones.
- **Detalle expandible:** operarios, ubicaciones, movimientos y resoluciones sin abandonar la factura.
- **Acciones contextuales:** no mostrar quitar, reasignar o cerrar cuando la API ya sabe que los rechazará; explicar el bloqueo y ofrecer la acción válida.
- **Números legibles:** cantidades alineadas, unidad siempre visible y diferencias con signo, texto y color.

### App: herramienta de piso

- Cabecera compacta durante conteo y ubicación.
- Una acción primaria fija y alcanzable con el pulgar.
- Cámara que se abre solo cuando hace falta y deja visible el contexto esencial.
- Producto, cantidad y ubicación con tipografía grande; notas y metadatos fuera del camino normal.
- Acciones `Confirmar`, `Corregir`, `Ubicar ahora` y `Siguiente` diferenciadas por jerarquía, no por color únicamente.
- Errores junto al paso que los causa y con recuperación clara: `Producto no asignado`, `Ubicación de otro almacén`, `Ya fue procesado`, `Sin conexión: pendiente de enviar`.
- Estados de carga, vacío, sin conexión, sincronización pendiente y permiso de cámara diseñados como parte del flujo.
- Objetivos táctiles de al menos 48 dp; no reducir el tamaño para hacer caber más controles.

No se recomienda un cambio de marca completo. Se recomienda **menos cabecera, menos navegación y más información de la tarea**.

---

## 7. Cambios técnicos necesarios

### Dominio y API

1. Introducir estado/progreso por línea o asignación y derivar el resumen de la factura.
2. Permitir ubicación por asignación liquidada, independientemente del resto.
3. Separar cantidades facturada, esperada, contada, aceptada y ubicada.
4. Sustituir el cierre ambiguo por resultados explícitos y permisos específicos.
5. Mantener inmutables los movimientos y la actualización atómica de movimiento + saldo + colocación.
6. Revisar el bloqueo por factura con pruebas de dos operarios trabajando en paralelo; proteger la asignación y los saldos sin frenar trabajo independiente.

### Idempotencia y concurrencia

- Claves obligatorias en ubicación y en cualquier comando que pueda repetirse desde la app.
- Reutilización del servicio de idempotencia ya existente para creación de facturas.
- Restricción de base de datos contra duplicados, no solo validación en memoria.
- Versión de asignación o control equivalente para detectar trabajo modificado mientras el dispositivo estaba desconectado.
- Pruebas reales en PostgreSQL para reintento concurrente, doble toque y respuesta perdida.

### Consultas para la app

- Reemplazar la descarga histórica de `/receipts/mine` por una cola de trabajo vigente filtrada en el servidor.
- Consulta directa de asignaciones abiertas por producto escaneado.
- Respuesta con lo mínimo necesario para la tarea y una actualización incremental después de cada comando.
- Caché local de la tarea abierta para no perder el formulario al rotar, bloquear o reabrir el teléfono.

### Migración de datos

El cambio de cantidades y estados requiere una migración cuidadosa:

- Rellenar cantidades nuevas desde los datos actuales cuando sean confiables.
- Reconstruir la cantidad original desde auditoría cuando sea posible.
- Marcar para conciliación los registros históricos cuyo original no pueda probarse; no inventar valores.
- Mantener compatibilidad temporal de lectura mientras se actualizan web y app.
- Actualizar la documentación funcional y los estados visibles junto con el cambio.

---

## 8. Plan de entrega por incrementos

Los tamaños son relativos y deben convertirse en calendario después de la observación y las decisiones de la fase 0.

| Fase | Resultado entregable | Alcance principal | Tamaño |
|---|---|---|---|
| **0. Validación operativa** | Flujo confirmado y prototipo navegable | Observar captura y piso, medir tiempos, revisar dispositivos/red, resolver decisiones abiertas, probar el flujo con 3–5 usuarios | S |
| **1. Integridad y desbloqueo** | API segura para avance por línea | Cierres explícitos, cantidades separadas, ubicación independiente, parcialidad, idempotencia, migración y pruebas de concurrencia | M–L |
| **2. Flujo móvil continuo** | Contar y ubicar sin cambiar de módulo | Cola de tareas, recepción contextual, escáner de material/ubicación, `Ubicar ahora`, parciales, feedback y reanudación | M–L |
| **3. Puesto web de recepción** | Captura, asignación y supervisión ágiles | Escaneo web, asignación masiva, bandejas por acción, detalle con ubicaciones y acciones contextuales | M |
| **4. Resiliencia y piloto** | Operación medida en un almacén | Consultas optimizadas, lectores físicos, manejo de conectividad, pruebas Android/iOS, capacitación y piloto controlado | M–L |
| **5. Optimización posterior** | Mejoras basadas en datos del piloto | Alias de códigos, sugerencias avanzadas, adjuntos, bultos, lotes u otras necesidades confirmadas | Variable |

### Estrategia de salida

- Entregar el flujo completo por incrementos, no primero todos los cambios visuales y al final las reglas.
- Probar primero con un almacén y un grupo pequeño de operarios.
- Conservar una ruta de contingencia para consultar y revertir la versión de app durante el piloto.
- No migrar todos los almacenes hasta obtener cero duplicados, conciliación de saldos y tiempos mejores que el proceso actual.

---

## 9. Criterios de aceptación del nuevo flujo

### Reglas de negocio

- Una línea liquidada puede ubicarse aunque otra tenga diferencia o esté sin contar.
- Una ubicación parcial actualiza inventario y remanente; una posterior completa la línea sin duplicarla.
- Reenviar la misma ubicación con la misma clave genera **un solo movimiento**.
- La cantidad original del proveedor permanece consultable después de una diferencia.
- Una recepción completada tiene conciliación entre aceptado, ubicado y movimientos.
- Cancelar o marcar material no recibido exige motivo y nunca se presenta como inventario surtido.
- Dos operarios pueden trabajar sobre la misma recepción sin perder actualizaciones ni bloquear líneas independientes.

### Experiencia

- El operario pasa de conteo a ubicación sin regresar al menú.
- No necesita volver a buscar el producto para ubicarlo.
- Puede completar una entrega parcial y reanudarla después.
- Puede identificar siempre qué falta, qué está bloqueado y cuál es la siguiente acción.
- El escaneo de un producto no asignado, una ubicación de otro almacén o un código desconocido explica cómo continuar.
- El alta web funciona con lector físico y con búsqueda manual de respaldo.

### Pruebas mínimas de liberación

- Unitarias para estados, cantidades, remanentes, validaciones e idempotencia.
- Integración HTTP/PostgreSQL para transiciones permitidas y rechazadas, permisos, concurrencia y duplicados.
- E2E web para captura con lector, asignación masiva, diferencias y seguimiento de ubicaciones.
- E2E Android e iOS para recepción → conteo → diferencia en otra línea → ubicación parcial → finalización.
- Prueba de respuesta perdida y reintento sin duplicar inventario.
- Prueba con cámara y con el lector físico elegido, en condiciones reales de iluminación y Wi-Fi.

---

## 10. Métricas para saber si realmente mejoró

Medir primero el proceso actual y comparar con el piloto:

- Tiempo desde factura creada hasta enviada a recepción.
- Tiempo desde asignación hasta primer conteo y hasta completar ubicación.
- Tiempo medio por línea y número de interacciones por línea.
- Tiempo de resolución de diferencias.
- Porcentaje de líneas que requieren búsqueda manual o ayuda.
- Latencia del escaneo hasta mostrar el producto/ubicación.
- Cantidad de operaciones pendientes de sincronizar y errores de red.
- Diferencias entre aceptado, ubicado y saldo generado.
- Recepciones completadas con diferencia abierta o sin ubicación: objetivo **0**.
- Movimientos duplicados por reintento: objetivo **0**.

Metas iniciales para validar en el piloto, no promesas sin línea base:

- Caso normal: **2 escaneos + 1 confirmación por línea**.
- Respuesta visible del escaneo: **menos de 1 segundo en la red del almacén** para el percentil 95.
- **100%** del inventario recibido con producto, cantidad, ubicación, persona y fecha trazables.

---

## 11. Decisiones que hay que confirmar con el negocio

1. **Cierre sin inventario:** ¿existe un caso real de registrar una factura solo documentalmente o toda factura confirmada debe pasar por recepción y ubicación?
2. **Asignación:** ¿el caso común es una persona por factura, por línea o por bulto? ¿Quién puede reasignar?
3. **Diferencias:** ¿qué resoluciones son válidas: aceptar real, devolver excedente, dejar faltante, dañado o cuarentena? ¿Quién las resuelve y desde qué dispositivo?
4. **Dispositivos y red:** ¿se usarán teléfonos con cámara, lectores Bluetooth/USB o ambos? ¿Hay zonas sin Wi-Fi?
5. **Ubicación:** ¿existen ubicaciones fijas por producto o reglas que la app pueda sugerir con seguridad?
6. **Códigos:** ¿basta el SKU interno o deben convivir códigos de proveedor y empaques?
7. **Terminología:** confirmar si en la operación “surtir” significa contar/recibir, ubicar o ambos, y usar ese lenguaje de forma consistente en web y app.

Estas respuestas cambian la configuración del flujo, pero no la prioridad de corregir idempotencia, cierres ambiguos y bloqueo global.

---

## 12. Recomendación final

El mayor retorno está en construir un **flujo de recepción continuo y paralelo por línea**, no en agregar más pantallas ni hacer un rediseño cosmético.

El orden recomendado es:

1. Asegurar que cada cierre, ajuste y movimiento tenga un significado correcto y no pueda duplicarse.
2. Permitir que el material avance sin esperar a toda la factura.
3. Llevar al operario por `escanear → confirmar → ubicar → siguiente` dentro de una sola tarea.
4. Convertir la web en una herramienta de asignación y supervisión con trabajo pendiente visible.
5. Pulir densidad, cabeceras y feedback visual con pruebas en el almacén.
6. Agregar capacidades avanzadas solo después de medir el piloto.

Con ese enfoque se conserva la inversión actual y se ataca directamente lo que hoy hace que el diseño y los flujos no terminen de convencer.
