package com.example.neuroinfo


import android.app.Application
import com.example.neuroinfo.data.RetrofitClient

class NeuroInfoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        RetrofitClient.init(this)
    }
}
