package com.example.neuroinfo

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.IBinder
import android.util.Log
import com.example.neuroinfo.model.CallNotificationDto
import com.example.neuroinfo.ui.incoming.IncomingCallActivity
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.app.Notification
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.app.NotificationChannel
import android.os.Build

class CallNotificationService : Service() {

    private lateinit var hubConnection: HubConnection
    private var mediaPlayer: MediaPlayer? = null

    // 💡 Замените на реальный адрес сервера, где находится SignalR хаб (тот же, что и BASE_URL)
    private val HUB_URL = "http://YOUR_BASE_URL_HERE/call"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // 💡 1. Инициализация и запуск SignalR
        initializeSignalR()
        // 💡 2. Создание уведомления для Foreground Service (требуется Android O+)
        startForeground(1, createForegroundNotification())
    }

    private fun initializeSignalR() {
        val sharedPrefs = getSharedPreferences("app_session", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("jwt_token", null)

        if (token == null) {
            Log.e("SignalR", "JWT Token is missing. Cannot connect.")
            return
        }

        // Настройка соединения
        hubConnection = HubConnectionBuilder.create(HUB_URL)
            // 💡 Добавляем токен для авторизации соединения с хабом
            .withHeader("Authorization", "Bearer $token")
            .build()

        // 💡 Прослушивание метода "ReceiveCall" (или как он назван на сервере)
        hubConnection.on("ReceiveCall", { notificationDto ->
            handleIncomingCall(notificationDto)
        }, CallNotificationDto::class.java)

        // Запуск соединения
        CoroutineScope(Dispatchers.IO).launch {
            try {
                hubConnection.start().blockingAwait()
                Log.i("SignalR", "Hub Connection successful.")
            } catch (e: Exception) {
                Log.e("SignalR", "Hub Connection failed: ${e.message}")
                // TODO: Реализовать логику переподключения
            }
        }
    }

    // Здесь должна быть логика создания Foreground Notification
    // (для простоты она пропущена, но она обязательна для Android 8.0+)
    private fun createForegroundNotification(): Notification {
        val CHANNEL_ID = "call_channel_id"
        val CHANNEL_NAME = "Incoming Calls"

        // Создание канала для Android 8.0+ (Oreo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        // Создание самого уведомления
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Активный вызов")
            .setContentText("Служба уведомлений активна")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // 💡 Логика обработки входящего вызова
    private fun handleIncomingCall(notification: CallNotificationDto) {
        Log.d("SignalR", "Incoming Call Received: ${notification.callNumber}")

        // 1. ПРОИГРЫВАНИЕ ЗВУКОВОГО СИГНАЛА
        playCallSound()

        // 2. АКТИВАЦИЯ ЭКРАНА И ЗАПУСК ACTIVITY
        launchIncomingCallActivity(notification)
    }

    private fun playCallSound() {
        // TODO: Замените R.raw.call_sound на реальный ID вашего звукового файла
        // mediaPlayer = MediaPlayer.create(this, R.raw.call_sound)
        // mediaPlayer?.isLooping = true // Должен играть, пока пользователь не отреагирует
        // mediaPlayer?.start()
    }

    private fun launchIncomingCallActivity(notification: CallNotificationDto) {
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            // Добавляем флаги для включения экрана/разблокировки
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP)

            // 💡 Флаги, необходимые для включения экрана, если приложение висит в фоне
            // В новых версиях Android (10+) для этого нужна специальная
            // системная привилегия (ACTION_MANAGE_OVERLAY_PERMISSION),
            // но эти флаги все еще помогают.
            // Также рекомендуется использовать KeyguardManager.newKeyguardLock

            // Передаем данные о вызове в Activity
            // TODO: Убедитесь, что CallNotificationDto Serializable или Parcelable
            putExtra("CALL_DATA", notification)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Очистка
        hubConnection.stop()
        mediaPlayer?.stop()
        mediaPlayer?.release()
    }
}