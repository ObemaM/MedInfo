package com.example.neuroinfo.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.neuroinfo.R
import com.example.neuroinfo.model.Hospitalization

class HospitalizationAdapter(
        private var items: List<Hospitalization>,
        private val onCallClicked: (Hospitalization) -> Unit
) : RecyclerView.Adapter<HospitalizationAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        // Привязка View-элементов
        val callNumberTextView: TextView = itemView.findViewById(R.id.call_number_text)
        val timeTextView: TextView = itemView.findViewById(R.id.time_data)
        val statusTextView: TextView = itemView.findViewById(R.id.status_text)
        val patientDetailsTextView: TextView = itemView.findViewById(R.id.patient_details_text)
        val callReasonTextView: TextView = itemView.findViewById(R.id.call_reason_text)
        val callAddressTextView: TextView = itemView.findViewById(R.id.call_address_text)

        val urgencyTextView: TextView = itemView.findViewById(R.id.urgency_data)

        fun bind(call: Hospitalization) {

            // Номер звонка
            val callNumber: String =
                    if (call.dayNumber != null && call.yearNumber != null) {
                        "${call.dayNumber}/${call.yearNumber}"
                    } else {
                        "Н/Д"
                    }

            // Номер и статус
            callNumberTextView.text = "Вызов №${callNumber}"
            statusTextView.text = call.status

            // Пациент
            patientDetailsTextView.text = buildString {
                append("${call.patientName ?: "Неизвестный пациент"}")
                append(", ${call.age ?: "Н/Д"} лет")
                append(", ${call.sex ?: "Н/Д"}")
            }

            // Время вызова
            val formattedTime =
                    call.formattedCallTime
                            ?: com.example.neuroinfo.util.DateFormatter.formatDateTime(call.callTime)
                                    .also { v -> call.formattedCallTime = v }
            timeTextView.text = "Дата: $formattedTime"

            // Срочность
            urgencyTextView.text = call.urgency?.let { "Срочность: $it" } ?: "Срочность неизвестна"

            // Причина
            callReasonTextView.text = call.reason ?: "Не указана"

            // Адрес
            callAddressTextView.text = buildString {
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
            itemView.setOnClickListener {
                val status = call.status?.lowercase()?.trim()
                if (status?.contains("архив") == true) {
                    return@setOnClickListener
                }
                onCallClicked(call)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Используем R.layout.item_hospitalization
        val view =
                LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_hospitalization, parent, false)
        return ViewHolder(view)
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
