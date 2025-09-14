package com.nexuslink.user.Adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nexuslink.user.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WorkExperienceAdapter(private var experienceList: List<Map<String, Any>>) :
    RecyclerView.Adapter<WorkExperienceAdapter.ExperienceViewHolder>() {

    class ExperienceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.titleTextView)
        val companyTextView: TextView = itemView.findViewById(R.id.companyTextView)
        val durationTextView: TextView = itemView.findViewById(R.id.durationTextView)
        val descriptionTextView: TextView = itemView.findViewById(R.id.descriptionTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExperienceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_experience_review, parent, false)
        return ExperienceViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExperienceViewHolder, position: Int) {
        val experience = experienceList[position]
        holder.titleTextView.text = experience["title"]?.toString() ?: "Not specified"
        holder.companyTextView.text = experience["company"]?.toString() ?: "Not specified"
        holder.descriptionTextView.text = experience["description"]?.toString() ?: "No description"

        val startDate = experience["startDate"] as? Long
        val endDate = experience["endDate"] as? Long

        val dateFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
        val durationText = if (startDate != null && endDate != null) {
            "${dateFormat.format(Date(startDate))} - ${dateFormat.format(Date(endDate))}"
        } else {
            "Duration not specified"
        }
        holder.durationTextView.text = durationText
    }

    override fun getItemCount(): Int = experienceList.size

    fun updateData(newExperienceList: List<Map<String, Any>>) {
        experienceList = newExperienceList
        notifyDataSetChanged()
    }
}