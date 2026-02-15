package com.desmond.ptt

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Centralized configuration store using EncryptedSharedPreferences.
 * All sensitive credentials (API keys, tokens, etc.) are stored here.
 */
object AppConfig {
    private const val PREFS_NAME = "desmond_ptt_config"
    
    private const val KEY_API_ID = "telegram_api_id"
    private const val KEY_API_HASH = "telegram_api_hash"
    private const val KEY_PHONE_NUMBER = "telegram_phone"
    private const val KEY_TARGET_CHAT = "target_chat_username"
    private const val KEY_TARGET_CHAT_ID = "target_chat_id"
    private const val KEY_BOT_TOKEN = "bot_token"
    private const val KEY_BOT_CHAT_ID = "bot_chat_id"
    private const val KEY_HOLD_DELAY = "hold_delay_ms"
    private const val KEY_MIN_RECORDING = "min_recording_ms"
    private const val KEY_SETUP_COMPLETE = "setup_complete"
    
    private var prefs: SharedPreferences? = null
    
    fun init(context: Context) {
        if (prefs != null) return
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Fallback to regular SharedPreferences if encryption fails
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
    
    var apiId: Int
        get() = prefs?.getInt(KEY_API_ID, 0) ?: 0
        set(value) { prefs?.edit()?.putInt(KEY_API_ID, value)?.apply() }
    
    var apiHash: String
        get() = prefs?.getString(KEY_API_HASH, "") ?: ""
        set(value) { prefs?.edit()?.putString(KEY_API_HASH, value)?.apply() }
    
    var phoneNumber: String
        get() = prefs?.getString(KEY_PHONE_NUMBER, "") ?: ""
        set(value) { prefs?.edit()?.putString(KEY_PHONE_NUMBER, value)?.apply() }
    
    var targetChatUsername: String
        get() = prefs?.getString(KEY_TARGET_CHAT, "") ?: ""
        set(value) { prefs?.edit()?.putString(KEY_TARGET_CHAT, value)?.apply() }
    
    var targetChatId: Long
        get() = prefs?.getLong(KEY_TARGET_CHAT_ID, 0L) ?: 0L
        set(value) { prefs?.edit()?.putLong(KEY_TARGET_CHAT_ID, value)?.apply() }
    
    var botToken: String
        get() = prefs?.getString(KEY_BOT_TOKEN, "") ?: ""
        set(value) { prefs?.edit()?.putString(KEY_BOT_TOKEN, value)?.apply() }
    
    var botChatId: String
        get() = prefs?.getString(KEY_BOT_CHAT_ID, "") ?: ""
        set(value) { prefs?.edit()?.putString(KEY_BOT_CHAT_ID, value)?.apply() }
    
    var holdDelayMs: Long
        get() = prefs?.getLong(KEY_HOLD_DELAY, 400L) ?: 400L
        set(value) { prefs?.edit()?.putLong(KEY_HOLD_DELAY, value)?.apply() }
    
    var minRecordingMs: Long
        get() = prefs?.getLong(KEY_MIN_RECORDING, 500L) ?: 500L
        set(value) { prefs?.edit()?.putLong(KEY_MIN_RECORDING, value)?.apply() }
    
    var isSetupComplete: Boolean
        get() = prefs?.getBoolean(KEY_SETUP_COMPLETE, false) ?: false
        set(value) { prefs?.edit()?.putBoolean(KEY_SETUP_COMPLETE, value)?.apply() }
    
    fun hasApiCredentials(): Boolean = apiId != 0 && apiHash.isNotEmpty()
    
    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}
