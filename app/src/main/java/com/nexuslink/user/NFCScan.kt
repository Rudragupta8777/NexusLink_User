package com.nexuslink.user

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.button.MaterialButton
import com.nexuslink.user.data.AttachIdRequest
import com.nexuslink.user.data.LoginResponse
import com.nexuslink.user.network.RetrofitInstance
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class NFCScan : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var pendingIntent: PendingIntent
    private lateinit var intentFilters: Array<IntentFilter>
    private lateinit var techLists: Array<Array<String>>
    private var vibrator: Vibrator? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null

    private lateinit var scanButton: MaterialButton
    private val handler = Handler(Looper.getMainLooper())
    private var dotCount = 0
    private var scanning = true
    private lateinit var runnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_nfcscan)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        scanButton = findViewById(R.id.scanButton)
        scanButton.isEnabled = false

        initializeNFC()
        initializeAudioAndVibration()
        startDotAnimation()
    }

    private fun initializeNFC() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC not supported on this device", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (!nfcAdapter!!.isEnabled) {
            Toast.makeText(this, "NFC is disabled. Please enable it in settings", Toast.LENGTH_LONG).show()
            return
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags
        )

        intentFilters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        )

        techLists = arrayOf(
            arrayOf(NfcA::class.java.name),
            arrayOf(NfcB::class.java.name),
            arrayOf(NfcF::class.java.name),
            arrayOf(NfcV::class.java.name)
        )
    }

    private fun initializeAudioAndVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )

                val afd = resources.openRawResourceFd(R.raw.beep_sound)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                prepare()
            }

            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                mediaPlayer?.setVolume(1.0f, 1.0f)
                                mediaPlayer?.start()
                            }
                            AudioManager.AUDIOFOCUS_LOSS -> {
                                mediaPlayer?.pause()
                                audioManager?.abandonAudioFocusRequest(audioFocusRequest!!)
                            }
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                mediaPlayer?.pause()
                            }
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                                mediaPlayer?.setVolume(0.2f, 0.2f)
                            }
                        }
                    }
                    .build()
            }
        } catch (e: Exception) {
            Log.e("NFCScan", "Error initializing MediaPlayer: ${e.message}")
            mediaPlayer = null
        }
    }

    private fun startDotAnimation() {
        runnable = object : Runnable {
            override fun run() {
                if (!scanning) return
                dotCount = (dotCount + 1) % 4
                val dots = ".".repeat(dotCount)
                scanButton.text = "Scanning$dots"
                handler.postDelayed(this, 500)
            }
        }
        handler.post(runnable)
    }

    // In NFCScan.kt, modify the onCardDetected function:
    private fun onCardDetected(uid: String) {
        scanning = false
        handler.removeCallbacks(runnable)

        scanButton.text = "Continue"
        scanButton.isEnabled = true

        scanButton.setOnClickListener {
            val token = intent.getStringExtra("token") ?: return@setOnClickListener
            sendUidToBackend(uid, token)
        }
    }

    private fun sendUidToBackend(uid: String, token: String) {
        RetrofitInstance.api.attachId(token, AttachIdRequest(uid))
            .enqueue(object : Callback<LoginResponse> {
                override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        Toast.makeText(this@NFCScan, "ID attached successfully", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@NFCScan, ProfileSetupActivity::class.java)
                        intent.putExtra("card_uid", uid)
                        intent.putExtra("token", token)
                        startActivity(intent)
                    } else {
                        Toast.makeText(this@NFCScan, "Failed to attach ID", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                    Toast.makeText(this@NFCScan, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            })
    }


    override fun onResume() {
        super.onResume()
        nfcAdapter?.let { adapter ->
            adapter.enableForegroundDispatch(this, pendingIntent, intentFilters, techLists)
            Log.d("NFCScan", "NFC foreground dispatch enabled")
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
        Log.d("NFCScan", "NFC foreground dispatch disabled")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d("NFCScan", "onNewIntent called with action: ${intent.action}")

        when (intent.action) {
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_NDEF_DISCOVERED -> {
                val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
                }

                tag?.let {
                    displayUid(it)
                } ?: run {
                    Toast.makeText(this, "No tag found", Toast.LENGTH_SHORT).show()
                    Log.w("NFCScan", "Tag is null in intent")
                }
            }
            else -> {
                Log.w("NFCScan", "Unknown intent action: ${intent.action}")
            }
        }
    }

    private fun displayUid(tag: Tag) {
        val uidBytes = tag.id
        val uid = uidBytes.joinToString(":") { String.format("%02X", it) }
        val techList = tag.techList.joinToString(", ")

        Toast.makeText(this, "Card detected: $uid", Toast.LENGTH_SHORT).show()
        Log.d("NFCScan", "Card UID: $uid")
        Log.d("NFCScan", "Supported technologies: $techList")

        playBeepSound()
        vibrateDevice()
        onCardDetected(uid)
    }

    private fun playBeepSound() {
        mediaPlayer?.let { player ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                    val audioFocusResult = audioManager?.requestAudioFocus(audioFocusRequest!!)
                    if (audioFocusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        if (!player.isPlaying) {
                            player.start()
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val audioFocusResult = audioManager?.requestAudioFocus(
                        null,
                        AudioManager.STREAM_NOTIFICATION,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                    )
                    if (audioFocusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        if (!player.isPlaying) {
                            player.start()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NFCScan", "Error playing beep sound: ${e.message}")
            }
        }
    }

    private fun vibrateDevice() {
        vibrator?.let { vib ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(200)
                }
            } catch (e: Exception) {
                Log.e("NFCScan", "Error vibrating device: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            } catch (e: Exception) {
                Log.e("NFCScan", "Error releasing MediaPlayer: ${e.message}")
            }
        }
        mediaPlayer = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager?.abandonAudioFocusRequest(audioFocusRequest!!)
        }
    }
}
