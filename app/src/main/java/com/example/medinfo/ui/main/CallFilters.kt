package com.example.medinfo.ui.main

import java.io.Serializable

enum class SexFilter : Serializable {
    ANY,
    MALE,
    FEMALE
}

data class CallFilters(
        val patientFullName: String? = null,
        val urgencyFrom: Int? = null,
        val urgencyTo: Int? = null,
        val sex: SexFilter = SexFilter.ANY,
        val ageFrom: Int? = null,
        val ageTo: Int? = null,
        val dateFromMillis: Long? = null,
        val dateToMillis: Long? = null,
        val dayNumber: Int? = null,
        val yearNumber: Int? = null
) : Serializable {
    fun isActive(): Boolean { // Для свечения иконки фильтров при активных фильтрах
        return !patientFullName.isNullOrBlank() ||
                urgencyFrom != null ||
                urgencyTo != null ||
                sex != SexFilter.ANY ||
                ageFrom != null ||
                ageTo != null ||
                dateFromMillis != null ||
                dateToMillis != null ||
                dayNumber != null ||
                yearNumber != null
    }
}
