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
    val updates: SharedFlow<List<HospitalizationResponseDto>> = _updates.asSharedFlow()

    fun emit(hospitalizations: List<HospitalizationResponseDto>) {
        if (hospitalizations.isEmpty()) return
        _updates.tryEmit(hospitalizations)
    }
}
