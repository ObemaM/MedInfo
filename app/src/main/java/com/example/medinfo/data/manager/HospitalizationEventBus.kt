package com.example.medinfo.data.manager

import com.example.medinfo.model.api.HospitalizationResponseDto
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// Шина realtime-обновлений госпитализаций из SignalR. SignalRService пушит сюда каждый
// HospitalizationNotification, а список на главном экране подписывается и точечно
// обновляет статусы уже загруженных вызовов — без перезагрузки страницы.
object HospitalizationEventBus {
    private val _updates = MutableSharedFlow<List<HospitalizationResponseDto>>(
        replay = 0,
        extraBufferCapacity = 16
    )
    private val latestById = mutableMapOf<String, HospitalizationResponseDto>()

    val updates: SharedFlow<List<HospitalizationResponseDto>> = _updates.asSharedFlow()

    fun emit(hospitalizations: List<HospitalizationResponseDto>) {
        if (hospitalizations.isEmpty()) return
        remember(hospitalizations)
        _updates.tryEmit(hospitalizations)
    }

    // Держим последние DTO из SignalR и серверных списков: когда сначала видим активный вызов,
    // а потом приходит PATIENT_CONDITION-сообщение, SignalR сможет поднять его в очередь решений по id.
    @Synchronized
    fun remember(hospitalizations: List<HospitalizationResponseDto>) {
        hospitalizations.forEach { latestById[it.id] = it }
    }

    @Synchronized
    fun latest(hospitalizationId: String): HospitalizationResponseDto? {
        return latestById[hospitalizationId]
    }
}
