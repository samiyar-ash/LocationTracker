package com.example.locationtracker

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {
        private const val REQUEST_LOCATION = 100
    }

    private lateinit var lastSentText: TextView

    private val locationSentReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {

                if (
                    intent?.action ==
                    LocationService.ACTION_LOCATION_SENT
                ) {

                    updateLastSentTime()
                }
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        createInterface()

        requestPermissions()
    }

    private fun createInterface() {

        val root =
            LinearLayout(this)

        root.orientation =
            LinearLayout.VERTICAL

        root.setPadding(
            32,
            48,
            32,
            32
        )

        val title =
            TextView(this)

        title.text =
            "Location Tracker"

        title.textSize = 24f

        title.setPadding(
            0,
            0,
            0,
            32
        )

        root.addView(title)

        lastSentText =
            TextView(this)

        lastSentText.text =
            "آخرین ارسال: هنوز ارسال نشده"

        lastSentText.textSize = 18f

        lastSentText.setPadding(
            0,
            0,
            0,
            32
        )

        root.addView(lastSentText)

        val sendButton =
            Button(this)

        sendButton.text =
            "ارسال فایل"

        sendButton.setOnClickListener {

            sendManualLocation()
        }

        root.addView(
            sendButton,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
    }

    private fun requestPermissions() {

        val permissions =
            mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {

            permissions.add(
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        }

        val missing =
            permissions.filter {

                ContextCompat.checkSelfPermission(
                    this,
                    it
                ) != PackageManager.PERMISSION_GRANTED
            }

        if (missing.isEmpty()) {

            startLocationService()

        } else {

            requestPermissions(
                missing.toTypedArray(),
                REQUEST_LOCATION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode ==
            REQUEST_LOCATION
        ) {

            if (
                grantResults.isNotEmpty() &&
                grantResults.all {
                    it == PackageManager.PERMISSION_GRANTED
                }
            ) {

                startLocationService()
            }
        }
    }

    private fun startLocationService() {

        val intent =
            Intent(
                this,
                LocationService::class.java
            )

        ContextCompat.startForegroundService(
            this,
            intent
        )
    }

    private fun sendManualLocation() {

        val intent =
            Intent(
                this,
                LocationService::class.java
            )

        intent.action =
            LocationService.ACTION_MANUAL_SEND

        ContextCompat.startForegroundService(
            this,
            intent
        )
    }

    private fun updateLastSentTime() {

        val formatter =
            SimpleDateFormat(
                "yyyy/MM/dd HH:mm:ss",
                Locale.getDefault()
            )

        val currentTime =
            formatter.format(
                Date()
            )

        lastSentText.text =
            "آخرین ارسال: $currentTime"
    }

    override fun onStart() {

        super.onStart()

        val filter =
            IntentFilter(
                LocationService.ACTION_LOCATION_SENT
            )

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            registerReceiver(
                locationSentReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )

        } else {

            registerReceiver(
                locationSentReceiver,
                filter
            )
        }
    }

    override fun onStop() {

        try {

            unregisterReceiver(
                locationSentReceiver
            )

        } catch (
            e: IllegalArgumentException
        ) {
            // قبلاً unregister شده است
        }

        super.onStop()
    }
}
