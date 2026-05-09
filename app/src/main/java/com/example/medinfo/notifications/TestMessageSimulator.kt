package com.example.medinfo.notifications

import android.content.Context
import com.example.medinfo.data.manager.CallsManager
import com.example.medinfo.data.manager.MessagesEventBus
import com.example.medinfo.model.api.MessageOrigin
import com.example.medinfo.model.api.MessageResponseDto
import com.example.medinfo.model.api.MessageType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

// Дев-хелпер: имитирует получение сообщения от бригады для нужного hospitalizationId.
// Используется в тестовых кнопках на экране звонка и в чате — чтобы было удобно
// проверить и realtime-обновление открытого чата, и системное уведомление.
//
// Можно безопасно удалить на проде — ни SignalR, ни боевой UI на этот объект не завязаны.
object TestMessageSimulator {

    fun simulateBrigadeMessage(
        context: Context,
        hospitalizationId: String,
        text: String = "Тестовое сообщение от бригады"
    ) {
        // Собираем валидный MessageResponseDto, как будто его прислал планшет бригады.
        // Всё остальное в приложении уже умеет с такими сообщениями работать.
        val message = MessageResponseDto(
            id = UUID.randomUUID().toString(),
            hospitalizationId = hospitalizationId,
            origin = MessageOrigin.TABLET.id, // важно: иначе ChatMessageNotifier его отфильтрует
            userId = null,
            type = MessageType.TEXT.id,
            isNotificationSent = true,
            receptionTime = currentIsoTime(),
            patientCondition = null,
            text = text
        )

        // 1) Кладём в шину сообщений — открытый ChatActivity подхватит и отрисует.
        MessagesEventBus.emit(message)

        // 2) Дёргаем notifier — внутри есть проверка "не показывать, если чат открыт",
        //    так что в открытом чате уведомления не будет, а в закрытом — будет.
        val title = buildChatTitle(hospitalizationId)
        ChatMessageNotifier.notifyIfNeeded(context, message, title)
    }

    private fun buildChatTitle(hospitalizationId: String): String {
        val hospitalization = CallsManager.calls.value
            .firstOrNull { it.id == hospitalizationId }
        return if (hospitalization != null) {
            val call = hospitalization.call
            "Вызов №${call.dayNumber}/${call.yearNumber}"
        } else {
            "Сообщение по вызову"
        }
    }

    private fun currentIsoTime(): String {
        // Формат, совпадающий с тем, что присылает бэкенд.
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }
}
