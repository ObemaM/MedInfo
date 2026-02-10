package com.example.medinfo.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.medinfo.databinding.ItemHospitalizationBinding
import com.example.medinfo.model.Hospitalization
import com.example.medinfo.util.DateFormatter

class HospitalizationAdapter(
    private var items: List<Hospitalization>,
    private val onCallClicked: (Hospitalization) -> Unit
) : RecyclerView.Adapter<HospitalizationAdapter.ViewHolder>() {

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
                val status = call.status?.lowercase()?.trim()
                if (status?.contains("архив") == true) {
                    return@setOnClickListener
                }
                onCallClicked(call)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHospitalizationBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<Hospitalization>) {
        items = newItems
        notifyDataSetChanged()
    }
}