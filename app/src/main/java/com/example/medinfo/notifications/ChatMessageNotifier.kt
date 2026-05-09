package com.example.medinfo.notifications

import android.Manifest
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
import com.example.medinfo.model.api.MessageOrigin
import com.example.medinfo.model.api.MessageResponseDto
import com.example.medinfo.model.api.MessageType
import com.example.medinfo.ui.chat.ChatActivity
import com.example.medinfo.ui.main.MainActivity
import com.example.medinfo.util.AppVisibilityTracker

// Показывает системные уведомления для входящих сообщений чата.
// Срабатывает только если соответствующий чат сейчас не открыт у пользователя.
object ChatMessageNotifier {

    const val CHANNEL_ID = "MedInfo_Chat_Messages"

    // Базовый id, к которому прибавляем хэш hospitalizationId — чтобы у каждого чата
    // было своё уведомление и они не затирали друг друга.
    private const val NOTIFICATION_ID_BASE = 2000

    // Главная точка входа. Вызывается из SignalRService на каждое входящее сообщение.
    fun notifyIfNeeded(
        context: Context,
        message: MessageResponseDto,
        chatTitle: String
    ) {
        if (!shouldShow(context, message))
            return

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
            MessageType.PATIENT_CONDITION -> "Получены данные о состоянии пациента"
            MessageType.TEXT -> message.text?.takeIf {it.isNotBlank()} ?: "Новое сообщение"
            else -> message.text?.takeIf { it.isNotBlank() } ?: "Новое сообщение"
        }
    }

    // PendingIntent, который открывает ChatActivity конкретного вызова.
    // TaskStackBuilder подкладывает MainActivity снизу — чтобы кнопка "Назад"
    // из чата вела в главный экран приложения, а не закрывала всё.
    private fun buildContentPendingIntent(
        context: Context,
        hospitalizationId: String,
        chatTitle: String
    ): PendingIntent {
        val chatIntent = Intent(context, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_HOSPITALIZATION_ID, hospitalizationId)
            putExtra(ChatActivity.EXTRA_CHAT_TITLE, chatTitle)
            putExtra(ChatActivity.EXTRA_READ_ONLY, false)
        }

        // Уникальный requestCode на каждый чат — иначе Android переиспользует один и тот же
        // PendingIntent и все уведомления будут открывать первый чат, по которому он создавался.
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

    // Удобный метод убрать старое уведомление по чату (например, когда пользователь
    // открыл чат и сообщения уже прочитаны).
    fun cancelFor(context: Context, hospitalizationId: String) {
        NotificationManagerCompat.from(context).cancel(notificationIdFor(hospitalizationId))
    }
}
