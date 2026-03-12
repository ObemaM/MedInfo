package com.example.medinfo.ui.incoming

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.*
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
        android.util.Log.i("CALL_LOG", "[IncomingCallActivity] ===== onCreate START =====")
        android.util.Log.i("CALL_LOG", "[IncomingCallActivity] Timestamp: ${System.currentTimeMillis()}")
        android.util.Log.i("CALL_LOG", "[IncomingCallActivity] Intent action: ${intent?.action}")
        android.util.Log.i("CALL_LOG", "[IncomingCallActivity] Intent flags: ${intent?.flags}")
        android.util.Log.i("CALL_LOG", "[IncomingCallActivity] Has CALL_DATA: ${intent?.hasExtra("CALL_DATA")}")
        android.util.Log.i("CALL_LOG", "[IncomingCallActivity] SavedInstanceState: ${savedInstanceState != null}")
        
        // Log stack trace to identify caller
        val stackTrace = Thread.currentThread().stackTrace
        android.util.Log.d("CALL_LOG", "[IncomingCallActivity] Call stack (first 10 frames):")
        stackTrace.take(10).forEachIndexed { index, element ->
            android.util.Log.d("CALL_LOG", "  [$index] ${element.className}.${element.methodName}")
        }
        
        super.onCreate(savedInstanceState)
        bringToFront()
        setupLockScreenFlags()

        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)
        android.util.Log.d("INCOMING_CALL", "Layout inflated and set")

        setupWindowFlags()
        preloadCachedCallsIfPossible()

        setupSidePanel()
        android.util.Log.d("INCOMING_CALL", "Side panel setup complete")
        
        android.util.Log.d("INCOMING_CALL", "Handling incoming intent...")
        handleIncomingIntent(intent)
        observeCallsQueue()

        binding.buttonConfirm.setOnClickListener { showConfirmAcceptDialog() }
        binding.buttonReject.setOnClickListener { showConfirmRejectDialog() }

        binding.closeButton.setOnClickListener { finish() }
        binding.infoButton.setOnClickListener { showCallDataDialog() }

        startContinuousAlerts()
        binding.buttonStopAlerts.setOnClickListener {
            stopAlerts()
            binding.buttonStopAlerts.visibility = android.view.View.GONE
        }
        binding.buttonStopAlerts.visibility = android.view.View.VISIBLE
        android.util.Log.i("CALL_LOG", "[IncomingCallActivity] ===== onCreate COMPLETE =====")
    }

    private fun preloadCachedCallsIfPossible() {
        if (cachedHospitalizations != null || cacheLoadJob?.isActive == true) return

        val sharedPrefs = getSharedPreferences("app_session", MODE_PRIVATE)
        val userLogin = sharedPrefs.getString("user_login", null)
        if (userLogin.isNullOrBlank()) return

        cacheLoadJob = lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) {
                callsCache.readCalls(userLogin)
            }
            cachedHospitalizations = cached
            currentCall?.let { displayCallDetails(it) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        android.util.Log.i("CALL_LOG", "[IncomingCallActivity] ===== onNewIntent called =====")
        android.util.Log.i("CALL_LOG", "[IncomingCallActivity] Timestamp: ${System.currentTimeMillis()}")
        android.util.Log.i("CALL_LOG", "[IncomingCallActivity] Intent action: ${intent.action}")
        android.util.Log.i("CALL_LOG", "[IncomingCallActivity] Intent flags: ${intent.flags}")
        android.util.Log.i("CALL_LOG", "[IncomingCallActivity] Has CALL_DATA: ${intent.hasExtra("CALL_DATA")}")
        
        super.onNewIntent(intent)
        setIntent(intent)

        binding.buttonStopAlerts.visibility = android.view.View.VISIBLE

        if (!IncomingCallRinger.isPlaying()) {
            android.util.Log.d("CALL_LOG", "[IncomingCallActivity] Starting ringer")
            IncomingCallRinger.start(this)
        }
        startVibration()

        handleIncomingIntent(intent)
        android.util.Log.i("CALL_LOG", "[IncomingCallActivity] ===== onNewIntent complete =====")
    }

    private fun handleIncomingIntent(intent: Intent?) {
        android.util.Log.d("CALL_LOG", "[IncomingCallActivity] handleIncomingIntent called")
        if (intent == null) {
            android.util.Log.w("CALL_LOG", "[IncomingCallActivity] Intent is NULL")
            return
        }

        val callFromIntent = intent.getSerializableExtra("CALL_DATA") as? CallNotificationDto
        android.util.Log.i("CALL_LOG", "[IncomingCallActivity] CALL_DATA from intent: Call#=${callFromIntent?.callNumber}, Status=${callFromIntent?.status}")

        if (callFromIntent != null) {
            android.util.Log.d("CALL_LOG", "[IncomingCallActivity] Adding call to CallsManager")
            CallsManager.addCall(callFromIntent)
        } else {
            android.util.Log.w("CALL_LOG", "[IncomingCallActivity] CALL_DATA is null or not CallNotificationDto")
        }
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
                ?.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                    override fun onDismissSucceeded() {
                        super.onDismissSucceeded()
                    }

                    override fun onDismissCancelled() {
                        super.onDismissCancelled()
                    }
                })
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

    private fun bringToFront() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    private fun setupWindowFlags() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        lifecycleScope.launch {
            CallsManager.calls.collect { list ->
                if (list.isEmpty()) {
                    stopAlerts()
                    finish()
                } else {
                    sideAdapter.submitList(list)

                    val currentId = currentCall?.callNumber
                    val currentStillExists = currentId != null && list.any { it.callNumber == currentId }

                    if (!currentStillExists) {
                        currentCall = null
                        displayCallDetails(list[0])
                    } else {
                        sideAdapter.setSelectedCallNumber(currentId)
                    }
                }
            }
        }
    }

    private fun displayCallDetails(call: CallNotificationDto) {
        android.util.Log.d("INCOMING_CALL", "displayCallDetails: callNumber=${call.callNumber}")
        currentCall = call
        sideAdapter.setSelectedCallNumber(call.callNumber)
        android.util.Log.d("INCOMING_CALL", "Selected call number set in adapter")

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
        infoBlock.patientDetailsText.text = "$patientName, $patientAge лет, $patientSex"

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

        val callId = call.callNumber
        val remainingMs = callId?.let { CallsManager.getRemainingIgnoreMillis(it) }
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
                    "Осталось: ${String.format("%02d", minutes)}:${String.format("%02d", secRemaining)}"

                // Если осталось меньше 10 сек — красим в красный
                if (totalSeconds <= 10) {
                    binding.tvTimer.setTextColor(resources.getColor(R.color.red_1, null))
                    binding.timerIcon.setColorFilter(resources.getColor(R.color.red_1, null))
                } else {
                    binding.tvTimer.setTextColor(resources.getColor(R.color.gray_1, null))
                    binding.timerIcon.setColorFilter(resources.getColor(R.color.gray_1, null))
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

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    callRepository.answerCall(callId, decision, comment)
                }

                if (result.isSuccess) {
                    Toast.makeText(this@IncomingCallActivity, "Отправлено", Toast.LENGTH_SHORT).show()
                    CallsManager.removeCall(callId)
                    currentCall = null
                } else {
                    Toast.makeText(this@IncomingCallActivity, "Ошибка сервера", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
            }
        }
    }

    // --- Вспомогательные методы (Звук/Вибро) ---

    /**
     * Start continuous alerts - ringer and vibration that persist until stopAlerts() is called
     */
    private fun startContinuousAlerts() {
        IncomingCallRinger.start(this)
        startVibration()
        binding.buttonStopAlerts.visibility = android.view.View.VISIBLE
    }

    /**
     * Start continuous vibration pattern (until manually stopped)
     */
    private fun startVibration() {
        vibrator?.cancel()
        
        val vib = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (vib?.hasVibrator() != true) return
        
        vibrator = vib
        val pattern = longArrayOf(0, 500, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vib.vibrate(pattern, 0)
        }
    }

    /**
     * Stop all alerts (ringer and vibration)
     */
    private fun stopAlerts() {
        stopRinger()
        stopVibration()
    }

    private fun stopRinger() {
        IncomingCallRinger.stop()
    }

    private fun stopVibration() {
        vibrator?.cancel()
        vibrator = null
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "MedInfo:WakeLock"
        )
        wakeLock?.acquire(3 * 60 * 1000L) // 3 минуты
    }

    private fun showCallDataDialog() {
        val call = currentCall ?: return

        val dialogBinding = com.example.medinfo.databinding.DialogCallDataBinding.inflate(layoutInflater)

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setGravity(android.view.Gravity.CENTER)

        val container = dialogBinding.dataContainer

        addDataField(container, "ФИО", call.fullName)
        addDataField(container, "Возраст", call.age)
        addDataField(container, "Пол", call.sex)
        addDataField(container, "Причина вызова", call.reason)
        addDataField(container, "Доп. информация", call.additionalInfo)
        addDataField(container, "Район", call.district)
        addDataField(container, "Населенный пункт", call.point)
        addDataField(container, "Улица", call.street)
        addDataField(container, "Дом", call.house)
        addDataField(container, "Квартира", call.apartment)
        addDataField(container, "Подъезд", call.enterance?.toString())
        addDataField(container, "Долгота", call.longitude?.toString())
        addDataField(container, "Широта", call.latitude?.toString())
        addDataField(container, "Номер бригады", call.brigadeNumber?.toString())
        addDataField(container, "Профиль бригады", call.brigadeProfile)
        addDataField(container, "Номер вызова", call.callNumber)
        addDataField(container, "Время вызова", call.callTime)
        addDataField(container, "Срочность", call.urgency?.toString())
        addDataField(container, "Статус", call.status)
        addDataField(container, "АД", call.bloodPressure)
        
        addDataField(container, "Сознание", call.consciousness)
        addDataField(container, "Судороги", call.convulsions?.let { if (it) "Да" else "Нет" })
        addDataField(container, "Глюкометрия", call.glucometry?.toString())
        addDataField(container, "ЧСС", call.heartRate?.toString())
        addDataField(container, "Кислородная поддержка", call.oxygenSupport?.let { if (it) "Да" else "Нет" })
        addDataField(container, "Беременность", call.pregnant?.let { if (it) "Да" else "Нет" })
        addDataField(container, "ЧДД", call.respirationRate?.toString())
        addDataField(container, "SpO2", call.spO2?.toString())
        addDataField(container, "Время от начала заболевания (ч)", call.startDisease?.toString())
        addDataField(container, "Стеноз", call.stenosis?.let { if (it) "Да" else "Нет" })
        addDataField(container, "Температура", call.temperature?.toString())
        addDataField(container, "LAMS", call.lams?.toString())
        addDataField(container, "mRS", call.mrs?.toString())
        addDataField(container, "VAS", call.vas?.toString())

        call.bleeding?.let { bleeding ->
            addDataField(container, "Кровотечение", bleeding.presence?.let { if (it) "Да" else "Нет" })
            addDataField(container, "Тип кровотечения", bleeding.type)
            bleeding.arterialTourniquet?.let { tourniquet ->
                addDataField(container, "Артериальный жгут", tourniquet.presence?.let { if (it) "Да" else "Нет" })
                addDataField(container, "Время наложения жгута", tourniquet.applicationTime)
            }
        }

        call.venousAccess?.let { venous ->
            addDataField(container, "Венозный доступ", venous.presence?.let { if (it) "Да" else "Нет" })
            addDataField(container, "Метод венозного доступа", venous.method?.joinToString(", "))
        }

        call.ifa?.let { ifa ->
            addDataField(container, "Протезирование ДП", ifa.presence?.let { if (it) "Да" else "Нет" })
            addDataField(container, "Инструменты", ifa.tool?.joinToString(", "))
            addDataField(container, "ИВЛ", ifa.alv?.let { if (it) "Да" else "Нет" })
        }

        addDataField(container, "ID сообщения", call.messageId?.toString())
        addDataField(container, "Текст сообщения", call.messageValue)
        addDataField(container, "DPRM", call.dprm)
        addDataField(container, "NGOD", call.ngod?.toString())
        addDataField(container, "NUMV", call.numv?.toString())
        addDataField(container, "SSMP", call.ssmp?.toString())
        addDataField(container, "TEAM", call.team?.toString())
        addDataField(container, "VOZR", call.vozr)

        dialogBinding.buttonClose.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun addDataField(container: android.widget.LinearLayout, label: String, value: String?) {
        if (value.isNullOrBlank()) return

        val fieldLayout = android.widget.LinearLayout(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = if (container.childCount > 0) 20 else 0
            }
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.data_field_background)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.field_padding_horizontal),
                resources.getDimensionPixelSize(R.dimen.field_padding_vertical),
                resources.getDimensionPixelSize(R.dimen.field_padding_horizontal),
                resources.getDimensionPixelSize(R.dimen.field_padding_vertical)
            )
        }

        val labelView = android.widget.TextView(this).apply {
            text = label
            setTextColor(resources.getColor(R.color.gray_1, null))
            textSize = 16f
            letterSpacing = 0.05f
            typeface = android.graphics.Typeface.create(typeface, android.graphics.Typeface.BOLD)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val valueView = android.widget.TextView(this).apply {
            text = value
            setTextColor(resources.getColor(R.color.gray_1, null))
            textSize = 16f
            gravity = android.view.Gravity.START
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (4 * resources.displayMetrics.density).toInt()
            }
        }

        fieldLayout.addView(labelView)
        fieldLayout.addView(valueView)
        container.addView(fieldLayout)
    }

    private fun showConfirmAcceptDialog() {
        ConfirmAcceptDialogFragment { confirmed ->
            if (confirmed) handleCallAnswer(true)
        }.show(supportFragmentManager, "ConfirmAcceptDialog")
    }

    private fun showConfirmRejectDialog() {
        ConfirmRejectDialogFragment { confirmed ->
            if (confirmed) handleCallAnswer(false)
        }.show(supportFragmentManager, "ConfirmRejectDialog")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlerts()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        countdownTimer?.cancel()
    }
}
