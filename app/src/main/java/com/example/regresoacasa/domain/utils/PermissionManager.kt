package com.example.regresoacasa.domain.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.regresoacasa.ui.viewmodel.NavigationViewModel

/**
 * Gestor centralizado de permisos de ubicación
 * Maneja solicitud, explicación y recuperación de permisos denegados
 */
object PermissionManager {
    
    const val REQ_LOCATION_PERMISSION = 1001
    
    /**
     * Verifica y solicita permiso de ubicación con diálogo explicativo si es necesario
     */
    fun checkAndRequestLocation(activity: Activity, onResult: (Boolean) -> Unit) {
        when {
            hasFineLocation(activity) -> onResult(true)
            
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity, 
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                showRationaleDialog(activity) { 
                    requestPermissions(activity) 
                }
            }
            
            else -> {
                requestPermissions(activity)
            }
        }
    }
    
    /**
     * Maneja el resultado de la solicitud de permisos desde MainActivity
     */
    fun handlePermissionResult(
        activity: Activity, 
        requestCode: Int, 
        permissions: Array<out String>, 
        grantResults: IntArray,
        onGranted: () -> Unit,
        onDeniedPermanent: () -> Unit
    ) {
        if (requestCode == REQ_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && 
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                onGranted()
            } else {
                // Permiso denegado
                if (!ActivityCompat.shouldShowRequestPermissionRationale(
                        activity, 
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )) {
                    // Denegado permanentemente ("Never ask again")
                    onDeniedPermanent()
                } else {
                    // Denegado temporalmente
                    onDeniedPermanent()
                }
            }
        }
    }
    
    /**
     * Muestra diálogo explicando por qué se necesita el permiso
     */
    private fun showRationaleDialog(activity: Activity, onConfirm: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Ubicación necesaria")
            .setMessage("Necesitamos tu ubicación para mostrar rutas en tiempo real y guiarte correctamente.")
            .setPositiveButton("Permitir") { _, _ -> onConfirm() }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    /**
     * Muestra diálogo para ir a configuración cuando el permiso fue denegado permanentemente
     */
    fun showSettingsDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("Activa la ubicación")
            .setMessage("Sin permiso de ubicación, la app no puede navegar. ¿Deseas ir a configuración para activarlo?")
            .setPositiveButton("Ir a configuración") { _, _ ->
                openAppSettings(activity)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    /**
     * Solicita permisos directamente
     */
    private fun requestPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            REQ_LOCATION_PERMISSION
        )
    }
    
    /**
     * Abre la pantalla de configuración de la aplicación
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    
    /**
     * Verifica si tiene permiso de ubicación fina
     */
    private fun hasFineLocation(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Verifica si tiene algún permiso de ubicación (fine o coarse)
     */
    fun hasAnyLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
