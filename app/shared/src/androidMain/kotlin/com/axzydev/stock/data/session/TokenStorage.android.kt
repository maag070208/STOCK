package com.axzydev.stock.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.axzydev.stock.AndroidContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

actual fun secureTokenStorage(): TokenStorage = KeystoreTokenStorage(checkNotNull(AndroidContext.app) { "AndroidContext.app is not set" })

// The token is encrypted with AES-GCM under a key that never leaves the Android Keystore. Only the ciphertext is in the app's files.
private class KeystoreTokenStorage(context: Context) : TokenStorage {

	private val prefs = context.getSharedPreferences("stockflow.session", Context.MODE_PRIVATE)

	override fun read(): String? {
		val stored = prefs.getString(TOKEN, null) ?: return null
		// A token that cannot be decrypted (a restored backup, a new key) is as good as none.
		return runCatching {
			val bytes = Base64.decode(stored, Base64.NO_WRAP)
			val cipher = Cipher.getInstance(TRANSFORMATION)
			cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
			String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
		}.getOrNull()
	}

	override fun write(token: String) {
		val cipher = Cipher.getInstance(TRANSFORMATION)
		cipher.init(Cipher.ENCRYPT_MODE, key())
		val bytes = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
		prefs.edit().putString(TOKEN, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
	}

	override fun clear() {
		prefs.edit().remove(TOKEN).apply()
	}

	private fun key(): SecretKey {
		val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
		(store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
		val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
			.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
			.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
			.setKeySize(256)
			.build()
		return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
	}

	private companion object {
		const val KEYSTORE = "AndroidKeyStore"
		const val ALIAS = "stockflow.session"
		const val TOKEN = "refresh_token"
		const val TRANSFORMATION = "AES/GCM/NoPadding"
		const val IV_SIZE = 12
	}
}
