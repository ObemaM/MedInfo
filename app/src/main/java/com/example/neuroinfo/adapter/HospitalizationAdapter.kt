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
    private var items: List<Hospitalization>,
    private val onCallClicked: (Int) -> Unit
) : RecyclerView.Adapter<HospitalizationAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.patientName)
        val status: TextView = itemView.findViewById(R.id.status)

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hospitalization, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.patientName
        holder.status.text = item.status

        holder.itemView.setOnClickListener {
            onCallClicked(position)
        }
    }

    override fun getItemCount() = items.size

    // Новый метод для обновления списка (фильтрация)
    fun updateList(newList: List<Hospitalization>) {
        items = newList
        notifyDataSetChanged()
    }
}