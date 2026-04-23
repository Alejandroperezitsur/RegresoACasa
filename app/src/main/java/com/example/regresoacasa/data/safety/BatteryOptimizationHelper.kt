package com.example.regresoacasa.data.safety

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

class BatteryOptimizationHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "BatteryOptHelper"
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true
    }

    fun requestIgnoreBatteryOptimizations(): Intent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isIgnoringBatteryOptimizations()) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                Log.d(TAG, "Battery optimization exclusion requested")
                return intent
            }
        }
        Log.d(TAG, "Battery optimization already ignored or not applicable")
        return null
    }

    fun openBatteryOptimizationSettings(): Intent {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
        Log.d(TAG, "Opening battery optimization settings")
        return intent
    }

    fun shouldShowBatteryOptimizationWarning(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations()
    }
}
