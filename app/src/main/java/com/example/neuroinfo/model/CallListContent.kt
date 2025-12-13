package com.example.neuroinfo.model

import com.google.gson.annotations.SerializedName

data class CallListContent(
    @SerializedName("calls") val calls: List<Hospitalization>, // Массив вызовов [cite: 378]
    @SerializedName("count") val count: Int // Общее количество записей [cite: 378]
)