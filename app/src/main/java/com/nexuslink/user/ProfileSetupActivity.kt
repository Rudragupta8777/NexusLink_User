package com.nexuslink.user

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.nexuslink.user.data.Education
import com.nexuslink.user.data.Portfolio
import com.nexuslink.user.data.Project
import com.nexuslink.user.data.StudentProfile
import com.nexuslink.user.data.WorkExperience
import com.nexuslink.user.databinding.ActivityProfileSetupBinding

class ProfileSetupActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileSetupBinding
    private var resumeUri: Uri? = null
    private val PICK_PDF_REQUEST = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGenderDropdown()
        setupUploadButton()
        setupContinueButton()
        prefillDataFromIntent()
    }

    private fun setupGenderDropdown() {
        val genders = listOf("Male", "Female", "Non-binary", "Prefer not to say")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders)
        (binding.genderDropdown.editText as? AutoCompleteTextView)?.setAdapter(adapter)
    }

    private fun setupUploadButton() {
        binding.uploadResumeButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/pdf"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, PICK_PDF_REQUEST)
        }
    }

    private fun setupContinueButton() {
        binding.continueButton.setOnClickListener {
            if (validateForm()) {
                val profile = createProfileFromInput()
                simulateResumeParsing(profile)

                val intent = Intent(this, ProfileReviewActivity::class.java).apply {
                    val gson = Gson()
                    val profileJson = gson.toJson(profile)
                    putExtra("profile", profileJson) // Pass as JSON string
                }
                startActivity(intent)
            }
        }
    }

    private fun prefillDataFromIntent() {
        intent.getStringExtra("card_uid")?.let {
            // You can use the card UID if needed
        }

        // Pre-fill with dummy university data (replace with actual data from your system)
        binding.nameEditText.setText("John Doe")
        binding.universityEditText.setText("Tech University")
        binding.majorEditText.setText("Computer Science")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_PDF_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                resumeUri = uri
                binding.uploadResumeButton.text = "Resume Uploaded ✓"
                Toast.makeText(this, "Resume selected successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validateForm(): Boolean {
        if (binding.nameEditText.text.isNullOrEmpty()) {
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
            return false
        }
        if (binding.genderDropdown.editText?.text.isNullOrEmpty()) {
            Toast.makeText(this, "Please select your gender", Toast.LENGTH_SHORT).show()
            return false
        }
        if (resumeUri == null) {
            Toast.makeText(this, "Please upload your resume", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun createProfileFromInput(): StudentProfile {
        val profile = StudentProfile(
            name = binding.nameEditText.text.toString(),
            gender = binding.genderDropdown.editText?.text.toString(),
            email = binding.emailEditText.text.toString(),
            phone = binding.phoneEditText.text.toString(),
            location = binding.locationEditText.text.toString()
        )

        // Set portfolio separately
        profile.portfolio = Portfolio(
            github = binding.githubEditText.text.toString(),
            linkedin = binding.linkedinEditText.text.toString()
        )

        return profile
    }

    private fun simulateResumeParsing(profile: StudentProfile) {
        // Simulate parsed data from resume (replace with actual resume parsing)
        profile.education.add(Education(
            degree = "Bachelor of Computer Science",
            institution = "Tech University",
            startYear = 2020,
            endYear = 2024
        ))

        profile.workExperience.add(WorkExperience(
            title = "Software Developer Intern",
            company = "Tech Corp",
            startDate = "Jun 2023",
            endDate = "Aug 2023",
            description = "Developed mobile applications and implemented new features"
        ))

        profile.projects.add(Project(
            title = "Student Management System",
            duration = "Jan 2024 - May 2024",
            techStack = listOf("Kotlin", "Android", "Firebase"),
            description = "A comprehensive student management application"
        ))

        profile.skills.addAll(listOf("Kotlin", "Java", "Android Development", "Firebase", "Git"))
    }
}