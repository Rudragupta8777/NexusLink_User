package com.nexuslink.user.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.nexuslink.user.data.ParsedResumeData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.util.regex.Pattern

class ImprovedResumeParser(private val context: Context) {

    companion object {
        private const val TAG = "ImprovedResumeParser"

        // Regex patterns for parsing
        private val EMAIL_PATTERN =
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
        private val PHONE_PATTERN =
            Pattern.compile("(?:\\+?\\d{1,3}[-.\\s]?)?(?:\\(\\d{1,4}\\)[-.\\s]?)?\\d{1,4}[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,9}")
        private val GITHUB_PATTERN =
            Pattern.compile("github\\.com/([\\w-]+)", Pattern.CASE_INSENSITIVE)
        private val LINKEDIN_PATTERN =
            Pattern.compile("linkedin\\.com/in/([\\w-]+)", Pattern.CASE_INSENSITIVE)
        private val YEAR_PATTERN = Pattern.compile("(19|20)\\d{2}")

        // Education keywords
        private val EDUCATION_KEYWORDS = listOf(
            "education", "academic", "qualification", "degree", "bachelor", "master",
            "phd", "doctorate", "diploma", "certificate", "b.s", "b.a", "m.s", "m.a",
            "m.b.a", "university", "college", "institute", "school", "vit", "vellore"
        )

        // Experience keywords
        private val EXPERIENCE_KEYWORDS = listOf(
            "experience", "work", "employment", "career", "professional", "internship",
            "employment history", "work history", "professional experience"
        )

        // Project keywords
        private val PROJECT_KEYWORDS = listOf(
            "project", "portfolio", "personal project", "academic project"
        )

        // Skills keywords
        private val SKILL_KEYWORDS = listOf(
            "skill", "technical skill", "competence", "expertise", "technology", "tool"
        )

        // Tech skills list
        private val TECH_SKILLS = listOf(
            "Python", "Go", "Java", "C", "C++", "Kotlin", "JavaScript", "HTML", "CSS",
            "Android SDK", "Google Maps SDK", "Node.js", "Express.js", "Firebase", "MongoDB",
            "GitHub", "Android Studio", "Postman", "Vercel", "Firebase Console", "TensorFlow",
            "Flask", "FastAPI", "ESP32", "Bluetooth", "Cloudinary", "TensorFlow.js"
        )
    }

    suspend fun parseResumeAdvanced(uri: Uri): ParsedResumeData? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting resume parsing")

            // First try Affinda for better parsing
            val affindaParser = AffindaParser(context)
            val affindaResult = affindaParser.parseResumeWithAffinda(uri)

            if (affindaResult != null && isValidParseResult(affindaResult)) {
                Log.d(TAG, "Successfully parsed resume with Affinda")
                return@withContext affindaResult
            }

            // Fallback to simple text extraction if Affinda fails
            Log.w(TAG, "Affinda parsing failed, falling back to text extraction")
            val fallbackResult = parseWithSimpleTextExtraction(uri)

            if (fallbackResult != null && isValidParseResult(fallbackResult)) {
                Log.d(TAG, "Successfully parsed resume with text extraction")
                return@withContext fallbackResult
            }

