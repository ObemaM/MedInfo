package com.example.medinfo.model

import com.google.gson.annotations.SerializedName

data class CallListContent(
    @SerializedName("calls") val calls: List<Hospitalization>, // Массив вызовов
    @SerializedName("count") val count: Int // Общее количество записей
)