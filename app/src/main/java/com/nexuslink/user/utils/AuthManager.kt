package com.nexuslink.user.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.nexuslink.user.data.Student

class AuthManager(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("NexusLinkAuth", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_ID_CARD_UID = "id_card_uid"
        private const val KEY_USER_GENDER = "user_gender"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }

    fun saveAuthData(token: String, student: Student) {
        with(sharedPreferences.edit()) {
            putString(KEY_TOKEN, token)
            putString(KEY_USER_ID, student._id)
            putString(KEY_USER_EMAIL, student.email)
            putString(KEY_USER_NAME, student.name)
            putString(KEY_ID_CARD_UID, student.idcard_uid)
            putString(KEY_USER_GENDER, student.gender)
            putBoolean(KEY_IS_LOGGED_IN, true)
            apply()
        }
        Log.d("AuthManager", "Token saved: $token")
    }

    fun getToken(): String? {
        val token = sharedPreferences.getString(KEY_TOKEN, null)
        Log.d("AuthManager", "Token retrieved: $token")
        return token
    }
    fun getUserId(): String? = sharedPreferences.getString(KEY_USER_ID, null)
    fun getUserEmail(): String? = sharedPreferences.getString(KEY_USER_EMAIL, null)
    fun getUserName(): String? = sharedPreferences.getString(KEY_USER_NAME, null)
    fun getIdCardUid(): String? = sharedPreferences.getString(KEY_ID_CARD_UID, null)
    fun getUserGender(): String? = sharedPreferences.getString(KEY_USER_GENDER, null)
    fun isLoggedIn(): Boolean = sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false)

    fun clearAuthData() {
        with(sharedPreferences.edit()) {
            remove(KEY_TOKEN)
            remove(KEY_USER_ID)
            remove(KEY_USER_EMAIL)
            remove(KEY_USER_NAME)
            remove(KEY_ID_CARD_UID)
            remove(KEY_USER_GENDER)
            putBoolean(KEY_IS_LOGGED_IN, false)
            apply()
        }
    }
}