package com.example.medinfo.ui.incoming

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.medinfo.R
import com.example.medinfo.data.manager.CallsManager
import com.example.medinfo.data.network.RetrofitClient
import com.example.medinfo.data.repository.HospitalizationRepository
import com.example.medinfo.databinding.ActivityIncomingCallBinding
import com.example.medinfo.databinding.DialogCallDataBinding
import com.example.medinfo.model.CallNotificationDto
import com.example.medinfo.model.api.CallResponseDto
import com.example.medinfo.model.api.HospitalizationDecision
import com.example.medinfo.model.api.HospitalizationResponseDto
import com.example.medinfo.util.DateFormatter
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IncomingCallActivity : AppCompatActivity() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val hospitalizationRepository = HospitalizationRepository(RetrofitClient.apiServiceService)
    private var vibrator: Vibrator? = null

    private lateinit var binding: ActivityIncomingCallBinding
    private lateinit var sideAdapter: SideTabsAdapter

    private var currentHospitalization: HospitalizationResponseDto? = null
    private var countdownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        android.util.Log.i("CALL_LOG", "[IncomingCallActivity] onCreate START")
        super.onCreate(savedInstanceState)

        bringToFront()
        setupLockScreenFlags()

        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowFlags()
        setupSidePanel()

        // Оставляем поддержку legacy-intent для локальных тестовых сценариев.
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        binding.buttonStopAlerts.visibility = android.view.View.VISIBLE

        if (!IncomingCallRinger.isPlaying()) {
            IncomingCallRinger.start(this)
        }
        startVibration()

        // Нужен только для совместимости с тестовыми CALL_DATA.
        handleIncomingIntent(intent)
    }

    // Читаем старый extra только для тестовых запусков экрана.
    private fun handleIncomingIntent(intent: Intent?) {
        val legacyCall = intent?.getSerializableExtra("CALL_DATA") as? CallNotificationDto ?: return
        CallsManager.upsertCall(legacyCall.toHospitalizationResponseDto())
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)
                ?.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {})
        } else {
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

    // Боковая панель теперь показывает госпитализации из нового SignalR-потока.
    private fun setupSidePanel() {
        sideAdapter = SideTabsAdapter { selectedHospitalization ->
            displayCallDetails(selectedHospitalization)
        }

        val flexboxLayoutManager = FlexboxLayoutManager(this).apply {
            flexDirection = FlexDirection.ROW
            flexWrap = FlexWrap.WRAP
        }
        binding.rvSideTabs.layoutManager = flexboxLayoutManager
        binding.rvSideTabs.adapter = sideAdapter
    }

    // Следим за очередью входящих госпитализаций через CallsManager.
    private fun observeCallsQueue() {
        lifecycleScope.launch {
            CallsManager.calls.collect { list ->
                if (list.isEmpty()) {
                    stopAlerts()
                    finish()
                } else {
                    sideAdapter.submitList(list)

                    val currentId = currentHospitalization?.id
                    val currentUpdated = currentId?.let { id -> list.firstOrNull { it.id == id } }

                    if (currentUpdated != null) {
                        displayCallDetails(currentUpdated)
                    } else {
                        displayCallDetails(list.first())
                    }
                }
            }
        }
    }

    // Заполняем экран данными новой госпитализации и вложенного вызова.
    private fun displayCallDetails(hospitalization: HospitalizationResponseDto) {
        currentHospitalization = hospitalization
        sideAdapter.setSelectedHospitalizationId(hospitalization.id)

        val responseCall = hospitalization.call
        val infoBlock = binding.patientInfoBlock

        infoBlock.callNumberText.text = "Вызов №${responseCall.dayNumber}/${responseCall.yearNumber}"
        infoBlock.statusText.text = hospitalization.statusName

        val patientName = listOfNotNull(
            responseCall.patientSurname,
            responseCall.patientName,
            responseCall.patientPatronymic
        ).joinToString(" ").ifBlank { "Неизвестный пациент" }

        infoBlock.patientDetailsText.text =
            "$patientName, ${responseCall.age ?: "Н/Д"} лет, ${responseCall.sex ?: "Н/Д"}"

        infoBlock.callReasonText.text = responseCall.reason ?: "Не указана"

        infoBlock.callAddressText.text = buildString {
            append("Район: ${responseCall.district ?: "Н/Д"}, ")
            if (!responseCall.point.isNullOrBlank()) {
                append("${responseCall.point.trim()}, ")
            }
            append("ул. ${responseCall.street ?: "Н/Д"}")
            if (!responseCall.house.isNullOrBlank()) {
                append(", д. ${responseCall.house.trim()}")
            }
            if (!responseCall.apartment.isNullOrBlank() && responseCall.apartment != "0") {
                append(", кв. ${responseCall.apartment.trim()}")
            }
        }

        infoBlock.timeData.text = "Дата: ${DateFormatter.formatDateTime(responseCall.callTime)}"
        infoBlock.urgencyData.text =
            responseCall.urgency?.let { "Срочность: $it" } ?: "Срочность неизвестна"

        val remainingMs = CallsManager.getRemainingIgnoreMillis(hospitalization.id)
        val secondsToShow = if (remainingMs != null) ((remainingMs + 999L) / 1000L).toInt() else 2400
        startVisualCountdown(secondsToShow)
        binding.messageEditText.setText("")
    }

    private fun startVisualCountdown(seconds: Int) {
        countdownTimer?.cancel()
        countdownTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSeconds = (millisUntilFinished / 1000).toInt()
                val minutes = totalSeconds / 60
                val secRemaining = totalSeconds % 60
                binding.tvTimer.text =
                    "Осталось: ${String.format("%02d", minutes)}:${String.format("%02d", secRemaining)}"

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
                currentHospitalization?.id?.let { hospitalizationId ->
                    CallsManager.removeCall(hospitalizationId)
                }
                currentHospitalization = null
            }
        }.start()
    }

    // Отправляем решение по госпитализации и опциональный комментарий отдельным сообщением.
    private fun handleDecision(accepted: Boolean) {
        val hospitalization = currentHospitalization ?: return
        val comment = binding.messageEditText.text.toString().trim()
        val decisionId = if (accepted) {
            HospitalizationDecision.ACCEPTED.id
        } else {
            HospitalizationDecision.REJECTED.id
        }

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (comment.isNotBlank()) {
                        hospitalizationRepository.sendMessage(
                            hospitalizationId = hospitalization.id,
                            messageText = comment
                        )
                    }

                    hospitalizationRepository.saveDecision(
                        hospitalizationId = hospitalization.id,
                        decisionId = decisionId
                    )
                }

                Toast.makeText(this@IncomingCallActivity, "Отправлено", Toast.LENGTH_SHORT).show()
                CallsManager.removeCall(hospitalization.id)
                currentHospitalization = null
            } catch (e: Exception) {
                Toast.makeText(
                    this@IncomingCallActivity,
                    e.message ?: "Ошибка сервера",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun startContinuousAlerts() {
        IncomingCallRinger.start(this)
        startVibration()
        binding.buttonStopAlerts.visibility = android.view.View.VISIBLE
    }


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
        wakeLock?.acquire(3 * 60 * 1000L)
    }

    // Диалог оставляем, но показываем только данные, которые реально есть в новом DTO вызова.
    private fun showCallDataDialog() {
        val hospitalization = currentHospitalization ?: return
        val responseCall = hospitalization.call

        val dialogBinding = DialogCallDataBinding.inflate(layoutInflater)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setGravity(Gravity.CENTER)

        val container = dialogBinding.dataContainer

        addDataField(
            container,
            "ФИО",
            listOfNotNull(
                responseCall.patientSurname,
                responseCall.patientName,
                responseCall.patientPatronymic
            ).joinToString(" ").ifBlank { null }
        )
        addDataField(container, "Возраст", responseCall.age)
        addDataField(container, "Пол", responseCall.sex)
        addDataField(container, "Причина вызова", responseCall.reason)
        addDataField(container, "Доп. информация", responseCall.additionalInfo)
        addDataField(container, "Кто вызвал", responseCall.whoCall)
        addDataField(container, "Тип вызова", responseCall.callType)
        addDataField(container, "Профиль вызова", responseCall.callProfile)
        addDataField(container, "Комментарий к вызову", responseCall.comment)
        addDataField(container, "Район", responseCall.district)
        addDataField(container, "Населенный пункт", responseCall.point)
        addDataField(container, "Улица", responseCall.street)
        addDataField(container, "Дом", responseCall.house)
        addDataField(container, "Квартира", responseCall.apartment)
        addDataField(container, "Подъезд", responseCall.entrance?.toString())
        addDataField(container, "Долгота", responseCall.longitude?.toString())
        addDataField(container, "Широта", responseCall.latitude?.toString())
        addDataField(container, "Номер бригады", responseCall.brigadeNumber?.toString())
        addDataField(container, "Профиль бригады", responseCall.brigadeProfile)
        addDataField(container, "Код ССМП бригады", responseCall.brigadeSmpCode.toString())
        addDataField(container, "Номер вызова", "${responseCall.dayNumber}/${responseCall.yearNumber}")
        addDataField(container, "Время вызова", DateFormatter.formatDateTime(responseCall.callTime))
        addDataField(container, "Срочность", responseCall.urgency?.toString())
        addDataField(container, "Статус вызова", responseCall.status)
        addDataField(container, "Статус госпитализации", hospitalization.statusName)
        addDataField(container, "Решение", hospitalization.decisionName)
        addDataField(container, "Место госпитализации", responseCall.hospitalizationPlace)
        addDataField(container, "Результат вызова", responseCall.callResult)
        addDataField(container, "Код МКБ", responseCall.mkbCode)
        addDataField(container, "Основной диагноз", responseCall.mainDiagnosis)
        addDataField(container, "Осложнение", responseCall.secondDiagnosis)
        addDataField(container, "Комментарий к диагнозу", responseCall.diagnosisComment)
        addDataField(container, "Вид травмы", responseCall.diseaseType)
        addDataField(container, "СНИЛС", responseCall.snils)
        addDataField(container, "Тип документа", responseCall.documentType)
        addDataField(container, "Номер документа", responseCall.documentNumber)
        addDataField(container, "СМО", responseCall.smo)
        addDataField(container, "Страховой полис", responseCall.insuranceNumber)
        addDataField(container, "Рация", responseCall.radio)
        addDataField(container, "Машина", responseCall.carNumber)
        addDataField(container, "Километраж", responseCall.mileage)
        addDataField(container, "Код территориальной ССМП", responseCall.territorialSmpCode?.toString())
        addDataField(container, "Номер подстанции", responseCall.substationSmp?.toString())
        addDataField(container, "Номер старшего", responseCall.seniorPersonalNumber)
        addDataField(container, "ФИО старшего", responseCall.seniorFullName)
        addDataField(container, "Первый помощник", responseCall.member1)
        addDataField(container, "Второй помощник", responseCall.member2)
        addDataField(container, "Водитель", responseCall.driver)

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
                topMargin = if (container.childCount > 0) 16 else 0
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
            textSize = 14f
            letterSpacing = 0.05f
            typeface = android.graphics.Typeface.create(typeface, android.graphics.Typeface.BOLD)
        }

        val valueView = android.widget.TextView(this).apply {
            text = value
            setTextColor(resources.getColor(R.color.gray_1, null))
            textSize = 16f
            gravity = Gravity.START
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
            if (confirmed) handleDecision(true)
        }.show(supportFragmentManager, "ConfirmAcceptDialog")
    }

    private fun showConfirmRejectDialog() {
        ConfirmRejectDialogFragment { confirmed ->
            if (confirmed) handleDecision(false)
        }.show(supportFragmentManager, "ConfirmRejectDialog")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlerts()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        countdownTimer?.cancel()
    }

    // Преобразование нужно только для старых тестовых сценариев.
    private fun CallNotificationDto.toHospitalizationResponseDto(): HospitalizationResponseDto {
        val (dayNumber, yearNumber) = parseLegacyCallNumber(callNumber)

        return HospitalizationResponseDto(
            id = callNumber ?: UUID.randomUUID().toString(),
            isNotificationSent = true,
            decisionId = HospitalizationDecision.NONE.id,
            decisionName = "Нет решения",
            statusId = 1,
            statusName = status ?: "Бригада в пути",
            notificationTime = null,
            decisionTime = null,
            call = CallResponseDto(
                id = UUID.randomUUID().toString(),
                brigadeSmpCode = ssmp ?: 0,
                dayNumber = dayNumber,
                yearNumber = yearNumber,
                status = status ?: "",
                hospitalizationPlace = null,
                callTime = callTime ?: "",
                transferTime = null,
                departureTime = null,
                brigadeArrivalTime = null,
                hospitalizationTime = null,
                arrivalHospitalTime = null,
                closeCallTime = null,
                backTime = null,
                reason = reason,
                additionalInfo = additionalInfo,
                whoCall = null,
                callType = null,
                callProfile = null,
                comment = null,
                urgency = urgency,
                callResult = null,
                mkbCode = null,
                mainDiagnosis = null,
                secondDiagnosis = null,
                diagnosisComment = null,
                diseaseType = null,
                place = null,
                sector = null,
                district = district,
                point = point,
                street = street,
                house = house,
                apartment = apartment,
                entrance = enterance,
                entranceCode = null,
                floor = null,
                longitude = longitude,
                latitude = latitude,
                patientName = fullName,
                patientSurname = null,
                patientPatronymic = null,
                sex = sex,
                age = age,
                birthDay = null,
                alcohol = false,
                snils = null,
                documentType = null,
                documentNumber = null,
                smo = null,
                insuranceNumber = null,
                brigadeNumber = brigadeNumber,
                brigadeProfile = brigadeProfile,
                radio = null,
                carNumber = null,
                mileage = null,
                territorialSmpCode = null,
                substationSmp = null,
                substationNumberControl = null,
                substationNumberBase = null,
                seniorPersonalNumber = null,
                seniorFullName = null,
                member1 = null,
                member2 = null,
                driver = null
            )
        )
    }

    private fun parseLegacyCallNumber(callNumber: String?): Pair<Int, Int> {
        val parts = callNumber?.split("/") ?: return 0 to 0
        val dayNumber = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val yearNumber = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return dayNumber to yearNumber
    }
}
