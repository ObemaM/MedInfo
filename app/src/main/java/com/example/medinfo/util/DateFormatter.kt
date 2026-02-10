package com.example.medinfo.util

import java.text.SimpleDateFormat
import java.util.Locale
import android.util.Log

object DateFormatter {
    private val inputFormat = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    }
    private val outputFormat = ThreadLocal.withInitial {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    }

    // Расшифровка даты
    fun formatDateTime(dateTime: String?): String {

        if (dateTime.isNullOrEmpty()) return "Время не указано"

        try {
            val normalized =
                    if (dateTime.length >= 19) {
                        dateTime.take(19)

                    } else {
                        dateTime
                    }
            val date = inputFormat.get()?.parse(normalized)
            return if (date != null) {
                outputFormat.get()?.format(date) ?: dateTime
            } else {
                dateTime
            }
        }

        // Если что-то пошло не так, то возвращаем в исходном виде
        catch (e: Exception) {
            Log.w("DateFormatter", "Не удалось отформатировать дату: $dateTime", e)
            return dateTime
        }
    }
}