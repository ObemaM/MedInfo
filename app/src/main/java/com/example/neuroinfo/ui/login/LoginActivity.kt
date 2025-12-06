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
import com.example.neuroinfo.ui.main.MainActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("user_session", Context.MODE_PRIVATE)

        // Check if the user is already logged in
        if (sharedPreferences.getBoolean("isLoggedIn", false)) {
            // If yes, go directly to MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish() // Close LoginActivity
            return // Stop further execution of onCreate
        }

        setContentView(R.layout.activity_login)

        val login = findViewById<EditText>(R.id.login)
        val password = findViewById<EditText>(R.id.password)
        val button = findViewById<Button>(R.id.loginButton)
        val valid = findViewById<TextView>(R.id.errorTextView)

        button.setOnClickListener { view ->
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)

            val correctLogin = "1"
            val correctPassword = "1"
            val inputLogin = login.text.toString()
            val inputPassword = password.text.toString()

            if (inputLogin == correctLogin && inputPassword == correctPassword) {
                // If credentials are correct, save the session and go to the main screen
                valid.visibility = View.GONE
                saveSession(inputLogin)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                valid.visibility = View.VISIBLE
            }
        }
    }

    private fun saveSession(login: String) {
        with(sharedPreferences.edit()) {
            putBoolean("isLoggedIn", true)
            putString("user_login", login)
            apply()
        }
    }
}
