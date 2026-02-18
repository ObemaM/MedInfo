package com.example.medinfo.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

// data class для десериализации JSON из SignalR
data class CallNotificationDto(
    @SerializedName("fullName") val fullName: String?,
    @SerializedName("age") val age: String?,
    @SerializedName("sex") val sex: String?,
    @SerializedName("reason") val reason: String?,
    @SerializedName("additionalInfo") val additionalInfo: String?,
    @SerializedName("district") val district: String?,
    @SerializedName("point") val point: String?,
    @SerializedName("street") val street: String?,
    @SerializedName("house") val house: String?,
    @SerializedName("apartment") val apartment: String?,
    @SerializedName("enterance") val enterance: Int?, // Верное название?
    @SerializedName("longitude") val longitude: Double?,
    @SerializedName("latitude") val latitude: Double?,
    @SerializedName("brigadeNumber") val brigadeNumber: Int?,
    @SerializedName("brigadeProfile") val brigadeProfile: String?,
    @SerializedName("callNumber") val callNumber: String?,
    @SerializedName("callTime") val callTime: String?,
    @SerializedName("urgency") val urgency: Int?,
    @SerializedName("status") val status: String?,
    @SerializedName("bloodPressure") val bloodPressure: String?,

    // Медицинские показатели пациента
    @SerializedName("consciousness") val consciousness: String?, // Сознание
    @SerializedName("convulsions") val convulsions: Boolean?, // Судороги
    @SerializedName("glucometry") val glucometry: Int?, // Глюкометрия
    @SerializedName("heartRate") val heartRate: Int?, // ЧСС
    @SerializedName("oxygenSupport") val oxygenSupport: Boolean?, // Кислородная поддержка
    @SerializedName("pregnant") val pregnant: Boolean?, // Беременность
    @SerializedName("respirationRate") val respirationRate: Int?, // ЧДД
    @SerializedName("spO2") val spO2: Int?, // Сатурация
    @SerializedName("startDisease") val startDisease: Int?, // Время от начала заболевания (часы)
    @SerializedName("stenosis") val stenosis: Boolean?, // Стеноз
    @SerializedName("temreture") val temperature: Double?, // Температура (опечатка в JSON)
    @SerializedName("LAMS") val lams: Int?, // Шкала LAMS
    @SerializedName("mRS") val mrs: Int?, // Шкала Рэнкина
    @SerializedName("VAS") val vas: Int?, // ВАШ

    // Кровотечение
    @SerializedName("bleeding") val bleeding: BleedingInfo?,

    // Венозный доступ
    @SerializedName("venousAccess") val venousAccess: VenousAccessInfo?,

    // Протезирование ДП
    @SerializedName("IFA") val ifa: IfaInfo?,

    // Системные поля
    @SerializedName("messageId") val messageId: Int?, // Тип сообщения (0 - данные пациента)
    @SerializedName("messageValue") val messageValue: String?, // Текст сообщения

    // Идентификация вызова и бригады
    @SerializedName("DPRM") val dprm: String?, // Дата вызова
    @SerializedName("NGOD") val ngod: Int?, // Годовой номер вызова
    @SerializedName("NUMV") val numv: Int?, // Суточный номер вызова
    @SerializedName("SSMP") val ssmp: Int?, // Код СМП бригады
    @SerializedName("TEAM") val team: Int?, // Номер бригады
    @SerializedName("VOZR") val vozr: String? // Возраст пациента (как строка)
) : Serializable

// Класс для информации о кровотечении
data class BleedingInfo(
    @SerializedName("presence") val presence: Boolean?, // Наличие
    @SerializedName("type") val type: String?, // Тип (внешнее/внутреннее)
    @SerializedName("arterialTourniquet") val arterialTourniquet: ArterialTourniquetInfo? // Жгут
) : Serializable

// Класс для артериального жгута
data class ArterialTourniquetInfo(
    @SerializedName("presence") val presence: Boolean?, // Наличие
    @SerializedName("applicationTime") val applicationTime: String? // Время наложения
) : Serializable

// Класс для венозного доступа
data class VenousAccessInfo(
    @SerializedName("presence") val presence: Boolean?, // Наличие
    @SerializedName("method") val method: List<String>? // Методы (может быть несколько)
) : Serializable

// Класс для протезирования ДП
data class IfaInfo(
    @SerializedName("presence") val presence: Boolean?, // Наличие
    @SerializedName("tool") val tool: List<String>?, // Инструменты
    @SerializedName("ALV") val alv: Boolean? // ИВЛ
) : Serializable