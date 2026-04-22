package com.example.medinfo.model

data class CallAnswerRequest(
    val callId: String,
    val decision: String // Accept или Reject
)