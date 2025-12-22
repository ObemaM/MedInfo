package com.example.neuroinfo.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.neuroinfo.R
import com.example.neuroinfo.model.CallNotificationDto
import com.example.neuroinfo.ui.incoming.IncomingCallActivity
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import io.reactivex.rxjava3.core.Single

class SignalRService : Service() {

    private var hubConnection: HubConnection? = null
    private val CHANNEL_ID = "NeuroInfo_SignalR_Channel"
    private val NOTIFICATION_ID = 101

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Сразу запускаем сервис как Foreground, чтобы Android его не закрыл
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NeuroInfo: Связь установлена")
            .setContentText("Ожидание новых вызовов...")
            .setSmallIcon(R.mipmap.ic_launcher) // Проверь, что иконка существует
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // 2. Инициализируем SignalR, если еще не сделано
        if (hubConnection == null) {
            initSignalR()
        }

        return START_STICKY // Перезапускать сервис, если система его все же прибьет
    }

    private fun initSignalR() {
        val sharedPrefs = getSharedPreferences("app_session", Context.MODE_PRIVATE)
        val token = sharedPrefs.getString("jwt_token", "") ?: ""

        // Твой адрес хаба
        val hubUrl = "http://46.146.213.95:27234/call"

        hubConnection = HubConnectionBuilder.create(hubUrl)
            .withAccessTokenProvider(Single.just(token)) // Передаем JWT токен
            .build()

        // 3. ПОДПИСКА НА СОБЫТИЕ "ReceiveCall"
        hubConnection?.on("ReceiveCall", { callData ->
            Log.d("SignalR", "ПРИШЕЛ НОВЫЙ ВЫЗОВ: ${callData.fullName}")

            // Запускаем экран входящего вызова
            showIncomingCall(callData)

        }, CallNotificationDto::class.java)

        // Логика переподключения
        hubConnection?.onClosed { exception ->
            Log.e("SignalR", "Соединение потеряно. Переподключение... ${exception?.message}")
            startHubConnection()
        }

        startHubConnection()
    }

    private fun startHubConnection() {
        Thread {
            try {
                hubConnection?.start()?.blockingAwait()
                Log.i("SignalR", "Успешно подключено к хабу!")
            } catch (e: Exception) {
                Log.e("SignalR", "Ошибка подключения: ${e.message}")
                // Ждем 5 секунд и пробуем снова
                Thread.sleep(5000)
                startHubConnection()
            }
        }.start()
    }

    private fun showIncomingCall(callData: CallNotificationDto) {
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            // Эти флаги нужны, чтобы экран открылся даже поверх заблокированного телефона
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("CALL_DATA", callData)
        }
        startActivity(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Системные уведомления NeuroInfo",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        hubConnection?.stop()
        super.onDestroy()
    }
}