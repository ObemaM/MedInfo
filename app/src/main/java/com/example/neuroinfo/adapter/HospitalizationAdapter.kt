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

        // 💡 Привязываем View-элементы к НОВЫМ ID
        val callNumberTextView: TextView = itemView.findViewById(R.id.call_number_text)
        val statusTextView: TextView = itemView.findViewById(R.id.status_text)
        val patientDetailsTextView: TextView = itemView.findViewById(R.id.patient_details_text)
        val callReasonTextView: TextView = itemView.findViewById(R.id.call_reason_text)
        val callAddressTextView: TextView = itemView.findViewById(R.id.call_address_text)
        // Если бы у нас был senior_info_text, мы бы добавили его сюда


        fun bind(call: Hospitalization) {

            // 1. HEADER (Номер и Статус)
            callNumberTextView.text = "Вызов №${call.callNumber}"
            statusTextView.text = call.status

            // 2. ПАЦИЕНТ
            patientDetailsTextView.text = buildString {
                append("${call.patientName ?: "Неизвестный пациент"}")
                append(", ${call.age ?: "Н/Д"} лет")
                append(", ${call.sex ?: "Н/Д"}")
            }

            // 3. ПРИЧИНА
            callReasonTextView.text = call.reason ?: "Не указана"

            // 4. АДРЕС
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
                onCallClicked(call)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Используем R.layout.item_hospitalization
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hospitalization, parent, false)
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