package com.nexuslink.user.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.nexuslink.user.data.ParsedResumeData
import com.nexuslink.user.network.AffindaCertification
import com.nexuslink.user.network.AffindaEducation
import com.nexuslink.user.network.AffindaResumeData
import com.nexuslink.user.network.AffindaSkill
import com.nexuslink.user.network.AffindaWorkExperience
import com.nexuslink.user.network.RetrofitInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class AffindaParser(private val context: Context) {

    companion object {
        private const val TAG = "AffindaParser"
        private const val AFFINDA_API_KEY = "Bearer aff_05423cca981dc287b3dafc8aff1a36b946bd67af"
    }

    suspend fun parseResumeWithAffinda(uri: Uri): ParsedResumeData? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting Affinda resume parsing")

            val filePart = prepareFilePart(uri) ?: run {
                Log.e(TAG, "Failed to prepare file part")
                return@withContext null
            }

            Log.d(TAG, "Making API call to Affinda")
            val response = RetrofitInstance.affindaApi.parseResume(AFFINDA_API_KEY, filePart).execute()

            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "Affinda API call successful")
                val affindaData = response.body()!!.data
                return@withContext convertAffindaToParsedResumeData(affindaData)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Affinda API error - Code: ${response.code()}, Body: $errorBody")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing with Affinda", e)
            return@withContext null
        }
    }


    private fun prepareFilePart(uri: Uri): MultipartBody.Part? {
        return try {
            Log.d(TAG, "Preparing file part from URI: $uri")

            val inputStream = context.contentResolver.openInputStream(uri)
            val tempFile = File.createTempFile("resume", ".pdf", context.cacheDir)

            FileOutputStream(tempFile).use { output ->
                inputStream?.copyTo(output)
            }
            inputStream?.close()

            Log.d(TAG, "Temp file created: ${tempFile.absolutePath}, Size: ${tempFile.length()} bytes")

            val requestFile = tempFile.asRequestBody("application/pdf".toMediaType())
            MultipartBody.Part.createFormData("file", "resume.pdf", requestFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing file part", e)
            null
        }
    }

    private fun convertAffindaToParsedResumeData(affindaData: AffindaResumeData): ParsedResumeData {
        Log.d(TAG, "Converting Affinda data to ParsedResumeData")

        val name = affindaData.name?.raw ?: affindaData.name?.let { "${it.first ?: ""} ${it.last ?: ""}".trim() } ?: ""
        val email = affindaData.emails?.firstOrNull() ?: ""
        val phone = affindaData.phoneNumbers?.firstOrNull() ?: ""
        val location = affindaData.location?.formatted ?: affindaData.location?.raw ?: ""
        val careerObjective = affindaData.objective ?: affindaData.summary ?: ""

        Log.d(TAG, "Basic info - Name: $name, Email: $email, Phone: $phone")

        val education = extractEducation(affindaData.education)
        val workExperience = extractWorkExperience(affindaData.workExperience)
        val skills = extractSkills(affindaData.skills)
        val trainings = extractTrainings(affindaData.certifications)
        val portfolio = extractPortfolio(affindaData.websites)

        Log.d(TAG, "Extracted - Education: ${education.size}, Work: ${workExperience.size}, Skills: ${skills.size}")

        return ParsedResumeData(
            name = name.takeIf { it.isNotBlank() },
            email = email.takeIf { it.isNotBlank() },
            phone = phone.takeIf { it.isNotBlank() },
            location = location.takeIf { it.isNotBlank() },
            careerObjective = careerObjective.takeIf { it.isNotBlank() },
            education = education,
            workExperience = workExperience,
            projects = ArrayList(), // Affinda doesn't extract projects directly
            skills = skills,
            accomplishments = extractAccomplishments(affindaData),
            trainings = trainings,
            extracurriculars = ArrayList(), // Affinda doesn't extract extracurriculars directly
            portfolio = portfolio
        )
    }

    private fun extractEducation(educationList: List<AffindaEducation>?): ArrayList<Map<String, Any>> {
        val education = ArrayList<Map<String, Any>>()

        educationList?.forEach { edu ->
            val degree = edu.accreditation?.education
                ?: edu.accreditation?.inputStr
                ?: "Degree"

            val institution = edu.organization ?: "Institution"

            val startYear = edu.dates?.startDate?.let { parseYearFromDate(it) } ?: 0
            val endYear = edu.dates?.endDate?.let { parseYearFromDate(it) } ?: 0

            Log.d(TAG, "Education entry - Degree: $degree, Institution: $institution, Years: $startYear-$endYear")

            education.add(mapOf(
                "degree" to degree,
                "institution" to institution,
                "startYear" to startYear,
                "endYear" to endYear
            ))
        }

        return education
    }

    private fun extractWorkExperience(workExpList: List<AffindaWorkExperience>?): ArrayList<Map<String, Any>> {
        val workExperience = ArrayList<Map<String, Any>>()

        workExpList?.forEach { work ->
            val title = work.jobTitle ?: "Position"
            val company = work.organization ?: "Company"
            val description = work.jobDescription ?: ""

            Log.d(TAG, "Work experience - Title: $title, Company: $company")

            workExperience.add(mapOf(
                "title" to title,
                "company" to company,
                "description" to description,
                "startDate" to (work.dates?.startDate ?: ""),
                "endDate" to (work.dates?.endDate ?: "")
            ))
        }

        return workExperience
    }

    private fun extractSkills(skillsList: List<AffindaSkill>?): ArrayList<String> {
        val skills = ArrayList<String>()

        skillsList?.forEach { skill ->
            skill.name?.let {
                skills.add(it)
                Log.d(TAG, "Skill extracted: $it")
            }
        }

        return skills
    }

    private fun extractAccomplishments(affindaData: AffindaResumeData): ArrayList<Map<String, Any>> {
        val accomplishments = ArrayList<Map<String, Any>>()

        // Extract publications as accomplishments
        affindaData.publications?.forEach { publication ->
            publication.title?.let {
                accomplishments.add(mapOf(
                    "title" to it,
                    "issuer" to "Publication",
                    "description" to "Published work",
                    "link" to ""
                ))
            }
        }
        return accomplishments
    }

    private fun extractTrainings(certifications: List<AffindaCertification>?): ArrayList<Map<String, Any>> {
        val trainings = ArrayList<Map<String, Any>>()

        certifications?.forEach { cert ->
            cert.name?.let {
                Log.d(TAG, "Certification extracted: $it")
                trainings.add(mapOf(
                    "title" to it,
                    "issuer" to "Certifying Body",
                    "description" to "Professional Certification",
                    "date" to System.currentTimeMillis()
                ))
            }
        }

        return trainings
    }

    private fun extractPortfolio(websites: List<String>?): Map<String, String> {
        val portfolio = mutableMapOf<String, String>()

        websites?.forEach { site ->
            Log.d(TAG, "Website extracted: $site")
            when {
                site.contains("github", ignoreCase = true) -> portfolio["github"] = site
                site.contains("linkedin", ignoreCase = true) -> portfolio["linkedin"] = site
                site.contains("behance", ignoreCase = true) -> portfolio["behance"] = site
                site.contains("dribbble", ignoreCase = true) -> portfolio["dribbble"] = site
                else -> {
                    if (portfolio["other"] == null) {
                        portfolio["other"] = site
                    } else {
                        portfolio["other"] = portfolio["other"] + ", $site"
                    }
                }
            }
        }

        return portfolio
    }

    private fun parseYearFromDate(dateString: String): Int {
        return try {
            // Try different date formats
            val formats = listOf(
                "yyyy-MM-dd",
                "yyyy-MM",
                "yyyy",
                "MM/yyyy",
                "MM/dd/yyyy",
                "dd/MM/yyyy"
            )

            for (format in formats) {
                try {
                    val date = SimpleDateFormat(format, Locale.getDefault()).parse(dateString)
                    val calendar = Calendar.getInstance()
                    calendar.time = date ?: continue
                    return calendar.get(Calendar.YEAR)
                } catch (e: Exception) {
                    continue
                }
            }

            // If all formats fail, try to extract year with regex
            val yearRegex = Regex("(19|20)\\d{2}")
            val matchResult = yearRegex.find(dateString)
            matchResult?.value?.toInt() ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing year from date: $dateString", e)
            0
        }
    }
}