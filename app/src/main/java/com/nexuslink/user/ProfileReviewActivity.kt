package com.nexuslink.user

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.nexuslink.user.Adapter.EducationAdapter
import com.nexuslink.user.Adapter.WorkExperienceAdapter
import com.nexuslink.user.Adapter.SkillsAdapter
import com.nexuslink.user.data.StudentProfile
import com.nexuslink.user.databinding.ActivityProfileReviewBinding
import com.nexuslink.user.network.ApiService
import com.nexuslink.user.network.RetrofitInstance
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ProfileReviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileReviewBinding
    private lateinit var apiService: ApiService
    private var authToken: String? = null

    companion object {
        private const val TAG = "ProfileReviewActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        apiService = RetrofitInstance.api
        authToken = intent.getStringExtra("token")

        if (authToken.isNullOrEmpty()) {
            Toast.makeText(this, "Authentication token is missing.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupRecyclerViews()
        fetchProfileData()

        binding.editButton.setOnClickListener {
            val intent = Intent(this, ProfileSetupActivity::class.java).apply {
                putExtra("token", authToken)
            }
            startActivity(intent)
            finish()
        }

        binding.saveButton.setOnClickListener {
            Toast.makeText(this, "Profile saved successfully!", Toast.LENGTH_SHORT).show()
            // Here you can navigate to the main dashboard or finish the activity
            finish()
        }
    }

    private fun setupRecyclerViews() {
        binding.educationRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.educationRecyclerView.adapter = EducationAdapter(emptyList())

        binding.experienceRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.experienceRecyclerView.adapter = WorkExperienceAdapter(emptyList())

        binding.skillsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.skillsRecyclerView.adapter = SkillsAdapter(emptyList())
    }

    private fun fetchProfileData() {
        val bearerToken = if (authToken!!.startsWith("Bearer ")) {
            authToken!!
        } else {
            "Bearer $authToken"
        }

        apiService.getProfile(bearerToken).enqueue(object : Callback<StudentProfile> {
            override fun onResponse(call: Call<StudentProfile>, response: Response<StudentProfile>) {
                if (response.isSuccessful) {
                    val profile = response.body()
                    profile?.let {
                        updateUI(it)
                    } ?: run {
                        Toast.makeText(this@ProfileReviewActivity, "Failed to load profile data.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    Log.e(TAG, "Failed to fetch profile: $errorBody")
                    Toast.makeText(this@ProfileReviewActivity, "Failed to load profile.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<StudentProfile>, t: Throwable) {
                Log.e(TAG, "Network error fetching profile", t)
                Toast.makeText(this@ProfileReviewActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateUI(profile: StudentProfile) {
        // Update basic info
        binding.nameTextView.text = profile.name ?: "N/A"
        binding.emailTextView.text = profile.email ?: "N/A"
        binding.phoneTextView.text = profile.phone ?: "N/A"
        binding.locationTextView.text = profile.location ?: "N/A"
        binding.careerObjectiveTextView.text = profile.careerObjective ?: "N/A"

        // Update RecyclerViews
        (binding.educationRecyclerView.adapter as EducationAdapter).updateData(profile.education)
        (binding.experienceRecyclerView.adapter as WorkExperienceAdapter).updateData(profile.workExperience)
        (binding.skillsRecyclerView.adapter as SkillsAdapter).updateData(profile.skills)
    }
}