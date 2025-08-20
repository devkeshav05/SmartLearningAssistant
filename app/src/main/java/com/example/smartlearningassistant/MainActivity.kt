package com.example.smartlearningassistant

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "✅ OpenCV loaded successfully!")
        } else {
            Log.e("OpenCV", "❌ Failed to load OpenCV")
        }
    }
}
