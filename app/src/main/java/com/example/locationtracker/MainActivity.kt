```kotlin
package com.example.locationtracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    companion object {
        private const val SERVER_URL =
            "https://vipgarden.ir/f/save.php"
    }

    private lateinit var statusText: TextView
    private lateinit var locationText: TextView

    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val locationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val fineGranted =
                permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true

            val coarseGranted =
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (fineGranted || coarseGranted) {

                statusText.text =
                    "مجوز موقعیت دریافت شد"

                startLocationService()

                requestBackgroundPermissionIfNeeded()

            } else {

                statusText.text =
                    "مجوز موقعیت داده نشد"
            }
        }

    private val backgroundPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {

                statusText.text =
                    "مجوز Background Location فعال شد"

            } else {

                statusText.text =
                    "Background Location فعال نیست"
            }
        }


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        createUI()

        requestPermissions()
    }


    private fun createUI() {

        val layout =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    40,
                    60,
                    40,
                    40
                )
            }


        val title =
            TextView(this).apply {

                text =
                    "Location Tracker"

                textSize =
                    26f
            }


        statusText =
            TextView(this).apply {

                text =
                    "در حال بررسی مجوز..."

                textSize =
                    18f

                setPadding(
                    0,
                    40,
                    0,
                    20
                )
            }


        locationText =
            TextView(this).apply {

                text =
                    "موقعیت: ---"

                textSize =
                    17f

                setPadding(
                    0,
                    10,
                    0,
                    30
                )
            }


        val sendButton =
            Button(this).apply {

                text =
                    "ارسال موقعیت دستی"

                textSize =
                    18f

                setOnClickListener {

                    sendLocationManually()
                }
            }


        layout.addView(title)

        layout.addView(statusText)

        layout.addView(locationText)

        layout.addView(sendButton)


        setContentView(layout)
    }


    private fun requestPermissions() {

        val fine =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarse =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED


        if (fine || coarse) {

            statusText.text =
                "مجوز موقعیت فعال است"

            startLocationService()

            requestBackgroundPermissionIfNeeded()

        } else {

            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }


    private fun requestBackgroundPermissionIfNeeded() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {

            val granted =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {

                backgroundPermissionLauncher.launch(
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
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


    private fun sendLocationManually() {

        statusText.text =
            "در حال دریافت موقعیت GPS..."


        val fineGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED


        if (!fineGranted && !coarseGranted) {

            statusText.text =
                "ابتدا مجوز Location را فعال کنید"

            return
        }


        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->

                if (location == null) {

                    statusText.text =
                        "GPS هنوز موقعیت ندارد. کمی صبر کنید."

                    return@addOnSuccessListener
                }


                val latitude =
                    location.latitude

                val longitude =
                    location.longitude

                val accuracy =
                    location.accuracy


                locationText.text =
                    """
                    Latitude: $latitude
                    Longitude: $longitude
                    Accuracy: $accuracy m
                    """.trimIndent()


                statusText.text =
                    "موقعیت دریافت شد؛ در حال ارسال..."


                sendToServer(
                    latitude,
                    longitude,
                    accuracy
                )
            }
            .addOnFailureListener { error ->

                statusText.text =
                    "خطا در دریافت GPS: ${error.message}"
            }
    }


    private fun sendToServer(
        latitude: Double,
        longitude: Double,
        accuracy: Float
    ) {

        val json =
            JSONObject().apply {

                put(
                    "latitude",
                    latitude
                )

                put(
                    "longitude",
                    longitude
                )

                put(
                    "accuracy",
                    accuracy
                )

                put(
                    "timestamp",
                    System.currentTimeMillis()
                )
            }


        val mediaType =
            "application/json; charset=utf-8"
                .toMediaType()


        val body =
            json.toString()
                .toRequestBody(mediaType)


        val request =
            Request.Builder()
                .url(SERVER_URL)
                .post(body)
                .build()


        httpClient
            .newCall(request)
            .enqueue(
                object : okhttp3.Callback {

                    override fun onFailure(
                        call: okhttp3.Call,
                        e: IOException
                    ) {

                        runOnUiThread {

                            statusText.text =
                                "خطا در ارسال: ${e.message}"
                        }
                    }


                    override fun onResponse(
                        call: okhttp3.Call,
                        response: okhttp3.Response
                    ) {

                        val responseBody =
                            response.body?.string()
                                ?: ""


                        runOnUiThread {

                            if (response.isSuccessful) {

                                statusText.text =
                                    "✓ موقعیت با موفقیت ارسال شد\n$responseBody"

                            } else {

                                statusText.text =
                                    "خطای سرور: HTTP ${response.code}\n$responseBody"
                            }
                        }

                        response.close()
                    }
                }
            )
    }
}
```
