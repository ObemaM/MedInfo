package com.example.medinfo.util

import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import com.example.medinfo.R
import com.example.medinfo.databinding.ItemHospitalizationBinding
import com.example.medinfo.model.api.CallResponseDto
import com.example.medinfo.model.api.HospitalizationDecision
import com.example.medinfo.model.api.HospitalizationResponseDto
import com.example.medinfo.model.api.HospitalizationStatus

// Единый биндинг карточки item_hospitalization. Используется и в списке (HospitalizationAdapter),
// и на экранах деталей/решения, чтобы архивное оформление, статусы, диагноз и дата выглядели
// одинаково. Раньше каждый экран биндил карточку вручную и они расходились (архив выглядел "старым").
// Таймер решения (decisionTimerText) — забота списка, поэтому здесь он только скрывается.
object HospitalizationSummaryBinder {

    fun bind(binding: ItemHospitalizationBinding, hospitalization: HospitalizationResponseDto) {
        val call = hospitalization.call

        bindCardBackground(binding, hospitalization)
        binding.callNumberText.text = "Вызов №${call.dayNumber}/${call.yearNumber}"
        bindStatusBadges(binding, hospitalization)
        binding.patientDetailsText.text = buildPatientLine(call)
        bindOptionalField(binding.birthDayLabel, binding.birthDayText, call.birthDay)
        binding.callReasonText.text = call.reason ?: "Не указана"
        bindOptionalField(binding.diagnosisLabel, binding.diagnosisText, hospitalization.consultationDiagnosis)
        binding.callAddressText.text = buildAddress(call).ifBlank { "Адрес не указан" }

        // Отображаем время начала консультации из поля consultationRequestTime
        val formattedDate = DateFormatter.formatDateTime(hospitalization.consultationRequestTime)
        binding.timeData.text = "Начало консультации: $formattedDate"
        binding.decisionTimerText.visibility = View.GONE
    }

    // Поля карточки, которых может не быть (диагноз, дата рождения): подпись и значение
    // показываем только при наличии данных, иначе обе строки скрыты.
    private fun bindOptionalField(label: View, value: TextView, text: String?) {
        if (text.isNullOrBlank()) {
            label.visibility = View.GONE
            value.visibility = View.GONE
        } else {
            value.text = text
            label.visibility = View.VISIBLE
            value.visibility = View.VISIBLE
        }
    }

    private fun bindCardBackground(
        binding: ItemHospitalizationBinding,
        hospitalization: HospitalizationResponseDto
    ) {
        val colorRes = when (HospitalizationDecision.fromId(hospitalization.decisionId)) {
            HospitalizationDecision.ACCEPTED -> R.color.decision_accepted_bg
            HospitalizationDecision.REJECTED -> R.color.decision_rejected_bg
            HospitalizationDecision.IGNORED -> R.color.decision_ignored_bg
            HospitalizationDecision.NONE,
            null -> R.color.blue_3
        }
        binding.root.backgroundTintList =
            ColorStateList.valueOf(binding.root.context.getColor(colorRes))
    }

    private fun bindStatusBadges(
        binding: ItemHospitalizationBinding,
        hospitalization: HospitalizationResponseDto
    ) {
        val decision = HospitalizationDecision.fromId(hospitalization.decisionId)
        val isArchive = HospitalizationStatus.fromId(hospitalization.statusId)?.isArchive == true

        if (isArchive) {
            // В архиве бейдж показывает решение, а под чертой — статус госпитализации.
            binding.statusText.text = decisionTitle(hospitalization, decision)
            binding.archiveStatusLabel.visibility = View.VISIBLE
            binding.decisionText.visibility = View.VISIBLE
            binding.decisionText.text = hospitalization.statusName
            return
        }

        binding.archiveStatusLabel.visibility = View.GONE
        binding.decisionText.visibility = View.GONE
        binding.statusText.text =
            if (decision == HospitalizationDecision.REJECTED ||
                decision == HospitalizationDecision.IGNORED
            ) {
                decisionTitle(hospitalization, decision)
            } else {
                hospitalization.statusName
            }
    }

    private fun decisionTitle(
        hospitalization: HospitalizationResponseDto,
        decision: HospitalizationDecision?
    ): String {
        hospitalization.decisionName.takeIf { it.isNotBlank() }?.let { return it }

        return when (decision) {
            HospitalizationDecision.ACCEPTED -> "Принята"
            HospitalizationDecision.REJECTED -> "Отклонена"
            HospitalizationDecision.IGNORED -> "Проигнорирована"
            HospitalizationDecision.NONE,
            null -> "Нет решения"
        }
    }

    private fun buildPatientLine(call: CallResponseDto): String {
        val name = listOfNotNull(
            call.patientSurname,
            call.patientName,
            call.patientPatronymic
        ).joinToString(" ").trim().ifBlank { "Неизвестный пациент" }

        return "$name, ${call.age ?: "Н/Д"} лет, ${call.sex ?: "Н/Д"}"
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
}
