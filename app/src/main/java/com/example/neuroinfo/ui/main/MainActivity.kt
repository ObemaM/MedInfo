package com.example.neuroinfo.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.neuroinfo.R
import com.example.neuroinfo.adapter.HospitalizationAdapter
import com.example.neuroinfo.data.FakeData
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private var allHospitalizations = FakeData.hospitalizations.toMutableList()
    private lateinit var adapter: HospitalizationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adapter = HospitalizationAdapter(allHospitalizations) { position ->
            allHospitalizations[position] = allHospitalizations[position].copy(status = "Просмотрена")
            adapter.notifyItemChanged(position)
        }

        findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            this.adapter = this@MainActivity.adapter
        }

        // Настройка нижнего меню
        findViewById<BottomNavigationView>(R.id.bottomNav).apply {
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_active -> {
                        adapter.updateList(allHospitalizations.filter { it.status != "Завершено" })
                        true
                    }
                    R.id.nav_archive -> {
                        adapter.updateList(allHospitalizations.filter { it.status == "Завершено" })
                        true
                    }
                    else -> false
                }
            }
        }
    }
}