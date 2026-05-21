package com.example.medinfo.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import com.example.medinfo.config.ConfigManager
import com.example.medinfo.ui.incoming.IncomingCallActivity
import com.example.medinfo.util.TestCallFactory

// Запланированный тестовый вызов для проверки работы Doze-режима. Значение проверяется
// и при планировании, и при срабатывании — чтобы выключение тумблера в конфиге глушило
// уже стоящие в очереди будильники.
class FakeCallAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ConfigManager.testCallEnabled) {
            android.util.Log.i(
                "CALL_LOG",
                "[FakeCallAlarmReceiver] Alarm fired but testCallEnabled=false, skipping"
            )
            return
        }

        android.util.Log.i("CALL_LOG", "[FakeCallAlarmReceiver] Alarm fired! Triggering fake call...")

        // Wake up the device.
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "MedInfo:FakeCallWakeLock"
        )
        wakeLock.acquire(30_000) // 30 seconds

        try {
            // Используем единый набор тестовых данных, что и кнопка на главном экране.
            val mockHospitalization = TestCallFactory.buildMockHospitalization()

            val fullScreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(IncomingCallActivity.EXTRA_HOSPITALIZATION, mockHospitalization)
                putExtra(IncomingCallActivity.EXTRA_IS_TEST_CALL, true)
            }

            context.startActivity(fullScreenIntent)
            android.util.Log.i(
                "CALL_LOG",
                "[FakeCallAlarmReceiver] Fake call activity launched successfully"
            )
        } catch (e: Exception) {
            android.util.Log.e(
                "CALL_LOG",
                "[FakeCallAlarmReceiver] Failed to launch activity: ${e.message}"
            )
        } finally {
            wakeLock.release()
        }
    }

    companion object {
        private const val REQUEST_CODE_FAKE_CALL = 1001

        fun scheduleFakeCall(
            context: Context,
            delayMinutes: Int = ConfigManager.fakeCallDelayMinutes
        ) {
            if (!ConfigManager.testCallEnabled) {
                android.widget.Toast.makeText(
                    context,
                    "Тестовый вызов выключен в настройках",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, FakeCallAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_FAKE_CALL,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerTime = System.currentTimeMillis() + (delayMinutes * 60 * 1000L)

            android.util.Log.i(
                "CALL_LOG",
                "[FakeCallAlarmReceiver] Scheduling fake call in $delayMinutes minutes at ${java.util.Date(triggerTime)}"
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    // Если exact alarm запрещён системой, оставляем тест рабочим через обычный будильник.
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                    android.widget.Toast.makeText(
                        context,
                        "Точный будильник недоступен, тестовый вызов запланирован примерно через $delayMinutes минут",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }

                android.widget.Toast.makeText(
                    context,
                    "Тестовый вызов запланирован через $delayMinutes минут",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } catch (e: SecurityException) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                android.widget.Toast.makeText(
                    context,
                    "Нет разрешения на точный будильник, тестовый вызов запланирован примерно через $delayMinutes минут",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }

        fun cancelFakeCall(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, FakeCallAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_FAKE_CALL,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            android.widget.Toast.makeText(
                context,
                "Тестовый вызов отменён",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }
}
