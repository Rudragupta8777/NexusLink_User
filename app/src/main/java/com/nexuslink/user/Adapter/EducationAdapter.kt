package com.nexuslink.user.Adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nexuslink.user.R

class EducationAdapter(private var educationList: List<Map<String, Any>>) :
    RecyclerView.Adapter<EducationAdapter.EducationViewHolder>() {

    class EducationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val degreeTextView: TextView = itemView.findViewById(R.id.degreeTextView)
        val institutionTextView: TextView = itemView.findViewById(R.id.institutionTextView)
        val yearsTextView: TextView = itemView.findViewById(R.id.yearsTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EducationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_education_review, parent, false)
        return EducationViewHolder(view)
    }

    override fun onBindViewHolder(holder: EducationViewHolder, position: Int) {
        val education = educationList[position]
        holder.degreeTextView.text = education["degree"]?.toString() ?: "Not specified"
        holder.institutionTextView.text = education["institution"]?.toString() ?: "Not specified"

        val startYear = education["startYear"]?.toString() ?: ""
        val endYear = education["endYear"]?.toString() ?: ""
        holder.yearsTextView.text = if (startYear.isNotEmpty() && endYear.isNotEmpty()) {
            "$startYear - $endYear"
        } else {
            "Years not specified"
        }
    }

    override fun getItemCount(): Int = educationList.size

    fun updateData(newEducationList: List<Map<String, Any>>) {
        educationList = newEducationList
        notifyDataSetChanged()
    }
}