package com.example.medinfo.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import kotlin.system.exitProcess

object PermissionManager {

    data class PermissionStatus(
        val allGranted: Boolean,
        val missingPermissions: List<MissingPermission>
    )

    data class MissingPermission(
        val name: String,
        val description: String,
        val settingsAction: () -> Unit
    )

    fun checkAllPermissions(context: Context): PermissionStatus {
        val missing = mutableListOf<MissingPermission>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                missing.add(
                    MissingPermission(
                        name = "Оптимизация батареи",
                        description = "Для работы в фоновом режиме необходимо отключить оптимизацию батареи для этого приложения."
                    ) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        try {
                            (context as Activity).startActivity(intent)
                        } catch (e: Exception) {
                            openAppSettings(context)
                        }
                    }
                )
            }
        }

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            missing.add(
                MissingPermission(
                    name = "Уведомления",
                    description = "Для работы приложения необходимо разрешить уведомления."
                ) {
                    val intent = Intent().apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        } else {
                            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            data = Uri.parse("package:${context.packageName}")
                        }
                    }
                    (context as Activity).startActivity(intent)
                }
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                missing.add(
                    MissingPermission(
                        name = "Отображение поверх других приложений",
                        description = "Для отображения вызовов на заблокированном экране необходимо разрешение."
                    ) {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        (context as Activity).startActivity(intent)
                    }
                )
            }
        }

        return PermissionStatus(
            allGranted = missing.isEmpty(),
            missingPermissions = missing
        )
    }

    fun enforcePermissions(activity: Activity, onAllGranted: (() -> Unit)? = null) {
        val status = checkAllPermissions(activity)
        
        if (status.allGranted) {
            onAllGranted?.invoke()
            return
        }

        showPermissionDialog(activity, status.missingPermissions)
    }

    private fun showPermissionDialog(activity: Activity, missingPermissions: List<MissingPermission>) {
        if (missingPermissions.isEmpty()) return

        val firstMissing = missingPermissions.first()

        AlertDialog.Builder(activity)
            .setTitle(firstMissing.name)
            .setMessage("${firstMissing.description}\n\nПриложение не может работать без этого разрешения.")
            .setCancelable(false)
            .setPositiveButton("Открыть настройки") { _, _ ->
                firstMissing.settingsAction()
            }
            .setNegativeButton("Выйти из приложения") { _, _ ->
                exitApp(activity)
            }
            .show()
    }

    fun exitApp(activity: Activity) {
        activity.finishAffinity()
        exitProcess(0)
    }

    private fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        (context as Activity).startActivity(intent)
    }
}
