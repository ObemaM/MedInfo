package com.example.neuroinfo.data

import com.example.neuroinfo.model.Hospitalization

object FakeData {
    val hospitalizations = listOf(
        Hospitalization("Иванов И.И.", "Не просмотрена"),
        Hospitalization("Петров П.П.", "Просмотрена"),
        Hospitalization("Сидоров С.С.", "Не просмотрена")
    )
}