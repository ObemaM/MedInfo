package com.example.neuroinfo.ui.main

import java.io.Serializable

enum class SexFilter : Serializable {
    ANY,
    MALE,
    FEMALE
}

data class CallFilters(
        val urgencyFrom: Int? = null,
        val urgencyTo: Int? = null,
        val sex: SexFilter = SexFilter.ANY,
        val ageFrom: Int? = null,
        val ageTo: Int? = null,
        val dateFromMillis: Long? = null,
        val dateToMillis: Long? = null,
        val callDayFrom: Int? = null,
        val callDayTo: Int? = null,
        val callYearFrom: Int? = null,
        val callYearTo: Int? = null
) : Serializable {
    fun isActive(): Boolean {
        return urgencyFrom != null ||
                urgencyTo != null ||
                sex != SexFilter.ANY ||
                ageFrom != null ||
                ageTo != null ||
                dateFromMillis != null ||
                dateToMillis != null ||
                callDayFrom != null ||
                callDayTo != null ||
                callYearFrom != null ||
                callYearTo != null
    }
}
