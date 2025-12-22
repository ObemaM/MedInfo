package com.example.neuroinfo.model

import java.io.Serializable
import com.google.gson.annotations.SerializedName

/**
 * Полная модель данных, представляющая один вызов (госпитализацию),
 * используется как в списке (get-calls), так и для уведомлений (SignalR).
 */
data class Hospitalization(
    // 1. ИДЕНТИФИКАТОР
    val id: String,

    // 2. ДАННЫЕ ПАЦИЕНТА И ПРИЧИНА
    @SerializedName("FullName")
    val patientFullName: String?, // ФИО пациента (как строка)

    // Если нужно разделение на части, используем дополнительные поля из get-calls API
    val patientName: String?,
    val patientSurname: String?,
    val patientPatronymic: String?,

    val age: String?,
    val sex: String?,
    val reason: String?, // Причина вызова
    val additionalInfo: String?, // Дополнительная информация

    // 3. АДРЕС И ЛОКАЦИЯ
    val district: String?, // Район
    val point: String?, // Населенный пункт
    val street: String?, // Улица
    val house: String?, // Дом
    val apartment: String?, // Квартира
    val entrance: Int?, // Подъезд
    val comment: String?, // Комментарий к адресу

    // 4. ГЕОДАННЫЕ
    val longitude: Double?, // Долгота
    val latitude: Double?, // Широта

    // 5. ДАННЫЕ ВЫЗОВА / БРИГАДЫ
    val brigadeNumber: Int?, // Номер бригады
    val brigadeProfile: String?, // Профиль бригады
    val seniorFullName: String?, // ФИО старшего

    var status: String?, // Текущий статус ("транспортировка", "Активен" и т.д.)
    val callNumber: String?, // Номер вызова (из dayNumber/yearNumber)
    val callTime: String?, // Время вызова
    val urgency: Int?, // Срочность

    // 6. ЛОКАЛЬНЫЕ ПОЛЯ (не из API, для нужд UI/логики)
    var isArchived: Boolean = false,
    var formattedCallTime: String? = null,
    var searchCache: String? = null
) : Serializable