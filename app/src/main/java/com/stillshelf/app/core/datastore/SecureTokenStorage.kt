package com.stillshelf.app.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class SecureTokenStorage @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val sharedPreferences: SharedPreferences by lazy {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        EncryptedSharedPreferences.create(
            TOKENS_FILE,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    suspend fun saveToken(serverId: String, token: String) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .putString(tokenKey(serverId), token)
                .apply()
        }
    }

    suspend fun getToken(serverId: String): String? = withContext(Dispatchers.IO) {
        sharedPreferences.getString(tokenKey(serverId), null)
    }

    suspend fun clearToken(serverId: String) {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit()
                .remove(tokenKey(serverId))
                .apply()
        }
    }

    private fun tokenKey(serverId: String): String = "token_$serverId"

    private companion object {
        const val TOKENS_FILE = "secure_tokens"
    }
}
