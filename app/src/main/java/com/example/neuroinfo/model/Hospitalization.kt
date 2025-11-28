package com.example.neuroinfo.model
import java.io.Serializable

data class Hospitalization(
    val id: Int,
    val patientName: String,
    var status: String,
    var message: String = ""
): Serializable