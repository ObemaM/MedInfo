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
        private val numberText = binding.tabCallNumber
        private val indicator = binding.urgencyIndicator

        fun bind(call: CallNotificationDto, isSelected: Boolean) {
            numberText.text = "№${call.callNumber}"

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

            // 2. Цвет индикатора в зависимости от срочности (Urgency)
            // Например: 1 - Красный (Экстренно), 2 - Желтый, 3 - Синий/Зеленый
            val color = when (call.urgency) {
                1 -> Color.RED
                2 -> Color.YELLOW
                else -> ContextCompat.getColor(itemView.context, R.color.main_1)
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