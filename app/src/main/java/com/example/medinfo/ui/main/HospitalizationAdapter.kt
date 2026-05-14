package com.example.medinfo.ui.main

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.medinfo.config.ConfigManager
import com.example.medinfo.data.manager.CallsManager
import com.example.medinfo.databinding.ItemHospitalizationBinding
import com.example.medinfo.model.Hospitalization
import com.example.medinfo.model.api.HospitalizationDecision
import com.example.medinfo.model.api.HospitalizationStatus
import com.example.medinfo.util.DateFormatter
import com.example.medinfo.util.DecisionTimerStage
import java.util.Locale

class HospitalizationAdapter(
    private var items: List<Hospitalization>,
    private val onCallClicked: (Hospitalization) -> Unit
) : RecyclerView.Adapter<HospitalizationAdapter.ViewHolder>() {

    private var showDecisionTimer = false

    init {
        setHasStableIds(true)
    }

    fun submitItems(newItems: List<Hospitalization>) {
        val oldItems = items
        val diff = DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = oldItems.size
                override fun getNewListSize(): Int = newItems.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return oldItems[oldItemPosition].id == newItems[newItemPosition].id
                }

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val old = oldItems[oldItemPosition]
                    val new = newItems[newItemPosition]
                    return old == new &&
                        formatRemainingTimeForCall(old) == formatRemainingTimeForCall(new)
                }
            }
        )
        items = newItems
        diff.dispatchUpdatesTo(this)
    }

    fun setShowDecisionTimer(show: Boolean) {
        if (showDecisionTimer == show) return
        showDecisionTimer = show
        notifyItemRangeChanged(0, itemCount, TIMER_PAYLOAD)
    }

    fun refreshDecisionTimersOnly() {
        if (!showDecisionTimer || itemCount == 0) return
        notifyItemRangeChanged(0, itemCount, TIMER_PAYLOAD)
    }

    inner class ViewHolder(private val binding: ItemHospitalizationBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(call: Hospitalization) {
            bindCardBackground(call)

            // Номер звонка
            val callNumber: String =
                    if (call.dayNumber != null && call.yearNumber != null) {
                        "${call.dayNumber}/${call.yearNumber}"
                    } else {
                        "Н/Д"
                    }

            // Номер и статус
            binding.callNumberText.text = "Вызов №${callNumber}"
            bindStatusBadges(call)

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

        private fun bindCardBackground(call: Hospitalization) {
            val colorRes = when (HospitalizationDecision.fromId(call.decisionId)) {
                HospitalizationDecision.ACCEPTED -> com.example.medinfo.R.color.decision_accepted_bg
                HospitalizationDecision.REJECTED -> com.example.medinfo.R.color.decision_rejected_bg
                HospitalizationDecision.IGNORED -> com.example.medinfo.R.color.decision_ignored_bg
                HospitalizationDecision.NONE,
                null -> com.example.medinfo.R.color.blue_3
            }

            binding.root.backgroundTintList = ColorStateList.valueOf(
                binding.root.context.getColor(colorRes)
            )
        }

        private fun bindStatusBadges(call: Hospitalization) {
            val decision = HospitalizationDecision.fromId(call.decisionId)
            val isArchive = call.details
                ?.let { HospitalizationStatus.fromId(it.statusId)?.isArchive == true }
                ?: call.isArchived

            if (isArchive) {
                binding.statusText.text = getDecisionTitle(call, decision)
                binding.archiveStatusLabel.visibility = View.VISIBLE
                binding.decisionText.visibility = View.VISIBLE
                binding.decisionText.text = call.status.orEmpty()
                return
            }

            binding.archiveStatusLabel.visibility = View.GONE
            binding.decisionText.visibility = View.GONE
            binding.statusText.text =
                if (decision == HospitalizationDecision.REJECTED ||
                    decision == HospitalizationDecision.IGNORED
                ) {
                    getDecisionTitle(call, decision)
                } else {
                    call.status.orEmpty()
                }
        }

        private fun getDecisionTitle(
            call: Hospitalization,
            decision: HospitalizationDecision?
        ): String {
            call.decisionName?.takeIf { it.isNotBlank() }?.let { return it }

            return when (decision) {
                HospitalizationDecision.ACCEPTED -> "Принята"
                HospitalizationDecision.REJECTED -> "Отклонена"
                HospitalizationDecision.IGNORED -> "Проигнорирована"
                HospitalizationDecision.NONE,
                null -> "Нет решения"
            }
        }

        fun bindDecisionTimer(call: Hospitalization) {
            if (!showDecisionTimer) {
                binding.decisionTimerText.visibility = View.GONE
                return
            }

            // Плашка нужна только во вкладке "Требуют решения" и обновляется таймером MainActivity.
            val remainingMillis = calculateRemainingMillis(call)
            binding.decisionTimerText.visibility = View.VISIBLE
            binding.decisionTimerText.text =
                if (remainingMillis == null) {
                    "Время на решение: неизвестно"
                } else if (remainingMillis <= 0L) {
                    "Время на решение истекло"
                } else {
                    "Осталось на решение: ${formatRemainingTime(remainingMillis)}"
                }

            val colorRes = DecisionTimerStage.colorRes(remainingMillis)
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

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(TIMER_PAYLOAD)) {
            holder.bindDecisionTimer(items[position])
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    // Количество элементов
    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long {
        return items[position].id.hashCode().toLong()
    }

    private fun formatRemainingTime(remainingMillis: Long): String {
        val totalSeconds = ((remainingMillis + 999L) / 1000L).coerceAtLeast(0L)
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }

    private fun formatRemainingTimeForCall(call: Hospitalization): String? {
        val remainingMillis = calculateRemainingMillis(call) ?: return null
        return formatRemainingTime(remainingMillis)
    }

    private fun calculateRemainingMillis(call: Hospitalization): Long? {
        call.details?.id?.let { hospitalizationId ->
            CallsManager.getRemainingIgnoreMillis(hospitalizationId)?.let { return it }
        }

        val details = call.details ?: return call.decisionRemainingMillis
        // В списке используем тот же источник, что и CallsManager: только время уведомления.
        // callTime описывает сам вызов и не должен уменьшать таймер нового решения.
        val startedAtMillis = DateFormatter.parseCallTimeMillis(details.notificationTime)
            ?: return call.decisionRemainingMillis

        return (startedAtMillis + ConfigManager.maxCallDurationMs - System.currentTimeMillis())
            .coerceIn(0L, ConfigManager.maxCallDurationMs)
    }

    private companion object {
        const val TIMER_PAYLOAD = "TIMER_PAYLOAD"
    }
}
