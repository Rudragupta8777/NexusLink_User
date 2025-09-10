package com.nexuslink.user

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        // Set up the click listener for the Google button
        val googleButton = findViewById<MaterialCardView>(R.id.btnGoogle)
        googleButton.setOnClickListener {
            // Create an Intent to navigate to the NFCScan activity
            val intent = Intent(this, NFCScan::class.java)
            startActivity(intent)
        }
    }
}