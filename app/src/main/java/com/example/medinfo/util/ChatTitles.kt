package com.example.medinfo.util

import com.example.medinfo.data.manager.CallsManager
import com.example.medinfo.data.manager.HospitalizationEventBus
import com.example.medinfo.model.api.HospitalizationResponseDto

// Заголовок чата: «Вызов № день/год». Номер ищем не только в очереди решений
// (CallsManager), но и в кэше загруженных/realtime госпитализаций — иначе
// уведомление по активному вызову открывает чат без номера.
object ChatTitles {
    const val FALLBACK = "Сообщение по вызову"

    fun forHospitalizationId(hospitalizationId: String): String {
        val hospitalization = HospitalizationEventBus.latest(hospitalizationId)
            ?: CallsManager.calls.value.firstOrNull { it.id == hospitalizationId }
        return forHospitalization(hospitalization)
    }

    fun forHospitalization(hospitalization: HospitalizationResponseDto?): String {
        val call = hospitalization?.call ?: return FALLBACK
        return "Вызов №${call.dayNumber}/${call.yearNumber}"
    }

    fun screenTitle(chatTitle: String?): String {
        return chatTitle?.takeIf { it.isNotBlank() }?.let { "Чат: $it" } ?: "Чат"
    }

    fun isUnresolved(chatTitle: String?): Boolean {
        return chatTitle.isNullOrBlank() || chatTitle == FALLBACK
    }
}
