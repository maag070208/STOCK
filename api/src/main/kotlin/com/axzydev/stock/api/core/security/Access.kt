package com.axzydev.stock.api.core.security

// Every endpoint under /api/v1 declares its access with one of these. The API does not start if one declares none.

// No token needed: health, sign-in and token refresh.
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Public

// Any signed-in user on a channel they may use.
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Authenticated

// A signed-in user who holds at least one of these permissions with a scope above NONE.
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresPermission(vararg val anyOf: String)

// Also open to a user whose password change is pending: the session endpoints and the change itself.
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AllowedBeforePasswordChange
