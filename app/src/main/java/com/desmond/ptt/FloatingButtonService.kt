package com.desmond.ptt

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.AnimationDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat

class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var micButton: ImageView
    private lateinit var vibrator: Vibrator
    private val handler = Handler(Looper.getMainLooper())
    
    private var audioRecorder: AudioRecorder? = null
    private var isRecording = false
    private var recordingStartTime = 0L
    private var rotateAnimation: RotateAnimation? = null
    
    companion object {
        const val CHANNEL_ID = "floating_ptt_channel"
        const val NOTIFICATION_ID = 100
        private const val TAG = "DesmondPTT"
        private val MIN_RECORDING_MS: Long get() = AppConfig.minRecordingMs
        
        @Volatile
        var isRunning = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "FloatingButtonService created")
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        createFloatingButton()
        
        // Show toast to confirm service started
        Toast.makeText(this, "PTT Ready - Hold to talk", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun createFloatingButton() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)
        micButton = floatingView.findViewById(R.id.floating_mic)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        params.x = 20

        windowManager.addView(floatingView, params)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var recordingTriggered = false
        val DRAG_THRESHOLD = 15f  // pixels before it counts as a drag
        val HOLD_DELAY = AppConfig.holdDelayMs

        val recordingRunnable = Runnable {
            if (!isDragging) {
                recordingTriggered = true
                startRecording()
            }
        }

        // Restore saved position
        val prefs = getSharedPreferences("ptt_prefs", Context.MODE_PRIVATE)
        params.x = prefs.getInt("button_x", 20)
        params.y = prefs.getInt("button_y", 0)

        micButton.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    recordingTriggered = false
                    // Start recording after hold delay (if not dragging)
                    handler.postDelayed(recordingRunnable, HOLD_DELAY)
                    true
                }
                
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    
                    if (!isDragging && (kotlin.math.abs(dx) > DRAG_THRESHOLD || kotlin.math.abs(dy) > DRAG_THRESHOLD)) {
                        // Crossed drag threshold — cancel recording, enter drag mode
                        isDragging = true
                        handler.removeCallbacks(recordingRunnable)
                        if (recordingTriggered) {
                            // Cancel recording if it already started
                            cancelRecording()
                            recordingTriggered = false
                        }
                    }
                    
                    if (isDragging) {
                        params.x = initialX - dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }
                
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(recordingRunnable)
                    
                    if (isDragging) {
                        // Save position
                        prefs.edit()
                            .putInt("button_x", params.x)
                            .putInt("button_y", params.y)
                            .apply()
                    } else if (recordingTriggered) {
                        stopRecordingAndSend()
                    }
                    // If neither dragging nor recording triggered (quick tap), do nothing
                    isDragging = false
                    recordingTriggered = false
                    true
                }
                
                else -> false
            }
        }
    }

    private fun cancelRecording() {
        if (!isRecording) return
        Log.d(TAG, "Recording cancelled (drag detected)")
        isRecording = false
        audioRecorder?.cancel()
        audioRecorder = null
        showReadyState()
        updateNotification("Hold to talk")
    }

    private fun startRecording() {
        if (isRecording) return
        
        Log.d(TAG, "Starting recording...")
        isRecording = true
        recordingStartTime = System.currentTimeMillis()
        vibrate(50)
        
        // Visual: Red background, mic icon
        micButton.setImageResource(R.drawable.ic_mic_recording)
        micButton.setBackgroundResource(R.drawable.floating_button_recording_bg)
        
        audioRecorder = AudioRecorder(this)
        audioRecorder?.startRecording()
        
        updateNotification("🔴 Recording...")
        Log.d(TAG, "Recording started")
    }

    private fun stopRecordingAndSend() {
        if (!isRecording) return
        
        val duration = System.currentTimeMillis() - recordingStartTime
        Log.d(TAG, "Stopping recording... duration: ${duration}ms")
        isRecording = false
        
        if (duration < MIN_RECORDING_MS) {
            Log.d(TAG, "Recording too short (${duration}ms < ${MIN_RECORDING_MS}ms), discarding")
            audioRecorder?.cancel()
            audioRecorder = null
            showReadyState()
            updateNotification("Hold to talk")
            return
        }
        
        vibrate(100)
        
        // Visual: Orange background, spinning animation
        showSendingState()
        updateNotification("📤 Sending...")
        
        audioRecorder?.stopRecording { audioFile ->
            Log.d(TAG, "Audio file: ${audioFile?.absolutePath}, exists: ${audioFile?.exists()}, size: ${audioFile?.length()}")
            
            if (audioFile != null && audioFile.exists() && audioFile.length() > 0) {
                Log.d(TAG, "Uploading to Telegram...")
                
                TelegramUploader.sendVoiceMessage(audioFile) { success ->
                    Log.d(TAG, "Upload result: $success")
                    
                    handler.post {
                        stopSpinAnimation()
                        
                        if (success) {
                            showSuccessState()
                            vibrate(50)
                            updateNotification("✅ Sent!")
                            Toast.makeText(this, "Sent to Desmond!", Toast.LENGTH_SHORT).show()
                        } else {
                            showErrorState()
                            vibrate(200)
                            updateNotification("❌ Send failed")
                            Toast.makeText(this, "Failed to send", Toast.LENGTH_SHORT).show()
                        }
                        
                        // Reset to ready state after 2 seconds
                        handler.postDelayed({
                            showReadyState()
                            updateNotification("Hold to talk")
                        }, 2000)
                    }
                }
            } else {
                Log.e(TAG, "No audio file or empty file")
                handler.post {
                    stopSpinAnimation()
                    showErrorState()
                    vibrate(200)
                    updateNotification("❌ No audio recorded")
                    Toast.makeText(this, "No audio - try holding longer", Toast.LENGTH_SHORT).show()
                    
                    handler.postDelayed({
                        showReadyState()
                        updateNotification("Hold to talk")
                    }, 2000)
                }
            }
        }
        
        audioRecorder = null
    }

    private fun showReadyState() {
        micButton.setImageResource(R.drawable.ic_mic_large)
        micButton.setBackgroundResource(R.drawable.floating_button_bg)
    }

    private fun showSendingState() {
        micButton.setImageResource(R.drawable.ic_mic_large)
        micButton.setBackgroundResource(R.drawable.floating_button_sending_bg)
        startSpinAnimation()
    }

    private fun showSuccessState() {
        micButton.setImageResource(R.drawable.ic_check)
        micButton.setBackgroundResource(R.drawable.floating_button_success_bg)
    }

    private fun showErrorState() {
        micButton.setImageResource(R.drawable.ic_error)
        micButton.setBackgroundResource(R.drawable.floating_button_error_bg)
    }

    private fun startSpinAnimation() {
        rotateAnimation = RotateAnimation(
            0f, 360f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 1000
            repeatCount = Animation.INFINITE
        }
        micButton.startAnimation(rotateAnimation)
    }

    private fun stopSpinAnimation() {
        rotateAnimation?.cancel()
        micButton.clearAnimation()
    }

    private fun vibrate(durationMs: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Desmond PTT",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Floating PTT button service"
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(status: String = "Hold to talk"): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔷 Desmond PTT")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, createNotification(status))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FloatingButtonService destroyed")
        isRunning = false
        audioRecorder?.cancel()
        stopSpinAnimation()
        
        try {
            windowManager.removeView(floatingView)
        } catch (_: Exception) {}
    }
}
