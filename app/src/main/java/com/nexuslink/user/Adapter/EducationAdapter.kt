package com.nexuslink.user.Adapter

import com.nexuslink.user.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nexuslink.user.data.Education

class EducationAdapter(private var items: MutableList<Education>) :
    RecyclerView.Adapter<EducationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val degree: TextView = view.findViewById(R.id.degreeTextView)
        val institution: TextView = view.findViewById(R.id.institutionTextView)
        val years: TextView = view.findViewById(R.id.yearsTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_education, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.degree.text = item.degree
        holder.institution.text = item.institution
        holder.years.text = "${item.startYear} - ${item.endYear}"
    }

    override fun getItemCount() = items.size

    fun getItems(): MutableList<Education> = items

    fun updateItems(newItems: MutableList<Education>) {
        items = newItems
        notifyDataSetChanged()
    }
}