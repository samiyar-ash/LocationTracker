package com.example.locationtracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

```
private val permissionLauncher =
    registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->

        val allGranted = permissions.values.all { it }

        if (allGranted) {
            startLocationService()
        }
    }

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    requestPermissions()
}

private fun requestPermissions() {

    val permissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        permissions.add(
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )
    }

    val missingPermissions = permissions.filter { permission ->
        ContextCompat.checkSelfPermission(
            this,
            permission
        ) != PackageManager.PERMISSION_GRANTED
    }

    if (missingPermissions.isEmpty()) {
        startLocationService()
    } else {
        permissionLauncher.launch(
            missingPermissions.toTypedArray()
        )
    }
}

private fun startLocationService() {

    val intent = Intent(
        this,
        LocationService::class.java
    )

    ContextCompat.startForegroundService(
        this,
        intent
    )
}
```

}
