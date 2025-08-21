package com.example.smartlearningassistant

import android.os.Bundle
import android.util.Log
import android.widget.TextView // Added import
import androidx.appcompat.app.AppCompatActivity
import org.opencv.android.OpenCVLoader

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val opencvStatusTextView = findViewById<TextView>(R.id.sampleTextView) // Get reference to TextView

        if (OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "✅ OpenCV initialized successfully!")
            opencvStatusTextView.text = "OpenCV Loaded Successfully!" // Update text
            // You can now safely use OpenCV methods
        } else {
            Log.e("OpenCV", "❌ Failed to initialize OpenCV.")
            opencvStatusTextView.text = "OpenCV Load Failed." // Update text
            // Handle initialization failure
        }
    }
}
