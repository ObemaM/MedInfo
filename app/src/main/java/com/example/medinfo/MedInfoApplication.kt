package com.example.medinfo


import android.app.Application
import com.example.medinfo.config.ConfigManager
import com.example.medinfo.data.network.RetrofitClient

class MedInfoApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        try {
            ConfigManager.initialize(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        RetrofitClient.init(this) // Инициализирует Retrofit с контекстом приложения
    }
}
