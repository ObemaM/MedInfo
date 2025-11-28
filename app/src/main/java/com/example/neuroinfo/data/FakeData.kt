package com.example.neuroinfo.data

import com.example.neuroinfo.model.Hospitalization

object FakeData {
    val hospitalizations = listOf(
        Hospitalization(1, "Иванов И.И.", "Не просмотрена"),
        Hospitalization(2, "Петров П.П.", "Просмотрена"),
        Hospitalization(3, "Сидоров С.С.", "На вызове"),
        Hospitalization(4, "Кузнецов К.К.", "Завершено")
    )
}