            Log.w(TAG, "Both parsing methods failed")
            return@withContext null

        } catch (e: Exception) {
            Log.e(TAG, "Resume parsing failed", e)
            null
        }
    }

    private fun parseWithSimpleTextExtraction(uri: Uri): ParsedResumeData? {
        return try {
            Log.d(TAG, "Starting text extraction")
            val text = extractTextFromPdf(uri)
            Log.d(TAG, "Extracted text length: ${text.length}")

            if (text.isNotEmpty()) {
                val result = parseTextContent(text)
                Log.d(TAG, "Text parsing result: $result")
                result
            } else {
                Log.w(TAG, "No text extracted from PDF")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Text extraction failed", e)
            null
        }
    }

    private fun extractTextFromPdf(uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val text = extractTextFromPdfStream(inputStream)
                Log.d(TAG, "PDF text extraction successful, length: ${text.length}")
                text
            } ?: ""
        } catch (e: IOException) {
            Log.e(TAG, "Failed to extract text from PDF", e)
            ""
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in PDF extraction", e)
            ""
        }
    }

    private fun extractTextFromPdfStream(inputStream: InputStream): String {
        // This is a simplified approach - in production, use a proper PDF library like iText or PDFBox
        return try {
            val text = inputStream.bufferedReader().use { it.readText() }
            Log.d(TAG, "Text stream extraction successful")
            text
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract text from PDF stream", e)
            ""
        }
    }

    private fun parseTextContent(text: String): ParsedResumeData {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        Log.d(TAG, "Parsing ${lines.size} lines of text")

        val name = extractName(lines)
        val email = extractEmail(text)
        val phone = extractPhone(text)
        val location = extractLocation(lines)
        val careerObjective = extractCareerObjective(lines)
        val education = extractEducation(lines)
        val workExperience = extractWorkExperience(lines)
        val projects = extractProjects(lines, text)
        val skills = extractSkills(text, lines)
        val accomplishments = extractAccomplishments(lines)
        val trainings = extractTrainings(lines)
        val extracurriculars = extractExtracurriculars(lines)
        val portfolio = extractPortfolioLinks(text)

        Log.d(
            TAG,
            "Parsed data - Name: $name, Email: $email, Phone: $phone, Education: ${education.size}, Skills: ${skills.size}"
        )

        return ParsedResumeData(
            name = name,
            email = email,
            phone = phone,
            location = location,
            careerObjective = careerObjective,
            education = education,
            workExperience = workExperience,
            projects = projects,
            skills = skills,
            accomplishments = accomplishments,
            trainings = trainings,
            extracurriculars = extracurriculars,
            portfolio = portfolio
        )
    }

    private fun extractName(lines: List<String>): String? {
        // Look for name in first few lines
        for (i in 0 until minOf(5, lines.size)) {
            val line = lines[i]
            // Name is usually short, doesn't contain common keywords, and has 2-4 words
            if (line.length in 5..50 && !containsCommonKeywords(line) &&
                line.split(" ").size in 2..4 &&
                !line.contains("@") &&
                !line.contains("http") &&
                line.any { it.isLetter() }
            ) {
                Log.d(TAG, "Name extracted: $line")
                return line
            }
        }
        Log.w(TAG, "No name found in text")
        return null
    }

    private fun extractEmail(text: String): String? {
        val matcher = EMAIL_PATTERN.matcher(text)
        val email = if (matcher.find()) matcher.group() else null
        Log.d(TAG, "Email extracted: $email")
        return email
    }

    private fun extractPhone(text: String): String? {
        val matcher = PHONE_PATTERN.matcher(text)
        val matches = mutableListOf<String>()
        while (matcher.find()) {
            matches.add(matcher.group())
        }
        // Return the first match that looks like a phone number
        val phone = matches.firstOrNull { it.replace(Regex("[^0-9]"), "").length in 10..15 }
        Log.d(TAG, "Phone extracted: $phone")
        return phone
    }

    private fun extractLocation(lines: List<String>): String? {
        // Look for location pattern (city, state or similar)
        for (line in lines.take(10)) {
            if (line.contains(",") && line.split(",").size == 2) {
                val parts = line.split(",").map { it.trim() }
                if (parts.all { it.length < 30 && it.isNotBlank() }) {
                    Log.d(TAG, "Location extracted: $line")
                    return line
                }
            }
            // Also check for Indian city names
            if (line.contains("Kolkata", ignoreCase = true) ||
                line.contains("West Bengal", ignoreCase = true) ||
                line.contains("India", ignoreCase = true) ||
                line.contains("Vellore", ignoreCase = true) ||
                line.contains("Tamil Nadu", ignoreCase = true)
            ) {
                Log.d(TAG, "Location extracted: $line")
                return line
            }
        }
        Log.w(TAG, "No location found")
        return null
    }

    private fun extractCareerObjective(lines: List<String>): String? {
        val objectiveKeywords = listOf("objective", "summary", "profile", "about")
        var inObjectiveSection = false
        val objectiveLines = mutableListOf<String>()

        for (line in lines) {
            val lowerLine = line.lowercase()

            if (objectiveKeywords.any { lowerLine.contains(it) }) {
                inObjectiveSection = true
                continue
            }

            if (inObjectiveSection) {
                if (line.length < 10 || isSectionHeader(line)) {
                    break
                }
                objectiveLines.add(line)
            }
        }

        val objective = if (objectiveLines.isNotEmpty()) {
            objectiveLines.joinToString(" ").take(300)
        } else {
            null
        }

        Log.d(TAG, "Career objective extracted: $objective")
        return objective
    }

    private fun extractEducation(lines: List<String>): ArrayList<Map<String, Any>> {
        val education = arrayListOf<Map<String, Any>>()
        var inEducationSection = false
        var currentEducation: MutableMap<String, Any>? = null

        for (i in lines.indices) {
            val line = lines[i]
            val lowerLine = line.lowercase()

            if (EDUCATION_KEYWORDS.any { lowerLine.contains(it) }) {
                inEducationSection = true
            }

            if (inEducationSection) {
                // Look for degree patterns
                if (lowerLine.contains("b.tech") || lowerLine.contains("bachelor") ||
                    lowerLine.contains("master") || lowerLine.contains("degree")
                ) {

                    currentEducation = mutableMapOf<String, Any>()

                    // Extract degree
                    val degree = when {
                        lowerLine.contains("b.tech") -> "Bachelor of Technology"
                        lowerLine.contains("bachelor") -> "Bachelor's Degree"
                        lowerLine.contains("master") -> "Master's Degree"
                        else -> line
                    }
                    currentEducation["degree"] = degree

                    // Look for institution in nearby lines
                    for (j in maxOf(0, i - 2) until minOf(lines.size, i + 3)) {
                        val nearbyLine = lines[j]
                        if (nearbyLine.contains("university", ignoreCase = true) ||
                            nearbyLine.contains("institute", ignoreCase = true) ||
                            nearbyLine.contains("college", ignoreCase = true) ||
                            nearbyLine.contains("vit", ignoreCase = true)
                        ) {
                            currentEducation["institution"] = nearbyLine
                            break
                        }
                    }

                    // Extract years
                    val years = extractYears(line)
                    if (years.size >= 2) {
                        currentEducation["startYear"] = years[0]
                        currentEducation["endYear"] = years[1]
                    } else if (years.size == 1) {
                        currentEducation["startYear"] = years[0]
                        currentEducation["endYear"] = years[0] + 4 // Assume 4-year degree
                    }

                    // Set defaults if not found
                    if (!currentEducation.containsKey("institution")) {
                        currentEducation["institution"] = "Institution"
                    }
                    if (!currentEducation.containsKey("startYear")) {
                        currentEducation["startYear"] = 0
                    }
                    if (!currentEducation.containsKey("endYear")) {
                        currentEducation["endYear"] = 0
                    }

                    education.add(currentEducation)
                    Log.d(TAG, "Education entry added: $currentEducation")
                    break // Assuming one main education entry
                }
            }
        }

        Log.d(TAG, "Total education entries extracted: ${education.size}")
        return education
    }

    private fun extractWorkExperience(lines: List<String>): ArrayList<Map<String, Any>> {
        val workExperience = arrayListOf<Map<String, Any>>()
        var inExperienceSection = false

        for (i in lines.indices) {
            val line = lines[i]
            val lowerLine = line.lowercase()

            if (EXPERIENCE_KEYWORDS.any { lowerLine.contains(it) }) {
                inExperienceSection = true
            }

            if (inExperienceSection) {
                // Look for job title patterns (usually followed by company name)
                if (line.length > 10 && line.length < 100 &&
                    !isSectionHeader(line) &&
                    (lowerLine.contains("intern") || lowerLine.contains("developer") ||
                            lowerLine.contains("engineer") || lowerLine.contains("analyst"))
                ) {

                    val workEntry = mutableMapOf<String, Any>()
                    workEntry["title"] = line

                    // Look for company in next few lines
                    for (j in i + 1 until minOf(lines.size, i + 3)) {
                        val companyLine = lines[j]
                        if (companyLine.length > 5 && companyLine.length < 80 && !isSectionHeader(
                                companyLine
                            )
                        ) {
                            workEntry["company"] = companyLine
                            break
                        }
                    }

                    // Look for description
                    for (j in i + 1 until minOf(lines.size, i + 5)) {
                        val descLine = lines[j]
                        if (descLine.length > 20) {
                            workEntry["description"] = descLine
                            break
                        }
                    }

                    // Set defaults
                    if (!workEntry.containsKey("company")) {
                        workEntry["company"] = "Company"
                    }
                    if (!workEntry.containsKey("description")) {
                        workEntry["description"] = ""
                    }

                    workExperience.add(workEntry)
                    Log.d(TAG, "Work experience entry added: $workEntry")
                }
            }
        }

        Log.d(TAG, "Total work experience entries extracted: ${workExperience.size}")
        return workExperience
    }

    private fun extractProjects(
        lines: List<String>,
        fullText: String
    ): ArrayList<Map<String, Any>> {
        val projects = arrayListOf<Map<String, Any>>()
        var inProjectSection = false

        for (i in lines.indices) {
            val line = lines[i]
            val lowerLine = line.lowercase()

            if (PROJECT_KEYWORDS.any { lowerLine.contains(it) }) {
                inProjectSection = true
            }

            if (inProjectSection) {
                // Look for project titles (usually longer lines that aren't section headers)
                if (line.length > 15 && line.length < 150 &&
                    !isSectionHeader(line) &&
                    (lowerLine.contains("app") || lowerLine.contains("system") ||
                            lowerLine.contains("web") || lowerLine.contains("platform") ||
                            line.contains("-") || line.contains("–"))
                ) {

                    val project = mutableMapOf<String, Any>()
                    project["title"] = line

                    // Look for description in next few lines
                    for (j in i + 1 until minOf(lines.size, i + 4)) {
                        val descLine = lines[j]
                        if (descLine.length > 20 && !isSectionHeader(descLine)) {
                            project["description"] = descLine
                            break
                        }
                    }

                    // Extract tech stack based on common technologies
                    val techStack = mutableListOf<String>()
                    val projectText = lines.drop(i).take(5).joinToString(" ")
                    TECH_SKILLS.forEach { tech ->
                        if (projectText.contains(tech, ignoreCase = true)) {
                            techStack.add(tech)
                        }
                    }

                    project["techStack"] = techStack
                    project["link"] = ""
                    project["duration"] = ""

                    if (!project.containsKey("description")) {
                        project["description"] = ""
                    }

                    projects.add(project)
                    Log.d(TAG, "Project entry added: $project")
                }
            }
        }

        Log.d(TAG, "Total project entries extracted: ${projects.size}")
        return projects
    }

    private fun extractSkills(text: String, lines: List<String>): ArrayList<String> {
        val skills = mutableSetOf<String>()
        val lowerText = text.lowercase()

        // Add skills based on technologies mentioned
        TECH_SKILLS.forEach { skill ->
            if (lowerText.contains(skill.lowercase())) {
                skills.add(skill)
            }
        }

        // Look for skills section
        var inSkillsSection = false
        for (line in lines) {
            val lowerLine = line.lowercase()

            if (SKILL_KEYWORDS.any { lowerLine.contains(it) }) {
                inSkillsSection = true
                continue
            }

            if (inSkillsSection) {
                // Parse skills from comma-separated or bullet-pointed lists
                if (line.contains(",")) {
                    val skillsFromLine =
                        line.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    skillsFromLine.forEach { skill ->
                        if (skill.length < 30 && skill.any { it.isLetter() }) {
                            skills.add(skill)
                        }
                    }
                } else if (line.startsWith("•") || line.startsWith("-") || line.startsWith("*")) {
                    val skill = line.substring(1).trim()
                    if (skill.length < 30 && skill.any { it.isLetter() }) {
                        skills.add(skill)
                    }
                }

                // Stop if we hit another section
                if (isSectionHeader(line)) {
                    break
                }
            }
        }

        Log.d(TAG, "Total skills extracted: ${skills.size}")
        return ArrayList(skills)
    }

    private fun extractAccomplishments(lines: List<String>): ArrayList<Map<String, Any>> {
        val accomplishments = arrayListOf<Map<String, Any>>()
        val accomplishmentKeywords =
            listOf("achievement", "accomplishment", "award", "recognition", "honor")

        for (i in lines.indices) {
            val line = lines[i]
            val lowerLine = line.lowercase()

            if (lowerLine.contains("winner") || lowerLine.contains("finalist") ||
                lowerLine.contains("hackathon") || lowerLine.contains("competition") ||
                accomplishmentKeywords.any { lowerLine.contains(it) }
            ) {

                val accomplishment = mutableMapOf<String, Any>()
                accomplishment["title"] = line

                // Extract issuer from context
                for (j in maxOf(0, i - 1) until minOf(lines.size, i + 2)) {
                    val contextLine = lines[j].lowercase()
                    if (contextLine.contains("hackathon") || contextLine.contains("competition") ||
                        contextLine.contains("university") || contextLine.contains("college")
                    ) {
                        accomplishment["issuer"] = lines[j]
                        break
                    }
                }

                accomplishment["description"] = accomplishment["issuer"] ?: "Achievement"
                accomplishment["link"] = ""

                if (!accomplishment.containsKey("issuer")) {
                    accomplishment["issuer"] = "Organization"
                }

                accomplishments.add(accomplishment)
                Log.d(TAG, "Accomplishment entry added: $accomplishment")
            }
        }

        Log.d(TAG, "Total accomplishment entries extracted: ${accomplishments.size}")
        return accomplishments
    }

    private fun extractTrainings(lines: List<String>): ArrayList<Map<String, Any>> {
        val trainings = arrayListOf<Map<String, Any>>()
        val trainingKeywords =
            listOf("certification", "certificate", "training", "course", "workshop")

        for (i in lines.indices) {
            val line = lines[i]
            val lowerLine = line.lowercase()

            if (trainingKeywords.any { lowerLine.contains(it) } ||
                lowerLine.contains("certified") || lowerLine.contains("diploma")) {

                val training = mutableMapOf<String, Any>()
                training["title"] = line

                // Look for issuer in nearby lines
                for (j in maxOf(0, i - 1) until minOf(lines.size, i + 3)) {
                    val issuerLine = lines[j]
                    if (issuerLine.contains("IBM") || issuerLine.contains("Google") ||
                        issuerLine.contains("Microsoft") || issuerLine.contains("Amazon") ||
                        issuerLine.contains("Oracle") || issuerLine.contains("Cisco")
                    ) {
                        training["issuer"] = issuerLine
                        break
                    }
                }

                training["description"] = "Professional certification"
                training["date"] = System.currentTimeMillis()

                if (!training.containsKey("issuer")) {
                    training["issuer"] = "Certifying Body"
                }

                trainings.add(training)
                Log.d(TAG, "Training entry added: $training")
            }
        }

        Log.d(TAG, "Total training entries extracted: ${trainings.size}")
        return trainings
    }

    private fun extractExtracurriculars(lines: List<String>): ArrayList<Map<String, Any>> {
        val extracurriculars = arrayListOf<Map<String, Any>>()
        val extracurricularKeywords =
            listOf("member", "volunteer", "leadership", "club", "society", "organization")

        for (i in lines.indices) {
            val line = lines[i]
            val lowerLine = line.lowercase()

            if (extracurricularKeywords.any { lowerLine.contains(it) } &&
                (lowerLine.contains("technical") || lowerLine.contains("core") ||
                        lowerLine.contains("president") || lowerLine.contains("secretary"))) {

                val extracurricular = mutableMapOf<String, Any>()

                // Extract role and organization
                if (line.contains(" at ") || line.contains(" - ")) {
                    val parts = line.split(" at ", " - ")
                    if (parts.size >= 2) {
                        extracurricular["role"] = parts[0].trim()
                        extracurricular["organization"] = parts[1].trim()
                    } else {
                        extracurricular["role"] = line
                        extracurricular["organization"] = "Organization"
                    }
                } else {
                    extracurricular["role"] = line
                    extracurricular["organization"] = "Organization"
                }

                // Look for description in next few lines
                for (j in i + 1 until minOf(lines.size, i + 3)) {
                    val descLine = lines[j]
                    if (descLine.length > 20 && !isSectionHeader(descLine)) {
                        extracurricular["description"] = descLine
                        break
                    }
                }

                if (!extracurricular.containsKey("description")) {
                    extracurricular["description"] = "Participated in organizational activities"
                }

                extracurriculars.add(extracurricular)
                Log.d(TAG, "Extracurricular entry added: $extracurricular")
            }
        }

        Log.d(TAG, "Total extracurricular entries extracted: ${extracurriculars.size}")
        return extracurriculars
    }

    private fun extractPortfolioLinks(text: String): Map<String, String> {
        val portfolio = mutableMapOf<String, String>()

        val githubMatcher = GITHUB_PATTERN.matcher(text)
        if (githubMatcher.find()) {
            portfolio["github"] = "https://github.com/${githubMatcher.group(1)}"
        }

        val linkedinMatcher = LINKEDIN_PATTERN.matcher(text)
        if (linkedinMatcher.find()) {
            portfolio["linkedin"] = "https://linkedin.com/in/${linkedinMatcher.group(1)}"
        }

        // Look for other portfolio URLs
        val urlPattern = Pattern.compile("https?://[\\w.-]+\\.[a-z]{2,}")
        val urlMatcher = urlPattern.matcher(text)
        while (urlMatcher.find()) {
            val url = urlMatcher.group()
            when {
                url.contains("behance") -> portfolio["behance"] = url
                url.contains("dribbble") -> portfolio["dribbble"] = url
                url.contains("portfolio") -> portfolio["portfolio"] = url
            }
        }

        Log.d(TAG, "Portfolio links extracted: $portfolio")
        return portfolio
    }

    private fun isValidParseResult(result: ParsedResumeData): Boolean {
        val isValid = result.name != null || result.email != null ||
                result.education.isNotEmpty() || result.projects.isNotEmpty() ||
                result.skills.isNotEmpty() || result.workExperience.isNotEmpty()

        Log.d(TAG, "Parse result validation: $isValid")
        return isValid
    }

    // Helper functions
    private fun containsCommonKeywords(line: String): Boolean {
        val keywords = listOf(
            "email",
            "phone",
            "address",
            "experience",
            "education",
            "skills",
            "project",
            "@",
            "http"
        )
        return keywords.any { line.lowercase().contains(it) }
    }

    private fun isSectionHeader(line: String): Boolean {
        return line.length < 50 && (line.contains(":") ||
                line.all { it.isUpperCase() || it.isWhitespace() || it in ":-" } ||
                line.endsWith(":"))
    }

    private fun extractYears(text: String): List<Int> {
        val matcher = YEAR_PATTERN.matcher(text)
        val years = mutableListOf<Int>()
        while (matcher.find()) {
            years.add(matcher.group().toInt())
        }
        return years.sorted()
    }
}