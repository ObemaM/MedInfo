package com.example.neuroinfo.ui.main

import android.widget.ImageButton
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.neuroinfo.R
import com.example.neuroinfo.adapter.HospitalizationAdapter
import com.example.neuroinfo.data.CallRepository
import com.example.neuroinfo.data.RetrofitClient
import com.example.neuroinfo.model.Hospitalization
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    // 💡 Принудительная инициализация в onCreate
    private lateinit var adapter: HospitalizationAdapter

    // Используем CallRepository, который инициализируется через RetrofitClient
    private val callRepository = CallRepository(RetrofitClient.apiService)
    private val mainScope = CoroutineScope(Dispatchers.Main)

    // Список данных, который будет обновляться
    private val hospitalizationList = mutableListOf<Hospitalization>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Убедитесь, что ваш макет называется activity_main
        setContentView(R.layout.activity_main)

        // 💡 ИЗМЕНЕНИЕ ЗДЕСЬ: используем ваш ID
        recyclerView = findViewById(R.id.recyclerView)

        // ... (остальная логика инициализации адаптера и загрузки данных остается прежней)

        // Добавьте логику для кнопок (опционально):
        findViewById<ImageButton>(R.id.profile_button).setOnClickListener {
            // TODO: Открыть диалог или Activity профиля
        }

        // TODO: Обработка Tab Layout (вкладок Активные/Архив)
        // findViewById<TabLayout>(R.id.tab_layout).addOnTabSelectedListener(...)

        fetchCalls()
    }

    /**
     * Загружает данные списка вызовов из API.
     */
    private fun fetchCalls() {
        mainScope.launch {
            try {
                // 1. Вызов с исправленными именами параметров
                val result = withContext(Dispatchers.IO) {
                    callRepository.getCalls(pageNumber = 1, pageSize = 20, getCount = true)
                }

                // 2. Обработка Result<CallListContent>
                if (result.isSuccess) {
                    // Успех: Получаем CallListContent
                    val content = result.getOrThrow()

                    // 3. ОБНОВЛЕНИЕ ДАННЫХ
                    hospitalizationList.clear()
                    hospitalizationList.addAll(content.calls)

                    // 4. Инициализация адаптера (если не инициализирован)
                    if (!::adapter.isInitialized) {
                        adapter = HospitalizationAdapter(
                            hospitalizationList,
                            onCallClicked = { call ->
                                // TODO: Реальная логика: Открытие нового Activity
                                Toast.makeText(this@MainActivity, "Клик по вызову: ${call.patientFullName}", Toast.LENGTH_SHORT).show()
                            }
                        )
                        recyclerView.adapter = adapter
                        recyclerView.layoutManager = LinearLayoutManager(this@MainActivity)
                    } else {
                        // 5. Обновление адаптера
                        adapter.notifyDataSetChanged()
                    }

                    Toast.makeText(this@MainActivity, "Загружено вызовов: ${hospitalizationList.size}", Toast.LENGTH_SHORT).show()

                } else {
                    // 6. Обработка ошибки API/репозитория
                    val error = result.exceptionOrNull()?.message ?: "Не удалось загрузить список вызовов."
                    Toast.makeText(this@MainActivity, "Ошибка: $error", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                // 7. Обработка ошибки сети/Coroutine
                Toast.makeText(this@MainActivity, "Ошибка сети при загрузке данных.", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }
}