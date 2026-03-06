package com.example.medinfo.data.manager

import android.os.SystemClock
import com.example.medinfo.config.ConfigManager
import com.example.medinfo.model.CallNotificationDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object CallsManager {
    private val _calls = MutableStateFlow<List<CallNotificationDto>>(emptyList())
    val calls: StateFlow<List<CallNotificationDto>> = _calls

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val activeJobs = mutableMapOf<String, Job>() // Храним таймеры по номеру вызова
    private val callAddedAtMs = mutableMapOf<String, Long>()

    init {
    }

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

            // Если статус "транспортировка" — запускаем таймер на 40 минут
            val statusLower = call.status?.lowercase()
            if (statusLower == "транспортировка") {
                startIgnoreTimer(call)
            } else {
            }

            return true
        }
        return false
    }

    fun getRemainingIgnoreMillis(callId: String, totalMillis: Long = ConfigManager.maxCallDurationMs): Long? {
        val addedAt = callAddedAtMs[callId]
        if (addedAt == null) {
            return null
        }
        val elapsed = SystemClock.elapsedRealtime() - addedAt
        val remaining = totalMillis - elapsed
        val result = if (remaining > 0) remaining else 0L
        return result
    }

    private fun startIgnoreTimer(call: CallNotificationDto) {
        val callId = call.callNumber ?: run {
            return
        }

        // Отменяем старый таймер, если он был (на всякий случай)
        activeJobs[callId]?.let { oldJob ->
            oldJob.cancel()
        }

        activeJobs[callId] = managerScope.launch {
            val remaining = getRemainingIgnoreMillis(callId) ?: ConfigManager.maxCallDurationMs
            delay(remaining) // Ждем до истечения таймера

            // Если через 40 минут вызов всё еще в списке — значит его проигнорировали
            val stillExists = _calls.value.any { it.callNumber == callId }
                if (stillExists) {
                handleIgnore(callId)
            } else {
            }
        }
    }

    private fun handleIgnore(callId: String) {
        // 1. Отправляем на сервер статус "Игнор"
        // Здесь нужно вызвать метод ApiService, аналогично answerCall, но со статусом "Ignore"

        // 2. Удаляем из списка
        removeCall(callId)
    }

    fun removeCall(callId: String) {
        activeJobs[callId]?.let { job ->
            job.cancel()
        }
        activeJobs.remove(callId)
        callAddedAtMs.remove(callId)
        val previousSize = _calls.value.size
        _calls.value = _calls.value.filter { it.callNumber != callId }
        val newSize = _calls.value.size
    }

    fun clearAll() {
        activeJobs.values.forEach { job ->
            job.cancel()
        }
        activeJobs.clear()
        callAddedAtMs.clear()
        _calls.value = emptyList()
    }
}