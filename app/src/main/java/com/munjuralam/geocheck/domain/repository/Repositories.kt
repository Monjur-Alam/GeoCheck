package com.munjuralam.geocheck.domain.repository

import com.munjuralam.geocheck.domain.model.AttendanceRecord
import com.munjuralam.geocheck.domain.model.AttendanceSchedule
import com.munjuralam.geocheck.domain.model.LocationSample
import com.munjuralam.geocheck.domain.model.OfficeLocation
import kotlinx.coroutines.flow.Flow

interface AttendanceRepository {
    val officeLocation: Flow<OfficeLocation?>
    val attendanceRecords: Flow<List<AttendanceRecord>>
    val schedule: Flow<AttendanceSchedule>

    suspend fun saveOfficeLocation(office: OfficeLocation)
    suspend fun addAttendanceRecord(record: AttendanceRecord)
    suspend fun saveSchedule(schedule: AttendanceSchedule)
}

interface LocationTracker {
    /** Emits whether device location services (GPS / network provider) are switched on. */
    val isLocationServiceEnabled: Flow<Boolean>

    /** Continuous high-accuracy updates. Requires location permission. */
    fun locationUpdates(): Flow<LocationSample>

    /** A single fresh high-accuracy fix, or `null` if none could be obtained. */
    suspend fun currentLocation(): LocationSample?
}
