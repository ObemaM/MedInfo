package com.example.medinfo.data.manager

import com.example.medinfo.model.api.MessageResponseDto
import com.example.medinfo.model.api.MessageType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// Шина realtime-сообщений из SignalR. SignalRService пушит сюда каждое
// MessageNotification, ChatActivity подписывается и фильтрует по hospitalizationId.
object MessagesEventBus {
    private val _incoming = MutableSharedFlow<MessageResponseDto>(
        replay = 0,
        extraBufferCapacity = 16
    )
    val incoming: SharedFlow<MessageResponseDto> = _incoming.asSharedFlow()

    private val latestMessagesByHospitalization = mutableMapOf<String, MutableList<MessageResponseDto>>()

    @Synchronized
    fun emit(message: MessageResponseDto) {
        val messages = latestMessagesByHospitalization.getOrPut(message.hospitalizationId) {
            mutableListOf()
        }
        messages.removeAll { it.id == message.id }
        messages.add(message)
        if (messages.size > MAX_CACHED_MESSAGES_PER_CALL) {
            messages.removeAt(0)
        }

        _incoming.tryEmit(message)
    }

    @Synchronized
    fun latestPatientConditionMessage(hospitalizationId: String): MessageResponseDto? {
        return latestMessagesByHospitalization[hospitalizationId]
            ?.lastOrNull {
                MessageType.fromId(it.type) == MessageType.PATIENT_CONDITION &&
                    it.patientCondition != null
            }
    }

    @Synchronized
    fun latestPhoneNumber(hospitalizationId: String): String? {
        return latestMessagesByHospitalization[hospitalizationId]
            ?.lastOrNull { !it.phoneNumber.isNullOrBlank() }
            ?.phoneNumber
    }

    private const val MAX_CACHED_MESSAGES_PER_CALL = 30
}
