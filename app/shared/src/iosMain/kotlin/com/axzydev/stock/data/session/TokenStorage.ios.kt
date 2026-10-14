package com.axzydev.stock.data.session

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

actual fun secureTokenStorage(): TokenStorage = KeychainTokenStorage()

// The token is a generic password in the Keychain, readable only on this device once it has been unlocked.
@OptIn(ExperimentalForeignApi::class)
private class KeychainTokenStorage : TokenStorage {

	override fun read(): String? = memScoped {
		val result = alloc<CFTypeRefVar>()
		val status = withQuery { query ->
			CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
			CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
			SecItemCopyMatching(query, result.ptr)
		}
		if (status != errSecSuccess) return null
		val data = CFBridgingRelease(result.value) as? NSData ?: return null
		val bytes = ByteArray(data.length.toInt())
		if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
		bytes.decodeToString()
	}

	override fun write(token: String) {
		clear()
		val bytes = token.encodeToByteArray()
		val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
		val value = CFBridgingRetain(data)
		withQuery { query ->
			CFDictionaryAddValue(query, kSecValueData, value)
			CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
			SecItemAdd(query, null)
		}
		CFRelease(value)
	}

	override fun clear() {
		withQuery { query -> SecItemDelete(query) }
	}

	// The query that names the token: a generic password of this service and account.
	private fun <T> withQuery(block: (CFMutableDictionaryRef?) -> T): T {
		val query = CFDictionaryCreateMutable(null, 0, kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
		val service = CFBridgingRetain(SERVICE)
		val account = CFBridgingRetain(ACCOUNT)
		CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
		CFDictionaryAddValue(query, kSecAttrService, service)
		CFDictionaryAddValue(query, kSecAttrAccount, account)
		try {
			return block(query)
		} finally {
			CFRelease(service)
			CFRelease(account)
			CFRelease(query)
		}
	}

	private companion object {
		const val SERVICE = "com.axzydev.stock"
		const val ACCOUNT = "refresh_token"
	}
}
