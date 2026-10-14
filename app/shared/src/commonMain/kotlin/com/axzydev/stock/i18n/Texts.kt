package com.axzydev.stock.i18n

// Keys are English. Each key has a Spanish and an English value; docs/i18n/messages.md is the register for error messages.
enum class Text {
	SIGN_IN_TITLE,
	USERNAME,
	PASSWORD,
	SIGN_IN,
	CHANGE_PASSWORD_TITLE,
	CHANGE_PASSWORD_HINT,
	CURRENT_PASSWORD,
	NEW_PASSWORD,
	SAVE_PASSWORD,
	SIGN_OUT,
	GREETING,
	SIGNING_IN,
	SHOW_PASSWORD,
	HIDE_PASSWORD,
	FIELD_REQUIRED,
	BRAND_INTRO,
	BRAND_LOADING,
	HEALTH_LABEL,
	HEALTH_CHECKING,
	HEALTH_UP,
	HEALTH_DOWN,
	HEALTH_UNREACHABLE,
	LOOKUP_OPEN,
	LOOKUP_TITLE,
	BACK,
	LOOKUP_FIELD,
	LOOKUP_SEARCH,
	LOOKUP_SEARCHING,
	LOOKUP_CHOOSE,
	PRODUCT_CODE,
	PRODUCT_BARCODE,
	PRODUCT_UNIT,
	PRODUCT_STOCK,
	CAMERA_ASK,
	CAMERA_ALLOW,
	CAMERA_DENIED,
	CAMERA_UNAVAILABLE,
	SIGN_IN_HEADLINE,
	SIGN_IN_HINT,
	CHANGE_PASSWORD_HEADLINE,
	HOME_PROMPT,
	LOOKUP_HINT,
	// The home hub: the modules, each with its own menu of pages.
	MODULES_LABEL,
	MODULES_HINT,
	MODULE_RECEIPTS,
	MODULE_RECEIPTS_HINT,
	MODULE_MATERIALS,
	MODULE_MATERIALS_HINT,
	MODULE_STOCK,
	MODULE_STOCK_HINT,
	MODULE_LOCATIONS,
	MODULE_LOCATIONS_HINT,
	MODULE_ORDERS,
	MODULE_ORDERS_HINT,
	PAGE_MY_RECEIPTS,
	PAGE_MY_RECEIPTS_HINT,
	PAGE_DISCREPANCIES,
	PAGE_DISCREPANCIES_HINT,
	PAGE_LOOKUP,
	PAGE_LOOKUP_HINT,
	PAGE_STOCK,
	PAGE_STOCK_HINT,
	PAGE_WAREHOUSES,
	PAGE_WAREHOUSES_HINT,
	PAGE_ORDERS,
	PAGE_ORDERS_HINT,
	COMING_SOON,
	COMING_SOON_HINT,
	BACK_HOME,
	BACK_MODULES,
	LOADING,
	LOAD_ERROR,
	RETRY,
	REFRESH,
	EMPTY,
	SUPPLIER,
	WAREHOUSE,
	EXPECTED,
	COUNTED,
	PLACED,
	LOCATIONS_COUNT,
	LOCATION_ONE,
	OUT_OF_STOCK,
	ACTIVE,
	INACTIVE,
}

