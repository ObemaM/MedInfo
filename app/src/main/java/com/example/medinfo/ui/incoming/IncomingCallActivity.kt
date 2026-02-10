package com.example.medinfo.ui.incoming

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
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
    private var mediaPlayer: MediaPlayer? = null
    private var cachedHospitalizations: List<Hospitalization>? = null
    private var cacheLoadJob: Job? = null

    private val incomingCallNotificationId = 102

    private lateinit var binding: ActivityIncomingCallBinding
    private lateinit var sideAdapter: SideTabsAdapter // Создадим далее

    private var currentCall: CallNotificationDto? = null
    private var countdownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        IncomingCallRinger.stop()

        // 1. Настройка отображения поверх блокировки (WakeLock + Keyguard)
        setupLockScreenFlags()

        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preloadCachedCallsIfPossible()

        // 2. Инициализация UI

        // 3. Настройка боковой панели (корешков)
        setupSidePanel()

        // 3.1. Если Activity открыли из уведомления/сервиса — подхватим CALL_DATA
        handleIncomingIntent(intent)

        // 4. Подписка на очередь вызовов
        observeCallsQueue()

        // 5. Кнопки
        binding.buttonConfirm.setOnClickListener { handleCallAnswer(true) }
        binding.buttonReject.setOnClickListener { handleCallAnswer(false) }

        // 6. Звук и вибрация
    }

    private fun preloadCachedCallsIfPossible() {
        if (cachedHospitalizations != null) return
        if (cacheLoadJob?.isActive == true) return

        val sharedPrefs = getSharedPreferences("app_session", MODE_PRIVATE)
        val userLogin = sharedPrefs.getString("user_login", null)
        if (userLogin.isNullOrBlank()) return

        cacheLoadJob = lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) {
                callsCache.readCalls(userLogin)
            }
            cachedHospitalizations = cached
            currentCall?.let { call ->
                displayCallDetails(call)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        IncomingCallRinger.stop()
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        val callFromIntent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra("CALL_DATA", CallNotificationDto::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("CALL_DATA") as? CallNotificationDto
            }

        if (callFromIntent != null) {
            CallsManager.addCall(callFromIntent)
        }
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
                ?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
        acquireWakeLock()
    }

    private fun setupSidePanel() {
        sideAdapter = SideTabsAdapter { selectedCall ->
            displayCallDetails(selectedCall)
        }

        val flexboxLayoutManager = FlexboxLayoutManager(this).apply {
            flexDirection = FlexDirection.ROW
            flexWrap = FlexWrap.WRAP
        }
        binding.rvSideTabs.layoutManager = flexboxLayoutManager
        binding.rvSideTabs.adapter = sideAdapter
    }

    private fun observeCallsQueue() {
        // Используем Coroutines для наблюдения за Flows в CallsManager
        lifecycleScope.launch {
            CallsManager.calls.collect { list ->
                if (list.isEmpty()) {
                    stopAlerts()
                    cancelIncomingCallNotification()
                    finish() // Если вызовов нет — закрываем экран
                } else {
                    sideAdapter.submitList(list)

                    val currentId = currentCall?.callNumber
                    val currentStillExists =
                        currentId != null && list.any { it.callNumber == currentId }

                    // Если текущий вызов пропал из очереди (например, истек таймер) —
                    // переключаемся на следующий
                    if (!currentStillExists) {
                        currentCall = null
                        displayCallDetails(list[0])
                    } else {
                        // Обновляем подсветку выбранной вкладки
                        sideAdapter.setSelectedCallNumber(currentId)
                    }
                }
            }
        }
    }

    private fun cancelIncomingCallNotification() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        notificationManager.cancel(incomingCallNotificationId)
    }

    private fun displayCallDetails(call: CallNotificationDto) {
        currentCall = call
        sideAdapter.setSelectedCallNumber(call.callNumber)

        val infoBlock = binding.patientInfoBlock

        val cached = findCachedHospitalization(call.callNumber)

        val callNumberText = when {
            cached?.dayNumber != null && cached.yearNumber != null -> "${cached.dayNumber}/${cached.yearNumber}"
            !call.callNumber.isNullOrBlank() -> call.callNumber
            else -> "Н/Д"
        }
        infoBlock.callNumberText.text = "Вызов №$callNumberText"

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

        val timeValue = cached?.callTime ?: call.callTime
        infoBlock.timeData.text = "Дата: ${DateFormatter.formatDateTime(timeValue)}"

        val urgency = cached?.urgency ?: call.urgency
        infoBlock.urgencyData.text =
            urgency?.let { "Срочность: $it" } ?: "Срочность неизвестна"

        // Очищаем поле комментария при переключении между пациентами
        val callId = call.callNumber
        val remainingMs =
            callId?.let { CallsManager.getRemainingIgnoreMillis(it) }
        val secondsToShow =
            if (remainingMs != null) {
                ((remainingMs + 999L) / 1000L).toInt()
            } else {
                2400
            }
        startVisualCountdown(secondsToShow)
        binding.messageEditText.setText("")
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

        IncomingCallRinger.stop()

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

    private fun startAlerts() {
        // Звук
        val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        mediaPlayer = MediaPlayer.create(this, notification)
        mediaPlayer?.isLooping = true
        mediaPlayer?.start()

        // Вибрация
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 500, 1000)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopAlerts() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()
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
        super.onDestroy()
        stopAlerts()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        countdownTimer?.cancel()
    }
}