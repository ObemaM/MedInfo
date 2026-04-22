package com.example.medinfo.model.api

// Для статусов госпитализации
enum class HospitalizationStatus(val id: Int) {
    NOT_LINKED_TO_CALL(0), // Не связана с вызовом
    CREW_EN_ROUTE(1), // Бригада в пути
    CREW_ON_SITE(2), // Бригада на территории
    COMPLETED(3), // Завершена
    REFERRED_TO_OTHER_LPU(4); // Пациент направлен в другое ЛПУ

    val isActive: Boolean
        get() = this == CREW_EN_ROUTE || this == CREW_ON_SITE

    val isArchive: Boolean
        get() = this == COMPLETED || this == REFERRED_TO_OTHER_LPU

    // Поиск статуса, по присланному id
    companion object {
        fun fromId(id: Int?): HospitalizationStatus? = entries.firstOrNull {
            it.id == id
        }
    }
}

// Решение о госпитализации при вызове
enum class HospitalizationDecision(val id: Int) {
    NONE(0), // Нет решения
    ACCEPTED(1), // Принята
    REJECTED(2), // Отклонена
    IGNORED(3); // Проигнорирована

    val requiresUserDecision: Boolean
        get() = this == NONE

    // Решение о госпитализации по id
    companion object {
        fun fromId(id: Int?): HospitalizationDecision? = entries.firstOrNull {
            it.id == id
        }
    }
}

// Для уведомлений
enum class ReceptionNotificationType(val id: Int) {
    NEW_HOSPITALIZATION(1), // Новая госпитализация
    BRIGADE_MESSAGE(2); // Сообщение от бригады

    companion object {
        fun fromId(id: Int?): ReceptionNotificationType? = entries.firstOrNull {
            it.id == id
        }
    }
}

// Для обработки сообщений
enum class MessageOrigin(val id: Int) {
    TABLET(1), // От бригады
    INFORMATOR_APP(2); // От информатора

    companion object {
        fun fromId(id: Int?): MessageOrigin? = entries.firstOrNull {
            it.id == id
        }
    }
}
