package com.example.medinfo.model

import com.example.medinfo.model.api.HospitalizationResponseDto
import java.io.Serializable

// Полная модель данных, представляющая один вызов (госпитализацию) в UI.
data class Hospitalization(
    // Идентификатор
    val id: String,

    // Данные пациента и причина
    val patientFullName: String?,
    val patientName: String?,
    val patientSurname: String?,
    val patientPatronymic: String?,
    val age: String?,
    val sex: String?,
    val reason: String?,
    val additionalInfo: String?,

    // Адрес и локация
    val district: String?,
    val point: String?,
    val street: String?,
    val house: String?,
    val apartment: String?,
    val entrance: Int?,
    val comment: String?,

    // Геоданные
    val longitude: Double?,
    val latitude: Double?,

    // Данные вызова/бригады
    val brigadeNumber: Int?,
    val brigadeProfile: String?,
    val seniorFullName: String?,
    val dayNumber: Int?,
    val yearNumber: Int?,
    var status: String?,
    val callTime: String?,
    val creationTime: String?,

    // Новые поля v1.1.6 по консультации и уведомлениям
    val consultationRequestTime: String? = null,
    val consultationNotificationTime: String? = null,
    val hospitalizationNotificationTime: String? = null,
    val consultationDiagnosis: String? = null,
    val urgency: Int?,

    // Локальные поля для UI/логики, не отдельные поля старого ApiService
    var isNotificationSent: Boolean = true,
    var isArchived: Boolean = false,
    val decisionId: Int? = null,
    val decisionName: String? = null,
    var formattedCallTime: String? = null,
    var searchCache: String? = null,
    var decisionRemainingMillis: Long? = null,
    val details: HospitalizationResponseDto? = null
) : Serializable
