package com.example.medinfo.util

import com.example.medinfo.R
import com.example.medinfo.config.ConfigManager

// Цвет таймера решения зависит от доли оставшегося времени относительно полного интервала из конфига.
// 60% и выше — зелёный, 30%–60% — жёлтый, ниже 30% — красный.
object DecisionTimerStage {

    private const val GREEN_THRESHOLD = 0.60
    private const val YELLOW_THRESHOLD = 0.30

    fun colorRes(remainingMs: Long?): Int {
        val total = ConfigManager.maxCallDurationMs
        if (remainingMs == null || total <= 0L) return R.color.border_gray_2
        val ratio = remainingMs.toDouble() / total.toDouble()
        return when {
            ratio >= GREEN_THRESHOLD -> R.color.green_1
            ratio >= YELLOW_THRESHOLD -> R.color.yellow_1
            else -> R.color.red_1
        }
    }
}
