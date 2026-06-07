package com.example.medinfo.data.manager

import android.content.Context
import android.os.SystemClock
import com.example.medinfo.config.ConfigManager
import com.example.medinfo.data.network.RetrofitClient
import com.example.medinfo.data.repository.HospitalizationRepository
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
import kotlinx.coroutines.withContext

// Синглтон, поэтому делаем методы через Synchronized
object CallsManager {

    // Актуальная очередь госпитализаций, требующих внимания пользователя.
    private val _calls = MutableStateFlow<List<HospitalizationResponseDto>>(emptyList())
    val calls: StateFlow<List<HospitalizationResponseDto>> = _calls

    // Отдельный scope нужен для таймеров авто-игнора.
    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val activeJobs = mutableMapOf<String, Job>()
    private val callAddedAtMs = mutableMapOf<String, Long>()
    private val callStartedAtWallMs = mutableMapOf<String, Long>()
    private var appContext: Context? = null

    // Защита от повторной отправки авто-игнора, если активность тоже хочет завершить вызов.
    private val ignoreSent = mutableSetOf<String>()

    // Репозиторий поднимается лениво, потому что Retrofit инициализируется в Application.onCreate.
    private val hospitalizationRepository by lazy {
        HospitalizationRepository(RetrofitClient.apiServiceService)
    }

    // Период переоценки: на случай, если значение конфига изменилось после старта таймера.
    private const val TIMER_RECHECK_INTERVAL_MS = 60_000L
    private const val TIMER_PREFS_NAME = "decision_timer_starts"
    private const val MISSING_TIMER_START = Long.MIN_VALUE

    @Synchronized
    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

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