fun textFor(key: Text, language: Language): String =
	when (language) {
		Language.ES -> when (key) {
			Text.SIGN_IN_TITLE -> "Iniciar sesión"
			Text.USERNAME -> "Usuario"
			Text.PASSWORD -> "Contraseña"
			Text.SIGN_IN -> "Entrar"
			Text.CHANGE_PASSWORD_TITLE -> "Cambia tu contraseña"
			Text.CHANGE_PASSWORD_HINT -> "Tu contraseña es temporal. Escribe una nueva de 5 a 72 caracteres."
			Text.CURRENT_PASSWORD -> "Contraseña temporal"
			Text.NEW_PASSWORD -> "Contraseña nueva"
			Text.SAVE_PASSWORD -> "Guardar contraseña"
			Text.SIGN_OUT -> "Cerrar sesión"
			Text.GREETING -> "Hola, {name}"
			Text.SIGNING_IN -> "Entrando…"
			Text.SHOW_PASSWORD -> "Mostrar"
			Text.HIDE_PASSWORD -> "Ocultar"
			Text.FIELD_REQUIRED -> "Este campo es obligatorio."
			Text.BRAND_INTRO -> "Intro de StockFlow"
			Text.BRAND_LOADING -> "Cargando StockFlow"
			Text.HEALTH_LABEL -> "Estado de la API"
			Text.HEALTH_CHECKING -> "Revisando la conexión…"
			Text.HEALTH_UP -> "Conectado a la API"
			Text.HEALTH_DOWN -> "La API responde con error"
			Text.HEALTH_UNREACHABLE -> "Sin conexión con la API"
			Text.LOOKUP_OPEN -> "Buscar material"
			Text.LOOKUP_TITLE -> "Buscar material"
			Text.BACK -> "Volver"
			Text.LOOKUP_FIELD -> "Código, código de barras o nombre"
			Text.LOOKUP_SEARCH -> "Buscar"
			Text.LOOKUP_SEARCHING -> "Buscando…"
			Text.LOOKUP_CHOOSE -> "{count} materiales coinciden. Elige uno."
			Text.PRODUCT_CODE -> "Código"
			Text.PRODUCT_BARCODE -> "Código de barras"
			Text.PRODUCT_UNIT -> "Unidad"
			Text.PRODUCT_STOCK -> "Existencias"
			Text.CAMERA_ASK -> "Permite la cámara para leer códigos de barras."
			Text.CAMERA_ALLOW -> "Permitir cámara"
			Text.CAMERA_DENIED -> "StockFlow no tiene permiso para usar la cámara. Puedes darlo en los ajustes del teléfono, o escribir el código abajo."
			Text.CAMERA_UNAVAILABLE -> "Este dispositivo no tiene cámara. Escribe el código o el nombre abajo."
			Text.SIGN_IN_HEADLINE -> "Entra y lleva al día las *entradas y salidas* del almacén."
			Text.SIGN_IN_HINT -> "Usa el usuario que te dio tu administrador."
			Text.CHANGE_PASSWORD_HEADLINE -> "Elige *tu propia contraseña* para empezar."
			Text.HOME_PROMPT -> "¿Qué vas a hacer hoy?"
			Text.LOOKUP_HINT -> "Lee el código de barras con la cámara, o escribe el código o el nombre."
			Text.MODULES_LABEL -> "Módulos"
			Text.MODULES_HINT -> "Elige un módulo para ver sus opciones."
			Text.MODULE_RECEIPTS -> "Entradas"
			Text.MODULE_RECEIPTS_HINT -> "Recibe y ubica el material de una factura."
			Text.MODULE_MATERIALS -> "Materiales"
			Text.MODULE_MATERIALS_HINT -> "Busca un material por código, nombre o código de barras."
			Text.MODULE_STOCK -> "Existencias"
			Text.MODULE_STOCK_HINT -> "Mira cuánto hay de cada material."
			Text.MODULE_LOCATIONS -> "Ubicaciones"
			Text.MODULE_LOCATIONS_HINT -> "Consulta los almacenes."
			Text.MODULE_ORDERS -> "Órdenes"
			Text.MODULE_ORDERS_HINT -> "Surte los pedidos que te asignen."
			Text.PAGE_MY_RECEIPTS -> "Mis recepciones"
			Text.PAGE_MY_RECEIPTS_HINT -> "Lo que te toca contar y ubicar."
			Text.PAGE_DISCREPANCIES -> "Discrepancias"
			Text.PAGE_DISCREPANCIES_HINT -> "Diferencias por resolver."
			Text.PAGE_LOOKUP -> "Buscar material"
			Text.PAGE_LOOKUP_HINT -> "Código, nombre o código de barras."
			Text.PAGE_STOCK -> "Por material"
			Text.PAGE_STOCK_HINT -> "Existencias por almacén."
			Text.PAGE_WAREHOUSES -> "Almacenes"
			Text.PAGE_WAREHOUSES_HINT -> "Los almacenes que puedes ver."
			Text.PAGE_ORDERS -> "Mis órdenes"
			Text.PAGE_ORDERS_HINT -> "Los pedidos que te asignen."
			Text.COMING_SOON -> "Próximamente"
			Text.COMING_SOON_HINT -> "Esta sección todavía no está lista."
			Text.BACK_HOME -> "Inicio"
			Text.BACK_MODULES -> "Módulos"
			Text.LOADING -> "Cargando…"
			Text.LOAD_ERROR -> "No se pudo cargar."
			Text.RETRY -> "Reintentar"
			Text.REFRESH -> "Actualizar"
			Text.EMPTY -> "No hay nada que mostrar."
			Text.SUPPLIER -> "Proveedor"
			Text.WAREHOUSE -> "Almacén"
			Text.EXPECTED -> "Esperado"
			Text.COUNTED -> "Contado"
			Text.PLACED -> "Ubicado"
			Text.LOCATIONS_COUNT -> "{count} ubicaciones"
			Text.LOCATION_ONE -> "1 ubicación"
			Text.OUT_OF_STOCK -> "Sin existencias"
			Text.ACTIVE -> "Activo"
			Text.INACTIVE -> "Inactivo"
		}
		Language.EN -> when (key) {
			Text.SIGN_IN_TITLE -> "Sign in"
			Text.USERNAME -> "Username"
			Text.PASSWORD -> "Password"
			Text.SIGN_IN -> "Sign in"
			Text.CHANGE_PASSWORD_TITLE -> "Change your password"
			Text.CHANGE_PASSWORD_HINT -> "Your password is temporary. Type a new one of 5 to 72 characters."
			Text.CURRENT_PASSWORD -> "Temporary password"
			Text.NEW_PASSWORD -> "New password"
			Text.SAVE_PASSWORD -> "Save password"
			Text.SIGN_OUT -> "Sign out"
			Text.GREETING -> "Hello, {name}"
			Text.SIGNING_IN -> "Signing in…"
			Text.SHOW_PASSWORD -> "Show"
			Text.HIDE_PASSWORD -> "Hide"
			Text.FIELD_REQUIRED -> "This field is required."
			Text.BRAND_INTRO -> "StockFlow intro"
			Text.BRAND_LOADING -> "Loading StockFlow"
			Text.HEALTH_LABEL -> "API status"
			Text.HEALTH_CHECKING -> "Checking the connection…"
			Text.HEALTH_UP -> "Connected to the API"
			Text.HEALTH_DOWN -> "The API answered with an error"
			Text.HEALTH_UNREACHABLE -> "No connection to the API"
			Text.LOOKUP_OPEN -> "Find material"
			Text.LOOKUP_TITLE -> "Find material"
			Text.BACK -> "Back"
			Text.LOOKUP_FIELD -> "Code, barcode or name"
			Text.LOOKUP_SEARCH -> "Search"
			Text.LOOKUP_SEARCHING -> "Searching…"
			Text.LOOKUP_CHOOSE -> "{count} materials match. Pick one."
			Text.PRODUCT_CODE -> "Code"
			Text.PRODUCT_BARCODE -> "Barcode"
			Text.PRODUCT_UNIT -> "Unit"
			Text.PRODUCT_STOCK -> "Stock"
			Text.CAMERA_ASK -> "Allow the camera to read barcodes."
			Text.CAMERA_ALLOW -> "Allow camera"
			Text.CAMERA_DENIED -> "StockFlow may not use the camera. You can allow it in the phone's settings, or type the code below."
			Text.CAMERA_UNAVAILABLE -> "This device has no camera. Type the code or the name below."
			Text.SIGN_IN_HEADLINE -> "Sign in to keep the warehouse's *ins and outs* up to date."
			Text.SIGN_IN_HINT -> "Use the username your administrator gave you."
			Text.CHANGE_PASSWORD_HEADLINE -> "Choose *a password of your own* to start."
			Text.HOME_PROMPT -> "What will you do today?"
			Text.LOOKUP_HINT -> "Read the barcode with the camera, or type the code or the name."
			Text.MODULES_LABEL -> "Modules"
			Text.MODULES_HINT -> "Pick a module to see its options."
			Text.MODULE_RECEIPTS -> "Inbound"
			Text.MODULE_RECEIPTS_HINT -> "Receive and place a supplier invoice's material."
			Text.MODULE_MATERIALS -> "Materials"
			Text.MODULE_MATERIALS_HINT -> "Find a material by code, name or barcode."
			Text.MODULE_STOCK -> "Stock"
			Text.MODULE_STOCK_HINT -> "See how much of each material there is."
			Text.MODULE_LOCATIONS -> "Locations"
			Text.MODULE_LOCATIONS_HINT -> "Look up the warehouses."
			Text.MODULE_ORDERS -> "Orders"
			Text.MODULE_ORDERS_HINT -> "Pick the orders assigned to you."
			Text.PAGE_MY_RECEIPTS -> "My receipts"
			Text.PAGE_MY_RECEIPTS_HINT -> "What you have to count and place."
			Text.PAGE_DISCREPANCIES -> "Discrepancies"
			Text.PAGE_DISCREPANCIES_HINT -> "Differences to resolve."
			Text.PAGE_LOOKUP -> "Find material"
			Text.PAGE_LOOKUP_HINT -> "Code, name or barcode."
			Text.PAGE_STOCK -> "By material"
			Text.PAGE_STOCK_HINT -> "Stock per warehouse."
			Text.PAGE_WAREHOUSES -> "Warehouses"
			Text.PAGE_WAREHOUSES_HINT -> "The warehouses you may see."
			Text.PAGE_ORDERS -> "My orders"
			Text.PAGE_ORDERS_HINT -> "The orders assigned to you."
			Text.COMING_SOON -> "Coming soon"
			Text.COMING_SOON_HINT -> "This section is not ready yet."
			Text.BACK_HOME -> "Home"
			Text.BACK_MODULES -> "Modules"
			Text.LOADING -> "Loading…"
			Text.LOAD_ERROR -> "It could not be loaded."
			Text.RETRY -> "Retry"
			Text.REFRESH -> "Refresh"
			Text.EMPTY -> "There is nothing to show."
			Text.SUPPLIER -> "Supplier"
			Text.WAREHOUSE -> "Warehouse"
			Text.EXPECTED -> "Expected"
			Text.COUNTED -> "Counted"
			Text.PLACED -> "Placed"
			Text.LOCATIONS_COUNT -> "{count} locations"
			Text.LOCATION_ONE -> "1 location"
			Text.OUT_OF_STOCK -> "Out of stock"
			Text.ACTIVE -> "Active"
			Text.INACTIVE -> "Inactive"
		}
	}
