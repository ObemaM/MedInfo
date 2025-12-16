package com.example.neuroinfo.util

import java.text.SimpleDateFormat
import java.util.Locale

object DateFormatter {
    // Расшифровка даты
    fun formatDateTime(dateTime: String?): String {
        if (dateTime.isNullOrEmpty()) return "Время не указано"

        try {
            // Принимаемый формат
            val inputFormat =
                java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss",
                    java.util.Locale.getDefault()
                )
            // Выводимый формат
            val outputFormat =
                java.text.SimpleDateFormat(
                    "dd.MM.yyyy HH:mm",
                    java.util.Locale.getDefault()
                )

            val date = inputFormat.parse(dateTime)

            return outputFormat.format(date!!) ?: dateTime
        }

        // Если что-то пошло не так, то возвращаем в исходном виде
        catch (e: Exception) {
            return dateTime
        }
    }
}