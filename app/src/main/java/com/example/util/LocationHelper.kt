package com.example.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

object LocationHelper {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundPermission(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun isGpsEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /** Best effort current location: fresh fix (8s) -> last known -> null. */
    @Suppress("MissingPermission")
    suspend fun getCurrent(context: Context): Location? {
        if (!hasPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        return try {
            val cts = CancellationTokenSource()
            val fresh = withTimeoutOrNull(8000) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
            }
            if (fresh == null) cts.cancel()
            fresh ?: client.lastLocation.await()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun mapsLink(lat: Double, lng: Double) = "https://maps.google.com/?q=$lat,$lng"
}
