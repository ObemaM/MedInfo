package com.example.neuroinfo.model

data class CallAnswerRequest(
    val callId: String,
    val decision: String // "Accept" или "Reject"
)