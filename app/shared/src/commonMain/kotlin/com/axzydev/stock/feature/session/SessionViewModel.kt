package com.axzydev.stock.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.axzydev.stock.data.api.ApiError
import com.axzydev.stock.data.api.Authorized
import com.axzydev.stock.data.session.AuthApi
import com.axzydev.stock.data.session.SessionUser
import com.axzydev.stock.data.session.TokenStorage
import com.axzydev.stock.data.session.Tokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Where the person is in the session. The screens read only this.
sealed interface SessionState {
	// Looking for a saved session when the app opens.
	data object Restoring : SessionState

	data class SignedOut(val error: String? = null, val submitting: Boolean = false) : SessionState

	// A temporary password: the person sets their own before anything else.
	data class PasswordChange(
		val user: SessionUser,
		val error: String? = null,
		val fieldErrors: Map<String, String> = emptyMap(),
		val submitting: Boolean = false,
	) : SessionState

	data class SignedIn(val user: SessionUser) : SessionState
}

// Signs in, keeps the session between launches, and ends it. Messages come from the API in the person's language;
// `unreachable` is the app's own message for when the API cannot be reached.
class SessionViewModel(
	private val api: AuthApi,
	private val storage: TokenStorage,
	private val unreachable: () -> String,
) : ViewModel(), Authorized {

	private val mutableState = MutableStateFlow<SessionState>(SessionState.Restoring)
	val state: StateFlow<SessionState> = mutableState.asStateFlow()

	// The access token lives in memory only. The refresh token is also kept in secure storage.
	private var tokens: Tokens? = null

	// One refresh at a time: a refresh token is used once, so calls that expire together share the new tokens.
	private val refreshing = Mutex()

	fun restore() {
		viewModelScope.launch {
			val saved = storage.read() ?: return@launch set(SessionState.SignedOut())
			try {
				val fresh = api.refresh(saved)
				keep(fresh)
				enter(api.me(fresh.token))
			} catch (e: CancellationException) {
				throw e
			} catch (e: ApiError) {
				// The session ended on the server: revoked, expired, or the account was deactivated.
				forget()
				set(SessionState.SignedOut())
			} catch (e: Exception) {
				// The saved session may still be good: it is kept for the next try.
				set(SessionState.SignedOut(error = unreachable()))
			}
		}
	}

	fun signIn(username: String, password: String) {
		set(SessionState.SignedOut(submitting = true))
		viewModelScope.launch {
			try {
				val result = api.login(username.trim(), password)
				keep(Tokens(result.token, result.refreshToken))
				enter(result.user)
			} catch (e: CancellationException) {
				throw e
			} catch (e: ApiError) {
				set(SessionState.SignedOut(error = e.message))
			} catch (e: Exception) {
				set(SessionState.SignedOut(error = unreachable()))
			}
		}
	}

	fun changePassword(currentPassword: String, newPassword: String) {
		val current = mutableState.value as? SessionState.PasswordChange ?: return
		val token = tokens?.token ?: return
		set(current.copy(submitting = true, error = null, fieldErrors = emptyMap()))
		viewModelScope.launch {
			try {
				keep(api.changePassword(token, currentPassword, newPassword))
				enter(api.me(tokens!!.token))
			} catch (e: CancellationException) {
				throw e
			} catch (e: ApiError) {
				val fields = e.details.associate { it.field to it.message }
				set(current.copy(error = if (fields.isEmpty()) e.message else null, fieldErrors = fields))
			} catch (e: Exception) {
				set(current.copy(error = unreachable()))
			}
		}
	}

	override suspend fun <T> withToken(call: suspend (token: String) -> T): T {
		val used = checkNotNull(tokens) { "no session" }
		try {
			return call(used.token)
		} catch (e: ApiError) {
			if (e.status != 401) throw e
		}
		val fresh = refreshing.withLock {
			val current = checkNotNull(tokens) { "no session" }
			// Another call refreshed it while this one waited.
			if (current.token != used.token) return@withLock current
			try {
				api.refresh(current.refreshToken).also(::keep)
			} catch (e: ApiError) {
				// The API ended the session: revoked, expired, or the account was deactivated.
				forget()
				set(SessionState.SignedOut())
				throw e
			}
		}
		return call(fresh.token)
	}

	// The session ends on this device at once. The API is told when it can be reached.
	fun signOut() {
		val ending = tokens
		forget()
		set(SessionState.SignedOut())
		if (ending != null) {
			viewModelScope.launch {
				try {
					api.logout(ending.token, ending.refreshToken)
				} catch (e: CancellationException) {
					throw e
				} catch (e: Exception) {
					// Already signed out here. The refresh token expires on its own on the server.
				}
			}
		}
	}

	private fun enter(user: SessionUser) = set(if (user.mustChangePassword) SessionState.PasswordChange(user) else SessionState.SignedIn(user))

	private fun keep(fresh: Tokens) {
		tokens = fresh
		storage.write(fresh.refreshToken)
	}

	private fun forget() {
		tokens = null
		storage.clear()
	}

	private fun set(state: SessionState) {
		mutableState.value = state
	}
}
