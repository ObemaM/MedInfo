package com.example.medinfo.data.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SignalRConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

// Общее состояние подключения к SignalR: сервис пишет, экраны читают и показывают врачу связь с сервером.
object SignalRConnectionState {
    private val _status = MutableStateFlow(SignalRConnectionStatus.DISCONNECTED)
    val status: StateFlow<SignalRConnectionStatus> = _status.asStateFlow()

    fun set(status: SignalRConnectionStatus) {
        _status.value = status
    }
}
