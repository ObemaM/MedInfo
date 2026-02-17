package com.example.medinfo.data.manager

import android.os.SystemClock
import android.util.Log
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
    const val MAX_CALL_DURATION_MS: Long = 2_400_00L

    private val _calls = MutableStateFlow<List<CallNotificationDto>>(emptyList())
    val calls: StateFlow<List<CallNotificationDto>> = _calls

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val activeJobs = mutableMapOf<String, Job>() // Храним таймеры по номеру вызова
    private val callAddedAtMs = mutableMapOf<String, Long>()

    init {
        Log.d("CallsManager", "=== CallsManager initialized, MAX_CALL_DURATION_MS=$MAX_CALL_DURATION_MS ===")
    }

    fun addCall(call: CallNotificationDto): Boolean {
        Log.d("CallsManager", "=== addCall: START call #${call.callNumber}, status=${call.status} ===")
        val currentList = _calls.value.toMutableList()
        if (currentList.none { it.callNumber == call.callNumber }) {
            Log.d("CallsManager", "=== addCall: Call #${call.callNumber} is new, adding to list ===")
            currentList.add(call)
            _calls.value = currentList
            Log.d("CallsManager", "=== addCall: Flow updated, new list size=${currentList.size} ===")

            call.callNumber?.let { callId ->
                if (!callAddedAtMs.containsKey(callId)) {
                    callAddedAtMs[callId] = SystemClock.elapsedRealtime()
                    Log.d("CallsManager", "=== addCall: Recorded timestamp for call #$callId at ${callAddedAtMs[callId]} ===")
                }
            }

            // Если статус "транспортировка" — запускаем таймер на 40 минут
            val statusLower = call.status?.lowercase()
            if (statusLower == "транспортировка") {
                Log.d("CallsManager", "=== addCall: Status is 'транспортировка', starting ignore timer ===")
                startIgnoreTimer(call)
            } else {
                Log.d("CallsManager", "=== addCall: Status is '$statusLower', no timer needed ===")
            }

            Log.d("CallsManager", "=== addCall: COMPLETE - added successfully ===")
            return true
        }
        Log.d("CallsManager", "=== addCall: Call #${call.callNumber} already exists, skipping ===")
        return false
    }

    fun getRemainingIgnoreMillis(callId: String, totalMillis: Long = MAX_CALL_DURATION_MS): Long? {
        val addedAt = callAddedAtMs[callId]
        if (addedAt == null) {
            Log.d("CallsManager", "=== getRemainingIgnoreMillis: No timestamp for call #$callId ===")
            return null
        }
        val elapsed = SystemClock.elapsedRealtime() - addedAt
        val remaining = totalMillis - elapsed
        val result = if (remaining > 0) remaining else 0L
        Log.d("CallsManager", "=== getRemainingIgnoreMillis: call #$callId, elapsed=${elapsed}ms, remaining=${result}ms ===")
        return result
    }

    private fun startIgnoreTimer(call: CallNotificationDto) {
        val callId = call.callNumber ?: run {
            Log.w("CallsManager", "=== startIgnoreTimer: Cannot start timer - callNumber is null ===")
            return
        }

        Log.d("CallsManager", "=== startIgnoreTimer: START for call #$callId ===")
        // Отменяем старый таймер, если он был (на всякий случай)
        activeJobs[callId]?.let { oldJob ->
            Log.d("CallsManager", "=== startIgnoreTimer: Cancelling existing timer for call #$callId ===")
            oldJob.cancel()
        }

        activeJobs[callId] = managerScope.launch {
            val remaining = getRemainingIgnoreMillis(callId) ?: MAX_CALL_DURATION_MS
            Log.d("CallsManager", "=== startIgnoreTimer: Waiting ${remaining}ms for call #$callId ===")
            delay(remaining) // Ждем до истечения таймера

            // Если через 40 минут вызов всё еще в списке — значит его проигнорировали
            val stillExists = _calls.value.any { it.callNumber == callId }
            Log.d("CallsManager", "=== startIgnoreTimer: Timer expired for call #$callId, stillExists=$stillExists ===")
            if (stillExists) {
                Log.d("CallsManager", "=== startIgnoreTimer: Call was IGNORED, handling ===")
                handleIgnore(callId)
            } else {
                Log.d("CallsManager", "=== startIgnoreTimer: Call already handled, doing nothing ===")
            }
        }
        Log.d("CallsManager", "=== startIgnoreTimer: Timer job started for call #$callId ===")
    }

    private fun handleIgnore(callId: String) {
        Log.d("CallsManager", "=== handleIgnore: START for call #$callId ===")
        // 1. Отправляем на сервер статус "Игнор"
        // Здесь нужно вызвать метод ApiService, аналогично answerCall, но со статусом "Ignore"
        Log.d("CallsManager", "=== handleIgnore: TODO - Send ignore status to server ===")

        // 2. Удаляем из списка
        Log.d("CallsManager", "=== handleIgnore: Removing call from list ===")
        removeCall(callId)
        Log.d("CallsManager", "=== handleIgnore: COMPLETE ===")
    }

    fun removeCall(callId: String) {
        Log.d("CallsManager", "=== removeCall: START for call #$callId ===")
        activeJobs[callId]?.let { job ->
            Log.d("CallsManager", "=== removeCall: Cancelling timer job for call #$callId ===")
            job.cancel()
        }
        activeJobs.remove(callId)
        callAddedAtMs.remove(callId)
        val previousSize = _calls.value.size
        _calls.value = _calls.value.filter { it.callNumber != callId }
        val newSize = _calls.value.size
        Log.d("CallsManager", "=== removeCall: Flow updated, size $previousSize -> $newSize ===")
        Log.d("CallsManager", "=== removeCall: COMPLETE ===")
    }

    fun clearAll() {
        Log.d("CallsManager", "=== clearAll: START, current jobs=${activeJobs.size}, calls=${_calls.value.size} ===")
        activeJobs.values.forEach { job ->
            Log.d("CallsManager", "=== clearAll: Cancelling job ===")
            job.cancel()
        }
        activeJobs.clear()
        callAddedAtMs.clear()
        _calls.value = emptyList()
        Log.d("CallsManager", "=== clearAll: COMPLETE - all cleared ===")
    }
}