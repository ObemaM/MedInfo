package com.example.medinfo


import android.app.Application
import com.example.medinfo.data.RetrofitClient

class MedInfoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        RetrofitClient.init(this) // Инициализирует Retrofit с контекстом приложения
    }
}
