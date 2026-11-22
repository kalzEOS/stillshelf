package com.stillshelf.app.absstats

import android.content.Context

/** Links credential hashes only after authenticated /api/me verification. Tokens
 * are never persisted here; an optional display username is cached by verified owner.
 * Unverified/offline observations stay isolated by hash.
 */
internal class AbsLocalAccounts(context: Context) {
    private val prefs = context.getSharedPreferences("abs-local-accounts", Context.MODE_PRIVATE)
    @Synchronized fun owner(key: String): String = prefs.getString(key, null) ?: key
    @Synchronized fun aliases(key: String): Set<String> {
        val owner = owner(key)
        return prefs.all.filter { it.key.matches(Regex("[a-f0-9]{64}")) && it.value == owner }.keys + owner + key
    }
    @Synchronized fun verified(key: String) = prefs.contains(key)
    @Synchronized fun displayName(key: String): String? = prefs.getString("name-${owner(key)}", null)
    @Synchronized fun recording(key: String): Boolean {
        val setting = "record-${owner(key)}"
        if (verified(key) || prefs.contains(setting)) return prefs.getBoolean(setting, true)
        // New credentials might belong to a previously opted-out user. Defer
        // retention until verification rather than temporarily enabling it.
        return prefs.all.none { it.key.startsWith("record-") && it.value == false }
    }
    @Synchronized fun clearedAt(key: String) = prefs.getLong("clear-${owner(key)}", 0)
    @Synchronized fun markCleared(key: String) { check(prefs.edit().putLong("clear-${owner(key)}", System.currentTimeMillis()).commit()) }
    @Synchronized fun setRecording(key: String, enabled: Boolean) {
        check(prefs.edit().putBoolean("record-${owner(key)}", enabled).commit())
    }
    @Synchronized fun bind(key: String, server: String, user: String, displayName: String? = null) {
        val owner = absLocalOwner(server, user)
        val previous = prefs.getString(key, null)
        require(previous == null || previous == owner) { "Authenticated account identity changed" }
        // A deferred decision is not a new user's preference. Existing verified
        // users keep their setting; genuinely new users keep the normal default.
        val enabled = prefs.getBoolean("record-$key", true)
        val edit = prefs.edit().putString(key, owner)
        if (!prefs.contains("record-$owner")) edit.putBoolean("record-$owner", enabled)
        displayName?.trim()?.takeIf { it.isNotEmpty() }?.let { edit.putString("name-$owner", it) }
        check(edit.commit())
    }
}
