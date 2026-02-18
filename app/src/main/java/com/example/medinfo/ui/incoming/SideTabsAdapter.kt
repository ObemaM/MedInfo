package com.example.medinfo.ui.incoming

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.medinfo.R
import com.example.medinfo.model.CallNotificationDto
import com.example.medinfo.databinding.ItemSideTabBinding


class SideTabsAdapter(
    private val onTabClick: (CallNotificationDto) -> Unit
) : ListAdapter<CallNotificationDto, SideTabsAdapter.TabViewHolder>(DiffCallback()) {

    // Храним ID выбранного вызова для визуальной подсветки
    private var selectedCallNumber: String? = null

    fun setSelectedCallNumber(callNumber: String?) {
        selectedCallNumber = callNumber
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val binding = ItemSideTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val call = getItem(position)
        holder.bind(call, call.callNumber == selectedCallNumber)

        holder.itemView.setOnClickListener {
            selectedCallNumber = call.callNumber

            // Перерисовываем только то, что изменилось
            notifyDataSetChanged()
            onTabClick(call)
        }
    }

    class TabViewHolder(private val binding: ItemSideTabBinding) : RecyclerView.ViewHolder(binding.root) {
        private val card = binding.tabCardView
        private val numberTextDay = binding.tabCallNumberDay

        private val numberTextYear = binding.tabCallNumberYear
        private val indicator = binding.urgencyIndicator

        // Функция для получения номера дня и года из CallNumber (для корректного отображения)
        fun CallNumberToDayAndYear(call: CallNotificationDto) : List<String> {
            var data = listOf("-", "-")
            if (call.callNumber != null) {
                data = call.callNumber.split("/")
                return data
            }
            else {
                return data
            }
        }

        fun bind(call: CallNotificationDto, isSelected: Boolean) {

            val callNumberParts : List<String> = CallNumberToDayAndYear(call)

            // Безопасное обращение
            numberTextDay.text = "№${callNumberParts.getOrNull(0) ?: ""}/"
            numberTextYear.text = callNumberParts.getOrNull(1) ?: "-"

            // 1. Подсветка выбранной вкладки
            if (isSelected) {
                card.strokeColor = ContextCompat.getColor(itemView.context, R.color.main_1)
                card.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.background_1))
                card.cardElevation = 8f
            } else {
                card.strokeColor = ContextCompat.getColor(itemView.context, R.color.border_gray_1)
                card.setCardBackgroundColor(Color.WHITE)
                card.cardElevation = 2f
            }

            // Цвет индикатора в зависимости от срочности
            val color = when (call.urgency) {
                in 3..6 -> ContextCompat.getColor(itemView.context, R.color.yellow_1)
                in 7 .. 9 -> ContextCompat.getColor(itemView.context, R.color.red_1)
                else -> ContextCompat.getColor(itemView.context, R.color.green_1)
            }
            indicator.setBackgroundColor(color)
        }

    }

    class DiffCallback : DiffUtil.ItemCallback<CallNotificationDto>() {
        override fun areItemsTheSame(oldItem: CallNotificationDto, newItem: CallNotificationDto): Boolean {
            return oldItem.callNumber == newItem.callNumber
        }

        override fun areContentsTheSame(oldItem: CallNotificationDto, newItem: CallNotificationDto): Boolean {
            return oldItem == newItem
        }
    }
}