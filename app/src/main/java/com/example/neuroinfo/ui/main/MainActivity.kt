package com.example.neuroinfo.ui.main

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.neuroinfo.R
import com.example.neuroinfo.adapter.HospitalizationAdapter
import com.example.neuroinfo.data.FakeData
import com.example.neuroinfo.model.Hospitalization
import com.example.neuroinfo.ui.incoming.IncomingCallActivity

class MainActivity : AppCompatActivity() {

    private var hospitalizations = FakeData.hospitalizations.toMutableList()
    private lateinit var adapter: HospitalizationAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adapter = HospitalizationAdapter(hospitalizations) { position ->
            hospitalizations[position] = hospitalizations[position].copy(status = "Просмотрена")
            adapter.notifyItemChanged(position)
        }

        findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerView).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            this.adapter = this@MainActivity.adapter
        }

        // Тестовая кнопка для входящего вызова
        findViewById<Button>(R.id.testCallButton).setOnClickListener {
            startActivity(Intent(this, IncomingCallActivity::class.java).apply {
                putExtra("patientName", "Иванов И.И.")
            })
        }
    }
}