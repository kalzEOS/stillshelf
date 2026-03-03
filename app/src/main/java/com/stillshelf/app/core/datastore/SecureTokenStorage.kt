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

@Singleton
class SecureTokenStorage @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    @Volatile
    private var sharedPreferences: SharedPreferences? = null
    @Volatile
    private var usingFallbackStorage: Boolean = false
    private val sharedPreferencesLock = Any()

    private fun getSharedPreferences(): SharedPreferences {
        sharedPreferences?.let { return it }
        return synchronized(sharedPreferencesLock) {
            sharedPreferences?.let { return@synchronized it }

            val initialized = initializeEncryptedPreferences()
            sharedPreferences = initialized
            initialized
        }
    }

    private fun initializeEncryptedPreferences(): SharedPreferences {
        val masterKeyAlias = runCatching {
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        }.getOrElse { throwable ->
            Log.e(TAG, "Unable to obtain master key alias. Falling back to local token prefs.", throwable)
            usingFallbackStorage = true
            return fallbackPreferences()
        }

        return try {
            usingFallbackStorage = false
            createEncryptedPreferences(masterKeyAlias)
        } catch (firstError: Throwable) {
            Log.w(TAG, "Encrypted token prefs failed. Clearing token prefs and retrying once.", firstError)
            runCatching { clearEncryptedPreferencesFile() }
            try {
                usingFallbackStorage = false
                createEncryptedPreferences(masterKeyAlias)
            } catch (secondError: Throwable) {
                Log.w(TAG, "Encrypted token prefs still failing. Resetting master key and retrying.", secondError)
                runCatching { deleteMasterKey(masterKeyAlias) }
                runCatching { clearEncryptedPreferencesFile() }
                try {
                    usingFallbackStorage = false
                    createEncryptedPreferences(masterKeyAlias)
                } catch (finalError: Throwable) {
                    Log.e(TAG, "Unable to initialize encrypted token prefs. Using local fallback prefs.", finalError)
                    usingFallbackStorage = true
                    fallbackPreferences()
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

    suspend fun saveToken(serverId: String, token: String) {
        withContext(Dispatchers.IO) {
            getSharedPreferences().edit()
                .putString(tokenKey(serverId), token)
                .apply()
            // If fallback is currently active, mirror into encrypted storage when possible.
            if (usingFallbackStorage) {
                runCatching {
                    val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                    createEncryptedPreferences(masterKeyAlias).edit()
                        .putString(tokenKey(serverId), token)
                        .apply()
                }
            }
        }
    }

    suspend fun getToken(serverId: String): String? = withContext(Dispatchers.IO) {
        val key = tokenKey(serverId)
        val primary = getSharedPreferences().getString(key, null)
        if (!primary.isNullOrBlank()) return@withContext primary

        val fallback = fallbackPreferences().getString(key, null)
        if (!fallback.isNullOrBlank()) {
            getSharedPreferences().edit().putString(key, fallback).apply()
            return@withContext fallback
        }

        val encrypted = runCatching {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            createEncryptedPreferences(masterKeyAlias).getString(key, null)
        }.getOrNull()
        if (!encrypted.isNullOrBlank()) {
            getSharedPreferences().edit().putString(key, encrypted).apply()
            return@withContext encrypted
        }

        null
    }

    suspend fun clearToken(serverId: String) {
        withContext(Dispatchers.IO) {
            val key = tokenKey(serverId)
            getSharedPreferences().edit()
                .remove(key)
                .apply()
            fallbackPreferences().edit()
                .remove(key)
                .apply()
            runCatching {
                val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                createEncryptedPreferences(masterKeyAlias).edit()
                    .remove(key)
                    .apply()
            }
        }
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
