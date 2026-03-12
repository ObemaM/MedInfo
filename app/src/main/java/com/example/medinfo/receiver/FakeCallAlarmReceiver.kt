package com.example.medinfo.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import com.example.medinfo.config.ConfigManager
import com.example.medinfo.model.ArterialTourniquetInfo
import com.example.medinfo.model.BleedingInfo
import com.example.medinfo.model.CallNotificationDto
import com.example.medinfo.model.IfaInfo
import com.example.medinfo.model.VenousAccessInfo
import com.example.medinfo.ui.incoming.IncomingCallActivity

class FakeCallAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.i("CALL_LOG", "[FakeCallAlarmReceiver] Alarm fired! Triggering fake call...")
        
        // Wake up the device
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "MedInfo:FakeCallWakeLock"
        )
        wakeLock.acquire(30000) // 30 seconds
        
        // Create fake call data
        val mockCall = CallNotificationDto(
            fullName = "ТЕСТОВЫЙ ВЫЗОВ (Doze Mode Test)",
            age = "45",
            sex = "Муж",
            reason = "Проверка работы в Doze режиме",
            additionalInfo = "Вызов через 35 минут",
            district = "Центральный",
            point = "Москва",
            street = "Тестовая",
            house = "1",
            apartment = "1",
            enterance = 1,
            longitude = 55.7558,
            latitude = 37.6176,
            brigadeNumber = 404,
            brigadeProfile = "Тестовая",
            callNumber = "TEST/${System.currentTimeMillis() % 1000}",
            callTime = java.time.Instant.now().toString(),
            urgency = 1,
            status = "транспортировка",
            bloodPressure = "120/80",
            consciousness = "Ясное",
            convulsions = false,
            glucometry = 5,
            heartRate = 72,
            oxygenSupport = true,
            pregnant = false,
            respirationRate = 16,
            spO2 = 98,
            startDisease = 2,
            stenosis = false,
            temperature = 36.6,
            lams = 0,
            mrs = 0,
            vas = 3,
            bleeding = BleedingInfo(presence = false, type = null, arterialTourniquet = ArterialTourniquetInfo(presence = false, applicationTime = null)),
            venousAccess = VenousAccessInfo(presence = true, method = listOf("Периферическая вена")),
            ifa = IfaInfo(presence = false, tool = null, alv = false),
            messageId = 0,
            messageValue = null,
            dprm = "2026-03-10",
            ngod = 2026,
            numv = 1,
            ssmp = 10,
            team = 404,
            vozr = "45"
        )
        
        // Launch incoming call activity
        val fullScreenIntent = Intent(context, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("CALL_DATA", mockCall)
        }
        
        try {
            context.startActivity(fullScreenIntent)
            android.util.Log.i("CALL_LOG", "[FakeCallAlarmReceiver] Fake call activity launched successfully")
        } catch (e: Exception) {
            android.util.Log.e("CALL_LOG", "[FakeCallAlarmReceiver] Failed to launch activity: ${e.message}")
        } finally {
            wakeLock.release()
        }
    }
    
    companion object {
        private const val REQUEST_CODE_FAKE_CALL = 1001
        
        fun scheduleFakeCall(context: Context, delayMinutes: Int = ConfigManager.fakeCallDelayMinutes) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            val intent = Intent(context, FakeCallAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_FAKE_CALL,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val triggerTime = System.currentTimeMillis() + (delayMinutes * 60 * 1000L)
            
            android.util.Log.i("CALL_LOG", "[FakeCallAlarmReceiver] Scheduling fake call in $delayMinutes minutes at ${java.util.Date(triggerTime)}")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Use setExactAndAllowWhileIdle to bypass Doze mode
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
            
            android.widget.Toast.makeText(context, "Тестовый вызов запланирован через $delayMinutes минут", android.widget.Toast.LENGTH_LONG).show()
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
            android.widget.Toast.makeText(context, "Тестовый вызов отменен", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
