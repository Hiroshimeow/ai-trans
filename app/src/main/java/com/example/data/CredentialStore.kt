package com.example.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

interface CredentialStore {
    fun saveSecret(alias: String, value: String)
    fun getSecret(alias: String): String?
    fun deleteSecret(alias: String)
}

class EncryptedCredentialStore(context: Context) : CredentialStore {
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "mcp_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun saveSecret(alias: String, value: String) {
        sharedPreferences.edit().putString(alias, value).apply()
    }

    override fun getSecret(alias: String): String? {
        return sharedPreferences.getString(alias, null)
    }

    override fun deleteSecret(alias: String) {
        sharedPreferences.edit().remove(alias).apply()
    }
}
