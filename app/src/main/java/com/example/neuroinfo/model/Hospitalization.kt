package com.example.neuroinfo.model

import java.io.Serializable
import com.google.gson.annotations.SerializedName

// Полная модель данных, представляющая один вызов (госпитализацию),
data class Hospitalization(
    // ИДЕНТИФИКАТОР
    val id: String,

    // ДАННЫЕ ПАЦИЕНТА И ПРИЧИНА
    @SerializedName("FullName")
    val patientFullName: String?, // ФИО пациента (как строка)
    val patientName: String?,
    val patientSurname: String?,
    val patientPatronymic: String?,

    val age: String?,
    val sex: String?,
    val reason: String?, // Причина вызова
    val additionalInfo: String?, // Дополнительная информация

    // АДРЕС И ЛОКАЦИЯ
    val district: String?, // Район
    val point: String?, // Населенный пункт
    val street: String?, // Улица
    val house: String?, // Дом
    val apartment: String?, // Квартира
    val entrance: Int?, // Подъезд
    val comment: String?, // Комментарий к адресу

    // ГЕОДАННЫЕ
    val longitude: Double?, // Долгота
    val latitude: Double?, // Широта

    // ДАННЫЕ ВЫЗОВА / БРИГАДЫ
    val brigadeNumber: Int?, // Номер бригады
    val brigadeProfile: String?, // Профиль бригады
    val seniorFullName: String?, // ФИО старшего

    @SerializedName(value = "DayNumber", alternate = ["dayNumber", "day_number"])
    val dayNumber: Int?, // День (для номера вызова)
    @SerializedName(value = "YearNumber", alternate = ["yearNumber", "year_number"])
    val yearNumber: Int?, // Год (для номера вызова)
    var status: String?, // Текущий статус ("транспортировка", "Активен" и т.д.)
    val callTime: String?, // Время вызова
    val urgency: Int?, // Срочность

    // ЛОКАЛЬНЫЕ ПОЛЯ (не из API, для нужд UI/логики)
    var isArchived: Boolean = false,
    var formattedCallTime: String? = null,
    var searchCache: String? = null
) : Serializable