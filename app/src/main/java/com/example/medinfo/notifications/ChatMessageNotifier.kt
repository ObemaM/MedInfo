package com.example.medinfo.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.medinfo.R
import com.example.medinfo.data.manager.CallsManager
import com.example.medinfo.model.api.MessageOrigin
import com.example.medinfo.model.api.MessageResponseDto
import com.example.medinfo.model.api.MessageType
import com.example.medinfo.ui.chat.ChatActivity
import com.example.medinfo.ui.main.MainActivity
import com.example.medinfo.util.AppVisibilityTracker

// Системные уведомления для входящих сообщений чата (если чат не открыт у пользователя).
object ChatMessageNotifier {

    const val CHANNEL_ID = "MedInfo_Chat_Messages"

    // База + хэш hospitalizationId — у каждого чата своё уведомление, не затирают друг друга.
    private const val NOTIFICATION_ID_BASE = 2000
    private const val MAX_NOTIFIED_MESSAGE_IDS = 100

    private val notifiedMessageIds = linkedSetOf<String>()

    // Lint не видит permission check в shouldShow → hasPostNotificationsPermission.
    @SuppressLint("MissingPermission")
    fun notifyIfNeeded(
        context: Context,
        message: MessageResponseDto,
        chatTitle: String
    ) {
        if (!shouldShow(context, message))
            return

        if (!rememberNotification(message.id)) return

        val notification = buildNotification(context, message, chatTitle)
        val notificationId = notificationIdFor(message.hospitalizationId)
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    private fun shouldShow(context: Context, message: MessageResponseDto): Boolean {
        // Не от планшета (бригады) — это либо своё отправленное сообщение, либо системное.
        if (MessageOrigin.fromId(message.origin) != MessageOrigin.TABLET) return false
        // Этот чат уже открыт пользователем — он и так видит сообщение, шторку не трогаем.
        if (isChatScreenOpenFor(message.hospitalizationId)) return false
        // Пользователь не дал разрешение POST_NOTIFICATIONS — система всё равно проигнорирует.
        if (!hasPostNotificationsPermission(context)) return false
        return true
    }

    @Synchronized
    private fun rememberNotification(messageId: String): Boolean {
        if (!notifiedMessageIds.add(messageId)) return false

        while (notifiedMessageIds.size > MAX_NOTIFIED_MESSAGE_IDS) {
            val oldest = notifiedMessageIds.firstOrNull() ?: break
            notifiedMessageIds.remove(oldest)
        }

        return true
    }

    // Как выглядит уведомление
    private fun buildNotification(
        context: Context,
        message: MessageResponseDto,
        chatTitle: String
    ): android.app.Notification {
        val contentIntent = buildContentPendingIntent(context, message.hospitalizationId, chatTitle)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(chatTitle)
            .setContentText(previewFor(message))
            // BigTextStyle — длинный текст раскроется при разворачивании уведомления.
            .setStyle(NotificationCompat.BigTextStyle().bigText(previewFor(message)))
            .setPriority(NotificationCompat.PRIORITY_HIGH) // важно на Android < 8 (там нет каналов)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true) // уведомление пропадёт после тапа
            .setContentIntent(contentIntent)
            .build()
    }

    // Превью текста сообщения в уведомлении
    private fun previewFor(message: MessageResponseDto): String {
        return when (MessageType.fromId(message.type)) {
            MessageType.PATIENT_CONDITION -> "Получены новые данные о состоянии пациента"
            MessageType.CONSULTATION_REQUEST -> "Запрошена консультация"
            MessageType.TEXT -> message.text?.takeIf {it.isNotBlank()} ?: "Новое сообщение"
            else -> message.text?.takeIf { it.isNotBlank() } ?: "Новое сообщение"
        }
    }

    // Открывает ChatActivity; MainActivity подкладывается снизу для корректной кнопки "Назад".
    private fun buildContentPendingIntent(
        context: Context,
        hospitalizationId: String,
        chatTitle: String
    ): PendingIntent {
        val knownHospitalization = CallsManager.calls.value.firstOrNull {
            it.id == hospitalizationId
        }
        val chatIntent = Intent(context, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_HOSPITALIZATION_ID, hospitalizationId)
            putExtra(ChatActivity.EXTRA_CHAT_TITLE, chatTitle)
            putExtra(ChatActivity.EXTRA_READ_ONLY, false)
            knownHospitalization?.let {
                putExtra(ChatActivity.EXTRA_DECISION_ID, it.decisionId)
                putExtra(ChatActivity.EXTRA_STATUS_ID, it.statusId)
            }
        }

        // Уникальный requestCode на чат — иначе PendingIntent будет один на всех.
        val requestCode = hospitalizationId.hashCode()

        // FLAG_IMMUTABLE обязателен с Android 12 (API 31).
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        return TaskStackBuilder.create(context)
            .addNextIntentWithParentStack(Intent(context, MainActivity::class.java))
            .addNextIntent(chatIntent)
            .getPendingIntent(requestCode, flags)
            ?: PendingIntent.getActivity(context, requestCode, chatIntent, flags)
    }

    private fun notificationIdFor(hospitalizationId: String): Int {
        return NOTIFICATION_ID_BASE + (hospitalizationId.hashCode() and 0x7fffffff) % 100_000
    }

    private fun isChatScreenOpenFor(hospitalizationId: String): Boolean {
        if (AppVisibilityTracker.isAppInForeground) {
            val currentScreen = AppVisibilityTracker.currentActivity()

            if (currentScreen is ChatActivity) {
                if (currentScreen.chatId == hospitalizationId)
                    return true
            }
        }

        return false
    }

    private fun hasPostNotificationsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Снять висящее уведомление по чату (например, когда юзер его открыл).
    fun cancelFor(context: Context, hospitalizationId: String) {
        NotificationManagerCompat.from(context).cancel(notificationIdFor(hospitalizationId))
    }
}
