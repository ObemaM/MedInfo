package com.example.medinfo.ui.details

import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.medinfo.R
import com.example.medinfo.databinding.ActivityHospitalizationDetailsBinding
import com.example.medinfo.model.api.CallResponseDto
import com.example.medinfo.model.api.HospitalizationResponseDto
import com.example.medinfo.model.api.HospitalizationStatus
import com.example.medinfo.ui.chat.ChatActivity
import com.example.medinfo.util.DateFormatter

class HospitalizationDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHospitalizationDetailsBinding
    private lateinit var hospitalization: HospitalizationResponseDto

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

        bindSummary(hospitalization)
        bindDetails(hospitalization)
        bindChatPlaceholder(hospitalization)
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
        val call = hospitalization.call
        val summary = binding.summaryBlock

        summary.callNumberText.text = "Вызов №${call.dayNumber}/${call.yearNumber}"
        summary.statusText.text = hospitalization.statusName

        val patientName = buildPatientName(call).ifBlank { "Неизвестный пациент" }
        summary.patientDetailsText.text =
            "$patientName, ${call.age ?: "Н/Д"} лет, ${call.sex ?: "Н/Д"}"
        summary.callReasonText.text = call.reason ?: "Не указана"
        summary.callAddressText.text = buildAddress(call).ifBlank { "Адрес не указан" }
        summary.timeData.text = "Дата: ${DateFormatter.formatDateTime(call.callTime)}"
        summary.urgencyData.text = call.urgency?.let { "Срочность: $it" } ?: "Срочность неизвестна"
        summary.root.setOnClickListener(null)
        summary.root.isClickable = false
    }

    private fun bindDetails(hospitalization: HospitalizationResponseDto) {
        val call = hospitalization.call
        val container = binding.fieldsContainer

        container.removeAllViews()

        // Выводим все непустые поля DTO, чтобы детали работали и для активных, и для архивных вызовов.
        addSection(container, "Госпитализация")
        addField(container, "ID госпитализации", hospitalization.id)
        addField(container, "Статус госпитализации", "${hospitalization.statusName} (${hospitalization.statusId})")
        addField(container, "Решение", "${hospitalization.decisionName} (${hospitalization.decisionId})")
        addField(container, "Время создания госпитализации", formatDateTime(hospitalization.creationTime))
        addField(container, "Уведомление отправлено", formatBoolean(hospitalization.isNotificationSent))
        addField(container, "Время подтверждения уведомления", formatDateTime(hospitalization.notificationTime))
        addField(container, "Время принятия решения", formatDateTime(hospitalization.decisionTime))

        addSection(container, "Вызов")
        addField(container, "ID вызова", call.id)
        addField(container, "Номер вызова", "${call.dayNumber}/${call.yearNumber}")
        addField(container, "Статус вызова", call.status)
        addField(container, "Код ССМП бригады", call.brigadeSmpCode.toString())
        addField(container, "Место госпитализации", call.hospitalizationPlace)
        addField(container, "Время вызова", formatDateTime(call.callTime))
        addField(container, "Передан бригаде", formatDateTime(call.transferTime))
        addField(container, "Выезд на вызов", formatDateTime(call.departureTime))
        addField(container, "Прибытие бригады", formatDateTime(call.brigadeArrivalTime))
        addField(container, "Начало госпитализации", formatDateTime(call.hospitalizationTime))
        addField(container, "Прибытие в стационар", formatDateTime(call.arrivalHospitalTime))
        addField(container, "Закрытие вызова", formatDateTime(call.closeCallTime))
        addField(container, "Возвращение на станцию", formatDateTime(call.backTime))
        addField(container, "Срочность", call.urgency?.toString())

        addSection(container, "Основная информация")
        addField(container, "Повод", call.reason)
        addField(container, "Дополнительная информация", call.additionalInfo)
        addField(container, "Кто вызвал", call.whoCall)
        addField(container, "Тип вызова", call.callType)
        addField(container, "Профиль вызова", call.callProfile)
        addField(container, "Комментарий к вызову", call.comment)
        addField(container, "Результат вызова", call.callResult)

        addSection(container, "Диагноз")
        addField(container, "Код МКБ", call.mkbCode)
        addField(container, "Основной диагноз", call.mainDiagnosis)
        addField(container, "Осложнение", call.secondDiagnosis)
        addField(container, "Комментарий к диагнозу", call.diagnosisComment)
        addField(container, "Вид травмы", call.diseaseType)

        addSection(container, "Адрес")
        addField(container, "Место", call.place)
        addField(container, "Сектор", call.sector?.toString())
        addField(container, "Район", call.district)
        addField(container, "Населенный пункт", call.point)
        addField(container, "Улица", call.street)
        addField(container, "Дом", call.house)
        addField(container, "Квартира", call.apartment)
        addField(container, "Подъезд", call.entrance?.toString())
        addField(container, "Код подъезда", call.entranceCode)
        addField(container, "Этаж", call.floor?.toString())
        addField(container, "Долгота", call.longitude?.toString())
        addField(container, "Широта", call.latitude?.toString())

        addSection(container, "Пациент")
        addField(container, "ФИО", buildPatientName(call))
        addField(container, "Фамилия", call.patientSurname)
        addField(container, "Имя", call.patientName)
        addField(container, "Отчество", call.patientPatronymic)
        addField(container, "Пол", call.sex)
        addField(container, "Возраст", call.age)
        addField(container, "Дата рождения", call.birthDay)
        addField(container, "Алкогольное опьянение", formatBoolean(call.alcohol))
        addField(container, "СНИЛС", call.snils)
        addField(container, "Тип документа", call.documentType)
        addField(container, "Номер документа", call.documentNumber)
        addField(container, "СМО", call.smo)
        addField(container, "Страховой полис", call.insuranceNumber)

        addSection(container, "Бригада")
        addField(container, "Номер бригады", call.brigadeNumber?.toString())
        addField(container, "Профиль бригады", call.brigadeProfile)
        addField(container, "Рация", call.radio)
        addField(container, "Номер машины", call.carNumber)
        addField(container, "Километраж", call.mileage)
        addField(container, "Код территориальной ССМП", call.territorialSmpCode?.toString())
        addField(container, "Номер подстанции", call.substationSmp?.toString())
        addField(container, "Подстанция по управлению", call.substationNumberControl?.toString())
        addField(container, "Подстанция базирования", call.substationNumberBase?.toString())
        addField(container, "Номер старшего", call.seniorPersonalNumber)
        addField(container, "ФИО старшего", call.seniorFullName)
        addField(container, "Первый помощник", call.member1)
        addField(container, "Второй помощник", call.member2)
        addField(container, "Водитель", call.driver)
    }

    private fun bindChatPlaceholder(hospitalization: HospitalizationResponseDto) {
        // В архиве чат скрываем: переписку планируем только для активных вызовов.
        val isArchive = HospitalizationStatus.fromId(hospitalization.statusId)?.isArchive == true
        binding.chatPlaceholderCard.visibility = if (isArchive) View.GONE else View.VISIBLE
        binding.chatPlaceholderCard.setOnClickListener {
            openChat(hospitalization)
        }
    }

    private fun openChat(hospitalization: HospitalizationResponseDto) {
        val intent = android.content.Intent(this, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_HOSPITALIZATION_ID, hospitalization.id)
            putExtra(
                ChatActivity.EXTRA_CHAT_TITLE,
                "Вызов №${hospitalization.call.dayNumber}/${hospitalization.call.yearNumber}"
            )
            putExtra(ChatActivity.EXTRA_READ_ONLY, false)
        }
        startActivity(intent)
    }

    private fun addSection(container: LinearLayout, title: String) {
        val sectionView = TextView(this).apply {
            text = title
            setTextColor(resources.getColor(R.color.gray_1, null))
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = if (container.childCount == 0) 8.dp() else 20.dp()
                leftMargin = 8.dp()
                rightMargin = 8.dp()
            }
        }
        container.addView(sectionView)
    }

    private fun addField(container: LinearLayout, label: String, value: String?) {
        if (value.isNullOrBlank()) return

        val fieldLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.data_field_background)
            setPadding(14.dp(), 8.dp(), 14.dp(), 8.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dp()
                leftMargin = 8.dp()
                rightMargin = 8.dp()
            }
        }

        val labelView = TextView(this).apply {
            text = label
            setTextColor(resources.getColor(R.color.field_label_color, null))
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val valueView = TextView(this).apply {
            text = value
            setTextColor(resources.getColor(R.color.gray_1, null))
            textSize = 16f
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 3.dp()
            }
        }

        fieldLayout.addView(labelView)
        fieldLayout.addView(valueView)
        container.addView(fieldLayout)
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

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_HOSPITALIZATION = "EXTRA_HOSPITALIZATION"
    }
}
