package com.example.medinfo.model.api

import java.io.Serializable

// POST /hospitalizations/get-hospitalizations
data class GetHospitalizationsRequestDto(
    val pageNumber: Int,
    val pageSize: Int,
    val getCount: Boolean,
    val filters: GetHospitalizationsFiltersRequestDto? = null
) : Serializable

data class GetHospitalizationsFiltersRequestDto(
    val statuses: List<Int>? = null,
    val decisions: List<Int>? = null,
    // null — не фильтровать; true/false — только с данными о состоянии пациента / без них.
    val hasPatientCondition: Boolean? = null,
    val patientFullName: String? = null,
    val hospitalizationDateTimeFrom: String? = null,
    val hospitalizationDateTimeTo: String? = null,
    val dayNumber: Int? = null,
    val yearNumber: Int? = null
) : Serializable

// GET для данных госпитализаций
data class GetHospitalizationsResponseDto(
    val count: Int?,
    val hospitalizations: List<HospitalizationResponseDto>
) : Serializable

data class HospitalizationResponseDto(
    val id: String,
    val decisionId: Int,
    val decisionName: String,
    val statusId: Int,
    val statusName: String,
    val creationTime: String?,
    val notificationTime: String?,
    val decisionTime: String?,
    val call: CallResponseDto,
    val isNotificationSent: Boolean
) : Serializable

data class CallResponseDto(
    val id: String,
    val brigadeSmpCode: Int,
    val dayNumber: Int,
    val yearNumber: Int,
    val status: String,
    val hospitalizationPlace: String?,
    val callTime: String,
    val transferTime: String?,
    val departureTime: String?,
    val brigadeArrivalTime: String?,
    val hospitalizationTime: String?,
    val arrivalHospitalTime: String?,
    val closeCallTime: String?,
    val backTime: String?,
    val reason: String?,
    val additionalInfo: String?,
    val whoCall: String?,
    val callType: String?,
    val callProfile: String?,
    val comment: String?,
    val urgency: Int?,
    val callResult: String?,
    val mkbCode: String?,
    val mainDiagnosis: String?,
    val secondDiagnosis: String?,
    val diagnosisComment: String?,
    val diseaseType: String?,
    val place: String?,
    val sector: Int?,
    val district: String?,
    val point: String?,
    val street: String?,
    val house: String?,
    val apartment: String?,
    val entrance: Int?,
    val entranceCode: String?,
    val floor: Int?,
    val longitude: Double?,
    val latitude: Double?,
    val patientName: String?,
    val patientSurname: String?,
    val patientPatronymic: String?,
    val sex: String?,
    val age: String?,
    val birthDay: String?,
    val alcohol: Boolean,
    val snils: String?,
    val documentType: String?,
    val documentNumber: String?,
    val smo: String?,
    val insuranceNumber: String?,
    val brigadeNumber: Int?,
    val brigadeProfile: String?,
    val radio: String?,
    val carNumber: String?,
    val mileage: String?,
    val territorialSmpCode: Int?,
    val substationSmp: Int?,
    val substationNumberControl: Int?,
    val substationNumberBase: Int?,
    val seniorPersonalNumber: String?,
    val seniorFullName: String?,
    val member1: String?,
    val member2: String?,
    val driver: String?
) : Serializable
