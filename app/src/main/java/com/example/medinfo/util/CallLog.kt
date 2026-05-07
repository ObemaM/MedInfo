package com.example.medinfo.util

import android.util.Log
import com.example.medinfo.model.api.HospitalizationResponseDto
import com.example.medinfo.model.api.MessageResponseDto

object CallLog {
    private const val TAG = "CALL_LOG"

    fun event(source: String, message: String) {
        Log.i(TAG, "[$source] $message")
    }

    fun hospitalization(source: String, call: HospitalizationResponseDto, message: String? = null) {
        Log.i(TAG, "[$source] ${buildHospitalizationMessage(call, message)}")
    }

    fun queue(
        source: String,
        action: String,
        call: HospitalizationResponseDto,
        queueSize: Int,
        isNew: Boolean? = null,
        remainingMs: Long? = null
    ) {
        val suffix = buildString {
            append("action=$action")
            append(" queueSize=$queueSize")
            isNew?.let { append(" isNew=$it") }
            remainingMs?.let { append(" remainingMs=$it") }
        }
        Log.i(TAG, "[$source] ${buildHospitalizationMessage(call, suffix)}")
    }

    fun message(source: String, message: MessageResponseDto, note: String? = null) {
        Log.i(
            TAG,
            buildString {
                append("[$source] message")
                append(" id=${message.id}")
                append(" hospitalizationId=${message.hospitalizationId}")
                append(" origin=${message.origin}")
                append(" type=${message.type}")
                append(" notificationSent=${message.isNotificationSent}")
                append(" receptionTime=${message.receptionTime}")
                note?.let { append(" note=$it") }
            }
        )
    }

    private fun buildHospitalizationMessage(
        call: HospitalizationResponseDto,
        message: String?
    ): String {
        // В диагностический лог выводим только технические поля, без ФИО, адреса и медицинских деталей пациента.
        return buildString {
            append("hospitalization")
            append(" id=${call.id}")
            append(" call=${call.call.dayNumber}/${call.call.yearNumber}")
            append(" status=${call.statusId}/${call.statusName}")
            append(" decision=${call.decisionId}/${call.decisionName}")
            append(" notificationSent=${call.isNotificationSent}")
            append(" creationTime=${call.creationTime ?: "-"}")
            append(" notificationTime=${call.notificationTime ?: "-"}")
            append(" callTime=${call.call.callTime}")
            message?.let { append(" note=$it") }
        }
    }
}
