package com.munjuralam.geocheck.ui.attendance

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/**
 * Reads the current permission state. "Permanently denied" can only be detected right after
 * a request, when the system no longer wants us to show a rationale.
 */
fun Activity.locationPermissionStatus(afterRequest: Boolean): PermissionStatus {
    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    return when {
        granted(Manifest.permission.ACCESS_FINE_LOCATION) -> PermissionStatus.Granted
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) -> PermissionStatus.ApproximateOnly
        afterRequest && !ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) -> PermissionStatus.PermanentlyDenied
        else -> PermissionStatus.Denied
    }
}

@Composable
fun rememberLocationPermissionRequester(onResult: (PermissionStatus) -> Unit): () -> Unit {
    val activity = LocalActivity.current
    val currentOnResult = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        activity?.let { currentOnResult.value(it.locationPermissionStatus(afterRequest = true)) }
    }
    return remember(launcher) { { launcher.launch(LOCATION_PERMISSIONS) } }
}

/** Shows the Play services "turn on location" dialog, falling back to system settings. */
@Composable
fun rememberEnableLocationRequester(onUnavailable: () -> Unit): () -> Unit {
    val activity = LocalActivity.current
    val currentOnUnavailable = rememberUpdatedState(onUnavailable)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { }

    return remember<() -> Unit>(activity, launcher) {
        request@{
            val host = activity ?: return@request currentOnUnavailable.value()
            val settingsRequest = LocationSettingsRequest.Builder()
                .addLocationRequest(
                    LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L).build(),
                )
                .setAlwaysShow(true)
                .build()
            LocationServices.getSettingsClient(host)
                .checkLocationSettings(settingsRequest)
                .addOnFailureListener { e ->
                    val launched = e is ResolvableApiException && runCatching {
                        launcher.launch(IntentSenderRequest.Builder(e.resolution).build())
                    }.isSuccess
                    if (!launched && !host.tryStart(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))) {
                        currentOnUnavailable.value()
                    }
                }
        }
    }
}

@Composable
fun rememberOpenAppSettings(): () -> Unit {
    val context = LocalContext.current
    return remember<() -> Unit>(context) {
        {
            context.tryStart(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

private fun Context.tryStart(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}
