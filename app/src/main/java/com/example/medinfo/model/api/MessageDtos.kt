package com.example.medinfo.model.api

import java.io.Serializable

// POST /hospitalizations/get-messages
data class GetMessagesRequestDto(
    val hospitalizationId: String
)

// POST /hospitalizations/send-message
data class SendMessageRequestDto(
    val hospitalizationId: String,
    val messageText: String
)

data class MessageResponseDto(
    val id: String,
    val hospitalizationId: String,
    val origin: Int,
    val userId: String?,
    val type: Int,
    val isNotificationSent: Boolean,
    val receptionTime: String,
    val patientCondition: PatientConditionResponseDto?,
    val text: String?,
    val phoneNumber: String?
) : Serializable

data class PatientConditionResponseDto(
    val id: String,
    val startDisease: Int?,
    val vozr: String?,
    val consciousness: String?,
    val bloodPressure: String?,
    val heartRate: Int?,
    val respirationRate: Int?,
    val temperature: Double?,
    val spO2: Int?,
    val vas: Int?,
    val glucometry: Double?,
    val pregnant: Boolean,
    val convulsions: Boolean,
    val stenosis: Boolean,
    val ifaPresence: Boolean,
    val ifaTool: List<String>?,
    val alv: Boolean,
    val venousAccessPresence: Boolean,
    val venousAccessMethod: List<String>?,
    val oxygenSupport: Boolean,
    val bleedingPresence: Boolean,
    val bleedingType: String?,
    val arterialTourniquetPresence: Boolean,
    val arterialTourniquetApplicationTime: String?,
    val mrs: Int?,
    val newsScore: Int?,
    val pewsScore: Int?,
    val algoverIndex: Double?,
    val lams: Int? = null
) : Serializable
