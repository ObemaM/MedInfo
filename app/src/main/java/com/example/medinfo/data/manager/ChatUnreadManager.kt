package com.example.medinfo.data.manager

import com.example.medinfo.model.api.MessageOrigin
import com.example.medinfo.model.api.MessageResponseDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Единое состояние непрочитанных сообщений чата. Храним его отдельно от экранов,
// чтобы бейдж на вызове не терялся при пересоздании Activity или поздней подписке.
object ChatUnreadManager {
    private val unreadMessageIdsByHospitalization = mutableMapOf<String, MutableSet<String>>()
    private val _unreadCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCounts: StateFlow<Map<String, Int>> = _unreadCounts.asStateFlow()

    @Synchronized
    fun markUnread(message: MessageResponseDto) {
        if (MessageOrigin.fromId(message.origin) != MessageOrigin.TABLET) return

        val ids = unreadMessageIdsByHospitalization.getOrPut(message.hospitalizationId) {
            mutableSetOf()
        }
        if (ids.add(message.id)) {
            publishCounts()
        }
    }

    @Synchronized
    fun markRead(hospitalizationId: String) {
        if (unreadMessageIdsByHospitalization.remove(hospitalizationId) != null) {
            publishCounts()
        }
    }

    @Synchronized
    fun unreadCount(hospitalizationId: String): Int {
        return unreadMessageIdsByHospitalization[hospitalizationId]?.size ?: 0
    }

    @Synchronized
    fun clearAll() {
        if (unreadMessageIdsByHospitalization.isEmpty()) return
        unreadMessageIdsByHospitalization.clear()
        publishCounts()
    }

    private fun publishCounts() {
        _unreadCounts.value = unreadMessageIdsByHospitalization
            .mapValues { (_, ids) -> ids.size }
    }
}
