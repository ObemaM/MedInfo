package com.example.medinfo.ui.incoming

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.medinfo.R
import com.example.medinfo.model.CallNotificationDto
import com.google.android.material.card.MaterialCardView

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
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_side_tab, parent, false)
        return TabViewHolder(view)
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

    class TabViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card = view.findViewById<MaterialCardView>(R.id.tab_card_view)
        private val numberText = view.findViewById<TextView>(R.id.tab_call_number)
        private val indicator = view.findViewById<View>(R.id.urgency_indicator)

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
            // Допустим: 1 - Красный (Экстренно), 2 - Желтый, 3 - Синий/Зеленый
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