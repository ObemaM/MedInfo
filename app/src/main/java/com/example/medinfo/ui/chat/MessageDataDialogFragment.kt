package com.example.medinfo.ui.chat

import android.app.Dialog
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.medinfo.R
import com.example.medinfo.databinding.DialogMessageDataBinding
import com.example.medinfo.model.api.PatientConditionResponseDto
import com.example.medinfo.util.DateFormatter

// Диалог состояния пациента, прилетевшего вместе с сообщением чата.
// Открывается автоматически из ChatActivity при получении PATIENT_CONDITION-сообщения.
// За счёт того, что это DialogFragment с собственным окном, он естественно отрисуется поверх
// уже открытого dialog_call_data, если тот будет показан в будущем.
class MessageDataDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogMessageDataBinding.inflate(requireActivity().layoutInflater)

        val condition = readConditionArgument()
        val receptionTime = arguments?.getString(ARG_RECEPTION_TIME)

        binding.receptionTimeText.text = receptionTime
            ?.let { DateFormatter.formatDateTime(it) }
            ?.let { "Получено: $it" }
            .orEmpty()

        binding.buttonClose.setOnClickListener { dismiss() }

        if (condition != null) {
            renderFields(binding.dataContainer, condition)
        } else {
            renderFields(binding.dataContainer, null)
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    // Заполняем диалог только клинически значимыми полями. Служебные поля (id) не показываем.
    private fun renderFields(container: LinearLayout, condition: PatientConditionResponseDto?) {
        container.removeAllViews()

        if (condition == null) {
            addPlaceholder(container, "Данные не получены")
            return
        }

        // Порядок и набор полей зафиксированы по требованию: ШКГ, LAMS, АД, ЧД, ЧСС,
        // время от начала заболевания, температура, глюкометрия, SpO2, судороги, беременность.
        addField(container, "Сознание (ШКГ)", condition.consciousness)
        addField(container, "LAMS", condition.lams?.toString())
        addField(container, "АД", condition.bloodPressure)
        addField(container, "ЧД", condition.respirationRate?.toString())
        addField(container, "ЧСС", condition.heartRate?.toString())
        addField(container, "Время от начала заболевания", condition.startDisease?.let { "$it ч" })
        addField(container, "Температура", condition.temperature?.let { formatNumber(it) })
        addField(container, "Глюкометрия", condition.glucometry?.let { formatNumber(it) })
        addField(container, "SpO2", condition.spO2?.let { "$it%" })
        addField(container, "Судороги", formatBoolean(condition.convulsions))
        addField(container, "Беременность", formatBoolean(condition.pregnant))

        if (container.childCount == 0) {
            addPlaceholder(container, "Нет значимых полей")
        }
    }

    private fun addField(container: LinearLayout, label: String, value: String?) {
        if (value.isNullOrBlank()) return

        val fieldLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.data_field_background)
            setPadding(14.dp(), 8.dp(), 14.dp(), 8.dp())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dp()
            }
        }

        val labelView = TextView(requireContext()).apply {
            text = label
            setTextColor(resources.getColor(R.color.field_label_color, null))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }

        val valueView = TextView(requireContext()).apply {
            text = value
            setTextColor(resources.getColor(R.color.gray_1, null))
            textSize = 16f
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 3.dp()
            }
        }

        fieldLayout.addView(labelView)
        fieldLayout.addView(valueView)
        container.addView(fieldLayout)
    }

    private fun addPlaceholder(container: LinearLayout, message: String) {
        val placeholder = TextView(requireContext()).apply {
            text = message
            setTextColor(resources.getColor(R.color.border_gray_2, null))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 16.dp(), 0, 16.dp())
        }
        container.addView(placeholder)
    }

    private fun readConditionArgument(): PatientConditionResponseDto? {
        val args = arguments ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            args.getSerializable(ARG_CONDITION, PatientConditionResponseDto::class.java)
        } else {
            @Suppress("DEPRECATION")
            args.getSerializable(ARG_CONDITION) as? PatientConditionResponseDto
        }
    }

    private fun formatBoolean(value: Boolean): String = if (value) "Да" else "Нет"

    // Дробные значения отдаём в "человеческом" виде: 36.6 вместо 36.600000.
    private fun formatNumber(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            "%.1f".format(value)
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        const val TAG = "MessageDataDialog"
        private const val ARG_CONDITION = "ARG_CONDITION"
        private const val ARG_RECEPTION_TIME = "ARG_RECEPTION_TIME"

        fun newInstance(
            condition: PatientConditionResponseDto,
            receptionTime: String?
        ): MessageDataDialogFragment {
            return MessageDataDialogFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_CONDITION, condition)
                    putString(ARG_RECEPTION_TIME, receptionTime)
                }
            }
        }
    }
}
