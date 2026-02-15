package com.desmond.ptt

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorder(private val context: Context) {
    
    companion object {
        private const val TAG = "DesmondPTT"
    }
    
    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false

    fun startRecording() {
        try {
            // Create temp file for audio - use m4a for better compatibility
            audioFile = File(context.cacheDir, "ptt_${System.currentTimeMillis()}.m4a")
            Log.d(TAG, "Audio file path: ${audioFile?.absolutePath}")
            
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            recorder?.apply {
                // Use DEFAULT - most compatible across Samsung devices
                setAudioSource(MediaRecorder.AudioSource.DEFAULT)
                
                // Use MPEG_4/AAC for universal compatibility
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setAudioChannels(1)
                setOutputFile(audioFile?.absolutePath)
                
                Log.d(TAG, "Preparing MediaRecorder with AAC...")
                prepare()
                Log.d(TAG, "Starting MediaRecorder...")
                start()
                isRecording = true
                Log.d(TAG, "Recording started successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            // Fallback to basic MIC source if VOICE_COMMUNICATION fails
            try {
                recorder?.release()
                recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }
                
                recorder?.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioSamplingRate(44100)
                    setAudioEncodingBitRate(128000)
                    setOutputFile(audioFile?.absolutePath)
                    
                    Log.d(TAG, "Fallback: Preparing with MIC source...")
                    prepare()
                    start()
                    isRecording = true
                    Log.d(TAG, "Fallback recording started")
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback also failed", e2)
                cleanup()
            }
        }
    }

    fun stopRecording(callback: (File?) -> Unit) {
        Log.d(TAG, "stopRecording called, isRecording: $isRecording")
        
        if (!isRecording) {
            Log.w(TAG, "Not recording, returning null")
            callback(null)
            return
        }
        
        try {
            recorder?.apply {
                Log.d(TAG, "Stopping MediaRecorder...")
                stop()
                Log.d(TAG, "Releasing MediaRecorder...")
                release()
            }
            recorder = null
            isRecording = false
            
            val fileSize = audioFile?.length() ?: 0
            Log.d(TAG, "Recording stopped. File size: $fileSize bytes")
            
            if (fileSize > 0) {
                callback(audioFile)
            } else {
                Log.e(TAG, "Audio file is empty!")
                callback(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording", e)
            cleanup()
            callback(null)
        }
    }

    fun cancel() {
        Log.d(TAG, "Recording cancelled")
        cleanup()
    }

    private fun cleanup() {
        try {
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null
        isRecording = false
        audioFile?.delete()
        audioFile = null
    }
}
