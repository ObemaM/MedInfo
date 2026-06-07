package com.example.medinfo.model.api

// Статусы госпитализации по спецификации v1.1.6.
enum class HospitalizationStatus(val id: Int) {
    CONSULTATION(1), // Консультация
    CREW_EN_ROUTE(2), // Бригада в пути
    CREW_ON_SITE(3), // Бригада на территории
    COMPLETED(4), // Завершен
    REFERRED_TO_OTHER_LPU(5); // Пациент направлен в другое ЛПУ

    // Активные госпитализации: 1, 2, 3.
    val isActive: Boolean
        get() = this == CONSULTATION || this == CREW_EN_ROUTE || this == CREW_ON_SITE

    // Завершенные госпитализации: 4, 5.
    val isArchive: Boolean
        get() = this == COMPLETED || this == REFERRED_TO_OTHER_LPU

    // По новой логике решение можно принимать только в статусе "Консультация".
    val allowsDecision: Boolean
        get() = this == CONSULTATION

    // Поиск статуса по id, присланному сервером.
    companion object {
        fun fromId(id: Int?): HospitalizationStatus? = entries.firstOrNull {
            it.id == id
        }
    }
}

// Типы сообщений.
enum class MessageType(val id: Int) {
    TEXT(1), // Текстовое сообщение
    PATIENT_CONDITION(2), // Данные состояния пациента
    CONSULTATION_REQUEST(3); // Триггер запроса консультации

    // Поиск типа сообщения по id.
    companion object {
        fun fromId(id: Int?): MessageType? = entries.firstOrNull {
            it.id == id
        }
    }
}

// Решение по госпитализации.
enum class HospitalizationDecision(val id: Int) {
    NONE(0), // Нет решения
    ACCEPTED(1), // Принята
    REJECTED(2), // Отклонена
    IGNORED(3); // Проигнорирована

    // Требуется ли действие пользователя.
    val requiresUserDecision: Boolean
        get() = this == NONE

    // Поиск решения по id.
    companion object {
        fun fromId(id: Int?): HospitalizationDecision? = entries.firstOrNull {
            it.id == id
        }
    }
}

// Типы подтверждения получения уведомлений.
enum class ReceptionNotificationType(val id: Int) {
    NEW_HOSPITALIZATION(1), // Новая госпитализация
    BRIGADE_MESSAGE(2); // Сообщение от бригады

    // Поиск типа подтверждения по id.
    companion object {
        fun fromId(id: Int?): ReceptionNotificationType? = entries.firstOrNull {
            it.id == id
        }
    }
}

// Источник сообщения.
enum class MessageOrigin(val id: Int) {
    TABLET(1), // От планшета бригады
    INFORMATOR_APP(2); // От информатора

    // Поиск источника сообщения по id.
    companion object {
        fun fromId(id: Int?): MessageOrigin? = entries.firstOrNull {
            it.id == id
        }
    }
}