        val sortedList = sortByDecisionDeadline(currentList)
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
        val addedAt = callAddedAtMs[hospitalizationId]
            ?: restoreTimerStartElapsed(hospitalizationId)
            ?: return null
        val elapsed = SystemClock.elapsedRealtime() - addedAt
        return (totalMillis - elapsed).coerceAtLeast(0L)
    }

    // Фиксирует локальный старт таймера решения, если серверного времени пока недостаточно.
    @Synchronized
    fun markDecisionTimerStarted(hospitalizationId: String) {
        if (callAddedAtMs.containsKey(hospitalizationId) ||
            restoreTimerStartElapsed(hospitalizationId) != null
        ) {
            CallLog.event("CallsManager", "mark decision timer keep existing hospitalizationId=$hospitalizationId")
            return
        }

        saveTimerStart(
            hospitalizationId = hospitalizationId,
            startedAtWallMillis = System.currentTimeMillis()
        )
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
        callStartedAtWallMs.clear()
        ignoreSent.clear()
        timerPrefs()?.edit()?.clear()?.apply()
        _calls.value = emptyList()
    }

    // Запускает таймер для госпитализации без решения.
    private fun startIgnoreTimer(call: HospitalizationResponseDto) {
        activeJobs[call.id]?.cancel()

        activeJobs[call.id] = managerScope.launch {
            val initialRemaining = getRemainingIgnoreMillis(call.id) ?: ConfigManager.maxCallDurationMs
            CallLog.queue(
                source = "CallsManager",
                action = "timer-started",
                call = call,
                queueSize = _calls.value.size,
                remainingMs = initialRemaining
            )

            // Дробим ожидание, чтобы реагировать на изменение maxCallDurationMs в конфиге без
            // перезапуска приложения. На каждом шаге берём актуальное оставшееся время.
            while (true) {
                val remaining = getRemainingIgnoreMillis(call.id) ?: 0L
                if (remaining <= 0L) break
                delay(remaining.coerceAtMost(TIMER_RECHECK_INTERVAL_MS))
            }

            val stillExists = _calls.value.any { it.id == call.id }
            if (stillExists) {
                CallLog.queue(
                    source = "CallsManager",
                    action = "timer-finished-auto-ignore",
                    call = call,
                    queueSize = _calls.value.size
                )
                sendAutoIgnoreSafe(call.id)
                removeCall(call.id)
            }
        }
    }

    // Отправляет на сервер DecisionId = IGNORED для просроченной госпитализации. Безопасна к
    // повторным вызовам и к сетевым ошибкам: локальный removeCall выполняется в любом случае.
    private suspend fun sendAutoIgnoreSafe(hospitalizationId: String) {
        synchronized(this) {
            if (!ignoreSent.add(hospitalizationId)) return
        }
        try {
            withContext(Dispatchers.IO) {
                hospitalizationRepository.saveDecision(
                    hospitalizationId = hospitalizationId,
                    decisionId = HospitalizationDecision.IGNORED.id
                )
            }
            CallLog.event("CallsManager", "auto-ignore sent hospitalizationId=$hospitalizationId")
        } catch (e: Exception) {
            CallLog.event(
                "CallsManager",
                "auto-ignore send FAILED hospitalizationId=$hospitalizationId error=${e.message}"
            )
        }
    }

    private fun ensureIgnoreTimer(call: HospitalizationResponseDto) {
        val timerStartWall = calculateTimerStartWall(call)
        val existingStartWall = getKnownTimerStartWall(call.id)

        // Если сервер или сохраненный локальный якорь раньше текущего, не даем вызову новые 45 минут.
        if (existingStartWall == null || timerStartWall < existingStartWall) {
            saveTimerStart(call.id, timerStartWall)
            activeJobs[call.id]?.cancel()
            activeJobs.remove(call.id)
            CallLog.queue(
                source = "CallsManager",
                action = if (existingStartWall == null) "timer-anchor-created" else "timer-anchor-corrected-from-server",
                call = call,
                queueSize = _calls.value.size,
                remainingMs = getRemainingIgnoreMillis(call.id)
            )
        } else {
            restoreTimerStartElapsed(call.id)
        }

        if (!activeJobs.containsKey(call.id)) {
            startIgnoreTimer(call)
        }
    }

    private fun calculateTimerStartWall(call: HospitalizationResponseDto): Long {
        val nowWall = System.currentTimeMillis()
        val serverStartWall = DateFormatter.parseCallTimeMillis(
            call.consultationNotificationTime ?: call.consultationRequestTime
        )
            ?.coerceAtMost(nowWall)
        val savedStartWall = getKnownTimerStartWall(call.id)
            ?.coerceAtMost(nowWall)

        // Если сервер не прислал время уведомления, берём ранее сохраненный локальный старт,
        // а если его ещё нет — момент первого получения на этом клиенте.
        return listOfNotNull(serverStartWall, savedStartWall).minOrNull() ?: nowWall
    }

    // Останавливает и удаляет таймер для конкретной госпитализации.
    private fun removeTimer(hospitalizationId: String) {
        activeJobs[hospitalizationId]?.cancel()
        activeJobs.remove(hospitalizationId)
        callAddedAtMs.remove(hospitalizationId)
        callStartedAtWallMs.remove(hospitalizationId)
        timerPrefs()?.edit()?.remove(hospitalizationId)?.apply()
        ignoreSent.remove(hospitalizationId)
    }

    private fun saveTimerStart(
        hospitalizationId: String,
        startedAtWallMillis: Long
    ) {
        val normalizedWall = startedAtWallMillis.coerceAtMost(System.currentTimeMillis())
        callStartedAtWallMs[hospitalizationId] = normalizedWall
        callAddedAtMs[hospitalizationId] = wallStartToElapsedStart(normalizedWall)
        timerPrefs()?.edit()?.putLong(hospitalizationId, normalizedWall)?.apply()
    }

    private fun restoreTimerStartElapsed(hospitalizationId: String): Long? {
        val startedAtWall = getKnownTimerStartWall(hospitalizationId) ?: return null
        val normalizedWall = startedAtWall.coerceAtMost(System.currentTimeMillis())
        callStartedAtWallMs[hospitalizationId] = normalizedWall
        val elapsedStart = wallStartToElapsedStart(normalizedWall)
        callAddedAtMs[hospitalizationId] = elapsedStart
        return elapsedStart
    }

    private fun getKnownTimerStartWall(hospitalizationId: String): Long? {
        callStartedAtWallMs[hospitalizationId]?.let { return it }

        val saved = timerPrefs()?.getLong(hospitalizationId, MISSING_TIMER_START)
            ?: return null
        if (saved == MISSING_TIMER_START) return null

        callStartedAtWallMs[hospitalizationId] = saved
        return saved
    }

    private fun wallStartToElapsedStart(startedAtWallMillis: Long): Long {
        val elapsedFromStart = (System.currentTimeMillis() - startedAtWallMillis).coerceAtLeast(0L)
        return SystemClock.elapsedRealtime() - elapsedFromStart
    }

    private fun timerPrefs() =
        appContext?.getSharedPreferences(TIMER_PREFS_NAME, Context.MODE_PRIVATE)

    // Таймер нужен только для активных госпитализаций без решения.
    private fun shouldStartIgnoreTimer(call: HospitalizationResponseDto): Boolean {
        return call.decisionId == HospitalizationDecision.NONE.id &&
            HospitalizationStatus.fromId(call.statusId)?.isActive == true
    }

    private fun sortByDecisionDeadline(
        calls: List<HospitalizationResponseDto>
    ): List<HospitalizationResponseDto> {
        return calls.sortedWith(
            // Очередь решений должна совпадать со вкладкой "Требуют решения":
            // выше показываем вызовы, у которых раньше истечет время на ответ врача.
            compareBy<HospitalizationResponseDto> {
                calculateDecisionDeadlineMillis(it)
            }.thenBy {
                DateFormatter.parseCallTimeMillis(it.creationTime)
                    ?: DateFormatter.parseCallTimeMillis(it.call.callTime)
                    ?: Long.MAX_VALUE
            }.thenBy { it.id }
        )
    }

    private fun calculateDecisionDeadlineMillis(call: HospitalizationResponseDto): Long {
        return calculateTimerStartWall(call) + ConfigManager.maxCallDurationMs
    }
}
