package com.example.medinfo.notifications

import android.content.Context
import com.example.medinfo.data.manager.ChatUnreadManager
import com.example.medinfo.data.manager.MessagesEventBus
import com.example.medinfo.model.api.MessageOrigin
import com.example.medinfo.model.api.MessageResponseDto
import com.example.medinfo.model.api.MessageType
import com.example.medinfo.model.api.PatientConditionResponseDto
import com.example.medinfo.ui.chat.ChatActivity
import com.example.medinfo.util.AppVisibilityTracker
import com.example.medinfo.util.ChatTitles
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

// Сервисный инструмент: имитирует входящее сообщение от бригады при включенном testCallEnabled.
object TestMessageSimulator {

    fun simulateBrigadeMessage(
        context: Context,
        hospitalizationId: String,
        text: String = "Тестовое сообщение от бригады"
    ) {
        // Валидный MessageResponseDto, как будто его прислал планшет бригады.
        val message = MessageResponseDto(
            id = UUID.randomUUID().toString(),
            hospitalizationId = hospitalizationId,
            origin = MessageOrigin.TABLET.id, // иначе ChatMessageNotifier отфильтрует
            userId = null,
            type = MessageType.TEXT.id,
            receptionTime = currentIsoTime(),
            notificationTime = currentIsoTime(),
            patientCondition = null,
            text = text,
            phoneNumber = null
        )

        // Шина — для открытого чата; notifier — для закрытого (он сам решит, показать или нет).
        if (shouldMarkUnread(message)) {
            ChatUnreadManager.markUnread(message)
        }
        MessagesEventBus.emit(message)
        val title = ChatTitles.forHospitalizationId(hospitalizationId)
        ChatMessageNotifier.notifyIfNeeded(context, message, title)
    }

    // PATIENT_CONDITION с витальными и phoneNumber — для отладки карточки состояния на экране решения и звонка.
    fun simulatePatientCondition(
        context: Context,
        hospitalizationId: String,
        phoneNumber: String = "+79041234192"
    ) {
        val message = MessageResponseDto(
            id = UUID.randomUUID().toString(),
            hospitalizationId = hospitalizationId,
            origin = MessageOrigin.TABLET.id,
            userId = null,
            type = MessageType.PATIENT_CONDITION.id,
            receptionTime = currentIsoTime(),
            notificationTime = currentIsoTime(),
            patientCondition = buildFakeCondition(),
            text = null,
            phoneNumber = phoneNumber
        )

        if (shouldMarkUnread(message)) {
            ChatUnreadManager.markUnread(message)
        }
        MessagesEventBus.emit(message)

        val title = ChatTitles.forHospitalizationId(hospitalizationId)
        ChatMessageNotifier.notifyIfNeeded(context, message, title)
    }

    private fun shouldMarkUnread(message: MessageResponseDto): Boolean {
        return MessageOrigin.fromId(message.origin) == MessageOrigin.TABLET &&
            !isChatScreenOpenFor(message.hospitalizationId)
    }

    private fun isChatScreenOpenFor(hospitalizationId: String): Boolean {
        if (!AppVisibilityTracker.isAppInForeground) return false

        val currentScreen = AppVisibilityTracker.currentActivity()
        return currentScreen is ChatActivity && currentScreen.chatId == hospitalizationId
    }

    // Правдоподобные витальные, чтобы поля не пропускались из-за null.
    private fun buildFakeCondition(): PatientConditionResponseDto {
        return PatientConditionResponseDto(
            id = UUID.randomUUID().toString(),
            startDisease = 2,
            vozr = "45",
            consciousness = "Ясное",
            bloodPressure = "130/85",
            heartRate = 88,
            respirationRate = 18,
            temperature = 36.9,
            spO2 = 96,
            vas = 4,
            glucometry = 5.4,
            pregnant = false,
            convulsions = false,
            stenosis = false,
            ifaPresence = false,
            ifaTool = null,
            alv = false,
            venousAccessPresence = true,
            venousAccessMethod = listOf("Периферическая вена"),
            oxygenSupport = false,
            bleedingPresence = false,
            bleedingType = null,
            arterialTourniquetPresence = false,
            arterialTourniquetApplicationTime = null,
            mrs = 1,
            newsScore = 3,
            pewsScore = null,
            algoverIndex = 0.7,
            lams = 2
        )
    }

    private fun currentIsoTime(): String =
        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}
