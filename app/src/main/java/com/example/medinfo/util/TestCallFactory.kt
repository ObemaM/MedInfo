package com.example.medinfo.util

import com.example.medinfo.model.api.CallResponseDto
import com.example.medinfo.model.api.HospitalizationDecision
import com.example.medinfo.model.api.HospitalizationResponseDto
import com.example.medinfo.model.api.HospitalizationStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

// Единый источник тестовых данных. Используется и кнопкой "Тестовый вызов" на главном
// экране, и FakeCallAlarmReceiver (Doze-проверка). После удаления legacy CallNotificationDto
// нет смысла держать одни и те же поля в нескольких местах.
object TestCallFactory {

    fun buildMockHospitalization(): HospitalizationResponseDto {
        val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val hospitalizationId = UUID.randomUUID().toString()
        val callId = UUID.randomUUID().toString()

        return HospitalizationResponseDto(
            id = hospitalizationId,
            decisionId = HospitalizationDecision.NONE.id,
            decisionName = "Нет решения",
            statusId = HospitalizationStatus.CONSULTATION.id,
            statusName = "Консультация",
            creationTime = now,
            decisionTime = null,
            consultationRequestTime = now,
            consultationNotificationTime = now,
            hospitalizationNotificationTime = null,
            consultationDiagnosis = "Боль в груди",
            call = CallResponseDto(
                id = callId,
                brigadeSmpCode = 10,
                dayNumber = 6,
                yearNumber = 2026,
                status = "транспортировка",
                hospitalizationPlace = null,
                callTime = now,
                transferTime = null,
                departureTime = null,
                brigadeArrivalTime = null,
                hospitalizationTime = null,
                arrivalHospitalTime = null,
                closeCallTime = null,
                backTime = null,
                reason = "Боль в груди",
                additionalInfo = "Аллергия на пенициллин",
                whoCall = null,
                callType = null,
                callProfile = null,
                comment = null,
                urgency = 1,
                callResult = null,
                mkbCode = null,
                mainDiagnosis = null,
                secondDiagnosis = null,
                diagnosisComment = null,
                diseaseType = null,
                place = null,
                sector = null,
                district = "Центральный",
                point = "Москва",
                street = "Ленина",
                house = "12",
                apartment = "45",
                entrance = 3,
                entranceCode = null,
                floor = null,
                longitude = 55.7558,
                latitude = 37.6176,
                patientName = "Иван",
                patientSurname = "Иванов",
                patientPatronymic = "Иванович",
                sex = "Муж",
                age = "45",
                birthDay = null,
                alcohol = false,
                snils = null,
                documentType = null,
                documentNumber = null,
                smo = null,
                insuranceNumber = null,
                brigadeNumber = 404,
                brigadeProfile = "Кардиологическая",
                radio = null,
                carNumber = null,
                mileage = null,
                territorialSmpCode = null,
                substationSmp = null,
                substationNumberControl = null,
                substationNumberBase = null,
                seniorPersonalNumber = null,
                seniorFullName = null,
                member1 = null,
                member2 = null,
                driver = null
            )
        )
    }

}
