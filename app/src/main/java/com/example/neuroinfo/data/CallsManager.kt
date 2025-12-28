package com.example.neuroinfo.data

import com.example.neuroinfo.model.CallNotificationDto
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object CallsManager {
    private val _calls = MutableStateFlow<List<CallNotificationDto>>(emptyList())
    val calls: StateFlow<List<CallNotificationDto>> = _calls

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val activeJobs = mutableMapOf<String, Job>() // Храним таймеры по номеру вызова

    fun addCall(call: CallNotificationDto) {
        val currentList = _calls.value.toMutableList()
        if (currentList.none { it.callNumber == call.callNumber }) {
            currentList.add(call)
            _calls.value = currentList

            // Если статус "состояние" — запускаем таймер на 30 секунд
            if (call.status?.lowercase() == "состояние") {
                startIgnoreTimer(call)
            }
        }
    }

    private fun startIgnoreTimer(call: CallNotificationDto) {
        val callId = call.callNumber ?: return

        // Отменяем старый таймер, если он был (на всякий случай)
        activeJobs[callId]?.cancel()

        activeJobs[callId] = managerScope.launch {
            delay(30000) // Ждем 30 секунд

            // Если через 30 сек вызов всё еще в списке — значит его проигнорировали
            if (_calls.value.any { it.callNumber == callId }) {
                handleIgnore(callId)
            }
        }
    }

    private fun handleIgnore(callId: String) {
        // 1. Отправляем на сервер статус "Игнор" (через твой Repository)
        // Здесь нужно вызвать метод API, аналогично answerCall, но со статусом "Ignore"

        // 2. Удаляем из списка
        removeCall(callId)

        // Тут можно добавить лог или аналитику
    }

    fun removeCall(callId: String) {
        activeJobs[callId]?.cancel()
        activeJobs.remove(callId)
        _calls.value = _calls.value.filter { it.callNumber != callId }
    }
}