package com.axzydev.stock.api.core.errors

// A business or validation failure. The envelope is built from its code; args fill the message placeholders.
class ApiException(val code: ErrorCode, vararg val args: Any) : RuntimeException(code.name)
