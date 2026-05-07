package com.example.medinfo.util

import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.example.medinfo.R
import com.example.medinfo.model.api.PatientConditionResponseDto

// Единый рендер 11 клинически значимых полей состояния пациента — используется и во встроенной
// панели на экранах вызова, и в диалоге чата, чтобы UI везде был одинаковый.
object PatientConditionFields {

    // Возвращает true, если хотя бы одно поле было отрисовано (есть смысл показывать секцию).
    fun render(container: LinearLayout, condition: PatientConditionResponseDto?): Boolean {
        container.removeAllViews()
        if (condition == null) return false

        val entries: List<Pair<String, String?>> = listOf(
            "Сознание (ШКГ)" to condition.consciousness,
            "LAMS" to condition.lams?.toString(),
            "АД" to condition.bloodPressure,
            "ЧД" to condition.respirationRate?.toString(),
            "ЧСС" to condition.heartRate?.toString(),
            "Время от начала заболевания" to condition.startDisease?.let { "$it ч" },
            "Температура" to condition.temperature?.let { formatNumber(it) },
            "Глюкометрия" to condition.glucometry?.let { formatNumber(it) },
            "SpO2" to condition.spO2?.let { "$it%" },
            "Судороги" to formatBoolean(condition.convulsions),
            "Беременность" to formatBoolean(condition.pregnant)
        )

        var rendered = 0
        entries.forEach { (label, value) ->
            if (!value.isNullOrBlank()) {
                addField(container, label, value)
                rendered += 1
            }
        }
        return rendered > 0
    }

    private fun addField(container: LinearLayout, label: String, value: String) {
        val context = container.context

        val fieldLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.data_field_background)
            setPadding(14.dp(context), 8.dp(context), 14.dp(context), 8.dp(context))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dp(context)
            }
        }

        val labelView = TextView(context).apply {
            text = label
            setTextColor(context.resources.getColor(R.color.field_label_color, null))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }

        val valueView = TextView(context).apply {
            text = value
            setTextColor(context.resources.getColor(R.color.gray_1, null))
            textSize = 16f
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 3.dp(context)
            }
        }

        fieldLayout.addView(labelView)
        fieldLayout.addView(valueView)
        container.addView(fieldLayout)
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

    private fun Int.dp(context: android.content.Context): Int =
        (this * context.resources.displayMetrics.density).toInt()
}
