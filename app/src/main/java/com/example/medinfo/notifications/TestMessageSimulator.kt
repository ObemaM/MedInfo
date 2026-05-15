package com.example.medinfo.notifications

import android.content.Context
import com.example.medinfo.data.manager.CallsManager
import com.example.medinfo.data.manager.MessagesEventBus
import com.example.medinfo.model.api.MessageOrigin
import com.example.medinfo.model.api.MessageResponseDto
import com.example.medinfo.model.api.MessageType
import com.example.medinfo.model.api.PatientConditionResponseDto
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

// Дев-хелпер: имитирует входящее сообщение от бригады. Безопасно удалить на проде.
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
            isNotificationSent = true,
            receptionTime = currentIsoTime(),
            patientCondition = null,
            text = text,
            phoneNumber = null
        )

        // Шина — для открытого чата; notifier — для закрытого (он сам решит, показать или нет).
        MessagesEventBus.emit(message)
        val title = buildChatTitle(hospitalizationId)
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
            isNotificationSent = true,
            receptionTime = currentIsoTime(),
            patientCondition = buildFakeCondition(),
            text = null,
            phoneNumber = phoneNumber
        )

        MessagesEventBus.emit(message)

        val title = buildChatTitle(hospitalizationId)
        ChatMessageNotifier.notifyIfNeeded(context, message, title)
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

    private fun currentIsoTime(): String =
        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}
