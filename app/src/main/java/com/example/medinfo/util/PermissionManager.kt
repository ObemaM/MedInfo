package com.example.medinfo.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlin.system.exitProcess

object PermissionManager {

    const val REQUEST_CODE_CALL_PHONE = 102
    private const val BATTERY_OPTIMIZATION_RECHECK_DELAY_MS = 1000L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var delayNextBatteryOptimizationCheck = false
    private var delayedPermissionCheck: Runnable? = null
    private var delayedPermissionDialog: AlertDialog? = null

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
                            delayNextBatteryOptimizationCheck = true
                            (context as Activity).startActivity(intent)
                        } catch (e: Exception) {
                            delayNextBatteryOptimizationCheck = true
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

        // CALL_PHONE — runtime-разрешение для ACTION_CALL из ChatActivity.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            missing.add(
                MissingPermission(
                    name = "Совершение звонков",
                    description = "Для прямого вызова бригады из чата приложению необходимо " +
                            "разрешение \"Телефон\"."
                ) {
                    // Системный диалог + настройки приложения (страховка на DON'T_ASK_AGAIN).
                    val activity = context as Activity
                    androidx.core.app.ActivityCompat.requestPermissions(
                        activity,
                        arrayOf(Manifest.permission.CALL_PHONE),
                        REQUEST_CODE_CALL_PHONE
                    )
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    activity.startActivity(intent)
                }
            )
        }

        return PermissionStatus(
            allGranted = missing.isEmpty(),
            missingPermissions = missing
        )
    }

    fun enforcePermissions(activity: Activity, onAllGranted: (() -> Unit)? = null) {
        if (delayedPermissionCheck != null) return

        if (delayNextBatteryOptimizationCheck) {
            delayNextBatteryOptimizationCheck = false

            // Показ секундного диалога ожидания, чтобы ОС точно проверила разрешения
            showDelayedPermissionDialog(activity)
            delayedPermissionCheck = Runnable {
                delayedPermissionCheck = null
                dismissDelayedPermissionDialog()
                if (!activity.isFinishing && !activity.isDestroyed) {
                    enforcePermissions(activity, onAllGranted)
                }
            }.also { check ->
                mainHandler.postDelayed(check, BATTERY_OPTIMIZATION_RECHECK_DELAY_MS)
            }
            return
        }

        val status = checkAllPermissions(activity)
        
        if (status.allGranted) {
            onAllGranted?.invoke()
            return
        }

        showPermissionDialog(activity, status.missingPermissions)
    }

    private fun showDelayedPermissionDialog(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed || delayedPermissionDialog?.isShowing == true) return

        delayedPermissionDialog = AlertDialog.Builder(activity)
            .setMessage("Проверка разрешений...")
            .setCancelable(false)
            .create()
            .also { it.show() }
    }

    private fun dismissDelayedPermissionDialog() {
        delayedPermissionDialog?.dismiss()
        delayedPermissionDialog = null
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
