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
import com.example.medinfo.model.CallNotificationDto
import com.example.medinfo.model.api.CallResponseDto
import com.example.medinfo.model.api.HospitalizationDecision
import com.example.medinfo.model.api.HospitalizationResponseDto
import com.example.medinfo.ui.chat.ChatActivity
import com.example.medinfo.util.CallLog
import com.example.medinfo.util.DateFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IncomingCallActivity : AppCompatActivity() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val hospitalizationRepository = HospitalizationRepository(RetrofitClient.apiServiceService)
    private var vibrator: Vibrator? = null

    private lateinit var binding: ActivityIncomingCallBinding

    private var currentHospitalization: HospitalizationResponseDto? = null
    private var requestedHospitalizationId: String? = null
    private var countdownTimer: CountDownTimer? = null
    private var countdownHospitalizationId: String? = null
    private var boundHospitalizationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        CallLog.event("IncomingCallActivity", "onCreate START")
        super.onCreate(savedInstanceState)

        bringToFront()
        setupLockScreenFlags()

        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWindowFlags()

        handleIncomingIntent(intent)
        observeCallsQueue()

        binding.buttonConfirm.setOnClickListener { showConfirmAcceptDialog() }
        binding.buttonReject.setOnClickListener { showConfirmRejectDialog() }
        binding.closeButton.setOnClickListener { finish() }
        binding.chatButton.setOnClickListener { openChatScreen() }
        binding.openChatCard.setOnClickListener { openChatScreen() }

        binding.buttonStopAlerts.setOnClickListener {
            stopAlerts()
            binding.buttonStopAlerts.visibility = android.view.View.GONE
        }

        if (shouldStartAlerts(intent)) {
            startContinuousAlerts()
        } else {
            stopAlerts()
            binding.buttonStopAlerts.visibility = android.view.View.GONE
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (shouldStartAlerts(intent)) {
            binding.buttonStopAlerts.visibility = android.view.View.VISIBLE
            if (!IncomingCallRinger.isPlaying()) {
                IncomingCallRinger.start(this)
            }
            startVibration()
        } else {
            stopAlerts()
            binding.buttonStopAlerts.visibility = android.view.View.GONE
        }

        handleIncomingIntent(intent)
    }

    // Читаем новую госпитализацию из списка или старый extra для тестовых запусков экрана.
    private fun handleIncomingIntent(intent: Intent?) {
        val hospitalization = readHospitalizationExtra(intent)
        if (hospitalization != null) {
            // После удаления нижней очереди экран должен оставаться на вызове, выбранном в списке.
            requestedHospitalizationId = hospitalization.id
            currentHospitalization = hospitalization
            CallLog.hospitalization("IncomingCallActivity", hospitalization, "received EXTRA_HOSPITALIZATION")
            CallsManager.upsertCall(hospitalization)
            return
        }

        val legacyCall = intent?.getSerializableExtra("CALL_DATA") as? CallNotificationDto ?: return
        val converted = legacyCall.toHospitalizationResponseDto()
        CallLog.hospitalization("IncomingCallActivity", converted, "received legacy CALL_DATA")
        CallsManager.upsertCall(converted)
    }

    private fun readHospitalizationExtra(intent: Intent?): HospitalizationResponseDto? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_HOSPITALIZATION, HospitalizationResponseDto::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_HOSPITALIZATION) as? HospitalizationResponseDto
        }
    }

    private fun shouldStartAlerts(intent: Intent?): Boolean {
        return intent?.getBooleanExtra(EXTRA_START_ALERTS, true) ?: true
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

    // Следим за текущей госпитализацией через CallsManager.
    private fun observeCallsQueue() {
        lifecycleScope.launch {
            CallsManager.calls.collect { list ->
                updateRemainingDecisionCount(list.size)
                if (list.isEmpty()) {
                    stopAlerts()
                    finish()
                } else {
                    val targetId = currentHospitalization?.id ?: requestedHospitalizationId
                    val currentUpdated = targetId?.let { id -> list.firstOrNull { it.id == id } }

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
        CallLog.hospitalization("IncomingCallActivity", hospitalization, "display details")

        if (boundHospitalizationId != hospitalization.id) {
            // Данные карточки биндим хотя бы один раз; отдельно следим только за тем, чтобы не перезапускать таймер.
            bindSummary(hospitalization)
            bindDetails(hospitalization)
            boundHospitalizationId = hospitalization.id
        }

        // Таймер берём из CallsManager, чтобы экран решения и список "Требуют решения" шли синхронно.
        val remainingMs = CallsManager.getRemainingIgnoreMillis(hospitalization.id)
        val secondsToShow = if (remainingMs != null) ((remainingMs + 999L) / 1000L).toInt() else 2400
        if (countdownHospitalizationId != hospitalization.id) {
            startVisualCountdown(hospitalization.id, secondsToShow)
        }
    }

    private fun updateRemainingDecisionCount(count: Int) {
        binding.remainingDecisionsText.text = "Требуют решения: $count"
        val color = if (count > 1) R.color.red_1 else R.color.main_1
        binding.remainingDecisionsText.backgroundTintList =
            android.content.res.ColorStateList.valueOf(resources.getColor(color, null))
    }

    private fun bindSummary(hospitalization: HospitalizationResponseDto) {
        val call = hospitalization.call
        val summary = binding.summaryBlock

        summary.callNumberText.text = "Вызов №${call.dayNumber}/${call.yearNumber}"
        summary.statusText.text = hospitalization.statusName
        summary.patientDetailsText.text =
            "${buildPatientName(call).ifBlank { "Неизвестный пациент" }}, ${call.age ?: "Н/Д"} лет, ${call.sex ?: "Н/Д"}"
        summary.callReasonText.text = call.reason ?: "Не указана"
        summary.callAddressText.text = buildAddress(call).ifBlank { "Адрес не указан" }
        summary.timeData.text = "Дата: ${DateFormatter.formatDateTime(call.callTime)}"
        summary.urgencyData.text = call.urgency?.let { "Срочность: $it" } ?: "Срочность неизвестна"
        summary.decisionTimerText.visibility = android.view.View.GONE
        summary.root.setOnClickListener(null)
        summary.root.isClickable = false
    }

    private fun bindDetails(hospitalization: HospitalizationResponseDto) {
        val call = hospitalization.call
        val container = binding.fieldsContainer

        container.removeAllViews()

        // На экране решения показываем тот же список полей, что и в деталях, чтобы интерфейс был единым.
        addSection(container, "Госпитализация")
        addDataField(container, "ID госпитализации", hospitalization.id)
        addDataField(container, "Статус госпитализации", "${hospitalization.statusName} (${hospitalization.statusId})")
        addDataField(container, "Решение", "${hospitalization.decisionName} (${hospitalization.decisionId})")
        addDataField(container, "Уведомление отправлено", formatBoolean(hospitalization.isNotificationSent))
        addDataField(container, "Время подтверждения уведомления", formatDateTime(hospitalization.notificationTime))
        addDataField(container, "Время принятия решения", formatDateTime(hospitalization.decisionTime))

        addSection(container, "Вызов")
        addDataField(container, "ID вызова", call.id)
        addDataField(container, "Номер вызова", "${call.dayNumber}/${call.yearNumber}")
        addDataField(container, "Статус вызова", call.status)
        addDataField(container, "Код ССМП бригады", call.brigadeSmpCode.toString())
        addDataField(container, "Место госпитализации", call.hospitalizationPlace)
        addDataField(container, "Время вызова", formatDateTime(call.callTime))
        addDataField(container, "Передан бригаде", formatDateTime(call.transferTime))
        addDataField(container, "Выезд на вызов", formatDateTime(call.departureTime))
        addDataField(container, "Прибытие бригады", formatDateTime(call.brigadeArrivalTime))
        addDataField(container, "Начало госпитализации", formatDateTime(call.hospitalizationTime))
        addDataField(container, "Прибытие в стационар", formatDateTime(call.arrivalHospitalTime))
        addDataField(container, "Закрытие вызова", formatDateTime(call.closeCallTime))
        addDataField(container, "Возвращение на станцию", formatDateTime(call.backTime))
        addDataField(container, "Срочность", call.urgency?.toString())

        addSection(container, "Основная информация")
        addDataField(container, "Повод", call.reason)
        addDataField(container, "Дополнительная информация", call.additionalInfo)
        addDataField(container, "Кто вызвал", call.whoCall)
        addDataField(container, "Тип вызова", call.callType)
        addDataField(container, "Профиль вызова", call.callProfile)
        addDataField(container, "Комментарий к вызову", call.comment)
        addDataField(container, "Результат вызова", call.callResult)

        addSection(container, "Диагноз")
        addDataField(container, "Код МКБ", call.mkbCode)
        addDataField(container, "Основной диагноз", call.mainDiagnosis)
        addDataField(container, "Осложнение", call.secondDiagnosis)
        addDataField(container, "Комментарий к диагнозу", call.diagnosisComment)
        addDataField(container, "Вид травмы", call.diseaseType)

        addSection(container, "Адрес")
        addDataField(container, "Место", call.place)
        addDataField(container, "Сектор", call.sector?.toString())
        addDataField(container, "Район", call.district)
        addDataField(container, "Населенный пункт", call.point)
        addDataField(container, "Улица", call.street)
        addDataField(container, "Дом", call.house)
        addDataField(container, "Квартира", call.apartment)
        addDataField(container, "Подъезд", call.entrance?.toString())
        addDataField(container, "Код подъезда", call.entranceCode)
        addDataField(container, "Этаж", call.floor?.toString())
        addDataField(container, "Долгота", call.longitude?.toString())
        addDataField(container, "Широта", call.latitude?.toString())

        addSection(container, "Пациент")
        addDataField(container, "ФИО", buildPatientName(call))
        addDataField(container, "Фамилия", call.patientSurname)
        addDataField(container, "Имя", call.patientName)
        addDataField(container, "Отчество", call.patientPatronymic)
        addDataField(container, "Пол", call.sex)
        addDataField(container, "Возраст", call.age)
        addDataField(container, "Дата рождения", call.birthDay)
        addDataField(container, "Алкогольное опьянение", formatBoolean(call.alcohol))
        addDataField(container, "СНИЛС", call.snils)
        addDataField(container, "Тип документа", call.documentType)
        addDataField(container, "Номер документа", call.documentNumber)
        addDataField(container, "СМО", call.smo)
        addDataField(container, "Страховой полис", call.insuranceNumber)

        addSection(container, "Бригада")
        addDataField(container, "Номер бригады", call.brigadeNumber?.toString())
        addDataField(container, "Профиль бригады", call.brigadeProfile)
        addDataField(container, "Рация", call.radio)
        addDataField(container, "Номер машины", call.carNumber)
        addDataField(container, "Километраж", call.mileage)
        addDataField(container, "Код территориальной ССМП", call.territorialSmpCode?.toString())
        addDataField(container, "Номер подстанции", call.substationSmp?.toString())
        addDataField(container, "Подстанция по управлению", call.substationNumberControl?.toString())
        addDataField(container, "Подстанция базирования", call.substationNumberBase?.toString())
        addDataField(container, "Номер старшего", call.seniorPersonalNumber)
        addDataField(container, "ФИО старшего", call.seniorFullName)
        addDataField(container, "Первый помощник", call.member1)
        addDataField(container, "Второй помощник", call.member2)
        addDataField(container, "Водитель", call.driver)
    }

    private fun startVisualCountdown(hospitalizationId: String, seconds: Int) {
        countdownTimer?.cancel()
        countdownHospitalizationId = hospitalizationId
        countdownTimer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val totalSeconds = (millisUntilFinished / 1000).toInt()
                val minutes = totalSeconds / 60
                val secRemaining = totalSeconds % 60
                binding.timerText.text =
                    "Осталось: ${String.format(Locale.ROOT, "%02d:%02d", minutes, secRemaining)}"

                if (totalSeconds <= 10) {
                    binding.timerText.setTextColor(resources.getColor(R.color.red_1, null))
                    binding.timerIcon.setColorFilter(resources.getColor(R.color.red_1, null))
                } else {
                    binding.timerText.setTextColor(resources.getColor(R.color.gray_1, null))
                    binding.timerIcon.setColorFilter(resources.getColor(R.color.main_1, null))
                }
            }

            override fun onFinish() {
                binding.timerText.text = "ВРЕМЯ ИСТЕКЛО"
                countdownHospitalizationId = null
                boundHospitalizationId = null
                currentHospitalization?.id?.let { hospitalizationId ->
                    CallsManager.removeCall(hospitalizationId)
                }
                currentHospitalization = null
            }
        }.start()
    }

    private fun openChatScreen() {
        val hospitalization = currentHospitalization
        if (hospitalization == null) {
            Toast.makeText(this, "Нет данных для открытия чата", Toast.LENGTH_SHORT).show()
            return
        }

        // Чат сразу привязываем к госпитализации, чтобы потом без переделок подключить историю сообщений.
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_HOSPITALIZATION_ID, hospitalization.id)
            putExtra(
                ChatActivity.EXTRA_CHAT_TITLE,
                "Вызов №${hospitalization.call.dayNumber}/${hospitalization.call.yearNumber}"
            )
            putExtra(ChatActivity.EXTRA_READ_ONLY, false)
        }
        startActivity(intent)
    }

    // Отправляем только решение по госпитализации. Сообщения будут жить в отдельном чате.
    private fun handleDecision(accepted: Boolean) {
        val hospitalization = currentHospitalization ?: return
        val decisionId = if (accepted) {
            HospitalizationDecision.ACCEPTED.id
        } else {
            HospitalizationDecision.REJECTED.id
        }

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
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

    private fun addSection(container: android.widget.LinearLayout, title: String) {
        val sectionView = android.widget.TextView(this).apply {
            text = title
            setTextColor(resources.getColor(R.color.gray_1, null))
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = if (container.childCount == 0) 8.dp() else 20.dp()
                leftMargin = 8.dp()
                rightMargin = 8.dp()
            }
        }
        container.addView(sectionView)
    }

    private fun buildPatientName(call: CallResponseDto): String {
        return listOfNotNull(
            call.patientSurname,
            call.patientName,
            call.patientPatronymic
        ).joinToString(" ").trim()
    }

    private fun buildAddress(call: CallResponseDto): String {
        return buildList {
            add(call.district)
            add(call.point)
            call.street?.let { add("ул. $it") }
            call.house?.let { add("д. $it") }
            call.apartment?.takeIf { it != "0" }?.let { add("кв. $it") }
        }
            .filterNot { it.isNullOrBlank() }
            .joinToString(", ")
    }

    private fun formatDateTime(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return DateFormatter.formatDateTime(value)
    }

    private fun formatBoolean(value: Boolean): String = if (value) "Да" else "Нет"

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
        countdownHospitalizationId = null
        boundHospitalizationId = null
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

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_HOSPITALIZATION = "EXTRA_HOSPITALIZATION"
        const val EXTRA_START_ALERTS = "EXTRA_START_ALERTS"
    }
}
