package com.example.medinfo.util

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.medinfo.R
import com.example.medinfo.model.api.PatientConditionResponseDto

// Единый рендер 11 клинически значимых полей состояния пациента — используется и во встроенной
// панели на экранах вызова, и в диалоге чата, чтобы UI везде был одинаковый.
object PatientConditionFields {

    // Возвращает true, если хотя бы одно поле было отрисовано (есть смысл показывать секцию).
    // showDividers — рисовать ли серые линии-разделители между полями (в диалоге чата отключено).
    fun render(
        container: LinearLayout,
        condition: PatientConditionResponseDto?,
        showDividers: Boolean = true
    ): Boolean {
        container.removeAllViews()
        if (condition == null) return false

        val entries: List<Pair<String, String?>> = listOf(
            "Время от начала заболевания" to condition.startDisease?.let { "$it ч" },
            "LAMS" to condition.lams?.toString(),
            "Сознание (ШКГ)" to condition.consciousness,
            "АД" to condition.bloodPressure,
            "ЧД" to condition.respirationRate?.toString(),
            "ЧСС" to condition.heartRate?.toString(),
            // Температура и глюкометрия: 0 — это "не измерено", такие значения скрываем.
            "Температура" to condition.temperature?.takeIf { it != 0.0 }?.let { formatNumber(it) },
            "Глюкометрия" to condition.glucometry?.takeIf { it != 0.0 }?.let { formatNumber(it) },
            "SpO2" to condition.spO2?.let { "$it%" },
            "Судороги" to formatBoolean(condition.convulsions),
            "Беременность" to formatBoolean(condition.pregnant)
        )

        var rendered = 0
        entries.forEach { (label, value) ->
            if (!value.isNullOrBlank()) {
                addField(container, label, value, showDividers)
                rendered += 1
            }
        }
        return rendered > 0
    }

    private fun addField(
        container: LinearLayout,
        label: String,
        value: String,
        showDivider: Boolean
    ) {
        val context = container.context

        val fieldLayout = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 2.dp(context)
            }
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(14.dp(context), 10.dp(context), 14.dp(context), 10.dp(context))
        }

        val labelView = TextView(context).apply {
            text = label
            setTextColor(ContextCompat.getColor(context, R.color.gray_1))
            textSize = 15f
            typeface = Typeface.DEFAULT
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.42f
            ).apply {
                rightMargin = 12.dp(context)
            }
        }

        val valueView = TextView(context).apply {
            text = value
            setTextColor(ContextCompat.getColor(context, R.color.black_1))
            textSize = 15f
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                0.58f
            )
        }

        fieldLayout.addView(labelView)
        fieldLayout.addView(valueView)
        container.addView(fieldLayout)

        if (!showDivider) return

        val divider = View(context).apply {
            setBackgroundColor(ContextCompat.getColor(context, R.color.border_gray_1))
            alpha = 0.65f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                leftMargin = 14.dp(context)
                rightMargin = 14.dp(context)
            }
        }
        container.addView(divider)
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
