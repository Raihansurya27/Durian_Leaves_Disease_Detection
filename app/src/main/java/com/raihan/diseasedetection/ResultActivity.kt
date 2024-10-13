package com.raihan.diseasedetection

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.viewpager2.widget.ViewPager2
import com.raihan.diseasedetection.adapter.SliderAdapter
import com.raihan.diseasedetection.databinding.ActivityResultBinding

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val photoPaths = intent.getStringArrayListExtra("PHOTO_PATHS")
        if(photoPaths != null){
            val adapter = SliderAdapter(photoPaths)
            binding.resultDetection.adapter = adapter
            binding.resultDetection.orientation = ViewPager2.ORIENTATION_HORIZONTAL
            binding.resultDetection.setCurrentItem(photoPaths.size -1, false)
        }

    }
}