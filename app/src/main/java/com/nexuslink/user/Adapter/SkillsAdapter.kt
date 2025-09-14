package com.nexuslink.user.Adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nexuslink.user.R

class SkillsAdapter(private var skillsList: List<String>) :
    RecyclerView.Adapter<SkillsAdapter.SkillViewHolder>() {

    class SkillViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val skillTextView: TextView = itemView.findViewById(R.id.skillTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SkillViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_skill_review, parent, false)
        return SkillViewHolder(view)
    }

    override fun onBindViewHolder(holder: SkillViewHolder, position: Int) {
        holder.skillTextView.text = skillsList[position]
    }

    override fun getItemCount(): Int = skillsList.size

    fun updateData(newSkillsList: List<String>) {
        skillsList = newSkillsList
        notifyDataSetChanged()
    }
}