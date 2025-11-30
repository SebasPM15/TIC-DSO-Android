package com.mateopilco.ticdso.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

object PermissionUtils {

    /**
     * Permisos requeridos según versión de Android
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_MEDIA_IMAGES
            )
        } else {
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    /**
     * Verifica si todos los permisos están otorgados
     */
    fun hasAllPermissions(context: Context): Boolean {
        return getRequiredPermissions().all {
            ContextCompat.checkSelfPermission(context, it) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Verifica si tiene permiso de cámara específicamente
     */
    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Launcher para solicitar permisos (usar en Activity/Fragment)
     */
    fun createPermissionLauncher(
        activity: ComponentActivity,
        onResult: (Boolean) -> Unit
    ) = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        onResult(allGranted)
    }
}

/**
 * Extension function para verificar permisos fácilmente
 */
fun Context.hasRequiredPermissions(): Boolean {
    return PermissionUtils.hasAllPermissions(this)
}