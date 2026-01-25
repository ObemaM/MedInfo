package com.example.neuroinfo.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.neuroinfo.R
import com.example.neuroinfo.data.CallsManager
import com.example.neuroinfo.model.CallNotificationDto
import com.example.neuroinfo.ui.incoming.IncomingCallActivity
import com.example.neuroinfo.util.IncomingCallRinger
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import io.reactivex.rxjava3.core.Single

class SignalRService : Service() {

    private var hubConnection: HubConnection? = null
    private val CHANNEL_ID_SERVICE = "NeuroInfo_SignalR_Service"
    private val CHANNEL_ID_INCOMING_CALL = "NeuroInfo_Incoming_Call"

    private val NOTIFICATION_ID_SERVICE = 101
    private val NOTIFICATION_ID_INCOMING_CALL = 102

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Запуск Foreground для живучести сервиса
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_SERVICE)
            .setContentTitle("NeuroInfo: Связь с сервером")
            .setContentText("Приложение готово к приему вызовов")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID_SERVICE, notification)

        if (hubConnection == null) {
            initSignalR()
        }

        return START_STICKY
    }

    private fun initSignalR() {
        val sharedPrefs = getSharedPreferences("app_session", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("jwt_token", "") ?: ""
        val hubUrl = "http://46.146.213.95:27234/call"

        hubConnection = HubConnectionBuilder.create(hubUrl)
            .withAccessTokenProvider(Single.just(token))
            .build()

        // Главный обработчик входящих данных
        hubConnection?.on("Receive", { callData ->
            Log.d("SignalR", "Пришел вызов №${callData.callNumber} со статусом: ${callData.status}")

            // 1. Всегда добавляем в менеджер очереди
            CallsManager.addCall(callData)

            // 2. Реагируем в зависимости от статуса (ТЗ заказчика)
            when (callData.status?.lowercase()) {
                "транспортировка" -> {
                    // Критическая ситуация: показываем экран и нотификацию
                    triggerFullscreenAlert(callData)
                    // Также обновляем список (врач увидит в MainActivity)
                    Log.d("SignalR", "Вызов добавлен в активные (транспортировка)")
                }
                "результат", "архив" -> {
                    // Удаляем из активных, так как вызов завершен
                    callData.callNumber?.let { CallsManager.removeCall(it) }
                    IncomingCallRinger.stop()
                }
            }
        }, CallNotificationDto::class.java)

        hubConnection?.onClosed { exception ->
            Log.e("SignalR", "Соединение закрыто. Ошибка: ${exception?.message}")
            startHubConnection()
        }

        startHubConnection()
    }

    private fun startHubConnection() {
        // Используем Thread только для старта, чтобы не блокировать UI
        Thread {
            try {
                hubConnection?.start()?.blockingAwait()
                Log.i("SignalR", "Соединение установлено")
            } catch (e: Exception) {
                Log.e("SignalR", "Ошибка старта: ${e.message}. Повтор через 5с...")
                Thread.sleep(5000)
                startHubConnection()
            }
        }.start()
    }

    private fun triggerFullscreenAlert(callData: CallNotificationDto) {
        IncomingCallRinger.start(this, 10_000L)

        // Создаем Intent для открытия IncomingCallActivity
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            // Передаем данные, но Activity также подхватит их из CallsManager
            putExtra("CALL_DATA", callData)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val caller = callData.fullName?.takeIf { it.isNotBlank() } ?: "Входящий вызов"
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_INCOMING_CALL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("СРОЧНО: Транспортировка")
            .setContentText("$caller - требуется подтверждение")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true) // Пробивает спящий режим
            .setAutoCancel(false)
            .setOngoing(true)
            .setTimeoutAfter(2_400_000) // Автоматически закрыть через 40 минут
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_INCOMING_CALL, notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return

            val serviceChannel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "NeuroInfo: подключение к серверу",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Фоновое уведомление для работы SignalR"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val incomingCallChannel = NotificationChannel(
                CHANNEL_ID_INCOMING_CALL,
                "NeuroInfo: входящие вызовы",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Канал для входящих вызовов (транспортировка)"
                enableVibration(true)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(incomingCallChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hubConnection?.stop()
        super.onDestroy()
    }
}