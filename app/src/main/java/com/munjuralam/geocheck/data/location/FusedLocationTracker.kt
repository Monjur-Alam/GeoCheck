package com.munjuralam.geocheck.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.munjuralam.geocheck.domain.model.GeoPoint
import com.munjuralam.geocheck.domain.model.LocationSample
import com.munjuralam.geocheck.domain.repository.LocationTracker
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await

class FusedLocationTracker(context: Context) : LocationTracker {

    private val appContext = context.applicationContext
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    override val isLocationServiceEnabled: Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(isLocationEnabledNow())
            }
        }
        trySend(isLocationEnabledNow())
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose { appContext.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    @SuppressLint("MissingPermission")
    override fun locationUpdates(): Flow<LocationSample> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setWaitForAccurateLocation(true)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toSample()) }
            }
        }

        fusedClient.lastLocation.addOnSuccessListener { last ->
            if (last != null && System.currentTimeMillis() - last.time < MAX_CACHED_FIX_AGE_MS) {
                trySend(last.toSample())
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            .addOnFailureListener { close(it) }

        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }.conflate()

    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): LocationSample? {
        if (!hasLocationPermission()) throw SecurityException("Location permission not granted")
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0)
            .setDurationMillis(CURRENT_LOCATION_TIMEOUT_MS)
            .build()
        val cancellation = CancellationTokenSource()
        return fusedClient.getCurrentLocation(request, cancellation.token)
            .await(cancellation)
            ?.toSample()
    }

    private fun isLocationEnabledNow(): Boolean =
        locationManager != null && LocationManagerCompat.isLocationEnabled(locationManager)

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun Location.toSample() = LocationSample(
        point = GeoPoint(latitude, longitude),
        accuracyMeters = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
        timestampMillis = time,
        isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else @Suppress("DEPRECATION") isFromMockProvider,
    )

    private companion object {
        const val UPDATE_INTERVAL_MS = 2_000L
        const val FASTEST_INTERVAL_MS = 1_000L
        const val MAX_CACHED_FIX_AGE_MS = 30_000L
        const val CURRENT_LOCATION_TIMEOUT_MS = 20_000L
    }
}
