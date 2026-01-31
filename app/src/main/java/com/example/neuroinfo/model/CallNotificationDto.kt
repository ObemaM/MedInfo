package com.example.neuroinfo.model

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
    @SerializedName("status") val status: String?
) : Serializable