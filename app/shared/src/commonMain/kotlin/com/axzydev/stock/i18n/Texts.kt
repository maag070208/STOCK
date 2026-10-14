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
}

fun textFor(key: Text, language: Language): String =
	when (language) {
		Language.ES -> when (key) {
			Text.SIGN_IN_TITLE -> "Iniciar sesión"
			Text.USERNAME -> "Usuario"
			Text.PASSWORD -> "Contraseña"
			Text.SIGN_IN -> "Entrar"
			Text.CHANGE_PASSWORD_TITLE -> "Cambia tu contraseña"
			Text.CHANGE_PASSWORD_HINT -> "Tu contraseña es temporal. Escribe una nueva de 10 a 72 caracteres."
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
		}
		Language.EN -> when (key) {
			Text.SIGN_IN_TITLE -> "Sign in"
			Text.USERNAME -> "Username"
			Text.PASSWORD -> "Password"
			Text.SIGN_IN -> "Sign in"
			Text.CHANGE_PASSWORD_TITLE -> "Change your password"
			Text.CHANGE_PASSWORD_HINT -> "Your password is temporary. Type a new one of 10 to 72 characters."
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
		}
	}
