package com.example.neuroinfo.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.example.neuroinfo.R
import com.example.neuroinfo.ui.main.MainActivity

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val login = findViewById<EditText>(R.id.login)
        val password = findViewById<EditText>(R.id.password)
        val button = findViewById<Button>(R.id.loginButton)

        button.setOnClickListener {
            // Пока просто переход на MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}