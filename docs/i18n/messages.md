# Error messages

**Status:** Draft, 2026-10-14. Every error code the specs use. The code is English and never changes. The message is what the user reads, in the locale chosen under [README.md](README.md) section 2.

Messages with `{{name}}` take values from the error's details.

| Code | `es` (default) | `en` |
|---|---|---|
| `FIELD_REQUIRED` | Este campo es obligatorio. | This field is required. |
| `FIELD_TOO_LONG` | El valor supera la longitud máxima permitida. | The value is longer than the maximum allowed. |
| `FORMAT_INVALID` | El formato no es válido. | The format is not valid. |
| `UNKNOWN_FIELD` | La solicitud contiene un campo que no existe. | The request contains a field that does not exist. |
| `DUPLICATE_VALUE` | Ya existe un registro con este valor. | A record with this value already exists. |
| `DATE_IN_FUTURE` | La fecha no puede ser futura. | The date cannot be in the future. |
| `QUANTITY_INVALID` | La cantidad no es válida. | The quantity is not valid. |
| `CATALOG_VALUE_INVALID` | El valor del catálogo no existe o está inactivo. | The catalog value does not exist or is inactive. |
| `EMAIL_INVALID` | El correo electrónico no es válido. | The email address is not valid. |
| `PHONE_INVALID` | El teléfono debe tener 10 dígitos. | The phone number must have 10 digits. |
| `TAX_ID_INVALID` | El RFC no es válido. | The tax ID (RFC) is not valid. |
| `POSTAL_CODE_INVALID` | El código postal debe tener 5 dígitos. | The postal code must have 5 digits. |
| `IP_ADDRESS_INVALID` | La dirección IP no es válida. | The IP address is not valid. |
| `BARCODE_INVALID` | El código de barras no es válido. | The barcode is not valid. |
| `BARCODE_NOT_FOUND` | No se encontró ningún material con ese código. | No material matches that code. |
| `INSUFFICIENT_PERMISSIONS` | No tienes permiso para realizar esta acción. | You do not have permission to perform this action. |
| `POLICY_DENIED` | Una política de la organización no permite esta acción. | An organization policy does not allow this action. |
| `POLICY_ACTION_UNKNOWN` | La acción de la política no existe. | The policy action does not exist. |
| `POLICY_FIELD_UNKNOWN` | La política usa un campo que la acción no expone. | The policy uses a field the action does not expose. |
| `ACCOUNT_DEACTIVATED` | Tu cuenta está dada de baja. | Your account has been deactivated. |
| `ACCOUNT_LOCKED` | La cuenta está bloqueada por demasiados intentos fallidos. Pide a un administrador que la desbloquee. | The account is locked after too many failed attempts. Ask an administrator to unlock it. |
| `TOKEN_REVOKED` | La sesión ya no es válida. Inicia sesión de nuevo. | The session is no longer valid. Sign in again. |
| `PASSWORD_CHANGE_REQUIRED` | Debes cambiar tu contraseña antes de continuar. | You must change your password before continuing. |
| `RATE_LIMITED` | Demasiadas solicitudes. Espera un momento e inténtalo de nuevo. | Too many requests. Wait a moment and try again. |
| `ANTI_LOCKOUT` | El cambio dejaría el sistema sin un administrador activo. | The change would leave the system without an active administrator. |
| `STOCK_INSUFFICIENT` | No hay existencias suficientes. Disponibles: {{available}}. | Not enough stock. Available: {{available}}. |
| `LOCATION_NOT_IN_WAREHOUSE` | La ubicación no pertenece a este almacén. | The location is not in this warehouse. |
| `LOCATION_EMPTY` | La ubicación no tiene este material. | The location does not hold this material. |
| `PRODUCT_NOT_IN_ASSIGNMENT` | Este material no está en tus asignaciones abiertas. | This material is not in your open assignments. |
| `PRODUCT_NOT_IN_ORDER` | Este material no está en la orden. | This material is not in the order. |
| `ORDER_LOCKED` | La orden está validada y ya no se puede cambiar. | The order is validated and can no longer be changed. |
| `IDEMPOTENCY_KEY_REUSED` | Esa clave de idempotencia ya se usó para otra solicitud. | That idempotency key was already used for another request. |
| `PRINTER_UNREACHABLE` | La impresora no responde. Revisa que esté encendida y conectada. | The printer does not respond. Check that it is on and connected. |
| `UNAUTHENTICATED` | Inicia sesión para continuar. | Sign in to continue. |
| `INVALID_CREDENTIALS` | El usuario o la contraseña no son correctos. | The username or password is not correct. |
| `PASSWORD_INVALID` | La contraseña debe tener entre 5 y 72 caracteres. | The password must have 5 to 72 characters. |
| `CURRENT_PASSWORD_WRONG` | La contraseña actual no es correcta. | The current password is not correct. |
| `PASSWORD_REUSED` | La nueva contraseña debe ser distinta de la actual. | The new password must be different from the current one. |
| `SELF_ROLE_CHANGE` | No puedes cambiar tus propios roles ni permisos. Pídelo a otro administrador. | You cannot change your own roles or permissions. Ask another administrator. |
| `SYSTEM_ROLE_PROTECTED` | Un rol del sistema no se puede eliminar, renombrar ni desactivar. | A system role cannot be deleted, renamed or deactivated. |
| `ROLE_IN_USE` | El rol está asignado a usuarios y no se puede eliminar. | The role is assigned to users and cannot be deleted. |
| `SYSTEM_LOCATION_PROTECTED` | La ubicación temporal es del sistema y no se puede desactivar. | The temporary location belongs to the system and cannot be deactivated. |
| `LOCATION_HAS_STOCK` | La ubicación tiene existencias. Muévelas antes de darla de baja. | The location holds stock. Move it before deactivating the location. |
| `IMPORT_FILE_INVALID` | El archivo no es una hoja de Excel (.xlsx) con las columnas del inventario. | The file is not an Excel sheet (.xlsx) with the inventory columns. |
| `IMPORT_FILE_CHANGED` | El archivo no es el que se revisó. Vuelve a revisarlo antes de importar. | The file is not the one that was reviewed. Review it again before importing. |
| `IMPORT_CONTROL_MISMATCH` | La suma de existencias del archivo no coincide con su total de control. | The stock in the file does not add up to its control total. |
| `CONTACT_CHANNEL_REQUIRED` | Indica un correo o un teléfono para el contacto. | Give the contact an email or a phone. |
| `ASSIGNMENT_TOTAL_MISMATCH` | Las cantidades asignadas deben sumar la cantidad de la línea. | The assigned quantities must add up to the line quantity. |
| `ASSIGNMENT_LOCKED` | Ya hay un conteo de esta línea: sus asignaciones ya no se pueden cambiar. | This line has a count: its assignments can no longer change. |
| `INVOICE_STATUS_INVALID` | La factura no está en el estado que permite esta acción. | The invoice is not in the status that allows this action. |
| `INVOICE_LOCKED` | La factura está cerrada y ya no se puede cambiar. | The invoice is closed and can no longer change. |
| `PLACEMENT_EXCEEDS_COUNT` | Lo colocado supera la cantidad contada. | What is placed is more than what was counted. |
| `ZPL_BLOCK_INVALID` | La plantilla debe ser una sola etiqueta: un ^XA al inicio y un ^XZ al final. | The template must be one label: a ^XA at the start and a ^XZ at the end. |
| `ZPL_COMMAND_NOT_ALLOWED` | La plantilla usa un comando ZPL que no está permitido. | The template uses a ZPL command that is not allowed. |
| `ZPL_PLACEHOLDER_UNKNOWN` | La plantilla usa un campo que no existe para este tipo de etiqueta. | The template uses a field this label type does not have. |
| `SCOPE_NOT_SUPPORTED` | El permiso no admite ese alcance. | The permission does not support that scope. |
| `NOT_FOUND` | No se encontró el registro. | The record was not found. |
| `METHOD_NOT_ALLOWED` | Esta operación no está permitida. | This operation is not allowed. |
| `INTERNAL_ERROR` | Ocurrió un error inesperado. Inténtalo de nuevo. | An unexpected error occurred. Try again. |

## Rules

1. **Every code has both messages.** The build fails on a code without a message in either locale (test F0-05).
   This table is the source: `node scripts/error-messages.mjs` writes the API's message files from it, and a test fails when they do not match.
2. **A message never shows the internal reason.** Stack traces, SQL and file paths are never in a message.
3. **Placeholders match.** `{{available}}` appears in both locales.
4. **`ACCESS_DENIED`** is an audit action, not an error code shown to users. It has no message here.
