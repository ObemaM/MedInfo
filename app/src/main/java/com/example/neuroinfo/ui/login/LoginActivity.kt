package com.example.neuroinfo.ui.login

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.neuroinfo.R
import com.example.neuroinfo.data.LoginRepository
import com.example.neuroinfo.data.RetrofitClient
import com.example.neuroinfo.data.TokenInterceptor
import com.example.neuroinfo.ui.main.MainActivity
import com.example.neuroinfo.util.toSha256
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var loginEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var errorTextView: TextView
    private lateinit var sharedPreferences: SharedPreferences

    private val loginRepository = LoginRepository(RetrofitClient.apiService)
    private val mainScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализация SharedPreferences
        sharedPreferences = getSharedPreferences("app_session", Context.MODE_PRIVATE)

        // ПРОВЕРКА СЕССИИ: Если пользователь уже вошел, сразу переходим в MainActivity
        if (sharedPreferences.getBoolean("isLoggedIn", false)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish() // Закрываем LoginActivity, чтобы пользователь не мог вернуться сюда кнопкой "назад"
            return // Прекращаем выполнение onCreate для LoginActivity
        }

        // Если пользователь не вошел, продолжаем и показываем экран входа
        setContentView(R.layout.activity_login)

        // Инициализация View-элементов
        loginEditText = findViewById(R.id.login)
        passwordEditText = findViewById(R.id.password)
        loginButton = findViewById(R.id.loginButton)
        errorTextView = findViewById(R.id.errorTextView)

        loginButton.setOnClickListener {
            errorTextView.visibility = View.GONE
            val inputLogin = loginEditText.text.toString()
            val inputPassword = passwordEditText.text.toString()

            if (inputLogin.isBlank() || inputPassword.isBlank()) {
                loginEditText.clearFocus()
                passwordEditText.clearFocus()
                hideKeyboard()
                errorTextView.text = "Введите логин и пароль"
                errorTextView.visibility = View.VISIBLE
                return@setOnClickListener
            }

            mainScope.launch {
                val inputPasswordHash = inputPassword.toSha256()
                try {
                    val result = withContext(Dispatchers.IO) {
                        loginRepository.login(inputLogin, inputPasswordHash)
                    }

                    if (result.success && result.content != null) {
                        saveSession(inputLogin, result.content)
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    } else {
                        loginEditText.clearFocus()
                        passwordEditText.clearFocus()
                        hideKeyboard()
                        val errorMessage = result.messages?.firstOrNull() ?: "Неизвестная ошибка аутентификации"
                        errorTextView.text = errorMessage
                        errorTextView.visibility = View.VISIBLE
                    }
                } catch (e: Exception) {
                    loginEditText.clearFocus()
                    passwordEditText.clearFocus()
                    hideKeyboard()
                    errorTextView.text = "Ошибка подключения к серверу: ${e.message}"
                    errorTextView.visibility = View.VISIBLE
                    e.printStackTrace()
                }
            }
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = currentFocus ?: window.decorView
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun saveSession(login: String, token: String) {
        TokenInterceptor.saveToken(this, token)
        with(sharedPreferences.edit()) {
            putBoolean("isLoggedIn", true)
            putString("user_login", login)
            apply()
        }
    }
}