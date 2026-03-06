package com.example.medinfo.ui.incoming

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Helper class to manage permissions needed for showing IncomingCallActivity
 * over other apps and on top of lock screen.
 *
 * Note: For Android 10+ (API 29+), apps need special permissions to start activities
 * from background services. This helper guides users to enable these permissions.
 */
object IncomingCallPermissionHelper {

    /**
     * Check if the app can draw overlays (SYSTEM_ALERT_WINDOW permission).
     * This is required for Android 10+ to show activities from background.
     */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Request permission to draw overlays.
     * This opens system settings where user can enable the permission.
     */
    fun requestOverlayPermission(activity: Activity, requestCode: Int = 1001) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !canDrawOverlays(activity)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivityForResult(intent, requestCode)
        }
    }

    /**
     * Show a dialog explaining why the overlay permission is needed.
     */
    fun showOverlayPermissionRationale(activity: Activity, onProceed: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Разрешение на показ поверх других приложений")
            .setMessage(
                "Для корректной работы входящих вызовов необходимо разрешение " +
                        "на отображение поверх других приложений. " +
                        "Это позволит показывать экран вызова даже когда приложение " +
                        "закрыто или экран заблокирован."
            )
            .setPositiveButton("Перейти в настройки") { _, _ ->
                onProceed()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    /**
     * Check all required permissions for IncomingCallActivity to work properly.
     * Returns true if all permissions are granted, false otherwise.
     */
    fun checkAllPermissions(context: Context): Boolean {
        val hasOverlay = canDrawOverlays(context)


        return hasOverlay
    }

    /**
     * Show permission settings to user.
     * Call this from LoginActivity or MainActivity to ensure permissions are granted.
     */
    fun ensurePermissions(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Для Android 10+ необходимо разрешение на отображение поверх других приложений для надежного запуска активности в фоне
            if (!canDrawOverlays(activity)) {
                showOverlayPermissionRationale(activity) {
                    requestOverlayPermission(activity)
                }
            }
        }
    }
}
