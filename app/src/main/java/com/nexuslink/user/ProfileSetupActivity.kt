package com.nexuslink.user

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.gson.Gson
import com.nexuslink.user.data.Accomplishment
import com.nexuslink.user.data.Education
import com.nexuslink.user.data.Extracurricular
import com.nexuslink.user.data.LoginResponse
import com.nexuslink.user.data.ParsedResumeData
import com.nexuslink.user.data.Project
import com.nexuslink.user.data.StudentProfile
import com.nexuslink.user.data.Training
import com.nexuslink.user.data.WorkExperience
import com.nexuslink.user.databinding.ActivityProfileSetupBinding
import com.nexuslink.user.network.ApiService
import com.nexuslink.user.network.GenderRequest
import com.nexuslink.user.network.RetrofitInstance
import com.nexuslink.user.utils.AuthManager
import com.nexuslink.user.utils.ImprovedResumeParser
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class ProfileSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileSetupBinding
    private lateinit var authManager: AuthManager
    private lateinit var apiService: ApiService
    private lateinit var resumeParser: ImprovedResumeParser
    private val gson = Gson()
    private var resumeUri: Uri? = null
    private var parsedData: ParsedResumeData? = null
    private val PICK_PDF_REQUEST = 1

    private var authToken: String? = null

    // Data holders for manual entries
    private val manualEducation = mutableListOf<Education>()
    private val manualWorkExperience = mutableListOf<WorkExperience>()
    private val manualProjects = mutableListOf<Project>()
    private val manualAccomplishments = mutableListOf<Accomplishment>()
    private val manualTrainings = mutableListOf<Training>()
    private val manualExtracurriculars = mutableListOf<Extracurricular>()

    companion object {
        private const val TAG = "ProfileSetupActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)
        apiService = RetrofitInstance.api
        resumeParser = ImprovedResumeParser(this)

        authToken = intent.getStringExtra("token")
        Log.d(TAG, "Received token: ${authToken?.take(20)}...")

        if (authToken.isNullOrEmpty()) {
            Toast.makeText(this, "Auth token is missing! Please log in again.", Toast.LENGTH_SHORT).show()
            redirectToLogin()
            return
        }

        setupUI()
    }

    private fun setupUI() {
        setupGenderDropdown()
        setupResumeUpload()
        setupDynamicSections()
        setupButtons()
        showResumeUploadMessage()
    }

    private fun setupGenderDropdown() {
        val genders = listOf("male", "female", "other")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders)
        (binding.genderDropdown.editText as? AutoCompleteTextView)?.setAdapter(adapter)
    }

    private fun setupResumeUpload() {
        binding.uploadResumeButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/pdf"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(intent, PICK_PDF_REQUEST)
        }
    }

    private fun setupDynamicSections() {
        // Education section
        binding.addEducationButton.setOnClickListener {
            showEducationDialog()
        }

        // Work Experience section
        binding.addWorkExperienceButton.setOnClickListener {
            showWorkExperienceDialog()
        }

        // Projects section
        binding.addProjectButton.setOnClickListener {
            showProjectDialog()
        }

        // Accomplishments section (if button exists in layout)
        try {
            binding.addAccomplishmentButton?.setOnClickListener {
                showAccomplishmentDialog()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Accomplishment button not found in layout")
        }

        // Trainings section (if button exists in layout)
        try {
            binding.addTrainingButton?.setOnClickListener {
                showTrainingDialog()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Training button not found in layout")
        }

        // Extracurriculars section (if button exists in layout)
        try {
            binding.addExtracurricularButton?.setOnClickListener {
                showExtracurricularDialog()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Extracurricular button not found in layout")
        }
    }

    private fun setupButtons() {
        binding.continueButton.setOnClickListener {
            if (validateForm()) {
                updateGender()
            }
        }

        try {
            binding.skipButton?.setOnClickListener {
                // Save minimal data and continue
                updateGender()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Skip button not found in layout")
        }
    }

    private fun showResumeUploadMessage() {
        Toast.makeText(this, "Upload your resume to auto-populate fields, or add details manually", Toast.LENGTH_LONG).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_PDF_REQUEST && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                resumeUri = uri
                binding.uploadResumeButton.text = "Parsing Resume..."
                binding.uploadResumeButton.isEnabled = false
                try {
                    binding.parseStatusText?.text = "Parsing resume..."
                } catch (e: Exception) {
                    Log.w(TAG, "Parse status text not found")
                }

                // Parse resume using coroutines instead of Thread
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        parsedData = resumeParser.parseResumeAdvanced(uri)
                    } catch (e: Exception) {
                        Log.e(TAG, "Resume parsing error", e)
                        parsedData = null
                    }
                    withContext(Dispatchers.Main) {
                        onResumeParsingComplete()
                    }
                }
            }
        }
    }

    private fun onResumeParsingComplete() {
        binding.uploadResumeButton.text = "Resume Parsed ✓"
        binding.uploadResumeButton.isEnabled = true

        parsedData?.let { data ->
            // Fix: This was passing data.education instead of data.workExperience
            if (data.workExperience.isNotEmpty()) {
                Log.d(TAG, "Populating ${data.workExperience.size} work experience entries")
                populateWorkExperienceFromParsed(data.workExperience) // Fixed!
            }
            Log.d(TAG, "Starting to populate UI with parsed data: $data")

            // Auto-fill basic fields with null checks
            data.phone?.let {
                Log.d(TAG, "Setting phone: $it")
                try {
                    binding.phoneEditText?.setText(it)
                } catch (e: Exception) {
                    Log.w(TAG, "Phone field not found in layout")
                }
            }

            data.location?.let {
                Log.d(TAG, "Setting location: $it")
                try {
                    binding.locationEditText?.setText(it)
                } catch (e: Exception) {
                    Log.w(TAG, "Location field not found in layout")
                }
            }

            data.careerObjective?.let {
                Log.d(TAG, "Setting career objective: ${it.take(50)}...")
                try {
                    binding.careerObjectiveEditText?.setText(it)
                } catch (e: Exception) {
                    Log.w(TAG, "Career objective field not found in layout")
                }
            }

            // Auto-populate skills
            if (data.skills.isNotEmpty()) {
                val skillsText = data.skills.joinToString(", ")
                Log.d(TAG, "Setting skills: $skillsText")
                try {
                    binding.skillsEditText?.setText(skillsText)
                } catch (e: Exception) {
                    Log.w(TAG, "Skills field not found in layout")
                }
            }

            // Populate portfolio links
            data.portfolio?.let { portfolio ->
                Log.d(TAG, "Setting portfolio links: $portfolio")
                try {
                    portfolio["github"]?.let { binding.githubEditText?.setText(it) }
                    portfolio["linkedin"]?.let { binding.linkedinEditText?.setText(it) }
                    portfolio["portfolio"]?.let { binding.portfolioEditText?.setText(it) }
                    portfolio["other"]?.let { binding.portfolioEditText?.setText(it) }
                } catch (e: Exception) {
                    Log.w(TAG, "Portfolio fields not found in layout")
                }
            }

            // Populate dynamic sections - Fixed the bug here!
            if (data.education.isNotEmpty()) {
                Log.d(TAG, "Populating ${data.education.size} education entries")
                populateEducationFromParsed(data.education)
            }

            if (data.workExperience.isNotEmpty()) {
                Log.d(TAG, "Populating ${data.workExperience.size} work experience entries")
                populateWorkExperienceFromParsed(data.workExperience) // Fixed: was passing data.education
            }

            if (data.projects.isNotEmpty()) {
                Log.d(TAG, "Populating ${data.projects.size} project entries")
                populateProjectsFromParsed(data.projects)
            }

            if (data.accomplishments.isNotEmpty()) {
                Log.d(TAG, "Populating ${data.accomplishments.size} accomplishment entries")
                populateAccomplishmentsFromParsed(data.accomplishments)
            }

            if (data.trainings.isNotEmpty()) {
                Log.d(TAG, "Populating ${data.trainings.size} training entries")
                populateTrainingsFromParsed(data.trainings)
            }

            if (data.extracurriculars.isNotEmpty()) {
                Log.d(TAG, "Populating ${data.extracurriculars.size} extracurricular entries")
                populateExtracurricularsFromParsed(data.extracurriculars)
            }

            // Show summary
            val summary = buildString {
                append("Resume successfully parsed!\n\n")
                data.name?.let { append("Name: $it\n") }
                data.email?.let { append("Email: $it\n") }
                data.phone?.let { append("Phone: $it\n") }
                data.location?.let { append("Location: $it\n") }

                if (data.education.isNotEmpty()) append("Education: ${data.education.size} entries\n")
                if (data.workExperience.isNotEmpty()) append("Work Experience: ${data.workExperience.size} entries\n")
                if (data.projects.isNotEmpty()) append("Projects: ${data.projects.size} entries\n")
                if (data.skills.isNotEmpty()) append("Skills: ${data.skills.size} skills\n")
                if (data.accomplishments.isNotEmpty()) append("Accomplishments: ${data.accomplishments.size} entries\n")
                if (data.trainings.isNotEmpty()) append("Certifications: ${data.trainings.size} entries\n")
                if (data.extracurriculars.isNotEmpty()) append("Extracurriculars: ${data.extracurriculars.size} entries\n")
            }

            try {
                binding.parseStatusText?.text = summary
            } catch (e: Exception) {
                Log.w(TAG, "Parse status text not found")
            }

            Toast.makeText(this, "Resume data populated! Please review and edit as needed.", Toast.LENGTH_LONG).show()

        } ?: run {
            Log.e(TAG, "Failed to parse resume - parsedData is null")
            try {
                binding.parseStatusText?.text = "Failed to parse resume. Please check the file and try again, or add details manually."
            } catch (e: Exception) {
                Log.w(TAG, "Parse status text not found")
            }
            Toast.makeText(this, "Failed to parse resume. Please add details manually.", Toast.LENGTH_SHORT).show()
        }
    }

    // Dialog for adding education
    private fun showEducationDialog(education: Education? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_education, null)
        val degreeEditText = dialogView.findViewById<TextInputEditText>(R.id.degreeEditText)
        val institutionEditText = dialogView.findViewById<TextInputEditText>(R.id.institutionEditText)
        val startYearEditText = dialogView.findViewById<TextInputEditText>(R.id.startYearEditText)
        val endYearEditText = dialogView.findViewById<TextInputEditText>(R.id.endYearEditText)

        // Pre-fill if editing
        education?.let {
            degreeEditText.setText(it.degree)
            institutionEditText.setText(it.institution)
            startYearEditText.setText(if (it.startYear > 0) it.startYear.toString() else "")
            endYearEditText.setText(if (it.endYear > 0) it.endYear.toString() else "")
        }

        AlertDialog.Builder(this)
            .setTitle(if (education == null) "Add Education" else "Edit Education")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newEducation = Education(
                    degree = degreeEditText.text.toString().trim(),
                    institution = institutionEditText.text.toString().trim(),
                    startYear = startYearEditText.text.toString().toIntOrNull() ?: 0,
                    endYear = endYearEditText.text.toString().toIntOrNull() ?: 0
                )

                if (newEducation.degree.isNotBlank() && newEducation.institution.isNotBlank()) {
                    if (education == null) {
                        manualEducation.add(newEducation)
                    } else {
                        val index = manualEducation.indexOf(education)
                        if (index >= 0) manualEducation[index] = newEducation
                    }
                    refreshEducationView()
                } else {
                    Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Dialog for adding work experience
    private fun showWorkExperienceDialog(workExp: WorkExperience? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_work_experience, null)
        val titleEditText = dialogView.findViewById<TextInputEditText>(R.id.titleEditText)
        val companyEditText = dialogView.findViewById<TextInputEditText>(R.id.companyEditText)
        val descriptionEditText = dialogView.findViewById<TextInputEditText>(R.id.descriptionEditText)

        // Pre-fill if editing
        workExp?.let {
            titleEditText.setText(it.title)
            companyEditText.setText(it.company)
            descriptionEditText.setText(it.description)
        }

        AlertDialog.Builder(this)
            .setTitle(if (workExp == null) "Add Work Experience" else "Edit Work Experience")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newWorkExp = WorkExperience(
                    title = titleEditText.text.toString().trim(),
                    company = companyEditText.text.toString().trim(),
                    description = descriptionEditText.text.toString().trim()
                )

                if (newWorkExp.title.isNotBlank() && newWorkExp.company.isNotBlank()) {
                    if (workExp == null) {
                        manualWorkExperience.add(newWorkExp)
                    } else {
                        val index = manualWorkExperience.indexOf(workExp)
                        if (index >= 0) manualWorkExperience[index] = newWorkExp
                    }
                    refreshWorkExperienceView()
                } else {
                    Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Dialog for adding projects
    private fun showProjectDialog(project: Project? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_project, null)
        val titleEditText = dialogView.findViewById<TextInputEditText>(R.id.projectTitleEditText)
        val descriptionEditText = dialogView.findViewById<TextInputEditText>(R.id.projectDescriptionEditText)
        val linkEditText = dialogView.findViewById<TextInputEditText>(R.id.projectLinkEditText)
        val techStackEditText = dialogView.findViewById<TextInputEditText>(R.id.techStackEditText)
        val durationEditText = dialogView.findViewById<TextInputEditText>(R.id.durationEditText)

        // Pre-fill if editing
        project?.let {
            titleEditText.setText(it.title)
            descriptionEditText.setText(it.description)
            linkEditText.setText(it.link)
            techStackEditText.setText(it.techStack.joinToString(", "))
            durationEditText.setText(it.duration)
        }

        AlertDialog.Builder(this)
            .setTitle(if (project == null) "Add Project" else "Edit Project")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val techStackList = techStackEditText.text.toString()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }

                val newProject = Project(
                    title = titleEditText.text.toString().trim(),
                    description = descriptionEditText.text.toString().trim(),
                    link = linkEditText.text.toString().trim(),
                    techStack = techStackList,
                    duration = durationEditText.text.toString().trim()
                )

                if (newProject.title.isNotBlank()) {
                    if (project == null) {
                        manualProjects.add(newProject)
                    } else {
                        val index = manualProjects.indexOf(project)
                        if (index >= 0) manualProjects[index] = newProject
                    }
                    refreshProjectsView()
                } else {
                    Toast.makeText(this, "Please enter project title", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Dialog for adding accomplishments
    private fun showAccomplishmentDialog(accomplishment: Accomplishment? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_accomplishment, null)
        val titleEditText = dialogView.findViewById<TextInputEditText>(R.id.accomplishmentTitleEditText)
        val issuerEditText = dialogView.findViewById<TextInputEditText>(R.id.accomplishmentIssuerEditText)
        val descriptionEditText = dialogView.findViewById<TextInputEditText>(R.id.accomplishmentDescriptionEditText)
        val linkEditText = dialogView.findViewById<TextInputEditText>(R.id.accomplishmentLinkEditText)

        // Pre-fill if editing
        accomplishment?.let {
            titleEditText.setText(it.title)
            issuerEditText.setText(it.issuer)
            descriptionEditText.setText(it.description)
            linkEditText.setText(it.link)
        }

        AlertDialog.Builder(this)
            .setTitle(if (accomplishment == null) "Add Accomplishment" else "Edit Accomplishment")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newAccomplishment = Accomplishment(
                    title = titleEditText.text.toString().trim(),
                    issuer = issuerEditText.text.toString().trim(),
                    description = descriptionEditText.text.toString().trim(),
                    link = linkEditText.text.toString().trim()
                )

                if (newAccomplishment.title.isNotBlank()) {
                    if (accomplishment == null) {
                        manualAccomplishments.add(newAccomplishment)
                    } else {
                        val index = manualAccomplishments.indexOf(accomplishment)
                        if (index >= 0) manualAccomplishments[index] = newAccomplishment
                    }
                    refreshAccomplishmentsView()
                } else {
                    Toast.makeText(this, "Please enter accomplishment title", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Dialog for adding trainings
    private fun showTrainingDialog(training: Training? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_training, null)
        val titleEditText = dialogView.findViewById<TextInputEditText>(R.id.trainingTitleEditText)
        val issuerEditText = dialogView.findViewById<TextInputEditText>(R.id.trainingIssuerEditText)
        val descriptionEditText = dialogView.findViewById<TextInputEditText>(R.id.trainingDescriptionEditText)
        val dateEditText = dialogView.findViewById<TextInputEditText>(R.id.trainingDateEditText)

        // Pre-fill if editing
        training?.let {
            titleEditText.setText(it.title)
            issuerEditText.setText(it.issuer)
            descriptionEditText.setText(it.description)
            it.date?.let { date ->
                val dateFormat = SimpleDateFormat("MM/yyyy", Locale.getDefault())
                dateEditText.setText(dateFormat.format(date))
            }
        }

        // Date picker for training date
        dateEditText.setOnClickListener {
            showDatePicker { selectedDate ->
                val dateFormat = SimpleDateFormat("MM/yyyy", Locale.getDefault())
                dateEditText.setText(dateFormat.format(selectedDate))
            }
        }

        AlertDialog.Builder(this)
            .setTitle(if (training == null) "Add Training" else "Edit Training")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val dateString = dateEditText.text.toString().trim()
                val date = if (dateString.isNotBlank()) {
                    try {
                        SimpleDateFormat("MM/yyyy", Locale.getDefault()).parse(dateString)
                    } catch (e: Exception) {
                        Date()
                    }
                } else null

                val newTraining = Training(
                    title = titleEditText.text.toString().trim(),
                    issuer = issuerEditText.text.toString().trim(),
                    description = descriptionEditText.text.toString().trim(),
                    date = date
                )

                if (newTraining.title.isNotBlank() && newTraining.issuer.isNotBlank()) {
                    if (training == null) {
                        manualTrainings.add(newTraining)
                    } else {
                        val index = manualTrainings.indexOf(training)
                        if (index >= 0) manualTrainings[index] = newTraining
                    }
                    refreshTrainingsView()
                } else {
                    Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Dialog for adding extracurriculars
    private fun showExtracurricularDialog(extracurricular: Extracurricular? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_extracurricular, null)
        val roleEditText = dialogView.findViewById<TextInputEditText>(R.id.extracurricularRoleEditText)
        val organizationEditText = dialogView.findViewById<TextInputEditText>(R.id.extracurricularOrganizationEditText)
        val descriptionEditText = dialogView.findViewById<TextInputEditText>(R.id.extracurricularDescriptionEditText)

        // Pre-fill if editing
        extracurricular?.let {
            roleEditText.setText(it.role)
            organizationEditText.setText(it.organization)
            descriptionEditText.setText(it.description)
        }

        AlertDialog.Builder(this)
            .setTitle(if (extracurricular == null) "Add Extracurricular" else "Edit Extracurricular")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newExtracurricular = Extracurricular(
                    role = roleEditText.text.toString().trim(),
                    organization = organizationEditText.text.toString().trim(),
                    description = descriptionEditText.text.toString().trim()
                )

                if (newExtracurricular.role.isNotBlank() && newExtracurricular.organization.isNotBlank()) {
                    if (extracurricular == null) {
                        manualExtracurriculars.add(newExtracurricular)
                    } else {
                        val index = manualExtracurriculars.indexOf(extracurricular)
                        if (index >= 0) manualExtracurriculars[index] = newExtracurricular
                    }
                    refreshExtracurricularsView()
                } else {
                    Toast.makeText(this, "Please fill required fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDatePicker(onDateSelected: (Date) -> Unit) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                onDateSelected(calendar.time)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun populateEducationFromParsed(parsedEducation: ArrayList<Map<String, Any>>) {
        Log.d(TAG, "populateEducationFromParsed called with ${parsedEducation.size} entries")

        parsedEducation.forEach { edu ->
            try {
                val degree = edu["degree"]?.toString() ?: ""
                val institution = edu["institution"]?.toString() ?: ""
                val startYear = when (val year = edu["startYear"]) {
                    is Int -> year
                    is String -> year.toIntOrNull() ?: 0
                    is Double -> year.toInt()
                    is Number -> year.toInt()
                    else -> 0
                }
                val endYear = when (val year = edu["endYear"]) {
                    is Int -> year
                    is String -> year.toIntOrNull() ?: 0
                    is Double -> year.toInt()
                    is Number -> year.toInt()
                    else -> 0
                }

                val education = Education(
                    degree = degree,
                    institution = institution,
                    startYear = startYear,
                    endYear = endYear
                )

                Log.d(TAG, "Adding education: $education")

                if (education.degree.isNotBlank() && education.institution.isNotBlank()) {
                    manualEducation.add(education)
                } else {
                    Log.w(TAG, "Skipping empty education entry: $education")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing education entry: $edu", e)
            }
        }

        Log.d(TAG, "Total education entries added: ${manualEducation.size}")
        refreshEducationView()
    }

    private fun populateWorkExperienceFromParsed(parsedWorkExp: ArrayList<Map<String, Any>>) {
        Log.d(TAG, "populateWorkExperienceFromParsed called with ${parsedWorkExp.size} entries")

        parsedWorkExp.forEach { work ->
            try {
                val title = work["title"]?.toString() ?: ""
                val company = work["company"]?.toString() ?: ""
                val description = work["description"]?.toString() ?: ""

                val workExp = WorkExperience(
                    title = title,
                    company = company,
                    description = description,
                    startDate = when (val date = work["startDate"]) {
                        is Long -> Date(date)
                        is String -> try {
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)
                        } catch (e: Exception) { null }
                        else -> null
                    },
                    endDate = when (val date = work["endDate"]) {
                        is Long -> Date(date)
                        is String -> try {
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)
                        } catch (e: Exception) { null }
                        else -> null
                    }
                )

                Log.d(TAG, "Adding work experience: $workExp")

                if (workExp.title.isNotBlank() && workExp.company.isNotBlank()) {
                    manualWorkExperience.add(workExp)
                } else {
                    Log.w(TAG, "Skipping empty work experience entry: $workExp")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing work experience entry: $work", e)
            }
        }

        Log.d(TAG, "Total work experience entries added: ${manualWorkExperience.size}")
        refreshWorkExperienceView()
    }


    private fun populateProjectsFromParsed(parsedProjects: ArrayList<Map<String, Any>>) {
        Log.d(TAG, "populateProjectsFromParsed called with ${parsedProjects.size} entries")

        parsedProjects.forEach { proj ->
            try {
                val title = proj["title"]?.toString() ?: ""
                val description = proj["description"]?.toString() ?: ""
                val link = proj["link"]?.toString() ?: ""
                val duration = proj["duration"]?.toString() ?: ""

                val techStack = when (val tech = proj["techStack"]) {
                    is List<*> -> tech.mapNotNull { it?.toString() }
                    is String -> tech.split(",").map { it.trim() }
                    else -> emptyList()
                }

                val project = Project(
                    title = title,
                    description = description,
                    link = link,
                    techStack = techStack,
                    duration = duration
                )

                Log.d(TAG, "Adding project: $project")

                if (project.title.isNotBlank()) {
                    manualProjects.add(project)
                } else {
                    Log.w(TAG, "Skipping empty project entry: $project")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing project entry: $proj", e)
            }
        }

        Log.d(TAG, "Total project entries added: ${manualProjects.size}")
        refreshProjectsView()
    }

    private fun populateAccomplishmentsFromParsed(parsedAccomplishments: ArrayList<Map<String, Any>>) {
        parsedAccomplishments.forEach { acc ->
            try {
                val accomplishment = Accomplishment(
                    title = acc["title"]?.toString() ?: "",
                    issuer = acc["issuer"]?.toString() ?: "",
                    description = acc["description"]?.toString() ?: "",
                    link = acc["link"]?.toString() ?: ""
                )
                if (accomplishment.title.isNotBlank()) {
                    manualAccomplishments.add(accomplishment)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing accomplishment entry", e)
            }
        }
        refreshAccomplishmentsView()
    }

    private fun populateTrainingsFromParsed(parsedTrainings: ArrayList<Map<String, Any>>) {
        parsedTrainings.forEach { train ->
            try {
                val training = Training(
                    title = train["title"]?.toString() ?: "",
                    issuer = train["issuer"]?.toString() ?: "",
                    description = train["description"]?.toString() ?: "",
                    date = (train["date"] as? Number)?.let { Date(it.toLong()) }
                )
                if (training.title.isNotBlank() && training.issuer.isNotBlank()) {
                    manualTrainings.add(training)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing training entry", e)
            }
        }
        refreshTrainingsView()
    }

    private fun populateExtracurricularsFromParsed(parsedExtracurriculars: ArrayList<Map<String, Any>>) {
        parsedExtracurriculars.forEach { extra ->
            try {
                val extracurricular = Extracurricular(
                    role = extra["role"]?.toString() ?: "",
                    organization = extra["organization"]?.toString() ?: "",
                    description = extra["description"]?.toString() ?: ""
                )
                if (extracurricular.role.isNotBlank() && extracurricular.organization.isNotBlank()) {
                    manualExtracurriculars.add(extracurricular)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error parsing extracurricular entry", e)
            }
        }
        refreshExtracurricularsView()
    }

    private fun refreshEducationView() {
        try {
            binding.educationContainer?.removeAllViews()
            manualEducation.forEachIndexed { index, education ->
                val itemView = createEducationItemView(education, index)
                binding.educationContainer?.addView(itemView)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Education container not found in layout")
        }
    }

    private fun refreshWorkExperienceView() {
        try {
            binding.workExperienceContainer?.removeAllViews()
            manualWorkExperience.forEachIndexed { index, workExp ->
                val itemView = createWorkExperienceItemView(workExp, index)
                binding.workExperienceContainer?.addView(itemView)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Work experience container not found in layout")
        }
    }

    private fun refreshProjectsView() {
        try {
            binding.projectsContainer?.removeAllViews()
            manualProjects.forEachIndexed { index, project ->
                val itemView = createProjectItemView(project, index)
                binding.projectsContainer?.addView(itemView)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Projects container not found in layout")
        }
    }

    private fun refreshAccomplishmentsView() {
        try {
            binding.accomplishmentsContainer?.removeAllViews()
            manualAccomplishments.forEachIndexed { index, accomplishment ->
                val itemView = createAccomplishmentItemView(accomplishment, index)
                binding.accomplishmentsContainer?.addView(itemView)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Accomplishments container not found in layout")
        }
    }

    private fun refreshTrainingsView() {
        try {
            binding.trainingsContainer?.removeAllViews()
            manualTrainings.forEachIndexed { index, training ->
                val itemView = createTrainingItemView(training, index)
                binding.trainingsContainer?.addView(itemView)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Trainings container not found in layout")
        }
    }

    private fun refreshExtracurricularsView() {
        try {
            binding.extracurricularsContainer?.removeAllViews()
            manualExtracurriculars.forEachIndexed { index, extracurricular ->
                val itemView = createExtracurricularItemView(extracurricular, index)
                binding.extracurricularsContainer?.addView(itemView)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Extracurriculars container not found in layout")
        }
    }

    private fun createEducationItemView(education: Education, index: Int): MaterialCardView {
        val cardView = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            cardElevation = 4f
            radius = 8f
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val titleText = android.widget.TextView(this).apply {
            text = "${education.degree} - ${education.institution}"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val yearText = android.widget.TextView(this).apply {
            text = "${education.startYear} - ${education.endYear}"
            textSize = 14f
            setTextColor(resources.getColor(android.R.color.darker_gray, null))
        }

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
        }

        val editButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Edit"
            setOnClickListener { showEducationDialog(education) }
        }

        val deleteButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Delete"
            setOnClickListener {
                manualEducation.removeAt(index)
                refreshEducationView()
            }
        }

        buttonLayout.addView(editButton)
        buttonLayout.addView(deleteButton)

        content.addView(titleText)
        content.addView(yearText)
        content.addView(buttonLayout)
        cardView.addView(content)

        return cardView
    }

    private fun createWorkExperienceItemView(workExp: WorkExperience, index: Int): MaterialCardView {
        val cardView = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            cardElevation = 4f
            radius = 8f
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val titleText = android.widget.TextView(this).apply {
            text = "${workExp.title} at ${workExp.company}"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val descriptionText = android.widget.TextView(this).apply {
            text = workExp.description
            textSize = 14f
            maxLines = 2
        }

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
        }

        val editButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Edit"
            setOnClickListener { showWorkExperienceDialog(workExp) }
        }

        val deleteButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Delete"
            setOnClickListener {
                manualWorkExperience.removeAt(index)
                refreshWorkExperienceView()
            }
        }

        buttonLayout.addView(editButton)
        buttonLayout.addView(deleteButton)

        content.addView(titleText)
        content.addView(descriptionText)
        content.addView(buttonLayout)
        cardView.addView(content)

        return cardView
    }

    private fun createProjectItemView(project: Project, index: Int): MaterialCardView {
        val cardView = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            cardElevation = 4f
            radius = 8f
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val titleText = android.widget.TextView(this).apply {
            text = project.title
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val techStackText = android.widget.TextView(this).apply {
            text = "Tech: ${project.techStack.joinToString(", ")}"
            textSize = 14f
            setTextColor(resources.getColor(android.R.color.darker_gray, null))
        }

        val descriptionText = android.widget.TextView(this).apply {
            text = project.description
            textSize = 14f
            maxLines = 2
        }

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
        }

        val editButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Edit"
            setOnClickListener { showProjectDialog(project) }
        }

        val deleteButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Delete"
            setOnClickListener {
                manualProjects.removeAt(index)
                refreshProjectsView()
            }
        }

        buttonLayout.addView(editButton)
        buttonLayout.addView(deleteButton)

        content.addView(titleText)
        content.addView(techStackText)
        content.addView(descriptionText)
        content.addView(buttonLayout)
        cardView.addView(content)

        return cardView
    }

    private fun createAccomplishmentItemView(accomplishment: Accomplishment, index: Int): MaterialCardView {
        val cardView = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            cardElevation = 4f
            radius = 8f
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val titleText = android.widget.TextView(this).apply {
            text = accomplishment.title
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val issuerText = android.widget.TextView(this).apply {
            text = "Issued by: ${accomplishment.issuer}"
            textSize = 14f
            setTextColor(resources.getColor(android.R.color.darker_gray, null))
        }

        val descriptionText = android.widget.TextView(this).apply {
            text = accomplishment.description
            textSize = 14f
            maxLines = 2
        }

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
        }

        val editButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Edit"
            setOnClickListener { showAccomplishmentDialog(accomplishment) }
        }

        val deleteButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Delete"
            setOnClickListener {
                manualAccomplishments.removeAt(index)
                refreshAccomplishmentsView()
            }
        }

        buttonLayout.addView(editButton)
        buttonLayout.addView(deleteButton)

        content.addView(titleText)
        content.addView(issuerText)
        content.addView(descriptionText)
        cardView.addView(content)

        return cardView
    }

    private fun createTrainingItemView(training: Training, index: Int): MaterialCardView {
        val cardView = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            cardElevation = 4f
            radius = 8f
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val titleText = android.widget.TextView(this).apply {
            text = training.title
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val issuerText = android.widget.TextView(this).apply {
            text = "From: ${training.issuer}"
            textSize = 14f
            setTextColor(resources.getColor(android.R.color.darker_gray, null))
        }

        val dateText = android.widget.TextView(this).apply {
            val dateFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())
            text = training.date?.let { "Completed: ${dateFormat.format(it)}" } ?: ""
            textSize = 14f
            setTextColor(resources.getColor(android.R.color.darker_gray, null))
        }

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
        }

        val editButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Edit"
            setOnClickListener { showTrainingDialog(training) }
        }

        val deleteButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Delete"
            setOnClickListener {
                manualTrainings.removeAt(index)
                refreshTrainingsView()
            }
        }

        buttonLayout.addView(editButton)
        buttonLayout.addView(deleteButton)

        content.addView(titleText)
        content.addView(issuerText)
        content.addView(dateText)
        content.addView(buttonLayout)
        cardView.addView(content)

        return cardView
    }

    private fun createExtracurricularItemView(extracurricular: Extracurricular, index: Int): MaterialCardView {
        val cardView = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            cardElevation = 4f
            radius = 8f
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val titleText = android.widget.TextView(this).apply {
            text = "${extracurricular.role} at ${extracurricular.organization}"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val descriptionText = android.widget.TextView(this).apply {
            text = extracurricular.description
            textSize = 14f
            maxLines = 2
        }

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
        }

        val editButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Edit"
            setOnClickListener { showExtracurricularDialog(extracurricular) }
        }

        val deleteButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Delete"
            setOnClickListener {
                manualExtracurriculars.removeAt(index)
                refreshExtracurricularsView()
            }
        }

        buttonLayout.addView(editButton)
        buttonLayout.addView(deleteButton)

        content.addView(titleText)
        content.addView(descriptionText)
        cardView.addView(content)

        return cardView
    }

    private fun validateForm(): Boolean {
        if (binding.genderDropdown.editText?.text.isNullOrEmpty()) {
            Toast.makeText(this, "Please select your gender", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun updateGender() {
        val gender = binding.genderDropdown.editText?.text.toString().trim()
        Log.d(TAG, "Updating gender to: $gender")

        if (authToken.isNullOrEmpty()) {
            Toast.makeText(this, "Auth token is missing! Please log in again.", Toast.LENGTH_SHORT).show()
            redirectToLogin()
            return
        }

        val bearerToken = if (authToken!!.startsWith("Bearer ")) {
            authToken!!
        } else {
            "Bearer $authToken"
        }

        val genderRequest = GenderRequest(gender)

        apiService.updateGender(bearerToken, genderRequest).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful && response.body()?.success == true) {
                    updateProfile()
                } else {
                    val errorMessage = response.errorBody()?.string() ?: "Unknown error"
                    Toast.makeText(this@ProfileSetupActivity, "Gender update failed: $errorMessage", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                Toast.makeText(this@ProfileSetupActivity, "Gender update failed: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateProfile() {
        if (authToken.isNullOrEmpty()) {
            Toast.makeText(this, "Auth token is missing!", Toast.LENGTH_SHORT).show()
            redirectToLogin()
            return
        }

        // Get skills from text field
        val skillsText = try {
            binding.skillsEditText?.text.toString()
        } catch (e: Exception) {
            ""
        }
        val skillsList = if (skillsText.isNotBlank()) {
            skillsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            parsedData?.skills ?: emptyList()
        }

        // Build portfolio links according to MongoDB schema
        val portfolioLinks = mutableMapOf<String, String>()
        val otherLinks = mutableListOf<String>()
        try {
            binding.githubEditText?.text.toString().takeIf { it.isNotBlank() }?.let { portfolioLinks["github"] = it }
            binding.linkedinEditText?.text.toString().takeIf { it.isNotBlank() }?.let { portfolioLinks["linkedin"] = it }
            binding.portfolioEditText?.text.toString().takeIf { it.isNotBlank() }?.let { otherLinks.add(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Portfolio fields not found in layout")
        }

        val portfolio = if (portfolioLinks.isNotEmpty() || otherLinks.isNotEmpty()) {
            portfolioLinks.plus("otherLinks" to otherLinks) as Map<String, Any>
        } else null

        // Get basic info
        val phone = try {
            binding.phoneEditText?.text.toString().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        } ?: parsedData?.phone

        val location = try {
            binding.locationEditText?.text.toString().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        } ?: parsedData?.location

        val careerObjective = try {
            binding.careerObjectiveEditText?.text.toString().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        } ?: parsedData?.careerObjective

        // Create a single list for each category, combining manual and parsed data.
        val combinedEducation = manualEducation.map { it.toMap() } + (parsedData?.education ?: emptyList())
        val combinedWorkExperience = manualWorkExperience.map { it.toMap() } + (parsedData?.workExperience ?: emptyList())
        val combinedProjects = manualProjects.map { it.toMap() } + (parsedData?.projects ?: emptyList())
        val combinedAccomplishments = manualAccomplishments.map { it.toMap() } + (parsedData?.accomplishments ?: emptyList())
        val combinedTrainings = manualTrainings.map { it.toMap() } + (parsedData?.trainings ?: emptyList())
        val combinedExtracurriculars = manualExtracurriculars.map { it.toMap() } + (parsedData?.extracurriculars ?: emptyList())

        // Create profile combining all data
        val profile = StudentProfile(
            phone = phone,
            location = location,
            careerObjective = careerObjective,
            portfolio = portfolio,
            education = ArrayList(combinedEducation),
            workExperience = ArrayList(combinedWorkExperience),
            projects = ArrayList(combinedProjects),
            skills = ArrayList(skillsList),
            accomplishments = ArrayList(combinedAccomplishments),
            extracurriculars = ArrayList(combinedExtracurriculars),
            trainings = ArrayList(combinedTrainings)
        )

        Log.d(TAG, "Profile update request: ${gson.toJson(profile)}")

        val bearerToken = if (authToken!!.startsWith("Bearer ")) {
            authToken!!
        } else {
            "Bearer $authToken"
        }

        apiService.updateProfile(bearerToken, profile).enqueue(object : Callback<StudentProfile> {
            override fun onResponse(call: Call<StudentProfile>, response: Response<StudentProfile>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@ProfileSetupActivity, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                    showProfileSummary(response.body()!!)
                    // Navigate to the next activity
                    val intent = Intent(this@ProfileSetupActivity, ProfileReviewActivity::class.java).apply {
                        putExtra("token", authToken)
                    }
                    startActivity(intent)
                    finish()
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    Log.e(TAG, "Profile update failed - Response: $errorBody")
                    Toast.makeText(this@ProfileSetupActivity, "Profile update failed: $errorBody", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<StudentProfile>, t: Throwable) {
                Log.e(TAG, "Profile update network error", t)
                Toast.makeText(this@ProfileSetupActivity, "Profile update failed: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // Helper function to convert data class to map for a combined list
    private fun Education.toMap(): Map<String, Any> = mapOf(
        "degree" to this.degree,
        "institution" to this.institution,
        "startYear" to this.startYear,
        "endYear" to this.endYear
    )

    private fun WorkExperience.toMap(): Map<String, Any> = mapOf(
        "title" to this.title,
        "company" to this.company,
        "description" to this.description,
        "startDate" to (this.startDate?.time ?: System.currentTimeMillis()),
        "endDate" to (this.endDate?.time ?: System.currentTimeMillis())
    )

    private fun Project.toMap(): Map<String, Any> = mapOf(
        "title" to this.title,
        "description" to this.description,
        "link" to this.link,
        "techStack" to this.techStack,
        "duration" to this.duration
    )

    private fun Accomplishment.toMap(): Map<String, Any> = mapOf(
        "title" to this.title,
        "issuer" to this.issuer,
        "description" to this.description,
        "link" to this.link
    )

    private fun Training.toMap(): Map<String, Any> = mapOf(
        "title" to this.title,
        "issuer" to this.issuer,
        "description" to this.description,
        "date" to (this.date?.time ?: System.currentTimeMillis())
    )

    private fun Extracurricular.toMap(): Map<String, Any> = mapOf(
        "role" to this.role,
        "organization" to this.organization,
        "description" to this.description
    )

    private fun showProfileSummary(profile: StudentProfile) {
        val summary = buildString {
            append("Profile created successfully!\n\n")
            append("Data saved:\n")
            profile.phone?.let { append("Phone: $it\n") }
            profile.location?.let { append("Location: $it\n") }
            profile.careerObjective?.let { append("Career Objective: Set\n") }
            if (profile.education.isNotEmpty()) append("Education: ${profile.education.size} entries\n")
            if (profile.workExperience.isNotEmpty()) append("Work Experience: ${profile.workExperience.size} entries\n")
            if (profile.projects.isNotEmpty()) append("Projects: ${profile.projects.size} entries\n")
            if (profile.skills.isNotEmpty()) append("Skills: ${profile.skills.size} skills\n")
            if (profile.accomplishments.isNotEmpty()) append("Accomplishments: ${profile.accomplishments.size} entries\n")
            if (profile.trainings.isNotEmpty()) append("Trainings: ${profile.trainings.size} entries\n")
            if (profile.extracurriculars.isNotEmpty()) append("Extracurriculars: ${profile.extracurriculars.size} entries\n")
        }

        Toast.makeText(this, summary, Toast.LENGTH_LONG).show()
    }

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}