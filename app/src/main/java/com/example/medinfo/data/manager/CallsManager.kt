package com.example.medinfo.data.manager

import android.os.SystemClock
import com.example.medinfo.config.ConfigManager
import com.example.medinfo.model.api.HospitalizationDecision
import com.example.medinfo.model.api.HospitalizationResponseDto
import com.example.medinfo.model.api.HospitalizationStatus
import com.example.medinfo.util.CallLog
import com.example.medinfo.util.DateFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Синглтон, поэтому делаем методы через Synchronized
object CallsManager {

    // Актуальная очередь госпитализаций, требующих внимания пользователя.
    private val _calls = MutableStateFlow<List<HospitalizationResponseDto>>(emptyList())
    val calls: StateFlow<List<HospitalizationResponseDto>> = _calls

    // Отдельный scope нужен для таймеров авто-игнора.
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val activeJobs = mutableMapOf<String, Job>()
    private val callAddedAtMs = mutableMapOf<String, Long>()

    // Добавляет новую госпитализацию в очередь или обновляет уже существующую.
    @Synchronized
    fun upsertCall(call: HospitalizationResponseDto): Boolean {
        val currentList = _calls.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == call.id }
        val isNew = existingIndex == -1

        if (existingIndex == -1) {
            currentList.add(call)
        } else {
            currentList[existingIndex] = call
        }

        val sortedList = sortByCreationTime(currentList)
        _calls.value = sortedList
        CallLog.queue(
            source = "CallsManager",
            action = if (isNew) "upsert-new" else "upsert-update",
            call = call,
            queueSize = sortedList.size,
            isNew = isNew
        )

        if (shouldStartIgnoreTimer(call)) {
            ensureIgnoreTimer(call)
        } else {
            CallLog.queue(
                source = "CallsManager",
                action = "not-requiring-timer-remove-timer",
                call = call,
                queueSize = sortedList.size,
                isNew = isNew
            )
            removeTimer(call.id)
        }

        return isNew
    }

    // Синхронизирует серверную вкладку "Требуют решения" с локальной очередью и таймерами.
    @Synchronized
    fun syncDecisionCallsFromServer(calls: List<HospitalizationResponseDto>) {
        CallLog.event("CallsManager", "sync decision calls from server count=${calls.size}")
        calls
            .filter { shouldStartIgnoreTimer(it) }
            .forEach {
                CallLog.hospitalization("CallsManager", it, "server-sync candidate")
                upsertCall(it)
            }
    }

    // Возвращает оставшееся время до авто-игнора госпитализации.
    @Synchronized
    fun getRemainingIgnoreMillis(
        hospitalizationId: String,
        totalMillis: Long = ConfigManager.maxCallDurationMs
    ): Long? {
        val addedAt = callAddedAtMs[hospitalizationId] ?: return null
        val elapsed = SystemClock.elapsedRealtime() - addedAt
        return (totalMillis - elapsed).coerceAtLeast(0L)
    }

    // Фиксирует локальный старт таймера решения, если серверного времени пока недостаточно.
    @Synchronized
    fun markDecisionTimerStarted(hospitalizationId: String) {
        callAddedAtMs.putIfAbsent(hospitalizationId, SystemClock.elapsedRealtime())
        CallLog.event("CallsManager", "mark decision timer started hospitalizationId=$hospitalizationId")
    }

    // Удаляет госпитализацию из очереди и очищает ее таймер.
    @Synchronized
    fun removeCall(hospitalizationId: String) {
        _calls.value.firstOrNull { it.id == hospitalizationId }?.let {
            CallLog.queue(
                source = "CallsManager",
                action = "remove",
                call = it,
                queueSize = (_calls.value.size - 1).coerceAtLeast(0)
            )
        } ?: CallLog.event("CallsManager", "remove missing hospitalizationId=$hospitalizationId")
        removeTimer(hospitalizationId)
        _calls.value = _calls.value.filterNot { it.id == hospitalizationId }
    }

    // Полная очистка очереди и всех таймеров.
    @Synchronized
    fun clearAll() {
        CallLog.event("CallsManager", "clear all queueSize=${_calls.value.size}")
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
            CallLog.queue(
                source = "CallsManager",
                action = "timer-started",
                call = call,
                queueSize = _calls.value.size,
                remainingMs = remaining
            )
            delay(remaining)

            val stillExists = _calls.value.any { it.id == call.id }
            if (stillExists) {
                CallLog.queue(
                    source = "CallsManager",
                    action = "timer-finished-auto-remove",
                    call = call,
                    queueSize = _calls.value.size
                )
                removeCall(call.id)
            }
        }
    }

    private fun ensureIgnoreTimer(call: HospitalizationResponseDto) {
        val timerStartElapsed = calculateTimerStartElapsed(call)
        val existingStartElapsed = callAddedAtMs[call.id]

        // Если сервер прислал более раннее время старта, исправляем локальный таймер, а не даем новые 45 минут.
        if (existingStartElapsed == null || timerStartElapsed < existingStartElapsed) {
            callAddedAtMs[call.id] = timerStartElapsed
            activeJobs[call.id]?.cancel()
            activeJobs.remove(call.id)
            CallLog.queue(
                source = "CallsManager",
                action = if (existingStartElapsed == null) "timer-anchor-created" else "timer-anchor-corrected-from-server",
                call = call,
                queueSize = _calls.value.size,
                remainingMs = getRemainingIgnoreMillis(call.id)
            )
        }

        if (!activeJobs.containsKey(call.id)) {
            startIgnoreTimer(call)
        }
    }

    private fun calculateTimerStartElapsed(call: HospitalizationResponseDto): Long {
        // Если сервер не прислал время уведомления, считаем стартом момент получения на этом клиенте.
        // Время создания вызова не подходит: бригада могла отправить данные заметно позже.
        val startedAtWallMillis = DateFormatter.parseCallTimeMillis(call.notificationTime)
            ?: return SystemClock.elapsedRealtime()

        val elapsedFromServerStart =
            (System.currentTimeMillis() - startedAtWallMillis).coerceAtLeast(0L)

        return SystemClock.elapsedRealtime() - elapsedFromServerStart
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

    // TODO: Для того чтобы сортировать по таймеру, у чего быстрее истекает время
    private fun sortByCreationTime(
        calls: List<HospitalizationResponseDto>
    ): List<HospitalizationResponseDto> {
        return calls.sortedWith(
            compareBy<HospitalizationResponseDto> {
                DateFormatter.parseCallTimeMillis(it.creationTime)
                    ?: DateFormatter.parseCallTimeMillis(it.call.callTime)
                    ?: Long.MAX_VALUE
            }.thenBy { it.id }
        )
    }
}
