package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "financas_mt_secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        const val KEY_THEME = "theme"
        const val KEY_PRIMARY_COLOR = "primary_color"
        const val KEY_DEFAULT_CURRENCY = "default_currency"
        const val KEY_HIDE_BALANCES = "hide_balances"
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_ENABLED = "pin_enabled"
        const val KEY_LAST_OPENED_DATE = "last_opened_date"
    }

    var theme: String
        get() = prefs.getString(KEY_THEME, "dark") ?: "dark"
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    var primaryColor: String
        get() = prefs.getString(KEY_PRIMARY_COLOR, "#1A73E8") ?: "#1A73E8"
        set(value) = prefs.edit().putString(KEY_PRIMARY_COLOR, value).apply()

    var defaultCurrency: String
        get() = prefs.getString(KEY_DEFAULT_CURRENCY, "MT") ?: "MT"
        set(value) = prefs.edit().putString(KEY_DEFAULT_CURRENCY, value).apply()

    var hideBalances: Boolean
        get() = prefs.getBoolean(KEY_HIDE_BALANCES, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_BALANCES, value).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, value).apply()

    var pinHash: String
        get() = prefs.getString(KEY_PIN_HASH, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PIN_HASH, value).apply()

    var pinEnabled: Boolean
        get() = prefs.getBoolean(KEY_PIN_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_PIN_ENABLED, value).apply()

    var lastOpenedDate: Long
        get() = prefs.getLong(KEY_LAST_OPENED_DATE, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_OPENED_DATE, value).apply()
}
