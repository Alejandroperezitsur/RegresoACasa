package com.example.regresoacasa.core.safety.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.regresoacasa.BuildConfig
import java.io.File

/**
 * V3 FASE 1 — ANTI-TAMPERING + INTEGRITY CHECK
 * 
 * Detecta si el dispositivo o la app han sido comprometidos.
 * Si se detecta compromiso, el sistema entra en modo crítico.
 */
class IntegrityGuard(private val context: Context) {
    
    /**
     * Resultado de verificación de integridad
     */
    data class IntegrityResult(
        val isCompromised: Boolean,
        val reasons: List<String>,
        val severity: Severity
    ) {
        enum class Severity {
            SAFE,
            WARNING,
            CRITICAL
        }
    }
    
    /**
     * Verifica si el dispositivo está comprometido
     */
    fun isDeviceCompromised(): Boolean {
        val result = checkIntegrity()
        return result.isCompromised
    }
    
    /**
     * Verifica si la app ha sido alterada
     */
    fun isAppTampered(): Boolean {
        val result = checkIntegrity()
        return result.isCompromised && result.severity == IntegrityResult.Severity.CRITICAL
    }
    
    /**
     * Verificación completa de integridad
     */
    fun checkIntegrity(): IntegrityResult {
        val reasons = mutableListOf<String>()
        var severity = IntegrityResult.Severity.SAFE
        
        // Verificar root
        if (isRooted()) {
            reasons.add("Device is rooted")
            severity = IntegrityResult.Severity.CRITICAL
        }
        
        // Verificar debuggable en producción
        if (isDebuggableInProduction()) {
            reasons.add("Debuggable build in production")
            severity = IntegrityResult.Severity.CRITICAL
        }
        
        // Verificar emulador
        if (isEmulator()) {
            reasons.add("Running on emulator")
            severity = IntegrityResult.Severity.WARNING
        }
        
        // Verificar apps de debugging instaladas
        if (hasDebuggingApps()) {
            reasons.add("Debugging apps detected")
            severity = IntegrityResult.Severity.WARNING
        }
        
        // Verificar Xposed framework
        if (hasXposedFramework()) {
            reasons.add("Xposed framework detected")
            severity = IntegrityResult.Severity.CRITICAL
        }
        
        // Verificar Frida
        if (hasFrida()) {
            reasons.add("Frida detected")
            severity = IntegrityResult.Severity.CRITICAL
        }
        
        return IntegrityResult(
            isCompromised = reasons.isNotEmpty(),
            reasons = reasons,
            severity = severity
        )
    }
    
    /**
     * Detecta si el dispositivo está rooteado
     */
    private fun isRooted(): Boolean {
        // Verificar paths comunes de root
        val rootPaths = listOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        
        for (path in rootPaths) {
            if (File(path).exists()) {
                return true
            }
        }
        
        // Verificar si puede ejecutar su
        try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val output = process.inputStream.bufferedReader().readText()
            if (output.trim().isNotEmpty()) {
                return true
            }
        } catch (e: Exception) {
            // No su encontrado
        }
        
        // Verificar build tags
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }
        
        return false
    }
    
    /**
     * Detecta si es un build debuggable en producción
     */
    private fun isDebuggableInProduction(): Boolean {
        if (BuildConfig.DEBUG) {
            // En desarrollo es aceptable
            return false
        }
        
        // Verificar si la app es debuggable
        return try {
            val appInfo = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_SIGNATURES
            )
            (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Detecta si está corriendo en emulador
     */
    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                "google_sdk" == Build.PRODUCT)
    }
    
    /**
     * Detecta apps de debugging instaladas
     */
    private fun hasDebuggingApps(): Boolean {
        val debuggingApps = listOf(
            "com.android.development",
            "com.android.development_settings",
            "com.saurik.substrate",
            "com.zachspong.temprootcheckjellybean",
            "com.amphoras.hiddify",
            "com.topjohnwu.magisk",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "eu.chainfire.supersu",
            "com.noshufou.android.su",
            "com.koushikdutta.rommanager",
            "com.koushikdutta.rommanager.license",
            "com.dimonvideo.luckypatcher",
            "com.chelpus.luckypatcher",
            "com.ramdroid.appquarantine",
            "com.ramdroid.appquarantinepro",
            "com.android.vending.billing.InAppBillingService.COIN",
            "com.android.vending.billing.InAppBillingService.LUCK"
        )
        
        val pm = context.packageManager
        for (packageName in debuggingApps) {
            try {
                pm.getPackageInfo(packageName, 0)
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // App no instalada
            }
        }
        
        return false
    }
    
    /**
     * Detecta Xposed framework
     */
    private fun hasXposedFramework(): Boolean {
        return try {
            throw Exception()
        } catch (e: Exception) {
            val stackTrace = e.stackTrace
            for (element in stackTrace) {
                if (element.className.contains("de.robv.android.xposed")) {
                    return true
                }
            }
            false
        }
    }
    
    /**
     * Detecta Frida (instrumentación dinámica)
     */
    private fun hasFrida(): Boolean {
        // Verificar puertos de Frida
        val fridaPorts = listOf(27042, 27043)
        
        for (port in fridaPorts) {
            try {
                val socket = java.net.Socket("127.0.0.1", port)
                socket.close()
                return true
            } catch (e: Exception) {
                // Puerto no abierto
            }
        }
        
        // Verificar librerias de Frida
        val fridaLibs = listOf("frida", "frida-gadget")
        for (lib in fridaLibs) {
            if (isLibraryLoaded(lib)) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Verifica si una librería está cargada
     */
    private fun isLibraryLoaded(libName: String): Boolean {
        try {
            val process = Runtime.getRuntime().exec("cat /proc/self/maps")
            val output = process.inputStream.bufferedReader().readText()
            return output.contains(libName)
        } catch (e: Exception) {
            return false
        }
    }
    
    /**
     * Obtiene el nivel de confianza del dispositivo
     */
    fun getTrustLevel(): Float {
        val result = checkIntegrity()
        
        return when (result.severity) {
            IntegrityResult.Severity.SAFE -> 1.0f
            IntegrityResult.Severity.WARNING -> 0.5f
            IntegrityResult.Severity.CRITICAL -> 0.0f
        }
    }
}
