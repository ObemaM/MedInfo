package com.example.medinfo.ui.incoming

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.medinfo.R
import com.example.medinfo.config.ConfigManager
import com.example.medinfo.data.manager.CallsManager
import com.example.medinfo.data.manager.MessagesEventBus
import com.example.medinfo.data.network.RetrofitClient
import com.example.medinfo.data.repository.HospitalizationRepository
import com.example.medinfo.databinding.ActivityIncomingCallBinding
import com.example.medinfo.model.api.CallResponseDto
import com.example.medinfo.model.api.HospitalizationDecision
import com.example.medinfo.model.api.HospitalizationResponseDto
import com.example.medinfo.model.api.MessageOrigin
import com.example.medinfo.model.api.MessageResponseDto
import com.example.medinfo.model.api.MessageType
import com.example.medinfo.model.api.PatientConditionResponseDto
import com.example.medinfo.notifications.TestMessageSimulator
import com.example.medinfo.ui.chat.ChatActivity
import com.example.medinfo.util.CallLog
import com.example.medinfo.util.DateFormatter
import com.example.medinfo.util.DecisionTimerStage
import com.example.medinfo.util.PatientConditionFields
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import java.util.Locale
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
    private var isFullDetailsExpanded: Boolean = false
    private var isPatientConditionExpanded: Boolean = false
    private var lastPatientCondition: PatientConditionResponseDto? = null
    // Последний номер телефона бригады из PATIENT_CONDITION-сообщений. Показывается в секции "Бригада".
    private var lastBrigadePhone: String? = null
    private val unreadChatMessageIds = mutableSetOf<String>()
    private var keepCurrentHospitalizationWhenMissingFromQueue: Boolean = false

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
        updateChatUnreadBadge()
        binding.fullDetailsCard.setOnClickListener {
            setFullDetailsExpanded(!isFullDetailsExpanded)
        }
        binding.patientConditionSection.patientConditionCard.setOnClickListener {
            setPatientConditionExpanded(!isPatientConditionExpanded)
        }

        observeIncomingPatientCondition()

        binding.buttonStopAlerts.setOnClickListener {
            stopAlerts()
            binding.buttonStopAlerts.visibility = android.view.View.GONE
        }

        // DEBUG: кнопка видна только когда в конфиге включён testCallEnabled.
        // Эмитит фейковое сообщение в чат: если ChatActivity открыт — оно появится сразу,
        // если закрыт — прилетит системное уведомление, тап по которому откроет чат.
        val debugVisibility =
            if (ConfigManager.testCallEnabled) android.view.View.VISIBLE else android.view.View.GONE
        binding.debugSimulateMessageButton.visibility = debugVisibility
        binding.debugSimulateConditionButton.visibility = debugVisibility

        binding.debugSimulateMessageButton.setOnClickListener {
            val id = currentHospitalization?.id ?: return@setOnClickListener
            TestMessageSimulator.simulateBrigadeMessage(this, id)
        }
        binding.debugSimulateConditionButton.setOnClickListener {
            val id = currentHospitalization?.id ?: return@setOnClickListener
            TestMessageSimulator.simulatePatientCondition(this, id)
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
            boundHospitalizationId = null
            keepCurrentHospitalizationWhenMissingFromQueue = !shouldStartAlerts(intent)

            if (shouldStartAlerts(intent)) {
                CallsManager.upsertCall(hospitalization)
            }
            displayCallDetails(hospitalization)

            CallLog.hospitalization("IncomingCallActivity", hospitalization, "received EXTRA_HOSPITALIZATION")
            return
        }

        val hospitalizationId = intent?.getStringExtra(EXTRA_HOSPITALIZATION_ID)
        if (!hospitalizationId.isNullOrBlank()) {
            val queuedHospitalization = CallsManager.calls.value.firstOrNull {
                it.id == hospitalizationId
            }
            if (queuedHospitalization != null) {
                requestedHospitalizationId = queuedHospitalization.id
                currentHospitalization = queuedHospitalization
                boundHospitalizationId = null
                keepCurrentHospitalizationWhenMissingFromQueue = !shouldStartAlerts(intent)
                displayCallDetails(queuedHospitalization)

                CallLog.hospitalization(
                    "IncomingCallActivity",
                    queuedHospitalization,
                    "received EXTRA_HOSPITALIZATION_ID"
                )
                return
            }

            CallLog.event(
                "IncomingCallActivity",
                "missing queued hospitalization id=$hospitalizationId"
            )
        }
    }

    private fun readHospitalizationExtra(intent: Intent?): HospitalizationResponseDto? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_HOSPITALIZATION, HospitalizationResponseDto::class.java)
        } else {
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
                    if (keepCurrentHospitalizationWhenMissingFromQueue && currentHospitalization != null) {
                        return@collect
                    }
                    stopAlerts()
                    finish()
                } else {
                    val targetId = currentHospitalization?.id ?: requestedHospitalizationId
                    val currentUpdated = targetId?.let { id -> list.firstOrNull { it.id == id } }

                    if (currentUpdated != null) {
                        displayCallDetails(currentUpdated)
                    } else if (keepCurrentHospitalizationWhenMissingFromQueue && currentHospitalization != null) {
                        return@collect
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
            unreadChatMessageIds.clear()
            updateChatUnreadBadge()
            lastBrigadePhone = null
            bindSummary(hospitalization)
            bindDetails(hospitalization)
            setFullDetailsExpanded(false)
            // Сбрасываем состояние "Состояние пациента" под новый вызов и подгружаем последнюю запись из чата.
            lastPatientCondition = null
            renderPatientCondition(null)
            fetchLatestPatientCondition(hospitalization.id)
            boundHospitalizationId = hospitalization.id
        }

        // Таймер берём из CallsManager, чтобы экран решения и список "Требуют решения" шли синхронно.
        val remainingMs = CallsManager.getRemainingIgnoreMillis(hospitalization.id)
        val fallbackSeconds = ((ConfigManager.maxCallDurationMs + 999L) / 1000L).toInt()
        val secondsToShow = if (remainingMs != null) ((remainingMs + 999L) / 1000L).toInt() else fallbackSeconds
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
        // Служебные id скрыты — врачу важны только осмысленные поля (статус, время, данные вызова).
        addSection(container, "Госпитализация")
        addDataField(container, "Статус госпитализации", hospitalization.statusName)
        addDataField(container, "Решение", hospitalization.decisionName)
        addDataField(container, "Время создания госпитализации", formatDateTime(hospitalization.creationTime))
        addDataField(container, "Уведомление отправлено", formatBoolean(hospitalization.isNotificationSent))
        addDataField(container, "Время подтверждения уведомления", formatDateTime(hospitalization.notificationTime))
        addDataField(container, "Время принятия решения", formatDateTime(hospitalization.decisionTime))

        addSection(container, "Вызов")
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
        // Телефон из PATIENT_CONDITION-сообщений, не из CallDto.
        addDataField(container, "Телефон бригады", lastBrigadePhone)
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

    private fun setFullDetailsExpanded(expanded: Boolean) {
        isFullDetailsExpanded = expanded
        binding.fieldsContainer.visibility =
            if (expanded) android.view.View.VISIBLE else android.view.View.GONE
        binding.fullDetailsArrow.rotation = if (expanded) 180f else 0f
        binding.fullDetailsArrow.contentDescription =
            if (expanded) "Свернуть полные данные вызова" else "Раскрыть полные данные вызова"
    }

    private fun setPatientConditionExpanded(expanded: Boolean) {
        isPatientConditionExpanded = expanded
        binding.patientConditionSection.patientConditionContainer.visibility =
            if (expanded) android.view.View.VISIBLE else android.view.View.GONE
        binding.patientConditionSection.patientConditionArrow.rotation = if (expanded) 180f else 0f
        binding.patientConditionSection.patientConditionArrow.contentDescription =
            if (expanded) "Свернуть состояние пациента" else "Раскрыть состояние пациента"
    }

    // Берём из истории сообщений последнее с PatientCondition. Если такого нет — карточка прячется.
    private fun fetchLatestPatientCondition(hospitalizationId: String) {
        lifecycleScope.launch {
            val messages = try {
                withContext(Dispatchers.IO) {
                    hospitalizationRepository.getMessages(hospitalizationId).content.orEmpty()
                }
            } catch (_: Exception) {
                emptyList()
            }

            val serverCondition = messages
                .lastOrNull {
                    MessageType.fromId(it.type) == MessageType.PATIENT_CONDITION &&
                        it.patientCondition != null
                }
                ?.patientCondition

            // Телефон может быть в другом сообщении, не там, где свежие condition — берём отдельно.
            val serverPhone = messages.lastOrNull { !it.phoneNumber.isNullOrBlank() }?.phoneNumber
            val cachedConditionMessage = MessagesEventBus.latestPatientConditionMessage(hospitalizationId)
            val condition = cachedConditionMessage?.patientCondition ?: serverCondition
            val phone = cachedConditionMessage?.phoneNumber
                ?: MessagesEventBus.latestPhoneNumber(hospitalizationId)
                ?: serverPhone

            if (boundHospitalizationId == hospitalizationId) {
                renderPatientCondition(condition)
                applyBrigadePhone(phone)
            }
        }
    }

    // Подписываемся на realtime-сообщения и обновляем карточку, если прилетела новая запись о пациенте.
    private fun observeIncomingPatientCondition() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                syncCachedPatientCondition()
                MessagesEventBus.incoming.collect { message: MessageResponseDto ->
                    val expectedId = boundHospitalizationId ?: return@collect
                    if (message.hospitalizationId != expectedId) return@collect

                    markChatMessageUnread(message)

                    // Телефон обновляется на любом сообщении: пустые значения игнорируются внутри.
                    applyBrigadePhone(message.phoneNumber)

                    if (MessageType.fromId(message.type) != MessageType.PATIENT_CONDITION) return@collect
                    val condition = message.patientCondition ?: return@collect
                    renderPatientCondition(condition)
                }
            }
        }
    }

    private fun markChatMessageUnread(message: MessageResponseDto) {
        if (MessageOrigin.fromId(message.origin) != MessageOrigin.TABLET) return
        if (unreadChatMessageIds.add(message.id)) {
            updateChatUnreadBadge()
        }
    }

    private fun clearChatUnreadMessages() {
        if (unreadChatMessageIds.isEmpty()) return
        unreadChatMessageIds.clear()
        updateChatUnreadBadge()
    }

    private fun updateChatUnreadBadge() {
        val unreadCount = unreadChatMessageIds.size
        binding.chatUnreadBadge.visibility =
            if (unreadCount > 0) android.view.View.VISIBLE else android.view.View.GONE
        if (unreadCount > 0) {
            binding.chatUnreadBadge.text = unreadCount.coerceAtMost(9).toString()
        }
    }

    private fun syncCachedPatientCondition() {
        val hospitalizationId = boundHospitalizationId ?: return
        val conditionMessage = MessagesEventBus.latestPatientConditionMessage(hospitalizationId)
        val phone = conditionMessage?.phoneNumber
            ?: MessagesEventBus.latestPhoneNumber(hospitalizationId)

        applyBrigadePhone(phone)
        conditionMessage?.patientCondition?.let { renderPatientCondition(it) }
    }

    // Запоминаем новый телефон и переотрисовываем секцию "Полные данные вызова",
    // чтобы поле "Телефон бригады" обновилось. null/пустое игнорируем по той же причине,
    // что и в ChatActivity: текстовые сообщения без phoneNumber не должны стирать актуальный номер.
    // Запоминает новый телефон и перерисовывает секцию "Бригада". null/пустое игнорируем.
    private fun applyBrigadePhone(phone: String?) {
        if (phone.isNullOrBlank()) return
        if (phone == lastBrigadePhone) return
        lastBrigadePhone = phone
        currentHospitalization?.let { bindDetails(it) }
    }

    private fun renderPatientCondition(condition: PatientConditionResponseDto?) {
        lastPatientCondition = condition
        val rendered = PatientConditionFields.render(
            binding.patientConditionSection.patientConditionContainer,
            condition
        )
        if (!rendered) {
            binding.patientConditionSection.patientConditionCard.visibility = android.view.View.GONE
            setPatientConditionExpanded(false)
        } else {
            binding.patientConditionSection.patientConditionCard.visibility = android.view.View.VISIBLE
            // По требованию — раскрываем сразу, как только появились данные.
            setPatientConditionExpanded(true)
        }
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

                // На экране вызова используем только два состояния: красный в финальной зоне,
                // иначе — стандартный цвет текста проекта. В списке "Требуют решения" остаётся
                // полная трёхцветная шкала.
                val colorRes = if (DecisionTimerStage.colorRes(millisUntilFinished) == R.color.red_1) {
                    R.color.red_1
                } else {
                    R.color.gray_1
                }
                val color = resources.getColor(colorRes, null)
                binding.timerText.setTextColor(color)
                binding.timerIcon.setColorFilter(color)
            }

            override fun onFinish() {
                // Авто-IGNORED и удаление инициирует CallsManager: его таймер истекает чуть раньше
                // визуального (visualSeconds = ceil(remainingMs / 1000)). Здесь только показываем
                // финальное состояние; при пустой очереди observeCallsQueue закроет экран.
                binding.timerText.text = "ВРЕМЯ ИСТЕКЛО"
                val expiredColor = resources.getColor(R.color.red_1, null)
                binding.timerText.setTextColor(expiredColor)
                binding.timerIcon.setColorFilter(expiredColor)
                countdownHospitalizationId = null
            }
        }.start()
    }

    private fun openChatScreen() {
        val hospitalization = currentHospitalization
        if (hospitalization == null) {
            Toast.makeText(this, "Нет данных для открытия чата", Toast.LENGTH_SHORT).show()
            return
        }

        clearChatUnreadMessages()

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
                finish()
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

    private fun addSection(container: LinearLayout, title: String) {
        val sectionView = TextView(this).apply {
            text = title
            setTextColor(ContextCompat.getColor(this@IncomingCallActivity, R.color.main_1))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.02f
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(ContextCompat.getColor(this@IncomingCallActivity, R.color.blue_3))
                setStroke(1.dp(), ContextCompat.getColor(this@IncomingCallActivity, R.color.border_gray_1))
                cornerRadius = 12.dp().toFloat()
            }
            setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = if (container.childCount == 0) 0 else 18.dp()
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

    private fun addDataField(container: LinearLayout, label: String, value: String?) {
        if (value.isNullOrBlank()) return

        val fieldLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 2.dp()
            }
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(14.dp(), 10.dp(), 14.dp(), 10.dp())
        }

        val labelView = TextView(this).apply {
            text = label
            setTextColor(ContextCompat.getColor(this@IncomingCallActivity, R.color.gray_1))
            textSize = 15f
            typeface = Typeface.DEFAULT
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.42f
            ).apply {
                rightMargin = 12.dp()
            }
        }

        val valueView = TextView(this).apply {
            text = value
            setTextColor(ContextCompat.getColor(this@IncomingCallActivity, R.color.black_1))
            textSize = 15f
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.58f
            )
        }

        fieldLayout.addView(labelView)
        fieldLayout.addView(valueView)
        container.addView(fieldLayout)

        val divider = android.view.View(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@IncomingCallActivity, R.color.border_gray_1))
            alpha = 0.65f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                leftMargin = 14.dp()
                rightMargin = 14.dp()
            }
        }
        container.addView(divider)
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

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_HOSPITALIZATION = "EXTRA_HOSPITALIZATION"
        const val EXTRA_HOSPITALIZATION_ID = "EXTRA_HOSPITALIZATION_ID"
        const val EXTRA_START_ALERTS = "EXTRA_START_ALERTS"
    }
}
