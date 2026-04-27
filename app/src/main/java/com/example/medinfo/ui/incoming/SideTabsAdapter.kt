package com.example.medinfo.ui.incoming

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.medinfo.R
import com.example.medinfo.databinding.ItemSideTabBinding
import com.example.medinfo.model.api.HospitalizationResponseDto

class SideTabsAdapter(
    private val onTabClick: (HospitalizationResponseDto) -> Unit
) : ListAdapter<HospitalizationResponseDto, SideTabsAdapter.TabViewHolder>(DiffCallback()) {

    // Храним id выбранной госпитализации для подсветки вкладки.
    private var selectedHospitalizationId: String? = null

    fun setSelectedHospitalizationId(hospitalizationId: String?) {
        selectedHospitalizationId = hospitalizationId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val binding = ItemSideTabBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, item.id == selectedHospitalizationId)

        holder.itemView.setOnClickListener {
            selectedHospitalizationId = item.id
            notifyDataSetChanged()
            onTabClick(item)
        }
    }

    class TabViewHolder(
        private val binding: ItemSideTabBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        private val card = binding.tabCardView
        private val numberTextDay = binding.tabCallNumberDay
        private val numberTextYear = binding.tabCallNumberYear
        private val indicator = binding.urgencyIndicator

        // Разбивает номер вызова на день и год для компактного отображения.
        private fun getDayAndYear(item: HospitalizationResponseDto): Pair<String, String> {
            val responseCall = item.call
            return responseCall.dayNumber.toString() to responseCall.yearNumber.toString()
        }

        fun bind(item: HospitalizationResponseDto, isSelected: Boolean) {
            val (dayNumber, yearNumber) = getDayAndYear(item)
            numberTextDay.text = "№$dayNumber/"
            numberTextYear.text = yearNumber

            if (isSelected) {
                card.strokeColor = ContextCompat.getColor(itemView.context, R.color.main_1)
                card.setCardBackgroundColor(
                    ContextCompat.getColor(itemView.context, R.color.background_1)
                )
                card.cardElevation = 8f
            } else {
                card.strokeColor = ContextCompat.getColor(itemView.context, R.color.border_gray_1)
                card.setCardBackgroundColor(Color.WHITE)
                card.cardElevation = 2f
            }

            // Цвет индикатора сохраняем по старой логике срочности.
            val color = when (item.call.urgency) {
                in 3..6 -> ContextCompat.getColor(itemView.context, R.color.yellow_1)
                in 7..9 -> ContextCompat.getColor(itemView.context, R.color.red_1)
                else -> ContextCompat.getColor(itemView.context, R.color.green_1)
            }
            indicator.setBackgroundColor(color)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<HospitalizationResponseDto>() {
        override fun areItemsTheSame(
            oldItem: HospitalizationResponseDto,
            newItem: HospitalizationResponseDto
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: HospitalizationResponseDto,
            newItem: HospitalizationResponseDto
        ): Boolean {
            return oldItem == newItem
        }
    }
}
