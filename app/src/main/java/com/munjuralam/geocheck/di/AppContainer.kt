package com.munjuralam.geocheck.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.munjuralam.geocheck.data.local.DataStoreAttendanceRepository
import com.munjuralam.geocheck.data.location.FusedLocationTracker
import com.munjuralam.geocheck.domain.geofence.GeofenceEvaluator
import com.munjuralam.geocheck.domain.repository.AttendanceRepository
import com.munjuralam.geocheck.domain.repository.LocationTracker

private val Context.attendanceDataStore by preferencesDataStore(name = "geocheck_attendance")

/** Manual DI container; one instance lives for the lifetime of the process. */
class AppContainer(context: Context) {
    val attendanceRepository: AttendanceRepository =
        DataStoreAttendanceRepository(context.applicationContext.attendanceDataStore)
    val locationTracker: LocationTracker = FusedLocationTracker(context)
    val geofenceEvaluator = GeofenceEvaluator()
}
