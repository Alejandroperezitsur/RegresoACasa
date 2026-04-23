package com.example.regresoacasa.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class SafetyHaptics(private val context: Context) {
    
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun heartbeat() {
        if (!hasVibrator()) return
        
        val pattern = longArrayOf(0, 50, 100, 50)
        vibrate(pattern, -1)
    }

    fun alertWarning() {
        if (!hasVibrator()) return
        
        val pattern = longArrayOf(0, 100, 100, 100, 100, 100)
        vibrate(pattern, -1)
    }

    fun alertDanger() {
        if (!hasVibrator()) return
        
        val pattern = longArrayOf(0, 200, 100, 200, 100, 200)
        vibrate(pattern, -1)
    }

    fun alertCritical() {
        if (!hasVibrator()) return
        
        val pattern = longArrayOf(0, 300, 100, 300, 100, 300, 100, 300)
        vibrate(pattern, -1)
    }

    fun checkIn() {
        if (!hasVibrator()) return
        
        val pattern = longArrayOf(0, 150)
        vibrate(pattern, -1)
    }

    fun countdownTick() {
        if (!hasVibrator()) return
        
        val pattern = longArrayOf(0, 30)
        vibrate(pattern, -1)
    }

    fun confirmation() {
        if (!hasVibrator()) return
        
        val pattern = longArrayOf(0, 50, 50, 50)
        vibrate(pattern, -1)
    }

    fun error() {
        if (!hasVibrator()) return
        
        val pattern = longArrayOf(0, 100, 50, 100, 50, 100)
        vibrate(pattern, -1)
    }

    private fun vibrate(pattern: LongArray, repeat: Int) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, repeat))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, repeat)
            }
        } catch (e: Exception) {
            Log.e("SafetyHaptics", "Error vibrating", e)
        }
    }

    private fun hasVibrator(): Boolean {
        return vibrator?.hasVibrator() == true
    }

    fun cancel() {
        vibrator?.cancel()
    }
}
