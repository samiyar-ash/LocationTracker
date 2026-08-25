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
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
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

        private const val CHANNEL_ID =
            "location_channel"

        private const val NOTIFICATION_ID =
            1001

        private const val SERVER_URL =
            "https://vipgarden.ir/f/save.php"

        const val ACTION_MANUAL_SEND =
            "com.example.locationtracker.MANUAL_SEND"

        const val ACTION_LOCATION_SENT =
            "com.example.locationtracker.LOCATION_SENT"

        private const val SEND_INTERVAL =
            15_000L
    }

    private lateinit var fusedLocationClient:
            FusedLocationProviderClient

    private val handler =
        android.os.Handler(Looper.getMainLooper())

    private val sendRunnable =
        object : Runnable {

            override fun run() {

                requestAndSendLocation()

                handler.postDelayed(
                    this,
                    SEND_INTERVAL
                )
            }
        }

    private val httpClient =
        OkHttpClient.Builder()
            .connectTimeout(
                20,
                TimeUnit.SECONDS
            )
            .readTimeout(
                20,
                TimeUnit.SECONDS
            )
            .writeTimeout(
                20,
                TimeUnit.SECONDS
            )
            .build()

    override fun onCreate() {

        super.onCreate()

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification()
        )

        fusedLocationClient =
            LocationServices
                .getFusedLocationProviderClient(
                    this
                )

        /*
         * اولین ارسال
         */
        requestAndSendLocation()

        /*
         * بعد از آن هر 15 ثانیه
         */
        handler.postDelayed(
            sendRunnable,
            SEND_INTERVAL
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        /*
         * ارسال دستی
         */
        if (
            intent?.action ==
            ACTION_MANUAL_SEND
        ) {

            requestAndSendLocation()
        }

        /*
         * اگر سرویس توسط Android کشته شد،
         * دوباره اجرا شود.
         */
        return START_STICKY
    }

    private fun requestAndSendLocation() {

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            return
        }

        /*
         * درخواست موقعیت تازه و دقیق
         */
        fusedLocationClient
            .getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            )
            .addOnSuccessListener { location ->

                if (location == null) {

                    /*
                     * اگر موقعیت تازه در دسترس نبود،
                     * آخرین موقعیت موجود را امتحان کن.
                     */
                    getLastKnownLocation()

                    return@addOnSuccessListener
                }

                sendLocation(
                    location.latitude,
                    location.longitude,
                    location.accuracy
                )
            }
            .addOnFailureListener {

                /*
                 * اگر دریافت موقعیت تازه شکست خورد،
                 * آخرین موقعیت موجود را امتحان کن.
                 */
                getLastKnownLocation()
            }
    }

    private fun getLastKnownLocation() {

        if (
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            return
        }

        fusedLocationClient
            .lastLocation
            .addOnSuccessListener { location ->

                if (location == null) {
                    return@addOnSuccessListener
                }

                /*
                 * حتی اگر accuracy ضعیف باشد،
                 * طبق درخواست تو باز هم ارسال می‌شود.
                 */
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

        val json =
            JSONObject()

        json.put(
            "latitude",
            latitude
        )

        json.put(
            "longitude",
            longitude
        )

        json.put(
            "accuracy",
            accuracy
        )

        json.put(
            "timestamp",
            System.currentTimeMillis()
        )

        val mediaType =
            "application/json; charset=utf-8"
                .toMediaType()

        val body =
            json.toString()
                .toRequestBody(
                    mediaType
                )

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

                        /*
                         * ارسال ناموفق بود.
                         * نوبت بعدی دوباره ارسال می‌شود.
                         */
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
                                Intent(
                                    ACTION_LOCATION_SENT
                                )

                            intent.setPackage(
                                packageName
                            )

                            sendBroadcast(
                                intent
                            )
                        }
                    }
                }
            )
    }

    private fun createNotification():
            Notification {

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
            .setPriority(
                NotificationCompat.PRIORITY_LOW
            )
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

            channel.description =
                "Location tracking service"

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

        /*
         * متوقف کردن ارسال‌های زمان‌بندی‌شده
         */
        handler.removeCallbacks(
            sendRunnable
        )

        /*
         * بستن HTTP client
         */
        httpClient
            .dispatcher
            .executorService
            .shutdown()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }
}
