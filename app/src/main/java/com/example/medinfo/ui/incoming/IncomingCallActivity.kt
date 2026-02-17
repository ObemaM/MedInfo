package com.example.medinfo.ui.incoming

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.medinfo.R
import com.example.medinfo.data.repository.CallRepository
import com.example.medinfo.data.cache.CallsCache
import com.example.medinfo.data.manager.CallsManager // Наш синглтон для очереди
import com.example.medinfo.data.network.RetrofitClient
import com.example.medinfo.model.CallNotificationDto
import com.example.medinfo.model.Hospitalization
import com.example.medinfo.databinding.ActivityIncomingCallBinding
import com.example.medinfo.util.DateFormatter
import com.example.medinfo.ui.incoming.IncomingCallRinger
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class IncomingCallActivity : AppCompatActivity() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val callRepository = CallRepository(RetrofitClient.apiServiceService)
    private val callsCache by lazy { CallsCache(applicationContext) }
    private var vibrator: Vibrator? = null
    private var cachedHospitalizations: List<Hospitalization>? = null
    private var cacheLoadJob: Job? = null

    private lateinit var binding: ActivityIncomingCallBinding
    private lateinit var sideAdapter: SideTabsAdapter // Создадим далее

    private var currentCall: CallNotificationDto? = null
    private var countdownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("IncomingCall", "=== onCreate: ACTIVITY CREATED ===")

        // Важно: немедленно выводим активность на передний план
        Log.d("IncomingCall", "=== onCreate: Calling bringToFront() ===")
        bringToFront()
        Log.d("IncomingCall", "=== onCreate: bringToFront() complete ===")

        // 1. Настройка отображения поверх блокировки (WakeLock + Keyguard)
        Log.d("IncomingCall", "=== onCreate: Setting up lock screen flags ===")
        setupLockScreenFlags()
        Log.d("IncomingCall", "=== onCreate: Lock screen flags set ===")

        Log.d("IncomingCall", "=== onCreate: Inflating layout ===")
        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d("IncomingCall", "=== onCreate: Layout set ===")

        // Дополнительные флаги окна для Android 10+ для отображения поверх других приложений
        Log.d("IncomingCall", "=== onCreate: Setting up window flags ===")
        setupWindowFlags()
        Log.d("IncomingCall", "=== onCreate: Window flags set ===")

        Log.d("IncomingCall", "=== onCreate: Preloading cached calls ===")
        preloadCachedCallsIfPossible()

        // 2. Инициализация UI

        // 3. Настройка боковой панели (корешков)
        Log.d("IncomingCall", "=== onCreate: Setting up side panel ===")
        setupSidePanel()
        Log.d("IncomingCall", "=== onCreate: Side panel setup complete ===")

        // 3.1. Если Activity открыли из уведомления/сервиса — подхватим CALL_DATA
        Log.d("IncomingCall", "=== onCreate: Handling incoming intent ===")
        handleIncomingIntent(intent)
        Log.d("IncomingCall", "=== onCreate: Intent handled ===")

        // 4. Подписка на очередь вызовов
        Log.d("IncomingCall", "=== onCreate: Starting to observe calls queue ===")
        observeCallsQueue()
        Log.d("IncomingCall", "=== onCreate: Calls queue observation started ===")

        // 5. Кнопки
        Log.d("IncomingCall", "=== onCreate: Setting up button click listeners ===")
        binding.buttonConfirm.setOnClickListener { 
            Log.d("IncomingCall", "=== CONFIRM button clicked ===")
            handleCallAnswer(true) 
        }
        binding.buttonReject.setOnClickListener { 
            Log.d("IncomingCall", "=== REJECT button clicked ===")
            handleCallAnswer(false) 
        }
        Log.d("IncomingCall", "=== onCreate: Buttons setup complete ===")

        // 6. Start continuous sound and vibration (they will continue until user presses stop button)
        Log.d("IncomingCall", "=== onCreate: Starting continuous alerts (ringer + vibration) ===")
        startContinuousAlerts()
        Log.d("IncomingCall", "=== onCreate: Continuous alerts started ===")

        // 7. Setup STOP ALERTS button
        Log.d("IncomingCall", "=== onCreate: Setting up STOP ALERTS button ===")
        binding.buttonStopAlerts.setOnClickListener {
            Log.d("IncomingCall", "=== STOP ALERTS button clicked ===")
            stopAlerts()
            // Hide the button after stopping alerts
            binding.buttonStopAlerts.visibility = android.view.View.GONE
            Log.d("IncomingCall", "=== STOP ALERTS button hidden ===")
        }
        // Make sure button is visible for new call
        binding.buttonStopAlerts.visibility = android.view.View.VISIBLE
        Log.d("IncomingCall", "=== onCreate: STOP ALERTS button is VISIBLE ===")

        Log.d("IncomingCall", "=== onCreate: ACTIVITY CREATION COMPLETE ===")
    }

    private fun preloadCachedCallsIfPossible() {
        Log.d("IncomingCall", "=== preloadCachedCallsIfPossible: Checking if load needed ===")
        if (cachedHospitalizations != null) {
            Log.d("IncomingCall", "=== preloadCachedCallsIfPossible: Already loaded, skipping ===")
            return
        }
        if (cacheLoadJob?.isActive == true) {
            Log.d("IncomingCall", "=== preloadCachedCallsIfPossible: Load job already active, skipping ===")
            return
        }

        val sharedPrefs = getSharedPreferences("app_session", MODE_PRIVATE)
        val userLogin = sharedPrefs.getString("user_login", null)
        if (userLogin.isNullOrBlank()) {
            Log.w("IncomingCall", "=== preloadCachedCallsIfPossible: No user login, cannot load cache ===")
            return
        }
        Log.d("IncomingCall", "=== preloadCachedCallsIfPossible: Starting cache load for user=$userLogin ===")

        cacheLoadJob = lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) {
                Log.d("IncomingCall", "=== preloadCachedCallsIfPossible: Reading from disk on IO thread ===")
                callsCache.readCalls(userLogin)
            }
            cachedHospitalizations = cached
            Log.d("IncomingCall", "=== preloadCachedCallsIfPossible: Loaded ${cached?.size} hospitalizations ===")
            currentCall?.let { call ->
                Log.d("IncomingCall", "=== preloadCachedCallsIfPossible: Updating display for current call ===")
                displayCallDetails(call)
            }
            Log.d("IncomingCall", "=== preloadCachedCallsIfPossible: Cache load complete ===")
        }
    }

    override fun onNewIntent(intent: Intent) {
        Log.d("IncomingCall", "=== onNewIntent: NEW INTENT RECEIVED ===")
        super.onNewIntent(intent)
        setIntent(intent)
        
        // When a NEW call arrives while activity is already open:
        // 1. Make sure the stop button is visible
        // 2. Restart alerts if they were stopped
        Log.d("IncomingCall", "=== onNewIntent: Ensuring stop button is visible ===")
        binding.buttonStopAlerts.visibility = android.view.View.VISIBLE
        
        Log.d("IncomingCall", "=== onNewIntent: Checking if alerts need to be restarted ===")
        if (!IncomingCallRinger.isPlaying()) {
            Log.d("IncomingCall", "=== onNewIntent: Ringer not playing, restarting alerts ===")
            startContinuousAlerts()
        } else {
            Log.d("IncomingCall", "=== onNewIntent: Ringer already playing, continuing ===")
        }
        
        Log.d("IncomingCall", "=== onNewIntent: Handling incoming intent ===")
        handleIncomingIntent(intent)
        Log.d("IncomingCall", "=== onNewIntent: COMPLETE ===")
    }

    private fun handleIncomingIntent(intent: Intent?) {
        Log.d("IncomingCall", "=== handleIncomingIntent: START ===")
        if (intent == null) {
            Log.w("IncomingCall", "=== handleIncomingIntent: Intent is NULL ===")
            return
        }
        Log.d("IncomingCall", "=== handleIncomingIntent: Intent extras=${intent.extras?.keySet()?.toList()} ===")
        val callFromIntent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("CALL_DATA", CallNotificationDto::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("CALL_DATA") as? CallNotificationDto
            }

        if (callFromIntent != null) {
            Log.d("IncomingCall", "=== handleIncomingIntent: Got call #${callFromIntent.callNumber}, status=${callFromIntent.status} ===")
            Log.d("IncomingCall", "=== handleIncomingIntent: Adding to CallsManager ===")
            CallsManager.addCall(callFromIntent)
            Log.d("IncomingCall", "=== handleIncomingIntent: Call added ===")
        } else {
            Log.w("IncomingCall", "=== handleIncomingIntent: No CALL_DATA found in intent ===")
        }
        Log.d("IncomingCall", "=== handleIncomingIntent: COMPLETE ===")
    }

    private fun setupLockScreenFlags() {
        Log.d("IncomingCall", "=== setupLockScreenFlags: START, SDK=${Build.VERSION.SDK_INT} ===")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            Log.d("IncomingCall", "=== setupLockScreenFlags: Using modern APIs (O_MR1+) ===")
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            Log.d("IncomingCall", "=== setupLockScreenFlags: setShowWhenLocked=true, setTurnScreenOn=true ===")
            // Try to dismiss keyguard so user can interact immediately
            Log.d("IncomingCall", "=== setupLockScreenFlags: Requesting keyguard dismiss ===")
            (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
                ?.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        super.onDismissSucceeded()
                        Log.d("IncomingCall", "=== setupLockScreenFlags: Keyguard DISMISSED SUCCESSFULLY ===")
                    }

                    override fun onDismissCancelled() {
                        super.onDismissCancelled()
                        Log.w("IncomingCall", "=== setupLockScreenFlags: Keyguard DISMISS CANCELLED ===")
                    }
                })
        } else {
            Log.d("IncomingCall", "=== setupLockScreenFlags: Using legacy window flags ===")
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
            Log.d("IncomingCall", "=== setupLockScreenFlags: Legacy flags added ===")
        }
        Log.d("IncomingCall", "=== setupLockScreenFlags: Acquiring wake lock ===")
        acquireWakeLock()
        Log.d("IncomingCall", "=== setupLockScreenFlags: COMPLETE ===")
    }

    private fun bringToFront() {
        Log.d("IncomingCall", "=== bringToFront: START ===")
        // Ensure this activity comes to the front when started
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d("IncomingCall", "=== bringToFront: Android 10+ detected, background start restrictions may apply ===")
        }

        // Request to be shown on top of lock screen immediately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            Log.d("IncomingCall", "=== bringToFront: Calling setShowWhenLocked(true) and setTurnScreenOn(true) ===")
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        Log.d("IncomingCall", "=== bringToFront: COMPLETE ===")
    }

    private fun setupWindowFlags() {
        Log.d("IncomingCall", "=== setupWindowFlags: START ===")
        // Additional flags to ensure window appears on top of everything
        Log.d("IncomingCall", "=== setupWindowFlags: Adding FLAG_KEEP_SCREEN_ON, FLAG_LAYOUT_IN_SCREEN, FLAG_LAYOUT_NO_LIMITS ===")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        // For Android 11+, request to show on top of other apps if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Log.d("IncomingCall", "=== setupWindowFlags: Android 11+, calling setDecorFitsSystemWindows(false) ===")
            window.setDecorFitsSystemWindows(false)
        }
        Log.d("IncomingCall", "=== setupWindowFlags: COMPLETE ===")
    }

    private fun setupSidePanel() {
        Log.d("IncomingCall", "=== setupSidePanel: START ===")
        sideAdapter = SideTabsAdapter { selectedCall ->
            Log.d("IncomingCall", "=== SidePanel: Call selected #${selectedCall.callNumber} ===")
            displayCallDetails(selectedCall)
        }

        val flexboxLayoutManager = FlexboxLayoutManager(this).apply {
            flexDirection = FlexDirection.ROW
            flexWrap = FlexWrap.WRAP
        }
        binding.rvSideTabs.layoutManager = flexboxLayoutManager
        binding.rvSideTabs.adapter = sideAdapter
        Log.d("IncomingCall", "=== setupSidePanel: COMPLETE ===")
    }

    private fun observeCallsQueue() {
        Log.d("IncomingCall", "=== observeCallsQueue: STARTING observation ===")
        // Используем Coroutines для наблюдения за Flows в CallsManager
        lifecycleScope.launch {
            CallsManager.calls.collect { list ->
                Log.d("IncomingCall", "=== observeCallsQueue: Flow emitted, list size=${list.size} ===")
                if (list.isEmpty()) {
                    Log.d("IncomingCall", "=== observeCallsQueue: List is empty, stopping alerts and finishing ===")
                    stopAlerts()
                    Log.d("IncomingCall", "=== observeCallsQueue: Calling finish() ===")
                    finish() // Если вызовов нет — закрываем экран
                } else {
                    Log.d("IncomingCall", "=== observeCallsQueue: Submitting ${list.size} calls to sideAdapter ===")
                    sideAdapter.submitList(list)

                    val currentId = currentCall?.callNumber
                    val currentStillExists =
                        currentId != null && list.any { it.callNumber == currentId }
                    Log.d("IncomingCall", "=== observeCallsQueue: currentId=$currentId, currentStillExists=$currentStillExists ===")

                    // Если текущий вызов пропал из очереди (например, истек таймер) —
                    // переключаемся на следующий
                    if (!currentStillExists) {
                        Log.d("IncomingCall", "=== observeCallsQueue: Current call no longer exists, switching to first in list ===")
                        currentCall = null
                        displayCallDetails(list[0])
                    } else {
                        Log.d("IncomingCall", "=== observeCallsQueue: Current call still exists, updating highlight ===")
                        // Обновляем подсветку выбранной вкладки
                        sideAdapter.setSelectedCallNumber(currentId)
                    }
                }
            }
        }
        Log.d("IncomingCall", "=== observeCallsQueue: Observation coroutine launched ===")
    }

    private fun displayCallDetails(call: CallNotificationDto) {
        Log.d("IncomingCall", "=== displayCallDetails: START for call #${call.callNumber} ===")
        currentCall = call
        sideAdapter.setSelectedCallNumber(call.callNumber)
        Log.d("IncomingCall", "=== displayCallDetails: Selected call #${call.callNumber} in sideAdapter ===")

        val infoBlock = binding.patientInfoBlock

        Log.d("IncomingCall", "=== displayCallDetails: Looking for cached hospitalization ===")
        val cached = findCachedHospitalization(call.callNumber)
        Log.d("IncomingCall", "=== displayCallDetails: Cached hospitalization found=${cached != null} ===")

        val callNumberText = when {
            cached?.dayNumber != null && cached.yearNumber != null -> "${cached.dayNumber}/${cached.yearNumber}"
            !call.callNumber.isNullOrBlank() -> call.callNumber
            else -> "Н/Д"
        }
        infoBlock.callNumberText.text = "Вызов №$callNumberText"
        Log.d("IncomingCall", "=== displayCallDetails: Set callNumberText=$callNumberText ===")

        val status = cached?.status ?: call.status
        infoBlock.statusText.text = status ?: "Неизвестно"

        val patientName =
            cached?.patientName
                ?: cached?.patientFullName
                ?: call.fullName
                ?: "Неизвестный пациент"
        val patientAge = cached?.age ?: call.age ?: "Н/Д"
        val patientSex = cached?.sex ?: call.sex ?: "Н/Д"
        infoBlock.patientDetailsText.text = buildString {
            append(patientName)
            append(", $patientAge лет")
            append(", $patientSex")
        }
        Log.d("IncomingCall", "=== displayCallDetails: Patient: $patientName, $patientAge лет, $patientSex ===")

        val reason = cached?.reason ?: call.reason
        infoBlock.callReasonText.text = reason ?: "Не указана"

        val district = cached?.district ?: call.district
        val point = cached?.point ?: call.point
        val street = cached?.street ?: call.street
        val house = cached?.house ?: call.house
        val apartment = cached?.apartment ?: call.apartment
        infoBlock.callAddressText.text = buildString {
            append("Район: ${district ?: "Н/Д"}, ")
            if (!point.isNullOrBlank()) {
                append("${point.trim()}, ")
            }
            append("ул. ${street ?: "Н/Д"}")
            if (!house.isNullOrBlank()) {
                append(", д. ${house.trim()}")
            }
            if (!apartment.isNullOrBlank() && apartment != "0") {
                append(", кв. ${apartment.trim()}")
            }
        }
        Log.d("IncomingCall", "=== displayCallDetails: Address set ===")

        val timeValue = cached?.callTime ?: call.callTime
        infoBlock.timeData.text = "Дата: ${DateFormatter.formatDateTime(timeValue)}"

        val urgency = cached?.urgency ?: call.urgency
        infoBlock.urgencyData.text =
            urgency?.let { "Срочность: $it" } ?: "Срочность неизвестна"

        // Очищаем поле комментария при переключении между пациентами
        val callId = call.callNumber
        Log.d("IncomingCall", "=== displayCallDetails: Calculating countdown for callId=$callId ===")
        val remainingMs =
            callId?.let { CallsManager.getRemainingIgnoreMillis(it) }
        val secondsToShow =
            if (remainingMs != null) {
                ((remainingMs + 999L) / 1000L).toInt()
            } else {
                2400
            }
        Log.d("IncomingCall", "=== displayCallDetails: Starting countdown with $secondsToShow seconds ===")
        startVisualCountdown(secondsToShow)
        binding.messageEditText.setText("")
        Log.d("IncomingCall", "=== displayCallDetails: COMPLETE ===")
    }

    private fun findCachedHospitalization(callNumber: String?): Hospitalization? {
        val number = callNumber?.trim().orEmpty()
        if (number.isEmpty()) return null

        val list = cachedHospitalizations ?: return null

        val normalized = number.lowercase(Locale.getDefault())
        return list.firstOrNull { h ->
            val hn =
                if (h.dayNumber != null && h.yearNumber != null) {
                    "${h.dayNumber}/${h.yearNumber}"
                } else {
                    null
                }
            hn?.lowercase(Locale.getDefault()) == normalized
        }
    }

    private fun startVisualCountdown(seconds: Int) {
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(seconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSeconds = (millisUntilFinished / 1000).toInt()
                val minutes = totalSeconds / 60
                val secRemaining = totalSeconds % 60
                binding.tvTimer.text =
                    "Осталось времени: ${String.format("%02d", minutes)}:${String.format("%02d", secRemaining)}"

                // Если осталось меньше 10 сек — красим в красный
                if (totalSeconds <= 10) {
                    binding.tvTimer.setTextColor(resources.getColor(R.color.red_1, null))
                } else {
                    binding.tvTimer.setTextColor(resources.getColor(R.color.gray_1, null))
                }
            }

            override fun onFinish() {
                binding.tvTimer.text = "ВРЕМЯ ИСТЕКЛО"
                currentCall?.callNumber?.let { callId ->
                    CallsManager.removeCall(callId)
                }
                currentCall = null
            }
        }.start()
    }

    private fun handleCallAnswer(accepted: Boolean) {
        val call = currentCall ?: return
        val callId = call.callNumber ?: return
        val comment = binding.messageEditText.text.toString()
        val decision = if (accepted) "Accept" else "Reject"

        // NOTE: We NO LONGER stop ringer here - user must press stop button manually
        // Ringer and vibration will continue until user presses "STOP ALERTS" button
        Log.d("IncomingCall", "=== handleCallAnswer: User clicked ${if (accepted) "ACCEPT" else "REJECT"}, but ringer continues ===")

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    callRepository.answerCall(callId, decision, comment)
                }

                if (result.isSuccess) {
                    Toast.makeText(this@IncomingCallActivity, "Отправлено", Toast.LENGTH_SHORT).show()
                    // Удаляем этот вызов из локальной очереди
                    CallsManager.removeCall(callId)
                    currentCall = null // Сбрасываем выбор, чтобы подхватился следующий из очереди
                } else {
                    Toast.makeText(this@IncomingCallActivity, "Ошибка сервера", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("CallAnswer", "Error: ${e.message}")
            }
        }
    }

    // --- Вспомогательные методы (Звук/Вибро) ---

    /**
     * Start continuous alerts - ringer and vibration that persist until stopAlerts() is called
     */
    private fun startContinuousAlerts() {
        Log.d("IncomingCall", "=== startContinuousAlerts: START ===")

        // Start continuous ringer
        Log.d("IncomingCall", "=== startContinuousAlerts: Starting ringer ===")
        IncomingCallRinger.start(this)
        Log.d("IncomingCall", "=== startContinuousAlerts: Ringer started ===")

        // Start continuous vibration
        Log.d("IncomingCall", "=== startContinuousAlerts: Starting vibration ===")
        startVibration()
        Log.d("IncomingCall", "=== startContinuousAlerts: Vibration started ===")

        // Show the stop alerts button
        Log.d("IncomingCall", "=== startContinuousAlerts: Making stop button visible ===")
        binding.buttonStopAlerts.visibility = android.view.View.VISIBLE

        Log.d("IncomingCall", "=== startContinuousAlerts: COMPLETE ===")
    }

    /**
     * Start continuous vibration pattern (until manually stopped)
     */
    private fun startVibration() {
        Log.d("IncomingCall", "=== startVibration: START ===")
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        // Pattern: wait 0ms, vibrate 500ms, pause 500ms, repeat
        val pattern = longArrayOf(0, 500, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("IncomingCall", "=== startVibration: Using VibrationEffect.createWaveform for Android O+ ===")
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            Log.d("IncomingCall", "=== startVibration: Using legacy vibrate pattern ===")
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
        Log.d("IncomingCall", "=== startVibration: Vibration started with pattern [0, 500, 500] ===")
    }

    /**
     * Stop all alerts (ringer and vibration)
     */
    private fun stopAlerts() {
        Log.d("IncomingCall", "=== stopAlerts: START ===")
        stopRinger()
        stopVibration()
        Log.d("IncomingCall", "=== stopAlerts: COMPLETE ===")
    }

    private fun stopRinger() {
        Log.d("IncomingCall", "=== stopRinger: Stopping ringer ===")
        IncomingCallRinger.stop()
        Log.d("IncomingCall", "=== stopRinger: Ringer stopped ===")
    }

    private fun stopVibration() {
        Log.d("IncomingCall", "=== stopVibration: Stopping vibration ===")
        vibrator?.cancel()
        Log.d("IncomingCall", "=== stopVibration: Vibration stopped ===")
    }

    /**
     * Legacy method - kept for compatibility but delegates to stopAlerts()
     */
    private fun stopAlertsLegacy() {
        Log.d("IncomingCall", "=== stopAlertsLegacy: Called (delegating to stopAlerts) ===")
        stopAlerts()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "MedInfo:WakeLock"
        )
        wakeLock?.acquire(3 * 60 * 1000L) // 3 минуты
    }

    override fun onDestroy() {
        Log.d("IncomingCall", "=== onDestroy: START ===")
        super.onDestroy()
        Log.d("IncomingCall", "=== onDestroy: Stopping alerts ===")
        stopAlerts()
        Log.d("IncomingCall", "=== onDestroy: Releasing wake lock ===")
        if (wakeLock?.isHeld == true) wakeLock?.release()
        Log.d("IncomingCall", "=== onDestroy: Cancelling countdown timer ===")
        countdownTimer?.cancel()
        Log.d("IncomingCall", "=== onDestroy: COMPLETE ===")
    }
}