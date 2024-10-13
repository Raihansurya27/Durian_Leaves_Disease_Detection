package com.raihan.diseasedetection

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        supportActionBar?.hide()

        // Delay halaman selanjutnya
        val splashView = findViewById<ImageView>(R.id.logo_holder_splash)
//        splashButton.setOnClickListener {
//            val intent = Intent(this@SplashActivity, MainActivity::class.java)
//            startActivity(intent)
//        }
        val handler = Handler(Looper.getMainLooper())
//        handler.postDelayed({
//            splashView
//            handler.postDelayed({
//                val intent = Intent(this@SplashActivity, MainActivity::class.java)
//                startActivity(intent)
//            }, 1500)
//        }, 6000)
        handler.postDelayed({
                val intent = Intent(this@SplashActivity, MainActivity::class.java)
                startActivity(intent)
            }, 4000)
    }
}