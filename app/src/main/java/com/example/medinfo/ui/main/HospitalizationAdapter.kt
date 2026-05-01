package com.example.medinfo.ui.main

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.medinfo.R
import com.example.medinfo.databinding.ItemHospitalizationBinding
import com.example.medinfo.model.Hospitalization
import com.example.medinfo.util.DateFormatter
import java.util.Locale

class HospitalizationAdapter(
    private var items: List<Hospitalization>,
    private val onCallClicked: (Hospitalization) -> Unit
) : RecyclerView.Adapter<HospitalizationAdapter.ViewHolder>() {

    private var showDecisionTimer = false

    fun setShowDecisionTimer(show: Boolean) {
        if (showDecisionTimer == show) return
        showDecisionTimer = show
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemHospitalizationBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(call: Hospitalization) {

            // Номер звонка
            val callNumber: String =
                    if (call.dayNumber != null && call.yearNumber != null) {
                        "${call.dayNumber}/${call.yearNumber}"
                    } else {
                        "Н/Д"
                    }

            // Номер и статус
            binding.callNumberText.text = "Вызов №${callNumber}"
            binding.statusText.text = call.status

            // Пациент
            binding.patientDetailsText.text = buildString {
                append("${call.patientName ?: "Неизвестный пациент"}")
                append(", ${call.age ?: "Н/Д"} лет")
                append(", ${call.sex ?: "Н/Д"}")
            }

            // Время вызова
            val formattedTime =
                    call.formattedCallTime
                            ?: DateFormatter.formatDateTime(call.callTime)
                                    .also { v -> call.formattedCallTime = v }
            binding.timeData.text = "Дата: $formattedTime"

            // Срочность
            binding.urgencyData.text = call.urgency?.let { "Срочность: $it" } ?: "Срочность неизвестна"

            bindDecisionTimer(call)

            // Причина
            binding.callReasonText.text = call.reason ?: "Не указана"

            // Адрес
            binding.callAddressText.text = buildString {
                append("Район: ${call.district ?: "Н/Д"}, ")
                append("ул. ${call.street ?: "Н/Д"}")
                if (!call.house.isNullOrEmpty()) {
                    append(", д. ${call.house}")
                }
                if (call.apartment != null && call.apartment != "0") {
                    append(", кв. ${call.apartment}")
                }
            }

            // Обработчик клика
            binding.root.setOnClickListener {
                onCallClicked(call)
            }
        }

        private fun bindDecisionTimer(call: Hospitalization) {
            if (!showDecisionTimer) {
                binding.decisionTimerText.visibility = View.GONE
                return
            }

            // Плашка нужна только во вкладке "Требуют решения" и обновляется таймером MainActivity.
            val remainingMillis = call.decisionRemainingMillis
            binding.decisionTimerText.visibility = View.VISIBLE
            binding.decisionTimerText.text =
                if (remainingMillis == null) {
                    "Время на решение: неизвестно"
                } else if (remainingMillis <= 0L) {
                    "Время на решение истекло"
                } else {
                    "Осталось на решение: ${formatRemainingTime(remainingMillis)}"
                }

            val colorRes =
                when {
                    remainingMillis == null -> R.color.border_gray_2
                    remainingMillis <= FIVE_MINUTES_MILLIS -> R.color.red_1
                    remainingMillis <= FIFTEEN_MINUTES_MILLIS -> R.color.yellow_1
                    else -> R.color.green_1
                }
            binding.decisionTimerText.backgroundTintList = ColorStateList.valueOf(
                binding.root.context.getColor(colorRes)
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Оформление вызова в RecyclerView
        val binding = ItemHospitalizationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    // Перерисовываем карточку с уже пересчитанным временем на решение.
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    // Количество элементов
    override fun getItemCount(): Int = items.size

    private fun formatRemainingTime(remainingMillis: Long): String {
        val totalSeconds = (remainingMillis / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }

    private companion object {
        const val FIVE_MINUTES_MILLIS = 5 * 60 * 1000L
        const val FIFTEEN_MINUTES_MILLIS = 15 * 60 * 1000L
    }
}
