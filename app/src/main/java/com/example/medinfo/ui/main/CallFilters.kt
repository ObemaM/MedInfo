package com.example.medinfo.ui.main

import java.io.Serializable

data class CallFilters(
        val dateFromMillis: Long? = null,
        val dateToMillis: Long? = null,
        val dayNumber: Int? = null,
        val yearNumber: Int? = null
) : Serializable {
    fun isActive(): Boolean { // Для свечения иконки фильтров при активных фильтрах
        return dateFromMillis != null ||
                dateToMillis != null ||
                dayNumber != null ||
                yearNumber != null
    }
}
