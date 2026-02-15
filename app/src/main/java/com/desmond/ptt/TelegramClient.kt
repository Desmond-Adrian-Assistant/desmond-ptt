package com.desmond.ptt

import android.content.Context
import android.util.Log
import com.desmond.ptt.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * TDLib-based Telegram client for sending messages as a user (not a bot).
 * Handles authentication flow and voice message sending.
 *
 * Credentials are loaded from AppConfig (EncryptedSharedPreferences).
 * Set them via the setup wizard before calling initialize().
 */
object TelegramClient {
    private const val TAG = "TelegramClient"
    
    private var client: Client? = null
    private var isInitialized = false
    
    // Auth state
    sealed class AuthState {
        object Initializing : AuthState()
        object WaitPhoneNumber : AuthState()
        object WaitCode : AuthState()
        data class WaitPassword(val hint: String) : AuthState()
        object Ready : AuthState()
        data class Error(val message: String) : AuthState()
    }
    
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    val authState: StateFlow<AuthState> = _authState
    
    private var pendingPhoneNumber: String? = null
    private var targetChatId: Long? = null
    // Track pending sends: local message ID → callback
    private val pendingSends = ConcurrentHashMap<Long, (Boolean) -> Unit>()
    
    fun initialize(context: Context) {
        if (isInitialized) return
        
        val apiId = BuildConfig.TELEGRAM_API_ID
        val apiHash = BuildConfig.TELEGRAM_API_HASH
        
        Log.d(TAG, "Initializing TDLib...")
        
        try {
            Client.execute(TdApi.SetLogVerbosityLevel(1))
            
            client = Client.create(
                { update -> handleUpdate(update) },
                { e -> Log.e(TAG, "TDLib error", e) },
                { e -> Log.e(TAG, "TDLib fatal error", e) }
            )
            
            val parameters = TdApi.SetTdlibParameters().apply {
                databaseDirectory = File(context.filesDir, "tdlib").absolutePath
                filesDirectory = File(context.cacheDir, "tdlib_files").absolutePath
                useFileDatabase = true
                useChatInfoDatabase = true
                useMessageDatabase = true
                useSecretChats = false
                this.apiId = apiId
                this.apiHash = apiHash
                systemLanguageCode = "en"
                deviceModel = "Android"
                applicationVersion = "1.0"
            }
            
            client?.send(parameters) { result ->
                if (result is TdApi.Error) {
                    Log.e(TAG, "Failed to set parameters: ${result.message}")
                    _authState.value = AuthState.Error(result.message)
                }
            }
            
            isInitialized = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "TDLib native libraries not found", e)
            _authState.value = AuthState.Error("TDLib not available - using fallback mode")
        } catch (e: Exception) {
            Log.e(TAG, "TDLib initialization failed", e)
            _authState.value = AuthState.Error("TDLib init failed: ${e.message}")
        }
    }
    
    private fun handleUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> {
                handleAuthState(update.authorizationState)
            }
            is TdApi.UpdateMessageSendSucceeded -> {
                val oldId = update.oldMessageId
                pendingSends.remove(oldId)?.let { cb ->
                    Log.d(TAG, "Message $oldId confirmed sent (new id: ${update.message.id})")
                    cb(true)
                }
            }
            is TdApi.UpdateMessageSendFailed -> {
                val oldId = update.oldMessageId
                Log.e(TAG, "Message $oldId FAILED: ${update.error.message}")
                pendingSends.remove(oldId)?.let { cb ->
                    cb(false)
                }
            }
            else -> {}
        }
    }
    
    private fun handleAuthState(state: TdApi.AuthorizationState) {
        Log.d(TAG, "Auth state: ${state.javaClass.simpleName}")
        
        when (state) {
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                _authState.value = AuthState.WaitPhoneNumber
            }
            is TdApi.AuthorizationStateWaitCode -> {
                _authState.value = AuthState.WaitCode
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                _authState.value = AuthState.WaitPassword(state.passwordHint)
            }
            is TdApi.AuthorizationStateReady -> {
                _authState.value = AuthState.Ready
                findTargetChat()
            }
            is TdApi.AuthorizationStateClosed -> {
                _authState.value = AuthState.Error("Connection closed")
            }
            else -> {
                Log.d(TAG, "Unhandled auth state: ${state.javaClass.simpleName}")
            }
        }
    }
    
    fun submitPhoneNumber(phoneNumber: String) {
        pendingPhoneNumber = phoneNumber
        client?.send(TdApi.SetAuthenticationPhoneNumber(phoneNumber, null)) { result ->
            if (result is TdApi.Error) {
                Log.e(TAG, "Phone number error: ${result.message}")
                _authState.value = AuthState.Error(result.message)
            }
        }
    }
    
    fun submitCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code)) { result ->
            if (result is TdApi.Error) {
                Log.e(TAG, "Code error: ${result.message}")
                _authState.value = AuthState.Error(result.message)
            }
        }
    }
    
    fun submitPassword(password: String) {
        client?.send(TdApi.CheckAuthenticationPassword(password)) { result ->
            if (result is TdApi.Error) {
                Log.e(TAG, "Password error: ${result.message}")
                _authState.value = AuthState.Error(result.message)
            }
        }
    }
    
    private fun findTargetChat() {
        // Use saved chat ID if available
        val savedChatId = AppConfig.targetChatId
        if (savedChatId != 0L) {
            targetChatId = savedChatId
            Log.d(TAG, "Using saved target chat ID: $savedChatId")
            return
        }
        
        // Otherwise search by username
        val username = AppConfig.targetChatUsername.ifEmpty { "DesmondAdrianBot" }
        if (username.isNotEmpty()) {
            client?.send(TdApi.SearchPublicChat(username)) { result ->
                when (result) {
                    is TdApi.Chat -> {
                        targetChatId = result.id
                        AppConfig.targetChatId = result.id
                        Log.d(TAG, "Found target chat: ${result.id}")
                    }
                    is TdApi.Error -> {
                        Log.e(TAG, "Failed to find target chat: ${result.message}")
                    }
                }
            }
        } else {
            Log.w(TAG, "No target chat configured")
        }
    }
    
    fun setTargetChat(username: String) {
        AppConfig.targetChatUsername = username
        AppConfig.targetChatId = 0L
        targetChatId = null
        if (_authState.value == AuthState.Ready) {
            findTargetChat()
        }
    }
    
    fun sendVoiceMessage(audioFile: File, callback: (Boolean) -> Unit) {
        val chatId = targetChatId
        if (chatId == null) {
            Log.e(TAG, "Target chat not found")
            callback(false)
            return
        }
        
        if (_authState.value != AuthState.Ready) {
            Log.e(TAG, "Not authenticated")
            callback(false)
            return
        }
        
        Log.d(TAG, "Sending voice message to chat $chatId")
        
        val voiceNote = TdApi.InputMessageVoiceNote().apply {
            this.voiceNote = TdApi.InputFileLocal(audioFile.absolutePath)
            duration = 0
        }
        
        val sendMessage = TdApi.SendMessage().apply {
            this.chatId = chatId
            this.inputMessageContent = voiceNote
        }
        
        client?.send(sendMessage) { result ->
            when (result) {
                is TdApi.Message -> {
                    // Message queued locally — wait for UpdateMessageSendSucceeded/Failed
                    Log.d(TAG, "Voice message queued locally, ID: ${result.id}, waiting for server confirm...")
                    pendingSends[result.id] = callback
                    // Timeout: if no confirmation in 15 seconds, treat as failure
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(15_000)
                        pendingSends.remove(result.id)?.let { cb ->
                            Log.e(TAG, "Send timed out for message ${result.id}")
                            cb(false)
                        }
                    }
                }
                is TdApi.Error -> {
                    Log.e(TAG, "Failed to send voice: ${result.message}")
                    callback(false)
                }
                else -> {
                    Log.e(TAG, "Unexpected result: ${result.javaClass.simpleName}")
                    callback(false)
                }
            }
        }
    }
    
    fun isReady(): Boolean = _authState.value == AuthState.Ready
}
