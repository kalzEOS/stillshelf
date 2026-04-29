package com.stillshelf.app.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.stillshelf.app.core.diagnostics.DiagnosticLogManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecureStorageUnavailableException : IllegalStateException(
    "Secure token storage is unavailable on this device."
)

@Singleton
class SecureTokenStorage @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val diagnosticLogManager: DiagnosticLogManager
) {
    @Volatile
    private var encryptedSharedPreferences: SharedPreferences? = null
    private val sharedPreferencesLock = Any()
    private val sessionTokens = mutableMapOf<String, String>()

    private fun getEncryptedSharedPreferences(): SharedPreferences? {
        encryptedSharedPreferences?.let { return it }
        return synchronized(sharedPreferencesLock) {
            encryptedSharedPreferences?.let { return@synchronized it }

            val initialized = initializeEncryptedPreferences()
            encryptedSharedPreferences = initialized
            initialized
        }
    }

    private fun initializeEncryptedPreferences(): SharedPreferences? {
        val masterKeyAlias = runCatching {
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        }.getOrElse { throwable ->
            diagnosticLogManager.logError(
                tag = TAG,
                message = "Unable to obtain master key alias for encrypted token prefs.",
                throwable = throwable
            )
            return null
        }

        return try {
            createEncryptedPreferences(masterKeyAlias)
        } catch (firstError: Throwable) {
            diagnosticLogManager.logWarning(
                tag = TAG,
                message = "Encrypted token prefs failed. Clearing token prefs and retrying once.",
                throwable = firstError
            )
            runCatching { clearEncryptedPreferencesFile() }
            try {
                createEncryptedPreferences(masterKeyAlias)
            } catch (secondError: Throwable) {
                diagnosticLogManager.logWarning(
                    tag = TAG,
                    message = "Encrypted token prefs still failing. Resetting master key and retrying.",
                    throwable = secondError
                )
                runCatching { deleteMasterKey(masterKeyAlias) }
                runCatching { clearEncryptedPreferencesFile() }
                try {
                    createEncryptedPreferences(masterKeyAlias)
                } catch (finalError: Throwable) {
                    diagnosticLogManager.logError(
                        tag = TAG,
                        message = "Unable to initialize encrypted token prefs.",
                        throwable = finalError
                    )
                    null
                }
            }
        }
    }

    private fun createEncryptedPreferences(masterKeyAlias: String): SharedPreferences {
        return EncryptedSharedPreferences.create(
            TOKENS_FILE,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun clearEncryptedPreferencesFile() {
        runCatching {
            context.deleteSharedPreferences(TOKENS_FILE)
        }
    }

    private fun deleteMasterKey(alias: String) {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    suspend fun saveToken(
        serverId: String,
        token: String,
        persistAcrossRestarts: Boolean = true,
        allowInsecureStorage: Boolean = false
    ) {
        saveSecret(
            key = tokenKey(serverId),
            value = token,
            inMemoryKey = serverId,
            persistAcrossRestarts = persistAcrossRestarts,
            allowInsecureStorage = allowInsecureStorage
        )
    }

    suspend fun getToken(serverId: String): String? = getSecret(
        key = tokenKey(serverId),
        inMemoryKey = serverId
    )

    suspend fun clearToken(serverId: String) {
        clearSecret(
            key = tokenKey(serverId),
            inMemoryKey = serverId
        )
    }

    suspend fun saveNamedSecret(
        key: String,
        value: String,
        persistAcrossRestarts: Boolean = true,
        allowInsecureStorage: Boolean = false
    ) {
        saveSecret(
            key = "secret_$key",
            value = value,
            inMemoryKey = "secret_$key",
            persistAcrossRestarts = persistAcrossRestarts,
            allowInsecureStorage = allowInsecureStorage
        )
    }

    suspend fun getNamedSecret(key: String): String? = getSecret(
        key = "secret_$key",
        inMemoryKey = "secret_$key"
    )

    suspend fun clearNamedSecret(key: String) {
        clearSecret(
            key = "secret_$key",
            inMemoryKey = "secret_$key"
        )
    }

    private suspend fun saveSecret(
        key: String,
        value: String,
        inMemoryKey: String,
        persistAcrossRestarts: Boolean,
        allowInsecureStorage: Boolean
    ) {
        withContext(Dispatchers.IO) {
            if (!persistAcrossRestarts) {
                clearPersistedToken(key)
                synchronized(sharedPreferencesLock) {
                    sessionTokens[inMemoryKey] = value
                }
                return@withContext
            }

            val encryptedPrefs = getEncryptedSharedPreferences()
            when {
                encryptedPrefs != null -> {
                    encryptedPrefs.edit()
                        .putString(key, value)
                        .apply()
                    fallbackPreferences().edit()
                        .remove(key)
                        .apply()
                }

                allowInsecureStorage -> {
                    fallbackPreferences().edit()
                        .putString(key, value)
                        .apply()
                }

                else -> throw SecureStorageUnavailableException()
            }

            synchronized(sharedPreferencesLock) {
                sessionTokens[inMemoryKey] = value
            }
        }
    }

    private suspend fun getSecret(
        key: String,
        inMemoryKey: String
    ): String? = withContext(Dispatchers.IO) {
        synchronized(sharedPreferencesLock) {
            sessionTokens[inMemoryKey]
        }?.let { return@withContext it }

        val encrypted = getEncryptedSharedPreferences()?.getString(key, null)
        if (!encrypted.isNullOrBlank()) {
            synchronized(sharedPreferencesLock) {
                sessionTokens[inMemoryKey] = encrypted
            }
            return@withContext encrypted
        }

        val fallback = fallbackPreferences().getString(key, null)
        if (!fallback.isNullOrBlank()) {
            val encryptedPrefs = getEncryptedSharedPreferences()
            if (encryptedPrefs != null) {
                encryptedPrefs.edit()
                    .putString(key, fallback)
                    .apply()
                fallbackPreferences().edit()
                    .remove(key)
                    .apply()
            }
            synchronized(sharedPreferencesLock) {
                sessionTokens[inMemoryKey] = fallback
            }
            return@withContext fallback
        }

        null
    }

    private suspend fun clearSecret(
        key: String,
        inMemoryKey: String
    ) {
        withContext(Dispatchers.IO) {
            clearPersistedToken(key)
            synchronized(sharedPreferencesLock) {
                sessionTokens.remove(inMemoryKey)
            }
        }
    }

    private fun clearPersistedToken(key: String) {
        getEncryptedSharedPreferences()?.edit()
            ?.remove(key)
            ?.apply()
        fallbackPreferences().edit()
            .remove(key)
            .apply()
    }

    private fun tokenKey(serverId: String): String = "token_$serverId"

    private companion object {
        const val TAG = "SecureTokenStorage"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TOKENS_FILE = "secure_tokens"
        const val FALLBACK_TOKENS_FILE = "secure_tokens_fallback"
    }

    private fun fallbackPreferences(): SharedPreferences {
        return context.getSharedPreferences(FALLBACK_TOKENS_FILE, Context.MODE_PRIVATE)
    }
}
