package com.example.medinfo.ui.details

import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.medinfo.R
import com.example.medinfo.data.manager.HospitalizationEventBus
import com.example.medinfo.data.manager.MessagesEventBus
import com.example.medinfo.data.network.RetrofitClient
import com.example.medinfo.data.repository.HospitalizationRepository
import com.example.medinfo.databinding.ActivityHospitalizationDetailsBinding
import com.example.medinfo.model.api.HospitalizationResponseDto
import com.example.medinfo.model.api.HospitalizationStatus
import com.example.medinfo.model.api.MessageType
import com.example.medinfo.model.api.PatientConditionResponseDto
import com.example.medinfo.ui.chat.ChatActivity
import com.example.medinfo.util.DateFormatter
import com.example.medinfo.util.HospitalizationSummaryBinder
import com.example.medinfo.util.PatientConditionFields
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HospitalizationDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHospitalizationDetailsBinding
    private lateinit var hospitalization: HospitalizationResponseDto

    private val hospitalizationRepository by lazy {
        HospitalizationRepository(RetrofitClient.apiServiceService)
    }

    private var isFullDetailsExpanded: Boolean = false
    private var isPatientConditionExpanded: Boolean = false

    // Последний номер телефона бригады из PATIENT_CONDITION-сообщений. Показывается в секции "Бригада".
    private var lastBrigadePhone: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHospitalizationDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val extra = readHospitalizationExtra()
        if (extra == null) {
            Toast.makeText(this, "Не удалось открыть данные вызова", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        hospitalization = extra
        binding.closeButton.setOnClickListener { finish() }
        binding.chatButton.setOnClickListener { openChat(hospitalization) }

        bindSummary(hospitalization)
        bindDetails(hospitalization)

        binding.fullDetailsCard.setOnClickListener {
            setFullDetailsExpanded(!isFullDetailsExpanded)
        }
        binding.patientConditionSection.patientConditionCard.setOnClickListener {
            setPatientConditionExpanded(!isPatientConditionExpanded)
        }
        setFullDetailsExpanded(false)

        renderPatientCondition(null)
        fetchLatestPatientCondition(hospitalization.id)
        observeIncomingPatientCondition(hospitalization.id)
        observeHospitalizationUpdates(hospitalization.id)
    }

    private fun setFullDetailsExpanded(expanded: Boolean) {
        isFullDetailsExpanded = expanded
        binding.fieldsContainer.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.fullDetailsArrow.rotation = if (expanded) 180f else 0f
        binding.fullDetailsArrow.contentDescription =
            if (expanded) "Свернуть полные данные вызова" else "Раскрыть полные данные вызова"
    }

    private fun setPatientConditionExpanded(expanded: Boolean) {
        isPatientConditionExpanded = expanded
        binding.patientConditionSection.patientConditionContainer.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.patientConditionSection.patientConditionArrow.rotation = if (expanded) 180f else 0f
        binding.patientConditionSection.patientConditionArrow.contentDescription =
            if (expanded) "Свернуть состояние пациента" else "Раскрыть состояние пациента"
    }

    private fun fetchLatestPatientCondition(hospitalizationId: String) {
        lifecycleScope.launch {
            val messages = try {
                withContext(Dispatchers.IO) {
                    hospitalizationRepository.getMessages(hospitalizationId).content.orEmpty()
                }
            } catch (_: Exception) {
                emptyList()
            }

            val condition = messages
                .lastOrNull {
                    MessageType.fromId(it.type) == MessageType.PATIENT_CONDITION &&
                        it.patientCondition != null
                }
                ?.patientCondition

            // Телефон может быть в другом сообщении, не там, где свежие condition — берём отдельно.
            val phone = messages.lastOrNull { !it.phoneNumber.isNullOrBlank() }?.phoneNumber

            renderPatientCondition(condition)
            applyBrigadePhone(phone)
        }
    }

    private fun observeIncomingPatientCondition(hospitalizationId: String) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MessagesEventBus.incoming.collect { message ->
                    if (message.hospitalizationId != hospitalizationId) return@collect

                    // Телефон обновляем на любом сообщении, в котором он есть.
                    applyBrigadePhone(message.phoneNumber)

                    if (MessageType.fromId(message.type) != MessageType.PATIENT_CONDITION) return@collect
                    val condition = message.patientCondition ?: return@collect
                    renderPatientCondition(condition)
                }
            }
        }
    }

    private fun observeHospitalizationUpdates(hospitalizationId: String) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                HospitalizationEventBus.updates.collect { updates ->
                    val updated = updates.lastOrNull { it.id == hospitalizationId } ?: return@collect
                    applyHospitalizationUpdate(updated)
                }
            }
        }
    }

    private fun applyHospitalizationUpdate(updated: HospitalizationResponseDto) {
        if (updated == hospitalization) return

        hospitalization = updated
        // Статусы, решение и времена приходят через HospitalizationNotification, а не через чат.
        // Поэтому обновляем сводку и полные данные отдельно от блока состояния пациента.
        bindSummary(updated)
        bindDetails(updated)
        setFullDetailsExpanded(isFullDetailsExpanded)
    }

    // Сохраняет телефон и перерисовывает "Полные данные вызова" (секция "Бригада").
    private fun applyBrigadePhone(phone: String?) {
        if (phone.isNullOrBlank()) return
        if (phone == lastBrigadePhone) return
        lastBrigadePhone = phone
        bindDetails(hospitalization)
    }

    private fun renderPatientCondition(condition: PatientConditionResponseDto?) {
        val rendered = PatientConditionFields.render(
            binding.patientConditionSection.patientConditionContainer,
            condition
        )
        if (!rendered) {
            binding.patientConditionSection.patientConditionCard.visibility = View.GONE
            setPatientConditionExpanded(false)
        } else {
            binding.patientConditionSection.patientConditionCard.visibility = View.VISIBLE
            setPatientConditionExpanded(true)
        }
    }

    private fun readHospitalizationExtra(): HospitalizationResponseDto? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra(EXTRA_HOSPITALIZATION, HospitalizationResponseDto::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra(EXTRA_HOSPITALIZATION) as? HospitalizationResponseDto
        }
    }

    private fun bindSummary(hospitalization: HospitalizationResponseDto) {
        val summary = binding.summaryBlock
        HospitalizationSummaryBinder.bind(summary, hospitalization)

        // На экране деталей карточка-сводка не кликабельна.
        summary.root.setOnClickListener(null)
        summary.root.isClickable = false
    }

    private fun bindDetails(hospitalization: HospitalizationResponseDto) {
        val call = hospitalization.call
        val container = binding.fieldsContainer

        container.removeAllViews()

        // Врачу в раскрытых данных важнее всего быстро увидеть пациента,
        // поэтому блок пациента держим первым, а служебные статусы ниже.
        addSection(container, "Пациент")
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

        addSection(container, "Госпитализация")
        addDataField(container, "Статус госпитализации", hospitalization.statusName)
        addDataField(container, "Решение", hospitalization.decisionName)
        addDataField(container, "Время создания госпитализации", formatDateTime(hospitalization.creationTime))
        addDataField(container, "Время запроса консультации", formatDateTime(hospitalization.consultationRequestTime))
        addDataField(container, "Подтверждение уведомления о консультации", formatDateTime(hospitalization.consultationNotificationTime))
        addDataField(container, "Подтверждение уведомления о госпитализации", formatDateTime(hospitalization.hospitalizationNotificationTime))
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
        addDataField(container, "Основной диагноз", hospitalization.consultationDiagnosis)
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

        addSection(container, "Бригада")
        // Телефон приходит из PATIENT_CONDITION-сообщений, а не из CallDto.
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

    private fun openChat(hospitalization: HospitalizationResponseDto) {
        val isArchive = HospitalizationStatus.fromId(hospitalization.statusId)?.isArchive == true
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_HOSPITALIZATION_ID, hospitalization.id)
            putExtra(
                ChatActivity.EXTRA_CHAT_TITLE,
                "Вызов №${hospitalization.call.dayNumber}/${hospitalization.call.yearNumber}"
            )
            putExtra(ChatActivity.EXTRA_READ_ONLY, isArchive)
            putExtra(ChatActivity.EXTRA_DECISION_ID, hospitalization.decisionId)
            putExtra(ChatActivity.EXTRA_STATUS_ID, hospitalization.statusId)
        }
        startActivity(intent)
    }

    private fun addSection(container: LinearLayout, title: String) {
        val sectionView = TextView(this).apply {
            text = title
            setTextColor(ContextCompat.getColor(this@HospitalizationDetailsActivity, R.color.main_1))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.02f
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(ContextCompat.getColor(this@HospitalizationDetailsActivity, R.color.blue_3))
                setStroke(1.dp(), ContextCompat.getColor(this@HospitalizationDetailsActivity, R.color.border_gray_1))
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
            setTextColor(ContextCompat.getColor(this@HospitalizationDetailsActivity, R.color.gray_1))
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
            setTextColor(ContextCompat.getColor(this@HospitalizationDetailsActivity, R.color.black_1))
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

        val divider = View(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@HospitalizationDetailsActivity, R.color.border_gray_1))
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

    private fun formatDateTime(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return DateFormatter.formatDateTime(value)
    }

    private fun formatBoolean(value: Boolean): String = if (value) "Да" else "Нет"

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_HOSPITALIZATION = "EXTRA_HOSPITALIZATION"
    }
}
