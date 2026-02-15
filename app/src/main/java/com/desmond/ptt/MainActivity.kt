package com.desmond.ptt

import android.Manifest
import android.content.Intent
import android.util.Log
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    
    // Auth UI
    private lateinit var authSection: LinearLayout
    private lateinit var phoneInput: EditText
    private lateinit var codeInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var submitButton: Button
    private lateinit var authStatus: TextView
    
    private var isServiceRunning = false
    private var currentAuthState: TelegramClient.AuthState = TelegramClient.AuthState.Initializing

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize config
        AppConfig.init(this)
        
        // Check if setup is needed
        if (!AppConfig.isSetupComplete) {
            showSetupWizard()
            return
        }
        
        showMainUI()
    }
    
    // ── Setup Wizard ──────────────────────────────────────────────────────
    
    private var setupStep = 0
    
    private fun showSetupWizard() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xFF1A1A2E.toInt())
        }
        
        val title = TextView(this).apply {
            text = "🔷 Desmond PTT Setup"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 32)
        }
        layout.addView(title)
        
        val subtitle = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFFAAAAAA.toInt())
            setPadding(0, 0, 0, 24)
        }
        layout.addView(subtitle)
        
        val input1 = EditText(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF666666.toInt())
            setBackgroundColor(0xFF333355.toInt())
            setPadding(24, 24, 24, 24)
        }
        layout.addView(input1)
        
        val input2 = EditText(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF666666.toInt())
            setBackgroundColor(0xFF333355.toInt())
            setPadding(24, 24, 24, 24)
            visibility = View.GONE
        }
        val params2 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 16 }
        layout.addView(input2, params2)
        
        val linkText = TextView(this).apply {
            setTextColor(0xFF4CAF50.toInt())
            textSize = 13f
            setPadding(0, 16, 0, 0)
            visibility = View.GONE
        }
        layout.addView(linkText)
        
        val nextButton = Button(this).apply {
            text = "Next"
            setBackgroundColor(0xFF4CAF50.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 32 }
        layout.addView(nextButton, btnParams)
        
        setContentView(layout)
        
        // Load API credentials from BuildConfig and skip to target chat step
        AppConfig.apiId = BuildConfig.TELEGRAM_API_ID
        AppConfig.apiHash = BuildConfig.TELEGRAM_API_HASH
        setupStep = 1
        
        fun showStep() {
            when (setupStep) {
                0 -> {
                    subtitle.text = "Step 1/3: Telegram API Credentials"
                    input1.hint = "API ID (number)"
                    input1.inputType = InputType.TYPE_CLASS_NUMBER
                    input1.text.clear()
                    input2.visibility = View.VISIBLE
                    input2.hint = "API Hash"
                    input2.inputType = InputType.TYPE_CLASS_TEXT
                    input2.text.clear()
                    linkText.visibility = View.VISIBLE
                    linkText.text = "Get credentials at https://my.telegram.org"
                    linkText.setOnClickListener {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://my.telegram.org")))
                    }
                    nextButton.text = "Next"
                }
                1 -> {
                    subtitle.text = "Step 2/3: Target Chat"
                    input1.hint = "Bot/chat username (e.g. MyBot)"
                    input1.inputType = InputType.TYPE_CLASS_TEXT
                    input1.text.clear()
                    input2.visibility = View.GONE
                    linkText.visibility = View.VISIBLE
                    linkText.text = "Username of the bot or chat to send voice messages to"
                    linkText.setOnClickListener(null)
                    nextButton.text = "Next"
                }
                2 -> {
                    subtitle.text = "Step 3/3: Optional Settings"
                    input1.hint = "Webhook URL (optional)"
                    input1.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                    input1.text.clear()
                    input2.visibility = View.GONE
                    linkText.visibility = View.VISIBLE
                    linkText.text = "Optional: URL to send audio for server-side transcription"
                    linkText.setOnClickListener(null)
                    nextButton.text = "Finish Setup"
                }
            }
        }
        
        showStep()
        
        nextButton.setOnClickListener {
            when (setupStep) {
                0 -> {
                    // API credentials from BuildConfig — skip straight to step 1
                    AppConfig.apiId = BuildConfig.TELEGRAM_API_ID
                    AppConfig.apiHash = BuildConfig.TELEGRAM_API_HASH
                    setupStep = 1
                    showStep()
                }
                1 -> {
                    val username = input1.text.toString().trim().removePrefix("@")
                    if (username.isEmpty()) {
                        Toast.makeText(this, "Enter a chat username", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    AppConfig.targetChatUsername = username
                    setupStep = 2
                    showStep()
                }
                2 -> {
                    val webhook = input1.text.toString().trim()
                    if (webhook.isNotEmpty()) {
                        AppConfig.webhookUrl = webhook
                    }
                    AppConfig.isSetupComplete = true
                    Toast.makeText(this, "✅ Setup complete!", Toast.LENGTH_LONG).show()
                    showMainUI()
                }
            }
        }
    }
    
    // ── Main UI ───────────────────────────────────────────────────────────
    
    private fun showMainUI() {
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status)
        toggleButton = findViewById(R.id.toggle_button)
        
        // Auth UI
        authSection = findViewById(R.id.auth_section)
        phoneInput = findViewById(R.id.phone_input)
        codeInput = findViewById(R.id.code_input)
        passwordInput = findViewById(R.id.password_input)
        submitButton = findViewById(R.id.submit_button)
        authStatus = findViewById(R.id.auth_status)

        toggleButton.setOnClickListener {
            if (checkPermissions()) {
                toggleFloatingButton()
            }
        }
        
        submitButton.setOnClickListener {
            handleAuthSubmit()
        }
        
        // Long-press toggle button opens settings
        toggleButton.setOnLongClickListener {
            showSettings()
            true
        }
        
        // Initialize TDLib
        try {
            TelegramClient.initialize(this)
        } catch (e: Exception) {
            Log.e("MainActivity", "TDLib init failed", e)
        }
        
        // Observe auth state
        lifecycleScope.launch {
            TelegramClient.authState.collectLatest { state ->
                currentAuthState = state
                updateAuthUI(state)
            }
        }

        updateUI()
    }
    
    // ── Settings Dialog ───────────────────────────────────────────────────
    
    private fun showSettings() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        
        val holdDelayInput = EditText(this).apply {
            hint = "Hold delay (ms)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(AppConfig.holdDelayMs.toString())
        }
        layout.addView(TextView(this).apply { text = "Hold delay before recording (ms):" })
        layout.addView(holdDelayInput)
        
        val minRecInput = EditText(this).apply {
            hint = "Min recording (ms)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(AppConfig.minRecordingMs.toString())
        }
        layout.addView(TextView(this).apply { 
            text = "\nMin recording duration (ms):"
        })
        layout.addView(minRecInput)
        
        val chatInput = EditText(this).apply {
            hint = "Target chat username"
            setText(AppConfig.targetChatUsername)
        }
        layout.addView(TextView(this).apply {
            text = "\nTarget chat username:"
        })
        layout.addView(chatInput)
        
        val webhookInput = EditText(this).apply {
            hint = "Webhook URL (optional)"
            setText(AppConfig.webhookUrl)
        }
        layout.addView(TextView(this).apply {
            text = "\nWebhook URL:"
        })
        layout.addView(webhookInput)
        
        AlertDialog.Builder(this)
            .setTitle("⚙️ PTT Settings")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                holdDelayInput.text.toString().toLongOrNull()?.let { AppConfig.holdDelayMs = it }
                minRecInput.text.toString().toLongOrNull()?.let { AppConfig.minRecordingMs = it }
                val newChat = chatInput.text.toString().trim().removePrefix("@")
                if (newChat.isNotEmpty() && newChat != AppConfig.targetChatUsername) {
                    TelegramClient.setTargetChat(newChat)
                }
                AppConfig.webhookUrl = webhookInput.text.toString().trim()
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Reset App") { _, _ ->
                AlertDialog.Builder(this)
                    .setTitle("Reset?")
                    .setMessage("This will clear all settings and credentials.")
                    .setPositiveButton("Reset") { _, _ ->
                        AppConfig.clear()
                        recreate()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .show()
    }
    
    private fun updateAuthUI(state: TelegramClient.AuthState) {
        runOnUiThread {
            when (state) {
                is TelegramClient.AuthState.Initializing -> {
                    authStatus.text = "Initializing Telegram..."
                    hideAllInputs()
                }
                is TelegramClient.AuthState.WaitPhoneNumber -> {
                    authStatus.text = "Enter your phone number (with country code):"
                    showInput(phoneInput, "Phone (+1234567890)")
                }
                is TelegramClient.AuthState.WaitCode -> {
                    authStatus.text = "Enter the code sent to your phone:"
                    showInput(codeInput, "Code")
                }
                is TelegramClient.AuthState.WaitPassword -> {
                    authStatus.text = "Enter your 2FA password (hint: ${state.hint}):"
                    showInput(passwordInput, "Password")
                }
                is TelegramClient.AuthState.Ready -> {
                    authStatus.text = "✅ Logged in! Voice messages will send as YOU."
                    authStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                    hideAllInputs()
                    submitButton.visibility = View.GONE
                }
                is TelegramClient.AuthState.Error -> {
                    authStatus.text = "❌ Error: ${state.message}"
                    authStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                }
            }
        }
    }
    
    private fun hideAllInputs() {
        phoneInput.visibility = View.GONE
        codeInput.visibility = View.GONE
        passwordInput.visibility = View.GONE
    }
    
    private fun showInput(input: EditText, hint: String) {
        hideAllInputs()
        input.visibility = View.VISIBLE
        input.hint = hint
        input.text.clear()
        submitButton.visibility = View.VISIBLE
    }
    
    private fun handleAuthSubmit() {
        when (currentAuthState) {
            is TelegramClient.AuthState.WaitPhoneNumber -> {
                val phone = phoneInput.text.toString().trim()
                if (phone.isNotEmpty()) {
                    TelegramClient.submitPhoneNumber(phone)
                }
            }
            is TelegramClient.AuthState.WaitCode -> {
                val code = codeInput.text.toString().trim()
                if (code.isNotEmpty()) {
                    TelegramClient.submitCode(code)
                }
            }
            is TelegramClient.AuthState.WaitPassword -> {
                val password = passwordInput.text.toString()
                if (password.isNotEmpty()) {
                    TelegramClient.submitPassword(password)
                }
            }
            else -> {}
        }
    }

    override fun onResume() {
        super.onResume()
        if (AppConfig.isSetupComplete) {
            updateUI()
        }
    }

    private fun checkPermissions(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant overlay permission", Toast.LENGTH_LONG).show()
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
            return false
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2
                )
                return false
            }
        }

        // Request battery optimization exemption
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            @Suppress("BatteryLife")
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }

        return true
    }

    private fun toggleFloatingButton() {
        val intent = Intent(this, FloatingButtonService::class.java)
        
        if (isServiceRunning) {
            stopService(intent)
            isServiceRunning = false
            Toast.makeText(this, "Floating button stopped", Toast.LENGTH_SHORT).show()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            isServiceRunning = true
            Toast.makeText(this, "Floating button started!", Toast.LENGTH_SHORT).show()
            moveTaskToBack(true)
        }
        
        updateUI()
    }

    private fun updateUI() {
        isServiceRunning = FloatingButtonService.isRunning
        
        if (isServiceRunning) {
            statusText.text = "🎤 Floating button active\n\nHold the button to talk!\n\nLong-press Start/Stop for settings."
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            toggleButton.text = "Stop Floating Button"
        } else {
            statusText.text = "Tap below to start the floating PTT button\n\nLong-press for settings."
            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            toggleButton.text = "Start Floating Button"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (checkPermissions()) {
                toggleFloatingButton()
            }
        }
    }
}
