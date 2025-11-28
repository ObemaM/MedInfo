package com.example.neuroinfo.ui.detail

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.neuroinfo.R
import com.example.neuroinfo.model.Hospitalization

class CallDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call_detail)

        val call = intent.getSerializableExtra("call") as Hospitalization

        findViewById<TextView>(R.id.patientName).text = call.patientName
        findViewById<TextView>(R.id.callStatus).text = "Статус: ${call.status}"

        val messageInput = findViewById<EditText>(R.id.messageInput)
        messageInput.setText(call.message)

        findViewById<Button>(R.id.sendButton).setOnClickListener {
            val message = messageInput.text.toString()
            // TODO: отправить на сервер
            Toast.makeText(this, "Сообщение отправлено: $message", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}