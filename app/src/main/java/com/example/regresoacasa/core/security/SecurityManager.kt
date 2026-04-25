package com.example.regresoacasa.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec

private val Context.securityDataStore: DataStore<Preferences> by preferencesDataStore(name = "security")

class SecurityManager(private val context: Context) {
    
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }
    
    private val dataStore: DataStore<Preferences> = context.securityDataStore
    private val MASTER_KEY_ALIAS = "SafeReturnMasterKey"
    private val IV_KEY = stringPreferencesKey("encryption_iv")
    
    suspend fun getOrCreateMasterKey(): SecretKey {
        val existingKey = keyStore.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }
        
        return generateMasterKey()
    }
    
    private fun generateMasterKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        
        val keyGenSpec = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .setRandomizedEncryptionRequired(true)
            .build()
        
        keyGenerator.init(keyGenSpec)
        return keyGenerator.generateKey()
    }
    
    suspend fun encrypt(data: String): String {
        val key = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(data.toByteArray())
        
        // Save IV for decryption
        dataStore.edit { preferences ->
            preferences[IV_KEY] = Base64.getEncoder().encodeToString(iv)
        }
        
        return Base64.getEncoder().encodeToString(encryptedBytes)
    }
    
    suspend fun decrypt(encryptedData: String): String {
        val key = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        
        val preferences = dataStore.data.first()
        val ivString = preferences[IV_KEY] ?: throw IllegalStateException("IV not found")
        val iv = Base64.getDecoder().decode(ivString)
        
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        
        val encryptedBytes = Base64.getDecoder().decode(encryptedData)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        
        return String(decryptedBytes)
    }
    
    fun getCertificatePinner(): okhttp3.CertificatePinner? {
        // Certificate pinning deshabilitado temporalmente.
        // Para habilitar, ejecutar: openssl s_client -connect api.openrouteservice.org:443 -showcerts
        // Extraer certificado raíz/intermedio y ejecutar:
        // openssl x509 -in cert.pem -pubkey -noout | openssl rsa -pubin -outform der | openssl dgst -sha256 -binary | openssl enc -base64
        // Reemplazar el placeholder con el hash real.
        // TODO: Implementar certificate pinning real con pins válidos.
        return null
    }
    
    fun isDeviceSecure(): Boolean {
        // PASO 15: Root detection completo
        // Check common root paths
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
            if (java.io.File(path).exists()) {
                return false
            }
        }
        
        // Check for dangerous properties
        val dangerousProps = listOf(
            "ro.debuggable" to "1",
            "ro.secure" to "0"
        )
        
        for ((prop, value) in dangerousProps) {
            try {
                val propValue = getSystemProperty(prop)
                if (propValue == value) {
                    return false
                }
            } catch (e: Exception) {
                // Ignore property check failures
            }
        }
        
        // Check for root apps installed
        val rootApps = listOf(
            "com.noshufou.android.su",
            "com.thirdparty.superuser",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.topjohnwu.magisk"
        )
        
        for (packageName in rootApps) {
            try {
                context.packageManager.getPackageInfo(packageName, 0)
                return false
            } catch (e: PackageManager.NameNotFoundException) {
                // App not installed, continue
            }
        }
        
        // Check for debuggable build
        val appInfo = try {
            context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } catch (e: Exception) {
            return false
        }
        if ((appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            return !com.example.regresoacasa.BuildConfig.DEBUG
        }
        
        // Check if running in emulator
        val isEmulator = isRunningInEmulator()
        if (isEmulator && !com.example.regresoacasa.BuildConfig.DEBUG) {
            return false
        }
        
        return true
    }
    
    private fun getSystemProperty(prop: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java)
            get.invoke(clazz, prop) as? String
        } catch (e: Exception) {
            null
        }
    }
    
    private fun isRunningInEmulator(): Boolean {
        val emulatorProps = listOf(
            "ro.product.model" to "sdk",
            "ro.product.device" to "generic",
            "ro.hardware" to "goldfish",
            "ro.hardware" to "ranchu"
        )
        
        for ((prop, value) in emulatorProps) {
            val propValue = getSystemProperty(prop)
            if (propValue == value) {
                return true
            }
        }
        
        // Check for emulator-specific features
        val features = listOf(
            "android.hardware.telephony",
            "android.hardware.camera"
        )
        
        for (feature in features) {
            if (!context.packageManager.hasSystemFeature(feature)) {
                return true
            }
        }
        
        return false
    }
}
