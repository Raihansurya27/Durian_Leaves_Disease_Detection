package com.raihan.diseasedetection

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Tombol Deteksi
        val searchButton = findViewById<Button>(R.id.home_search_button)
        searchButton.setOnClickListener {
            val intent = Intent(this@MainActivity, CameraActivity::class.java)
            startActivity(intent)
        }
    }
}