package com.example.regresoacasa.data.safety

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.regresoacasa.data.local.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SafetyWatchdog(
    private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val scope: CoroutineScope
) {
    companion object {
        private const val GPS_UPDATE_THRESHOLD_MS = 60000L
        private const val MONITOR_CYCLE_THRESHOLD_MS = 120000L
        private const val CHECK_INTERVAL_MS = 10000L
        private const val PREF_LAST_GPS_UPDATE = "last_gps_update"
        private const val PREF_LAST_MONITOR_CYCLE = "last_monitor_cycle"
        private const val PREF_WATCHDOG_ACTIVE = "watchdog_active"
    }

    private val _watchdogState = MutableStateFlow<WatchdogState>(WatchdogState.Idle)
    val watchdogState = _watchdogState.asStateFlow()

    private var checkJob: Job? = null
    private var isRunning = false

    data class WatchdogState(
        val status: Status,
        val lastGpsUpdate: Long,
        val lastMonitorCycle: Long,
        val timeSinceLastGps: Long,
        val timeSinceLastCycle: Long
    ) {
        enum class Status {
            IDLE,
            MONITORING,
            WARNING,
            CRITICAL
        }

        companion object {
            val Idle = WatchdogState(
                Status.IDLE,
                0L,
                0L,
                0L,
                0L
            )
        }
    }

    fun start() {
        if (isRunning) {
            Log.d("SafetyWatchdog", "Watchdog already running")
            return
        }

        isRunning = true
        _watchdogState.value = WatchdogState.Idle.copy(status = WatchdogState.Status.MONITORING)
        
        checkJob = scope.launch(Dispatchers.IO) {
            while (isRunning) {
                checkSystemHealth()
                delay(CHECK_INTERVAL_MS)
            }
        }

        Log.d("SafetyWatchdog", "Watchdog started")
    }

    fun stop() {
        isRunning = false
        checkJob?.cancel()
        _watchdogState.value = WatchdogState.Idle
        Log.d("SafetyWatchdog", "Watchdog stopped")
    }

    fun updateGpsTimestamp(timestamp: Long) {
        preferencesManager.saveLong(PREF_LAST_GPS_UPDATE, timestamp)
        Log.d("SafetyWatchdog", "GPS timestamp updated: $timestamp")
    }

    fun updateMonitorCycleTimestamp(timestamp: Long) {
        preferencesManager.saveLong(PREF_LAST_MONITOR_CYCLE, timestamp)
        Log.d("SafetyWatchdog", "Monitor cycle timestamp updated: $timestamp")
    }

    private suspend fun checkSystemHealth() {
        val now = System.currentTimeMillis()
        val lastGpsUpdate = preferencesManager.getLong(PREF_LAST_GPS_UPDATE, 0L)
        val lastMonitorCycle = preferencesManager.getLong(PREF_LAST_MONITOR_CYCLE, 0L)

        val timeSinceLastGps = if (lastGpsUpdate > 0) now - lastGpsUpdate else 0L
        val timeSinceLastCycle = if (lastMonitorCycle > 0) now - lastMonitorCycle else 0L

        val state = WatchdogState(
            status = determineStatus(timeSinceLastGps, timeSinceLastCycle),
            lastGpsUpdate = lastGpsUpdate,
            lastMonitorCycle = lastMonitorCycle,
            timeSinceLastGps = timeSinceLastGps,
            timeSinceLastCycle = timeSinceLastCycle
        )

        _watchdogState.value = state

        when (state.status) {
            WatchdogState.Status.CRITICAL -> {
                Log.e("SafetyWatchdog", "CRITICAL: System appears dead - GPS: ${timeSinceLastGps}ms, Cycle: ${timeSinceLastCycle}ms")
                triggerCriticalAlert()
            }
            WatchdogState.Status.WARNING -> {
                Log.w("SafetyWatchdog", "WARNING: System degraded - GPS: ${timeSinceLastGps}ms, Cycle: ${timeSinceLastCycle}ms")
            }
            WatchdogState.Status.MONITORING -> {
                Log.d("SafetyWatchdog", "System healthy - GPS: ${timeSinceLastGps}ms, Cycle: ${timeSinceLastCycle}ms")
            }
            WatchdogState.Status.IDLE -> {
            }
        }
    }

    private fun determineStatus(timeSinceLastGps: Long, timeSinceLastCycle: Long): WatchdogState.Status {
        val gpsCritical = timeSinceLastGps > GPS_UPDATE_THRESHOLD_MS * 2
        val cycleCritical = timeSinceLastCycle > MONITOR_CYCLE_THRESHOLD_MS * 2
        val gpsWarning = timeSinceLastGps > GPS_UPDATE_THRESHOLD_MS
        val cycleWarning = timeSinceLastCycle > MONITOR_CYCLE_THRESHOLD_MS

        return when {
            gpsCritical || cycleCritical -> WatchdogState.Status.CRITICAL
            gpsWarning || cycleWarning -> WatchdogState.Status.WARNING
            else -> WatchdogState.Status.MONITORING
        }
    }

    private fun triggerCriticalAlert() {
        val intent = Intent("com.example.regresoacasa.ACTION_CRITICAL_ALERT").apply {
            putExtra("reason", "watchdog_critical")
            putExtra("last_gps_update", _watchdogState.value.lastGpsUpdate)
            putExtra("last_monitor_cycle", _watchdogState.value.lastMonitorCycle)
        }
        context.sendBroadcast(intent)
        Log.d("SafetyWatchdog", "Critical alert broadcast sent")
    }

    fun attemptServiceRestart() {
        val intent = Intent("com.example.regresoacasa.ACTION_RESTART_SERVICE")
        context.sendBroadcast(intent)
        Log.d("SafetyWatchdog", "Service restart request sent")
    }

    fun isSystemHealthy(): Boolean {
        val state = _watchdogState.value
        return state.status == WatchdogState.Status.MONITORING
    }

    fun getLastKnownGpsTimestamp(): Long {
        return preferencesManager.getLong(PREF_LAST_GPS_UPDATE, 0L)
    }

    fun getLastKnownMonitorTimestamp(): Long {
        return preferencesManager.getLong(PREF_LAST_MONITOR_CYCLE, 0L)
    }
}
