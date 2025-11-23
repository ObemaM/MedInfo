package com.example.neuroinfo.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.neuroinfo.R
import com.example.neuroinfo.model.Hospitalization

class HospitalizationAdapter(
    private val items: List<Hospitalization>,
    private val onMarkAsViewed: (Int) -> Unit
) : RecyclerView.Adapter<HospitalizationAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.patientName)
        val status: TextView = itemView.findViewById(R.id.status)
        val markAsViewed: Button = itemView.findViewById(R.id.markAsViewed)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hospitalization, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.status.text = item.status
        holder.markAsViewed.setOnClickListener {
            onMarkAsViewed(position)
        }
    }

    override fun getItemCount() = items.size
}