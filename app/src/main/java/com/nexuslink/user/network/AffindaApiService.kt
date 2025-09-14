package com.nexuslink.user.network

import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Header

interface AffindaApiService {
    @Multipart
    @POST("documents")
    fun parseResume(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part
    ): Call<AffindaResumeResponse>
}

data class AffindaResumeResponse(
    val data: AffindaResumeData
)

data class AffindaResumeData(
    val name: AffindaName?,
    val phoneNumbers: List<String>?,
    val emails: List<String>?,
    val websites: List<String>?,
    val dateOfBirth: String?,
    val location: AffindaLocation?,
    val objective: String?,
    val summary: String?,
    val totalYearsExperience: Int?,
    val education: List<AffindaEducation>?,
    val workExperience: List<AffindaWorkExperience>?,
    val skills: List<AffindaSkill>?,
    val certifications: List<AffindaCertification>?,
    val publications: List<AffindaPublication>?,
    val referees: List<AffindaReferee>?
)

data class AffindaName(
    val first: String?,
    val last: String?,
    val raw: String?
)

data class AffindaLocation(
    val formatted: String?,
    val raw: String?
)

data class AffindaEducation(
    val organization: String?,
    val accreditation: AffindaAccreditation?,
    val grade: AffindaGrade?,
    val location: AffindaLocation?,
    val dates: AffindaDates?
)

data class AffindaWorkExperience(
    val jobTitle: String?,
    val organization: String?,
    val location: AffindaLocation?,
    val jobDescription: String?,
    val dates: AffindaDates?
)

data class AffindaSkill(
    val name: String?
)

data class AffindaCertification(
    val name: String?
)

data class AffindaPublication(
    val title: String?
)

data class AffindaReferee(
    val name: String?
)

data class AffindaAccreditation(
    val education: String?,
    val inputStr: String?
)

data class AffindaGrade(
    val raw: String?
)

data class AffindaDates(
    val startDate: String?,
    val endDate: String?,
    val monthsInPosition: Int?,
    val isCurrent: Boolean?
)