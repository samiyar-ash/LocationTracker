package com.example.locationtracker

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LocationService : Service() {

    companion object {

        private const val CHANNEL_ID = "location_channel"
        private const val NOTIFICATION_ID = 1001

        private const val SERVER_URL =
            "https://vipgarden.ir/f/save.php"

        const val ACTION_MANUAL_SEND =
            "com.example.locationtracker.MANUAL_SEND"

        const val ACTION_LOCATION_SENT =
            "com.example.locationtracker.LOCATION_SENT"
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification()
        )

        fusedLocationClient =
            LocationServices.getFusedLocationProviderClient(this)

        startLocationUpdates()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (intent?.action == ACTION_MANUAL_SEND) {
            requestManualLocation()
        }

        return START_STICKY
    }

    private fun startLocationUpdates() {

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        val locationRequest =
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                15_000L
            )
                .setMinUpdateIntervalMillis(10_000L)
                .setMaxUpdateDelayMillis(15_000L)
                .setWaitForAccurateLocation(true)
                .build()

        locationCallback =
            object : LocationCallback() {

                override fun onLocationResult(
                    result: LocationResult
                ) {

                    val location =
                        result.lastLocation ?: return

                    /*
                     * اگر دقت خیلی ضعیف باشد،
                     * فعلاً ارسال نمی‌کنیم.
                     */
                    if (location.accuracy > 50f) {
                        return
                    }

                    sendLocation(
                        location.latitude,
                        location.longitude,
                        location.accuracy
                    )
                }
            }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            mainLooper
        )
    }

    private fun requestManualLocation() {

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient
            .getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            )
            .addOnSuccessListener { location ->

                if (location == null) {
                    return@addOnSuccessListener
                }

                sendLocation(
                    location.latitude,
                    location.longitude,
                    location.accuracy
                )
            }
    }

    private fun sendLocation(
        latitude: Double,
        longitude: Double,
        accuracy: Float
    ) {

        val json = JSONObject()

        json.put("latitude", latitude)
        json.put("longitude", longitude)
        json.put("accuracy", accuracy)
        json.put(
            "timestamp",
            System.currentTimeMillis()
        )

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

        httpClient.newCall(request)
            .enqueue(
                object : okhttp3.Callback {

                    override fun onFailure(
                        call: okhttp3.Call,
                        e: IOException
                    ) {
                        // ارسال ناموفق بود
                    }

                    override fun onResponse(
                        call: okhttp3.Call,
                        response: okhttp3.Response
                    ) {

                        val success =
                            response.isSuccessful

                        response.close()

                        if (success) {

                            val intent =
                                Intent(ACTION_LOCATION_SENT)

                            intent.setPackage(
                                packageName
                            )

                            sendBroadcast(intent)
                        }
                    }
                }
            )
    }

    private fun createNotification(): Notification {

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle(
                "Location Tracker"
            )
            .setContentText(
                "ردیابی موقعیت فعال است"
            )
            .setSmallIcon(
                android.R.drawable.ic_menu_mylocation
            )
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Location Tracking",
                    NotificationManager.IMPORTANCE_LOW
                )

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    override fun onDestroy() {

        if (
            ::fusedLocationClient.isInitialized &&
            ::locationCallback.isInitialized
        ) {

            fusedLocationClient
                .removeLocationUpdates(
                    locationCallback
                )
        }

        httpClient.dispatcher.executorService.shutdown()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}
