package com.stillshelf.app.core.datastore

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
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
    @param:ApplicationContext private val context: Context
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
            Log.e(TAG, "Unable to obtain master key alias for encrypted token prefs.", throwable)
            return null
        }

        return try {
            createEncryptedPreferences(masterKeyAlias)
        } catch (firstError: Throwable) {
            Log.w(TAG, "Encrypted token prefs failed. Clearing token prefs and retrying once.", firstError)
            runCatching { clearEncryptedPreferencesFile() }
            try {
                createEncryptedPreferences(masterKeyAlias)
            } catch (secondError: Throwable) {
                Log.w(TAG, "Encrypted token prefs still failing. Resetting master key and retrying.", secondError)
                runCatching { deleteMasterKey(masterKeyAlias) }
                runCatching { clearEncryptedPreferencesFile() }
                try {
                    createEncryptedPreferences(masterKeyAlias)
                } catch (finalError: Throwable) {
                    Log.e(TAG, "Unable to initialize encrypted token prefs.", finalError)
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
        withContext(Dispatchers.IO) {
            val key = tokenKey(serverId)
            if (!persistAcrossRestarts) {
                clearPersistedToken(key)
                synchronized(sharedPreferencesLock) {
                    sessionTokens[serverId] = token
                }
                return@withContext
            }

            val encryptedPrefs = getEncryptedSharedPreferences()
            when {
                encryptedPrefs != null -> {
                    encryptedPrefs.edit()
                        .putString(key, token)
                        .apply()
                    fallbackPreferences().edit()
                        .remove(key)
                        .apply()
                }

                allowInsecureStorage -> {
                    fallbackPreferences().edit()
                        .putString(key, token)
                        .apply()
                }

                else -> throw SecureStorageUnavailableException()
            }

            synchronized(sharedPreferencesLock) {
                sessionTokens[serverId] = token
            }
        }
    }

    suspend fun getToken(serverId: String): String? = withContext(Dispatchers.IO) {
        synchronized(sharedPreferencesLock) {
            sessionTokens[serverId]
        }?.let { return@withContext it }

        val key = tokenKey(serverId)
        val encrypted = getEncryptedSharedPreferences()?.getString(key, null)
        if (!encrypted.isNullOrBlank()) {
            synchronized(sharedPreferencesLock) {
                sessionTokens[serverId] = encrypted
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
                sessionTokens[serverId] = fallback
            }
            return@withContext fallback
        }

        null
    }

    suspend fun clearToken(serverId: String) {
        withContext(Dispatchers.IO) {
            val key = tokenKey(serverId)
            clearPersistedToken(key)
            synchronized(sharedPreferencesLock) {
                sessionTokens.remove(serverId)
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
