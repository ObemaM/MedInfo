package com.example.medinfo.model.api

// POST /hospitalizations/confirm-reception
data class ConfirmReceptionRequestDto(
    val type: Int,
    val ids: List<String>
)

// POST /hospitalizations/save-decision
data class SaveDecisionRequestDto(
    val hospitalizationId: String,
    val decisionId: Int
)
