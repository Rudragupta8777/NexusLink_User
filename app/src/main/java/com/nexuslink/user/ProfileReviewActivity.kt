package com.nexuslink.user

import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.nexuslink.user.Adapter.EducationAdapter
import com.nexuslink.user.data.Education
import com.nexuslink.user.data.StudentProfile

class ProfileReviewActivity : AppCompatActivity() {
    private lateinit var profile: StudentProfile
    private lateinit var educationAdapter: EducationAdapter
    private lateinit var nameTextView: TextView
    private lateinit var emailTextView: TextView
    private lateinit var phoneTextView: TextView
    private lateinit var locationTextView: TextView
    private lateinit var educationRecyclerView: RecyclerView
    private lateinit var saveButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile_review)

        // Initialize views directly without view binding
        nameTextView = findViewById(R.id.nameTextView)
        emailTextView = findViewById(R.id.emailTextView)
        phoneTextView = findViewById(R.id.phoneTextView)
        locationTextView = findViewById(R.id.locationTextView)
        educationRecyclerView = findViewById(R.id.educationRecyclerView)
        saveButton = findViewById(R.id.saveButton)

        // Get profile from intent (for now, we'll create a dummy profile)
        profile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("profile", StudentProfile::class.java) ?: createDummyProfile()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("profile") ?: createDummyProfile()
        }

        setupRecyclerViews()
        populateData()
        setupSaveButton()
    }

    private fun createDummyProfile(): StudentProfile {
        // Create a dummy profile for testing
        return StudentProfile(
            name = "John Doe",
            email = "john.doe@example.com",
            phone = "+1 234-567-8900",
            location = "New York, NY",
            education = mutableListOf(
                Education(
                    degree = "Bachelor of Computer Science",
                    institution = "Tech University",
                    startYear = 2020,
                    endYear = 2024
                )
            ),
            workExperience = mutableListOf(),
            projects = mutableListOf(),
            skills = mutableListOf("Kotlin", "Java", "Android Development")
        )
    }

    private fun setupRecyclerViews() {
        // Education RecyclerView
        educationAdapter = EducationAdapter(profile.education)
        educationRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ProfileReviewActivity)
            adapter = educationAdapter
        }

        // TODO: Initialize other RecyclerViews for experience, projects, skills
    }

    private fun populateData() {
        nameTextView.text = profile.name
        emailTextView.text = profile.email
        phoneTextView.text = profile.phone
        locationTextView.text = profile.location

        // Set up edit listeners
        nameTextView.setOnClickListener { showEditDialog("Name", profile.name) { profile.name = it } }
        emailTextView.setOnClickListener { showEditDialog("Email", profile.email) { profile.email = it } }
        phoneTextView.setOnClickListener { showEditDialog("Phone", profile.phone) { profile.phone = it } }
        locationTextView.setOnClickListener { showEditDialog("Location", profile.location) { profile.location = it } }
    }

    private fun showEditDialog(title: String, currentValue: String, onSave: (String) -> Unit) {
        // Implement a dialog for editing fields
        // You can use MaterialAlertDialogBuilder or create a custom dialog
        Toast.makeText(this, "Edit $title functionality to be implemented", Toast.LENGTH_SHORT).show()
    }

    private fun setupSaveButton() {
        saveButton.setOnClickListener {
            // Update profile with edited data
            profile.education = educationAdapter.getItems()

            // Save profile locally or send to backend
            Toast.makeText(this, "Profile saved successfully!", Toast.LENGTH_SHORT).show()

            // Navigate to next screen or finish
            finish()
        }
    }
}