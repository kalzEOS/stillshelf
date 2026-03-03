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
            return context.getSharedPreferences(FALLBACK_TOKENS_FILE, Context.MODE_PRIVATE)
        }

        return runCatching {
            createEncryptedPreferences(masterKeyAlias)
        }.recoverCatching { firstError ->
            Log.w(TAG, "Encrypted token prefs failed. Clearing token prefs and retrying once.", firstError)
            clearEncryptedPreferencesFile()
            createEncryptedPreferences(masterKeyAlias)
        }.recoverCatching { secondError ->
            Log.w(TAG, "Encrypted token prefs still failing. Resetting master key and retrying.", secondError)
            runCatching { deleteMasterKey(masterKeyAlias) }
            clearEncryptedPreferencesFile()
            createEncryptedPreferences(masterKeyAlias)
        }.getOrElse { finalError ->
            Log.e(TAG, "Unable to initialize encrypted token prefs. Using local fallback prefs.", finalError)
            context.getSharedPreferences(FALLBACK_TOKENS_FILE, Context.MODE_PRIVATE)
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
        }
    }

    suspend fun getToken(serverId: String): String? = withContext(Dispatchers.IO) {
        getSharedPreferences().getString(tokenKey(serverId), null)
    }

    suspend fun clearToken(serverId: String) {
        withContext(Dispatchers.IO) {
            getSharedPreferences().edit()
                .remove(tokenKey(serverId))
                .apply()
        }
    }

    private fun tokenKey(serverId: String): String = "token_$serverId"

    private companion object {
        const val TAG = "SecureTokenStorage"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TOKENS_FILE = "secure_tokens"
        const val FALLBACK_TOKENS_FILE = "secure_tokens_fallback"
    }
}
