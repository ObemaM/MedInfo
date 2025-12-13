package com.example.neuroinfo.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

// Используем data class для десериализации JSON из SignalR
data class CallNotificationDto(
    @SerializedName("FullName") val fullName: String?,
    @SerializedName("Age") val age: String?,
    @SerializedName("Sex") val sex: String?,
    @SerializedName("Reason") val reason: String?,
    @SerializedName("AdditionalInfo") val additionalInfo: String?,
    @SerializedName("District") val district: String?,
    @SerializedName("Point") val point: String?,
    @SerializedName("Street") val street: String?,
    @SerializedName("House") val house: String?,
    @SerializedName("Apartment") val apartment: String?,
    @SerializedName("Entrance") val entrance: Int?,
    @SerializedName("Longitude") val longitude: Double?,
    @SerializedName("Latitude") val latitude: Double?,
    @SerializedName("BrigadeNumber") val brigadeNumber: Int?,
    @SerializedName("BrigadeProfile") val brigadeProfile: String?,
    @SerializedName("CallNumber") val callNumber: String?,
    @SerializedName("CallTime") val callTime: String?,
    @SerializedName("Urgency") val urgency: Int?,
    @SerializedName("Status") val status: String?
) : Serializable