package com.example.neuroinfo.ui.incoming

import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.neuroinfo.R

class IncomingCallActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔓 Включить экран, даже если заблокирован
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        setContentView(R.layout.activity_incoming_call)

        // 📢 Проиграть звук
        playCallSound()

        // 📄 Показать данные (можно передавать через intent)
        val patientName = intent.getStringExtra("patientName") ?: "Новый вызов"
        findViewById<TextView>(R.id.patientInfo).text = patientName

        // ✅ Принять
        findViewById<Button>(R.id.acceptButton).setOnClickListener {
            // TODO: отправить на сервер "принято"
            finish()
        }

        // ❌ Отказаться
        findViewById<Button>(R.id.rejectButton).setOnClickListener {
            finish()
        }
    }

    private fun playCallSound() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@IncomingCallActivity, ringtoneUri)
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        super.onDestroy()
    }
}