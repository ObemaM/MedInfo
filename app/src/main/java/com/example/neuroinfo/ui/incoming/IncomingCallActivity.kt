package com.example.neuroinfo.ui.incoming

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.neuroinfo.R
import com.example.neuroinfo.data.CallRepository
import com.example.neuroinfo.data.CallsCache
import com.example.neuroinfo.data.CallsManager // Наш синглтон для очереди
import com.example.neuroinfo.data.RetrofitClient
import com.example.neuroinfo.model.CallNotificationDto
import com.example.neuroinfo.model.Hospitalization
import com.example.neuroinfo.util.DateFormatter
import com.example.neuroinfo.util.IncomingCallRinger
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
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
    private val callRepository = CallRepository(RetrofitClient.apiService)
    private val callsCache by lazy { CallsCache(applicationContext) }
    private var vibrator: Vibrator? = null
    private var mediaPlayer: MediaPlayer? = null
    private var cachedHospitalizations: List<Hospitalization>? = null
    private var cacheLoadJob: Job? = null

    private val incomingCallNotificationId = 102

    private lateinit var messageEditText: TextInputEditText
    private lateinit var sideTabsRecyclerView: RecyclerView
    private lateinit var sideAdapter: SideTabsAdapter // Создадим далее

    private var currentCall: CallNotificationDto? = null
    private var countdownTimer: CountDownTimer? = null
    private lateinit var timerTextView: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        IncomingCallRinger.stop()

        // 1. Настройка отображения поверх блокировки (WakeLock + Keyguard)
        setupLockScreenFlags()

        setContentView(R.layout.activity_incoming_call)

        preloadCachedCallsIfPossible()

        timerTextView = findViewById(R.id.tv_timer)

        // 2. Инициализация UI
        messageEditText = findViewById(R.id.message_edit_text)
        sideTabsRecyclerView = findViewById(R.id.rv_side_tabs)

        val btnAccept = findViewById<MaterialButton>(R.id.button_confirm)
        val btnReject = findViewById<MaterialButton>(R.id.button_reject)

        // 3. Настройка боковой панели (корешков)
        setupSidePanel()

        // 3.1. Если Activity открыли из уведомления/сервиса — подхватим CALL_DATA
        handleIncomingIntent(intent)

        // 4. Подписка на очередь вызовов
        observeCallsQueue()

        // 5. Кнопки
        btnAccept.setOnClickListener { handleCallAnswer(true) }
        btnReject.setOnClickListener { handleCallAnswer(false) }

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
        sideTabsRecyclerView.layoutManager = flexboxLayoutManager
        sideTabsRecyclerView.adapter = sideAdapter
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
                    // Если сейчас ничего не выбрано — показываем первый из списка
                    if (currentCall == null) {
                        displayCallDetails(list[0])
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

        // Используем ID из вашего item_hospitalization.xml
        val infoBlock = findViewById<View>(R.id.patient_info_block)

        val cached = findCachedHospitalization(call.callNumber)

        val callNumberText = when {
            cached?.dayNumber != null && cached.yearNumber != null -> "${cached.dayNumber}/${cached.yearNumber}"
            !call.callNumber.isNullOrBlank() -> call.callNumber
            else -> "Н/Д"
        }
        infoBlock.findViewById<TextView>(R.id.call_number_text).text = "Вызов №$callNumberText"

        val status = cached?.status ?: call.status
        infoBlock.findViewById<TextView>(R.id.status_text).text = status ?: "Неизвестно"

        val patientName =
            cached?.patientName
                ?: cached?.patientFullName
                ?: call.fullName
                ?: "Неизвестный пациент"
        val patientAge = cached?.age ?: call.age ?: "Н/Д"
        val patientSex = cached?.sex ?: call.sex ?: "Н/Д"
        infoBlock.findViewById<TextView>(R.id.patient_details_text).text = buildString {
            append(patientName)
            append(", $patientAge лет")
            append(", $patientSex")
        }

        val reason = cached?.reason ?: call.reason
        infoBlock.findViewById<TextView>(R.id.call_reason_text).text = reason ?: "Не указана"

        val district = cached?.district ?: call.district
        val point = cached?.point ?: call.point
        val street = cached?.street ?: call.street
        val house = cached?.house ?: call.house
        val apartment = cached?.apartment ?: call.apartment
        infoBlock.findViewById<TextView>(R.id.call_address_text).text = buildString {
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
        infoBlock.findViewById<TextView>(R.id.time_data).text = "Дата: ${DateFormatter.formatDateTime(timeValue)}"

        val urgency = cached?.urgency ?: call.urgency
        infoBlock.findViewById<TextView>(R.id.urgency_data).text =
            urgency?.let { "Срочность: $it" } ?: "Срочность неизвестна"

        // Очищаем поле комментария при переключении между пациентами
        val callId = call.callNumber
        val remainingMs =
            callId?.let { CallsManager.getRemainingIgnoreMillis(it) }
        val secondsToShow =
            if (remainingMs != null) {
                ((remainingMs + 999L) / 1000L).toInt()
            } else {
                30
            }
        startVisualCountdown(secondsToShow)
        messageEditText.setText("")
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
                val secRemaining = millisUntilFinished / 1000
                timerTextView.text = "Осталось времени: 00:${String.format("%02d", secRemaining)}"

                // Если осталось меньше 10 сек — красим в красный
                if (secRemaining <= 10) {
                    timerTextView.setTextColor(resources.getColor(R.color.red_1, null))
                } else {
                    timerTextView.setTextColor(resources.getColor(R.color.gray_1, null))
                }
            }

            override fun onFinish() {
                timerTextView.text = "ВРЕМЯ ИСТЕКЛО"
            }
        }.start()
    }

    private fun handleCallAnswer(accepted: Boolean) {
        val call = currentCall ?: return
        val callId = call.callNumber ?: return
        val comment = messageEditText.text.toString()
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
            "NeuroInfo:WakeLock"
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