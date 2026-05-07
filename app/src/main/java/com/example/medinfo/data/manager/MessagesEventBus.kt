package com.example.medinfo.data.manager

import com.example.medinfo.model.api.MessageResponseDto
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

    fun emit(message: MessageResponseDto) {
        _incoming.tryEmit(message)
    }
}
