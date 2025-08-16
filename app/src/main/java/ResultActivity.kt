package com.example.smartlearningassistant

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class ResultActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tvRecognizedText: TextView
    private lateinit var btnBack: Button
    private lateinit var tts: TextToSpeech
    private var textToRead: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        tvRecognizedText = findViewById(R.id.tvRecognizedText)
        btnBack = findViewById(R.id.btnBack)

        textToRead = intent.getStringExtra("recognizedText") ?: ""
        tvRecognizedText.text = textToRead

        tts = TextToSpeech(this, this)

        btnBack.setOnClickListener {
            finish()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
            if (textToRead.isNotEmpty()) {
                tts.speak(textToRead, TextToSpeech.QUEUE_FLUSH, null, "tts1")
            }
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
