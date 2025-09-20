package com.nexuslink.user

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.Toast
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
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
import com.nexuslink.user.data.Project
import com.nexuslink.user.data.StudentProfile
import com.nexuslink.user.data.Training
import com.nexuslink.user.data.WorkExperience
import com.nexuslink.user.databinding.ActivityProfileSetupBinding
import com.nexuslink.user.network.ApiService
import com.nexuslink.user.data.GenderRequest
import com.nexuslink.user.network.ParserApiService
import com.nexuslink.user.network.RetrofitInstance
import com.nexuslink.user.utils.AuthManager
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import android.app.Activity
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import com.nexuslink.user.ParsingData.Content
import com.nexuslink.user.ParsingData.GeminiRequest
import com.nexuslink.user.ParsingData.GeminiResponse
import com.nexuslink.user.ParsingData.ParsedResumeData
import com.nexuslink.user.ParsingData.Part
import com.nexuslink.user.network.*
import java.io.InputStream
import java.util.*

class ProfileSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileSetupBinding
    private lateinit var authManager: AuthManager
    private lateinit var apiService: ApiService
    private val gson = Gson()
    private lateinit var parserApiService: ParserApiService
    private var authToken: String? = null
    private val manualEducation = mutableListOf<Education>()
    private val manualWorkExperience = mutableListOf<WorkExperience>()
    private val manualProjects = mutableListOf<Project>()
    private val manualAccomplishments = mutableListOf<Accomplishment>()
    private val manualTrainings = mutableListOf<Training>()
    private val manualExtracurriculars = mutableListOf<Extracurricular>()

    companion object {
        private const val TAG = "ProfileSetupActivity"
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleFileSelection(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)
        apiService = RetrofitInstance.api
        parserApiService = ParserRetrofitInstance.api


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
        setupDynamicSections()
        setupButtons()
        setupResumeUpload()
    }

    private fun setupResumeUpload() {
        binding.uploadResumeButton.setOnClickListener {
            openFilePicker()
        }
    }

    private fun handleFileSelection(uri: Uri) {
        binding.parseStatusText.text = "Reading file..."
        try {
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Toast.makeText(this, "Failed to open file.", Toast.LENGTH_SHORT).show()
                return
            }
            Thread {
                val extractedText = extractTextFromPdf(inputStream)
                runOnUiThread {
                    if (extractedText.isNullOrBlank()) {
                        binding.parseStatusText.text = "Could not extract text from PDF."
                        Toast.makeText(this, "Failed to read PDF content.", Toast.LENGTH_SHORT).show()
                    } else {
                        binding.parseStatusText.text = "Parsing resume with AI..."
                        parseResumeWithGemini(extractedText)
                    }
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "File handling failed", e)
            Toast.makeText(this, "Error processing file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractTextFromPdf(inputStream: InputStream): String? {
        return try {
            val reader = PdfReader(inputStream)
            val numPages = reader.numberOfPages
            val stringBuilder = StringBuilder()
            for (i in 1..numPages) {
                stringBuilder.append(PdfTextExtractor.getTextFromPage(reader, i))
            }
            reader.close()
            stringBuilder.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting PDF text with iTextG", e)
            null
        }
    }

    private fun parseResumeWithGemini(resumeText: String) {
        val prompt = """
        Analyze the following resume text and extract the information into a clean JSON object.
        The JSON object must strictly follow this structure.
        For fields you cannot determine, use null or default values like 0 or empty lists.
        {
          "phone": "string (or null)",
          "location": "string (city and state/country, or null)",
          "career_objective": "string (a 1-2 sentence summary, or null)",
          "portfolio": { "github": "string (or null)", "linkedin": "string (or null)", "otherLinks": [] },
          "skills": ["string"],
          "education": [{ "degree": "string", "institution": "string", "startYear": 0, "endYear": 0 }],
          "work_experience": [{ "title": "string", "company": "string", "description": "string (a brief summary)", "startDate": null, "endDate": null }],
          "projects": [{ "title": "string", "description": "string", "link": "string (or null)", "techStack": ["string"], "duration": "string (or null)" }],
          "trainings": [{ "title": "string", "issuer": "string", "description": "string (or null)", "date": null }]
        }
        Do not include any text, explanations, or markdown formatting before or after the JSON object. Provide only the raw JSON.

        Resume Text:
        ---
        $resumeText
        """.trimIndent()

        val endpoint = "v1beta/models/gemini-1.5-flash-latest:generateContent"


        val fullUrl = ParserRetrofitInstance.getUrlWithKey(endpoint)
        val request = GeminiRequest(contents = listOf(Content(parts = listOf(Part(text = prompt)))))

        parserApiService.getParsedResume(fullUrl, request).enqueue(object : Callback<GeminiResponse> {
            override fun onResponse(call: Call<GeminiResponse>, response: Response<GeminiResponse>) {
                if (response.isSuccessful) {
                    val jsonText = response.body()?.candidates?.firstOrNull()
                        ?.content?.parts?.firstOrNull()?.text
                        ?.replace("```json", "")?.replace("```", "")?.trim()

                    if (jsonText != null) {
                        try {
                            val parsedData = gson.fromJson(jsonText, ParsedResumeData::class.java)
                            populateUiWithParsedData(parsedData)
                            binding.parseStatusText.text = "Parsing complete! Review the details."
                            Toast.makeText(this@ProfileSetupActivity, "Resume parsed successfully!", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.e(TAG, "JSON parsing from Gemini failed", e)
                            binding.parseStatusText.text = "AI response was not valid JSON."
                        }
                    } else {
                        binding.parseStatusText.text = "AI returned an empty response."
                    }
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    Log.e(TAG, "Gemini API Error: $errorBody")
                    binding.parseStatusText.text = "Parsing failed. Please check API key."
                }
            }
            override fun onFailure(call: Call<GeminiResponse>, t: Throwable) {
                Log.e(TAG, "Gemini network call failed", t)
                binding.parseStatusText.text = "Network error. Could not reach AI service."
            }
        })
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*" // Allow any file type, but we'll check for PDF/DOCX
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        }
        filePickerLauncher.launch(intent)
    }

    private fun populateUiWithParsedData(data: ParsedResumeData) {
        // Basic Info
        data.phone?.let { binding.phoneEditText.setText(it) }
        data.location?.let { binding.locationEditText.setText(it) }
        data.careerObjective?.let { binding.careerObjectiveEditText.setText(it) }

        // Professional Links from the nested portfolio object
        data.portfolio?.github?.let { binding.githubEditText.setText(it) }
        data.portfolio?.linkedin?.let { binding.linkedinEditText.setText(it) }
        data.portfolio?.otherLinks?.firstOrNull()?.let { binding.portfolioEditText.setText(it) }

        // Skills
        data.skills?.let {
            if (it.isNotEmpty()) {
                binding.skillsEditText.setText(it.joinToString(", "))
            }
        }

        // Education
        manualEducation.clear()
        data.education?.let {
            manualEducation.addAll(it)
        }
        refreshEducationView()

        // Work Experience
        manualWorkExperience.clear()
        data.workExperience?.let {
            manualWorkExperience.addAll(it)
        }
        refreshWorkExperienceView()

        // Projects
        manualProjects.clear()
        data.projects?.let {
            manualProjects.addAll(it)
        }
        refreshProjectsView()

        // Trainings (Certifications)
        manualTrainings.clear()
        data.trainings?.let {
            manualTrainings.addAll(it)
        }
        refreshTrainingsView()
    }


    private fun setupGenderDropdown() {
        val genders = listOf("male", "female", "other")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders)
        (binding.genderDropdown.editText as? AutoCompleteTextView)?.setAdapter(adapter)
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

        // Accomplishments section
        binding.addAccomplishmentButton.setOnClickListener {
            showAccomplishmentDialog()
        }

        // Trainings section
        binding.addTrainingButton.setOnClickListener {
            showTrainingDialog()
        }

        // Extracurriculars section
        binding.addExtracurricularButton.setOnClickListener {
            showExtracurricularDialog()
        }
    }

    private fun setupButtons() {
        binding.continueButton.setOnClickListener {
            if (validateForm()) {
                updateGender()
            }
        }

        binding.skipButton.setOnClickListener {
            updateGender()
        }
    }

    private fun showEducationDialog(education: Education? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_education, null)
        val degreeEditText = dialogView.findViewById<TextInputEditText>(R.id.degreeEditText)
        val institutionEditText = dialogView.findViewById<TextInputEditText>(R.id.institutionEditText)
        val startYearEditText = dialogView.findViewById<TextInputEditText>(R.id.startYearEditText)
        val endYearEditText = dialogView.findViewById<TextInputEditText>(R.id.endYearEditText)

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
            // Convert Long back to a formatted Date string for display
            it.date?.let { timestamp ->
                val dateFormat = SimpleDateFormat("MM/yyyy", Locale.getDefault())
                dateEditText.setText(dateFormat.format(Date(timestamp)))
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

                // THIS IS THE FIX: Convert the parsed Date object to a Long timestamp
                val dateTimestamp: Long? = if (dateString.isNotBlank()) {
                    try {
                        SimpleDateFormat("MM/yyyy", Locale.getDefault()).parse(dateString)?.time
                    } catch (e: Exception) {
                        null
                    }
                } else null

                val newTraining = Training(
                    title = titleEditText.text.toString().trim(),
                    issuer = issuerEditText.text.toString().trim(),
                    description = descriptionEditText.text.toString().trim(),
                    date = dateTimestamp // Now the type is correct (Long?)
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

        // Fixed: Use the correct Material Design attribute
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

        // Fixed: Use the correct Material Design attribute
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

        // Fixed: Use the correct Material Design attribute
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

        // Fixed: Use the correct Material Design attribute
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
        content.addView(buttonLayout)
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

        // Fixed: Use the correct Material Design attribute
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

        // Fixed: Use the correct Material Design attribute
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
        content.addView(buttonLayout)
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
            emptyList()
        }

        // Build portfolio links according to MongoDB schema
        val portfolioLinks = mutableMapOf<String, Any>()
        val otherLinks = mutableListOf<String>()

        try {
            binding.githubEditText?.text.toString().takeIf { it.isNotBlank() }?.let {
                portfolioLinks["github"] = it
            }
            binding.linkedinEditText?.text.toString().takeIf { it.isNotBlank() }?.let {
                portfolioLinks["linkedin"] = it
            }
            binding.portfolioEditText?.text.toString().takeIf { it.isNotBlank() }?.let {
                otherLinks.add(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Portfolio fields not found in layout")
        }

        // Add otherLinks to portfolio if not empty
        if (otherLinks.isNotEmpty()) {
            portfolioLinks["otherLinks"] = otherLinks
        }

        val portfolio = if (portfolioLinks.isNotEmpty()) portfolioLinks else null

        // Get basic info
        val phone = try {
            binding.phoneEditText?.text.toString().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }

        val location = try {
            binding.locationEditText?.text.toString().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }

        val careerObjective = try {
            binding.careerObjectiveEditText?.text.toString().takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }

        // Create profile with manual entries only
        val profile = StudentProfile(
            phone = phone,
            location = location,
            careerObjective = careerObjective,
            portfolio = portfolio,
            education = ArrayList(manualEducation.map { it.toMap() }),
            workExperience = ArrayList(manualWorkExperience.map { it.toMap() }),
            projects = ArrayList(manualProjects.map { it.toMap() }),
            skills = ArrayList(skillsList),
            accomplishments = ArrayList(manualAccomplishments.map { it.toMap() }),
            extracurriculars = ArrayList(manualExtracurriculars.map { it.toMap() }),
            trainings = ArrayList(manualTrainings.map { it.toMap() })
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
        "startDate" to (this.startDate ?: System.currentTimeMillis()),
        "endDate" to (this.endDate ?: System.currentTimeMillis())
    )

    // REPLACE THIS FUNCTION
    private fun Project.toMap(): Map<String, Any> = mapOf(
        "title" to this.title,
        "description" to this.description,
        // The fix is to provide a default value if link is null
        "link" to (this.link ?: ""),
        "techStack" to this.techStack,
        // The fix is to provide a default value if duration is null
        "duration" to (this.duration ?: "")
    )

    private fun Accomplishment.toMap(): Map<String, Any> = mapOf(
        "title" to this.title,
        "issuer" to this.issuer,
        "description" to this.description,
        "link" to this.link
    )

    // REPLACE THIS FUNCTION
    private fun Training.toMap(): Map<String, Any> = mapOf(
        "title" to this.title,
        "issuer" to this.issuer,
        // The fix is to provide a default value if description is null
        "description" to (this.description ?: ""),
        "date" to (this.date ?: System.currentTimeMillis())
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