package com.example.medinfo.model

import com.example.medinfo.model.api.HospitalizationResponseDto
import java.io.Serializable

// Полная модель данных, представляющая один вызов (госпитализацию),
data class Hospitalization(
    // Идентификатор
    val id: String,

    // Данные пациента и причина
    val patientFullName: String?, // ФИО пациента (как строка)
    val patientName: String?,
    val patientSurname: String?,
    val patientPatronymic: String?,

    val age: String?,
    val sex: String?,
    val reason: String?, // Причина вызова
    val additionalInfo: String?, // Дополнительная информация

    // Адрес и локация
    val district: String?, // Район
    val point: String?, // Населенный пункт
    val street: String?, // Улица
    val house: String?, // Дом
    val apartment: String?, // Квартира
    val entrance: Int?, // Подъезд
    val comment: String?, // Комментарий к адресу

    // Геоданные
    val longitude: Double?, // Долгота
    val latitude: Double?, // Широта

    // Данные вызова/бригады
    val brigadeNumber: Int?, // Номер бригады
    val brigadeProfile: String?, // Профиль бригады
    val seniorFullName: String?, // ФИО старшего

    val dayNumber: Int?, // День (для номера вызова)
    val yearNumber: Int?, // Год (для номера вызова)
    var status: String?, // Текущий статус ("транспортировка", "Активен" и т.д.)
    val callTime: String?, // Время вызова
    val creationTime: String?, // Время создания госпитализации
    val urgency: Int?, // Срочность

    // Локальные поля (не из ApiService, для нужд UI/логики)
    var isNotificationSent: Boolean = true,
    var isArchived: Boolean = false,
    val decisionId: Int? = null,
    var formattedCallTime: String? = null,
    var searchCache: String? = null,
    var decisionRemainingMillis: Long? = null,
    val details: HospitalizationResponseDto? = null
) : Serializable
