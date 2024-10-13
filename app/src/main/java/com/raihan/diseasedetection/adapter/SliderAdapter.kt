package com.raihan.diseasedetection.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.raihan.diseasedetection.databinding.SliderItemBinding

class SliderAdapter(private var photoPaths: List<String>) :
    RecyclerView.Adapter<SliderAdapter.SliderAdapterViewHolder>(){

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SliderAdapterViewHolder {
        val view = SliderItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SliderAdapterViewHolder(view)
    }

    override fun onBindViewHolder(holder: SliderAdapterViewHolder, position: Int) {
        holder.bind(photoPaths[position])
    }

    override fun getItemCount(): Int = photoPaths.size

    inner class SliderAdapterViewHolder(private val view: SliderItemBinding):
        RecyclerView.ViewHolder(view.root){
        fun bind(photoPath: String){
            Glide.with(view.resultPhoto.context)
                .load(photoPath)
                .into(view.resultPhoto)
        }
    }
}