package com.example.medinfo.data.signalr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.medinfo.R
import com.example.medinfo.config.ConfigManager
import com.example.medinfo.data.manager.CallsManager
import com.example.medinfo.data.manager.MessagesEventBus
import com.example.medinfo.data.network.RetrofitClient
import com.example.medinfo.data.repository.HospitalizationRepository
import com.example.medinfo.notifications.ChatMessageNotifier
import com.example.medinfo.model.api.HospitalizationDecision
import com.example.medinfo.model.api.HospitalizationResponseDto
import com.example.medinfo.model.api.HospitalizationStatus
import com.example.medinfo.model.api.MessageOrigin
import com.example.medinfo.model.api.MessageResponseDto
import com.example.medinfo.model.api.MessageType
import com.example.medinfo.model.api.ReceptionNotificationType
import com.example.medinfo.ui.incoming.InAppIncomingCallAlert
import com.example.medinfo.ui.incoming.IncomingCallActivity
import com.example.medinfo.ui.incoming.IncomingCallRinger
import com.example.medinfo.util.AppVisibilityTracker
import com.example.medinfo.util.CallLog
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SignalRService : Service() {

    private val testModeDisableSignalR = ConfigManager.testModeDisableSignalR
    private val hospitalizationRepository by lazy {
        HospitalizationRepository(RetrofitClient.apiServiceService)
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var hubConnection: HubConnection? = null
    private val channelIdService = ConfigManager.notificationChannelIdService
    private val notificationIdService = ConfigManager.notificationIdService

    private val foregroundRingDurationMs = 5000L
    private val decisionStateLock = Any()
    private val pendingDecisionCalls = mutableMapOf<String, HospitalizationResponseDto>()
    private val patientConditionReadyIds = mutableSetOf<String>()

    // Проверяем, что у приложения еще есть активная сессия.
    private fun isSessionActive(): Boolean {
        val sharedPrefs = getSharedPreferences(ConfigManager.sessionPrefsName, Context.MODE_PRIVATE)
        val isLoggedIn = sharedPrefs.getBoolean("isLoggedIn", false)
        val token = sharedPrefs.getString(ConfigManager.jwtTokenKey, "") ?: ""
        return isLoggedIn && token.isNotBlank()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isSessionActive()) {
            stopAndCleanup()
            return START_NOT_STICKY
        }

        val notification = NotificationCompat.Builder(this, channelIdService)
            .setContentTitle("Ожидание уведомлений")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(notificationIdService, notification)

        if (hubConnection == null) {
            initSignalR()
        }

        return START_STICKY
    }

    // Подключаемся к новому SignalR-хабу и подписываемся на два вида уведомлений.
    private fun initSignalR() {
        if (testModeDisableSignalR) {
            android.util.Log.i("CALL_LOG", "[SignalR] TEST MODE: SignalR disabled for testing")
            return
        }

        if (!isSessionActive()) {
            stopAndCleanup()
            return
        }

        val sharedPrefs = getSharedPreferences(ConfigManager.sessionPrefsName, Context.MODE_PRIVATE)
        val token = sharedPrefs.getString(ConfigManager.jwtTokenKey, "") ?: ""
        val hubUrl = ConfigManager.signalrHubUrl

        hubConnection = HubConnectionBuilder.create(hubUrl)
            .withAccessTokenProvider(Single.just(token))
            .build()

        hubConnection?.on(
            "HospitalizationNotification",
            { items ->
                if (!isSessionActive()) {
                    stopAndCleanup()
                    return@on
                }
                handleHospitalizationNotifications(items.orEmpty())
            },
            Array<HospitalizationResponseDto>::class.java
        )

        hubConnection?.on(
            "MessageNotification",
            { items ->
                if (!isSessionActive()) {
                    stopAndCleanup()
                    return@on
                }
                handleMessageNotifications(items.orEmpty())
            },
            Array<MessageResponseDto>::class.java
        )

        hubConnection?.onClosed {
            if (isSessionActive()) {
                startHubConnection()
            } else {
                stopAndCleanup()
            }
        }

        startHubConnection()
    }

    // Обрабатываем госпитализации как основной источник очереди входящих решений.
    private fun handleHospitalizationNotifications(items: Array<out HospitalizationResponseDto>) {
        CallLog.event("SignalR", "HospitalizationNotification count=${items.size}")

        val idsToConfirm = items
            .filter { !it.isNotificationSent }
            .map { it.id }

        if (idsToConfirm.isNotEmpty()) {
            CallLog.event("SignalR", "confirm hospitalization notifications count=${idsToConfirm.size}")
        }

        if (idsToConfirm.isNotEmpty()) {
            serviceScope.launch {
                runCatching {
                    hospitalizationRepository.confirmReception(
                        type = ReceptionNotificationType.NEW_HOSPITALIZATION,
                        ids = idsToConfirm
                    )
                }
            }
        }

        items.forEach { hospitalization ->
            CallLog.hospitalization(
                source = "SignalR",
                call = hospitalization,
                message = "received requiresDecision=${hospitalization.requiresIncomingDecision()}"
            )

            if (hospitalization.requiresIncomingDecision()) {
                handleIncomingDecisionCandidate(hospitalization)
            } else {
                CallLog.hospitalization(
                    source = "SignalR",
                    call = hospitalization,
                    message = "not requiring decision, removing from local queue"
                )
                synchronized(decisionStateLock) {
                    pendingDecisionCalls.remove(hospitalization.id)
                    patientConditionReadyIds.remove(hospitalization.id)
                }
                CallsManager.removeCall(hospitalization.id)
                if (CallsManager.calls.value.isEmpty()) {
                    IncomingCallRinger.stop()
                }
            }
        }
    }

    // Для сообщений подтверждаем только уведомления от планшета.
    private fun handleMessageNotifications(items: Array<out MessageResponseDto>) {
        CallLog.event("SignalR", "MessageNotification count=${items.size}")

        // Realtime: пробрасываем все сообщения в шину — открытый ChatActivity
        // подпишется и отрисует сообщение, если оно для его hospitalizationId.
        items.forEach { MessagesEventBus.emit(it) }

        // Системные уведомления для сообщений в "неоткрытых" чатах. Внутри notifier
        // сам решает, нужно ли его показывать (проверка origin/foreground/permission).
        items.forEach { message ->
            val chatTitle = buildChatTitle(message.hospitalizationId)
            ChatMessageNotifier.notifyIfNeeded(this, message, chatTitle)
        }

        items
            .filter { MessageOrigin.fromId(it.origin) == MessageOrigin.TABLET }
            // Сообщение от бригады считается моментом, когда врачу реально нужно начать принимать решение.
            .forEach { message ->
                if (ConfigManager.decisionTriggerMode == ConfigManager.DecisionTriggerMode.PATIENT_CONDITION) {
                    if (message.isPatientConditionFromTablet()) {
                        handlePatientConditionDecisionTrigger(message)
                    }
                } else {
                    CallLog.message("SignalR", message, "tablet message starts/keeps decision timer")
                    CallsManager.markDecisionTimerStarted(message.hospitalizationId)
                }
            }

        val idsToConfirm = items
            .filter { !it.isNotificationSent && MessageOrigin.fromId(it.origin) == MessageOrigin.TABLET }
            .map { it.id }

        if (idsToConfirm.isNotEmpty()) {
            CallLog.event("SignalR", "confirm brigade message notifications count=${idsToConfirm.size}")
        }

        if (idsToConfirm.isNotEmpty()) {
            serviceScope.launch {
                runCatching {
                    hospitalizationRepository.confirmReception(
                        type = ReceptionNotificationType.BRIGADE_MESSAGE,
                        ids = idsToConfirm
                    )
                }
            }
        }
    }

    // Для правильного названия чата
    private fun handleIncomingDecisionCandidate(hospitalization: HospitalizationResponseDto) {
        if (ConfigManager.decisionTriggerMode == ConfigManager.DecisionTriggerMode.HOSPITALIZATION) {
            promoteToDecisionCall(hospitalization)
            return
        }

        val shouldPromote = synchronized(decisionStateLock) {
            pendingDecisionCalls[hospitalization.id] = hospitalization
            patientConditionReadyIds.contains(hospitalization.id)
        }

        if (shouldPromote) {
            promoteToDecisionCall(hospitalization)
        } else {
            CallLog.hospitalization(
                source = "SignalR",
                call = hospitalization,
                message = "waiting for PATIENT_CONDITION before decision call"
            )
        }
    }

    private fun handlePatientConditionDecisionTrigger(message: MessageResponseDto) {
        val hospitalization = synchronized(decisionStateLock) {
            patientConditionReadyIds.add(message.hospitalizationId)
            pendingDecisionCalls[message.hospitalizationId]
        }

        CallsManager.markDecisionTimerStarted(message.hospitalizationId)

        if (hospitalization != null && hospitalization.requiresIncomingDecision()) {
            promoteToDecisionCall(hospitalization)
        } else {
            CallLog.message("SignalR", message, "PATIENT_CONDITION received before hospitalization candidate")
        }
    }

    private fun promoteToDecisionCall(hospitalization: HospitalizationResponseDto) {
        val isNew = CallsManager.upsertCall(hospitalization)

        synchronized(decisionStateLock) {
            pendingDecisionCalls.remove(hospitalization.id)
        }

        if (isNew) {
            alertIncomingDecisionCall(hospitalization)
        } else {
            CallLog.hospitalization(
                source = "SignalR",
                call = hospitalization,
                message = "updated existing decision call"
            )
        }
    }

    private fun MessageResponseDto.isPatientConditionFromTablet(): Boolean {
        return MessageOrigin.fromId(origin) == MessageOrigin.TABLET &&
            MessageType.fromId(type) == MessageType.PATIENT_CONDITION &&
            patientCondition != null
    }

    private fun buildChatTitle(hospitalizationId: String): String {
        val hospitalization = CallsManager.calls.value.firstOrNull { it.id == hospitalizationId }
        return if (hospitalization != null) {
            val call = hospitalization.call
            "Вызов №${call.dayNumber}/${call.yearNumber}"
        } else {
            "Сообщение по вызову"
        }
    }

    // Запуск подключения вынесен отдельно, чтобы переиспользовать при реконнекте.
    private fun startHubConnection() {
        Thread {
            try {
                if (!isSessionActive()) {
                    stopAndCleanup()
                    return@Thread
                }
                hubConnection?.start()?.blockingAwait()
            } catch (e: Exception) {
                Thread.sleep(5000)
                if (isSessionActive()) {
                    startHubConnection()
                } else {
                    stopAndCleanup()
                }
            }
        }.start()
    }

    private fun stopAndCleanup() {
        stopAndCleanupInternal(stopSelf = true)
    }

    // Единая очистка подключения, звонка и очереди.
    private fun stopAndCleanupInternal(stopSelf: Boolean) {
        try {
            hubConnection?.stop()
        } catch (_: Exception) {
        }

        hubConnection = null
        IncomingCallRinger.stop()
        CallsManager.clearAll()
        synchronized(decisionStateLock) {
            pendingDecisionCalls.clear()
            patientConditionReadyIds.clear()
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationIdService)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        if (stopSelf) {
            stopSelf()
        }
    }

    // Если врач уже на IncomingCallActivity — показываем in-app алерт с brief-звоном.
    // Иначе запускаем экран входящего вызова на весь экран.
    private fun alertIncomingDecisionCall(hospitalization: HospitalizationResponseDto) {
        val foregroundActivity =
            if (AppVisibilityTracker.isAppInForeground) {
                AppVisibilityTracker.currentActivity()
            } else {
                null
            }

        if (foregroundActivity is IncomingCallActivity) {
            CallLog.hospitalization(
                source = "SignalR",
                call = hospitalization,
                message = "new incoming decision call, showing in-app top alert"
            )
            IncomingCallRinger.startBrief(this, foregroundRingDurationMs)
            InAppIncomingCallAlert.show(foregroundActivity, hospitalization)
        } else {
            CallLog.hospitalization(
                source = "SignalR",
                call = hospitalization,
                message = "new incoming decision call, opening fullscreen alert"
            )
            triggerFullscreenAlert(hospitalization)
        }
    }

    private fun triggerFullscreenAlert(hospitalization: HospitalizationResponseDto) {
        IncomingCallRinger.start(this)

        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            putExtra(IncomingCallActivity.EXTRA_HOSPITALIZATION, hospitalization)
        }

        try {
            startActivity(fullScreenIntent)
        } catch (e: Exception) {
            android.util.Log.e("CALL_LOG", "[SignalR] FAILED to launch incoming call activity: ${e.message}", e)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            val serviceChannel = NotificationChannel(
                channelIdService,
                "MedInfo: подключение к серверу",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Фоновое уведомление для работы SignalR"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }

            // Канал для входящих сообщений чата. IMPORTANCE_HIGH — чтобы уведомление
            // всплывало сверху (heads-up), со звуком и вибрацией. Пользователь может
            // потом сам приглушить его в системных настройках — это нормально.
            val chatMessagesChannel = NotificationChannel(
                ChatMessageNotifier.CHANNEL_ID,
                "Сообщения чата",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Новые сообщения от бригады по вызовам"
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }

            manager.createNotificationChannel(serviceChannel)
            manager.createNotificationChannel(chatMessagesChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopAndCleanupInternal(stopSelf = false)
        super.onDestroy()
    }

    // Во входящий экран попадают только активные госпитализации без решения.
    private fun HospitalizationResponseDto.requiresIncomingDecision(): Boolean {
        return decisionId == HospitalizationDecision.NONE.id &&
            HospitalizationStatus.fromId(statusId)?.isActive == true
    }
}
