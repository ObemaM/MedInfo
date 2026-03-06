package com.example.medinfo


import android.app.Application
import com.example.medinfo.config.ConfigManager
import com.example.medinfo.data.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MedInfoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize configuration first
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ConfigManager.initialize(this@MedInfoApplication)
            } catch (e: Exception) {
                // Config will fall back to defaults if initialization fails
                e.printStackTrace()
            }
        }
        
        RetrofitClient.init(this) // Инициализирует Retrofit с контекстом приложения
    }
}
