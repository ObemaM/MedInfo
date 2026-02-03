package com.example.medinfo.data

import android.os.SystemClock
import com.example.medinfo.model.CallNotificationDto
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object CallsManager {
    const val MAX_CALL_DURATION_MS: Long = 2_400_00L

    private val _calls = MutableStateFlow<List<CallNotificationDto>>(emptyList())
    val calls: StateFlow<List<CallNotificationDto>> = _calls

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val activeJobs = mutableMapOf<String, Job>() // Храним таймеры по номеру вызова
    private val callAddedAtMs = mutableMapOf<String, Long>()

    fun addCall(call: CallNotificationDto): Boolean {
        val currentList = _calls.value.toMutableList()
        if (currentList.none { it.callNumber == call.callNumber }) {
            currentList.add(call)
            _calls.value = currentList

            call.callNumber?.let { callId ->
                if (!callAddedAtMs.containsKey(callId)) {
                    callAddedAtMs[callId] = SystemClock.elapsedRealtime()
                }
            }

            // Если статус "транспортировка" — запускаем таймер на 2 минуты
            if (call.status?.lowercase() == "транспортировка") {
                startIgnoreTimer(call)
            }

            return true
        }

        return false
    }

    fun getRemainingIgnoreMillis(callId: String, totalMillis: Long = MAX_CALL_DURATION_MS): Long? {
        val addedAt = callAddedAtMs[callId] ?: return null
        val elapsed = SystemClock.elapsedRealtime() - addedAt
        val remaining = totalMillis - elapsed
        return if (remaining > 0) remaining else 0L
    }

    private fun startIgnoreTimer(call: CallNotificationDto) {
        val callId = call.callNumber ?: return

        // Отменяем старый таймер, если он был (на всякий случай)
        activeJobs[callId]?.cancel()

        activeJobs[callId] = managerScope.launch {
            val remaining = getRemainingIgnoreMillis(callId) ?: MAX_CALL_DURATION_MS
            delay(remaining) // Ждем до истечения таймера

            // Если через 40 минут вызов всё еще в списке — значит его проигнорировали
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
        callAddedAtMs.remove(callId)
        _calls.value = _calls.value.filter { it.callNumber != callId }
    }

    fun clearAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        callAddedAtMs.clear()
        _calls.value = emptyList()
    }
}