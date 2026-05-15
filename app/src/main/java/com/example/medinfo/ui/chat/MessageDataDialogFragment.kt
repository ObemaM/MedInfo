package com.example.medinfo.ui.chat

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.medinfo.R
import com.example.medinfo.databinding.DialogMessageDataBinding
import com.example.medinfo.model.api.PatientConditionResponseDto
import com.example.medinfo.util.DateFormatter
import com.example.medinfo.util.DialogSizing
import com.example.medinfo.util.PatientConditionFields

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

        renderFields(binding.dataContainer, condition)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()

        // Размеры и позицию задаём ДО показа — иначе диалог видимо "прыгает" к центру.
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        DialogSizing.apply(dialog.window, binding.root, binding.scrollView)

        return dialog
    }

    // Заполняем диалог только клинически значимыми полями. Служебные поля (id) не показываем.
    private fun renderFields(container: LinearLayout, condition: PatientConditionResponseDto?) {
        val rendered = PatientConditionFields.render(container, condition, showDividers = false)
        if (!rendered) {
            addPlaceholder(
                container,
                if (condition == null) "Данные не получены" else "Нет значимых полей"
            )
        }
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
