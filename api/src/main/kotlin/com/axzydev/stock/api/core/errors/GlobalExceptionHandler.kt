package com.axzydev.stock.api.core.errors

import com.axzydev.stock.api.core.validation.ValidationException
import org.slf4j.LoggerFactory
import org.springframework.context.MessageSource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.ErrorResponse as SpringErrorResponse
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import tools.jackson.databind.exc.UnrecognizedPropertyException
import java.util.Locale

@RestControllerAdvice
class GlobalExceptionHandler(private val messages: MessageSource) {

	private val log = LoggerFactory.getLogger(javaClass)

	@ExceptionHandler(ApiException::class)
	fun handleApi(ex: ApiException, locale: Locale): ResponseEntity<ErrorResponse> =
		envelope(ex.code, ex.code.httpStatus, locale, ex.args.map { it.toString() }.toTypedArray())

	// The envelope's own code is the first field's. Every field is in `details`.
	@ExceptionHandler(ValidationException::class)
	fun handleValidation(ex: ValidationException, locale: Locale): ResponseEntity<ErrorResponse> {
		val first = ex.errors.first().code
		val details = ex.errors.map { ErrorDetail(it.field, it.code.name, message(it.code, locale)) }
		return envelope(first, first.httpStatus, locale, details = details)
	}

	@ExceptionHandler(HttpMessageNotReadableException::class)
	fun handleUnreadable(ex: HttpMessageNotReadableException, locale: Locale): ResponseEntity<ErrorResponse> {
		val unknown = generateSequence<Throwable>(ex) { it.cause }.filterIsInstance<UnrecognizedPropertyException>().firstOrNull()
			?: return envelope(ErrorCode.FORMAT_INVALID, ErrorCode.FORMAT_INVALID.httpStatus, locale)
		val detail = ErrorDetail(unknown.propertyName, ErrorCode.UNKNOWN_FIELD.name, message(ErrorCode.UNKNOWN_FIELD, locale))
		return envelope(ErrorCode.UNKNOWN_FIELD, ErrorCode.UNKNOWN_FIELD.httpStatus, locale, details = listOf(detail))
	}

	// A value in the path or the query that is not what the endpoint takes, such as an id that is not a UUID.
	@ExceptionHandler(MethodArgumentTypeMismatchException::class)
	fun handleMismatch(ex: MethodArgumentTypeMismatchException, locale: Locale): ResponseEntity<ErrorResponse> =
		envelope(ErrorCode.FORMAT_INVALID, ErrorCode.FORMAT_INVALID.httpStatus, locale)

	// A unique key the service did not see in time, when two requests race.
	@ExceptionHandler(DataIntegrityViolationException::class)
	fun handleIntegrity(ex: DataIntegrityViolationException, locale: Locale): ResponseEntity<ErrorResponse> {
		log.warn("data integrity violation: {}", ex.mostSpecificCause.javaClass.simpleName)
		return envelope(ErrorCode.DUPLICATE_VALUE, ErrorCode.DUPLICATE_VALUE.httpStatus, locale)
	}

	@ExceptionHandler(Exception::class)
	fun handleUnexpected(ex: Exception, locale: Locale): ResponseEntity<ErrorResponse> {
		// The web framework's own errors carry their status: a path that does not exist, a method the path does not take.
		if (ex is SpringErrorResponse) {
			val status = ex.statusCode.value()
			val code = when (status) {
				404 -> ErrorCode.NOT_FOUND
				405 -> ErrorCode.METHOD_NOT_ALLOWED
				in 400..499 -> ErrorCode.FORMAT_INVALID
				else -> ErrorCode.INTERNAL_ERROR
			}
			if (code != ErrorCode.INTERNAL_ERROR) return envelope(code, status, locale)
		}
		// The detail goes to the log only, never to the client.
		log.error("unhandled error", ex)
		return envelope(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.httpStatus, locale)
	}

	private fun message(code: ErrorCode, locale: Locale, args: Array<String> = emptyArray()): String =
		messages.getMessage(code.messageKey, args, locale)

	private fun envelope(
		code: ErrorCode,
		status: Int,
		locale: Locale,
		args: Array<String> = emptyArray(),
		details: List<ErrorDetail> = emptyList(),
	): ResponseEntity<ErrorResponse> {
		val body = ErrorResponse(
			error = HttpStatus.valueOf(status).reasonPhrase,
			code = code.name,
			message = message(code, locale, args),
			details = details,
		)
		return ResponseEntity.status(status).body(body)
	}
}
