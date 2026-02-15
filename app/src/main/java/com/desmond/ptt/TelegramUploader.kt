package com.desmond.ptt

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Handles uploading voice recordings to Telegram.
 * 
 * Primary: TDLib (sends as the authenticated user)
 * Fallback: Bot API + optional webhook (configurable via AppConfig)
 */
object TelegramUploader {
    
    private const val TAG = "DesmondPTT"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun sendVoiceMessage(audioFile: File, callback: (Boolean) -> Unit) {
        Log.d(TAG, "sendVoiceMessage called")
        Log.d(TAG, "Audio file: ${audioFile.absolutePath}, size: ${audioFile.length()} bytes")
        
        CoroutineScope(Dispatchers.IO).launch {
            var success = false
            
            try {
                // Try TDLib first (sends as user, not bot!)
                if (TelegramClient.isReady()) {
                    Log.d(TAG, "Using TDLib (user mode)...")
                    var tdlibDone = false
                    var tdlibSuccess = false
                    
                    TelegramClient.sendVoiceMessage(audioFile) { result ->
                        tdlibSuccess = result
                        tdlibDone = true
                    }
                    
                    // Wait for TDLib callback (max 30s)
                    var waited = 0
                    while (!tdlibDone && waited < 30000) {
                        Thread.sleep(100)
                        waited += 100
                    }
                    
                    if (tdlibSuccess) {
                        Log.d(TAG, "TDLib send successful!")
                        success = true
                    } else {
                        Log.w(TAG, "TDLib send failed, falling back to webhook...")
                    }
                }
                
                // Fallback: webhook (for transcription) + bot API (for audio playback)
                if (!success) {
                    Log.d(TAG, "Using fallback (webhook + bot)...")
                    
                    // Send to Telegram bot if configured
                    val botToken = AppConfig.botToken
                    val botChatId = AppConfig.botChatId
                    if (botToken.isNotEmpty() && botChatId.isNotEmpty()) {
                        val telegramOk = sendToTelegramBot(audioFile, botToken, botChatId)
                        Log.d(TAG, "Telegram bot send: $telegramOk")
                        success = telegramOk
                    }
                    
                    // Send to webhook if configured
                    val webhookUrl = AppConfig.webhookUrl
                    if (webhookUrl.isNotEmpty()) {
                        val webhookOk = sendToWebhook(audioFile, webhookUrl)
                        Log.d(TAG, "Webhook send: $webhookOk")
                        success = success || webhookOk
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload exception", e)
            } finally {
                val deleted = audioFile.delete()
                Log.d(TAG, "Audio file deleted: $deleted")
            }
            
            withContext(Dispatchers.Main) {
                callback(success)
            }
        }
    }

    private fun sendToTelegramBot(audioFile: File, botToken: String, chatId: String): Boolean {
        Log.d(TAG, "Sending audio to Telegram bot...")
        
        val url = "https://api.telegram.org/bot$botToken/sendAudio"
        
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("title", "PTT Recording")
            .addFormDataPart(
                "audio", 
                "ptt.m4a",
                audioFile.asRequestBody("audio/mp4".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            response.use { resp ->
                val body = resp.body?.string()
                Log.d(TAG, "Telegram response: ${resp.code}")
                
                if (resp.isSuccessful && body != null) {
                    val json = JSONObject(body)
                    json.optBoolean("ok", false)
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Telegram error", e)
            false
        }
    }
    
    private fun sendToWebhook(audioFile: File, webhookUrl: String): Boolean {
        Log.d(TAG, "Sending to webhook: $webhookUrl")
        
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "audio",
                audioFile.name,
                audioFile.asRequestBody("audio/mp4".toMediaType())
            )
            .build()
        
        val request = Request.Builder()
            .url(webhookUrl)
            .post(requestBody)
            .build()
        
        return try {
            val response = client.newCall(request).execute()
            response.use { resp ->
                Log.d(TAG, "Webhook response: ${resp.code}")
                resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Webhook error", e)
            false
        }
    }
}
