package com.example.neuroinfo.ui.login

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.neuroinfo.R // Убедитесь, что R.id.* существует
import com.example.neuroinfo.data.LoginRepository
import com.example.neuroinfo.data.RetrofitClient
import com.example.neuroinfo.ui.main.MainActivity
import com.example.neuroinfo.util.toSha256

// Импорты для асинхронной работы
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    // Убедитесь, что у вас есть все необходимые поля для View
    private lateinit var loginEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var errorTextView: TextView
    private lateinit var sharedPreferences: SharedPreferences

    // 💡 1. Инициализация репозитория и Coroutine Scope
    private val loginRepository = LoginRepository(RetrofitClient.apiService)
    private val mainScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login) // Убедитесь, что ID макета верный

        // Инициализация SharedPreferences
        sharedPreferences = getSharedPreferences("app_session", Context.MODE_PRIVATE)

        // Инициализация View-элементов
        loginEditText = findViewById(R.id.login)
        passwordEditText = findViewById(R.id.password)
        loginButton = findViewById(R.id.loginButton)
        errorTextView = findViewById(R.id.errorTextView)

        loginButton.setOnClickListener {
            // Сброс видимости ошибок
            errorTextView.visibility = View.GONE

            val inputLogin = loginEditText.text.toString()
            val inputPassword = passwordEditText.text.toString()

            // Простая валидация
            if (inputLogin.isBlank() || inputPassword.isBlank()) {
                errorTextView.text = "Введите логин и пароль"
                errorTextView.visibility = View.VISIBLE
                return@setOnClickListener
            }

            // 💡 2. Запуск корутины для выполнения асинхронного запроса
            mainScope.launch {

                // 3. ХЭШИРУЕМ ПАРОЛЬ ПЕРЕД ОТПРАВКОЙ
                val inputPasswordHash = inputPassword.toSha256()

                try {
                    // Переключаемся на поток ввода/вывода (Dispatchers.IO) для сетевого запроса
                    val result = withContext(Dispatchers.IO) {
                        loginRepository.login(inputLogin, inputPasswordHash)
                    }

                    // Обработка ответа (возвращаемся на Dispatchers.Main)
                    if (result.success && result.content != null) {
                        // УСПЕХ:
                        saveSession(inputLogin, result.content)

                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    } else {
                        // ОШИБКА АУТЕНТИФИКАЦИИ (сообщение от сервера)
                        val errorMessage = result.messages?.firstOrNull() ?: "Неизвестная ошибка аутентификации"
                        errorTextView.text = errorMessage
                        errorTextView.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    // ОШИБКА СЕТИ (сервер недоступен, таймаут и т.п.)
                    errorTextView.text = "Ошибка подключения к серверу: ${e.message}"
                    errorTextView.visibility = View.VISIBLE
                    e.printStackTrace()
                }
            }
        }
    }

    // 💡 4. Функция сохранения JWT-токена
    private fun saveSession(login: String, token: String) {
        with(sharedPreferences.edit()) {
            putBoolean("isLoggedIn", true)
            putString("user_login", login)
            putString("jwt_token", token) // Сохраняем токен для дальнейших запросов!
            apply()
        }
    }
}