package com.example.medinfo.data.manager

import android.os.SystemClock
import com.example.medinfo.config.ConfigManager
import com.example.medinfo.model.api.HospitalizationDecision
import com.example.medinfo.model.api.HospitalizationResponseDto
import com.example.medinfo.model.api.HospitalizationStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object CallsManager {

    // Актуальная очередь госпитализаций, требующих внимания пользователя.
    private val _calls = MutableStateFlow<List<HospitalizationResponseDto>>(emptyList())
    val calls: StateFlow<List<HospitalizationResponseDto>> = _calls

    // Отдельный scope нужен для таймеров авто-игнора.
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val activeJobs = mutableMapOf<String, Job>()
    private val callAddedAtMs = mutableMapOf<String, Long>()

    // Добавляет новую госпитализацию в очередь или обновляет уже существующую.
    fun upsertCall(call: HospitalizationResponseDto): Boolean {
        val currentList = _calls.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == call.id }
        val isNew = existingIndex == -1

        if (existingIndex == -1) {
            currentList.add(call)
        } else {
            currentList[existingIndex] = call
        }

        _calls.value = currentList

        if (shouldStartIgnoreTimer(call)) {
            callAddedAtMs.putIfAbsent(call.id, SystemClock.elapsedRealtime())
            if (!activeJobs.containsKey(call.id)) {
                startIgnoreTimer(call)
            }
        } else {
            removeTimer(call.id)
        }

        return isNew
    }

    // Возвращает оставшееся время до авто-игнора госпитализации.
    fun getRemainingIgnoreMillis(
        hospitalizationId: String,
        totalMillis: Long = ConfigManager.maxCallDurationMs
    ): Long? {
        val addedAt = callAddedAtMs[hospitalizationId] ?: return null
        val elapsed = SystemClock.elapsedRealtime() - addedAt
        return (totalMillis - elapsed).coerceAtLeast(0L)
    }

    // Фиксирует локальный старт таймера решения, если серверного времени пока недостаточно.
    fun markDecisionTimerStarted(hospitalizationId: String) {
        callAddedAtMs.putIfAbsent(hospitalizationId, SystemClock.elapsedRealtime())
    }

    // Удаляет госпитализацию из очереди и очищает ее таймер.
    fun removeCall(hospitalizationId: String) {
        removeTimer(hospitalizationId)
        _calls.value = _calls.value.filterNot { it.id == hospitalizationId }
    }

    // Полная очистка очереди и всех таймеров.
    fun clearAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        callAddedAtMs.clear()
        _calls.value = emptyList()
    }

    // Запускает таймер для госпитализации без решения.
    private fun startIgnoreTimer(call: HospitalizationResponseDto) {
        activeJobs[call.id]?.cancel()

        activeJobs[call.id] = managerScope.launch {
            val remaining = getRemainingIgnoreMillis(call.id) ?: ConfigManager.maxCallDurationMs
            delay(remaining)

            val stillExists = _calls.value.any { it.id == call.id }
            if (stillExists) {
                removeCall(call.id)
            }
        }
    }

    // Останавливает и удаляет таймер для конкретной госпитализации.
    private fun removeTimer(hospitalizationId: String) {
        activeJobs[hospitalizationId]?.cancel()
        activeJobs.remove(hospitalizationId)
        callAddedAtMs.remove(hospitalizationId)
    }

    // Таймер нужен только для активных госпитализаций без решения.
    private fun shouldStartIgnoreTimer(call: HospitalizationResponseDto): Boolean {
        return call.decisionId == HospitalizationDecision.NONE.id &&
            HospitalizationStatus.fromId(call.statusId)?.isActive == true
    }
}
