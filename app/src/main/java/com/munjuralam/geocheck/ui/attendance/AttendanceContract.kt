package com.munjuralam.geocheck.ui.attendance

import com.munjuralam.geocheck.domain.geofence.GeofenceEvaluator
import com.munjuralam.geocheck.domain.geofence.GeofenceStatus
import com.munjuralam.geocheck.domain.geofence.isWithinOffice
import com.munjuralam.geocheck.domain.model.AttendanceRecord
import com.munjuralam.geocheck.domain.model.AttendanceSchedule
import com.munjuralam.geocheck.domain.model.AttendanceType
import com.munjuralam.geocheck.domain.model.LocationSample
import com.munjuralam.geocheck.domain.model.OfficeLocation
import com.munjuralam.geocheck.domain.model.TimeWindow

enum class PermissionStatus { NotRequested, Granted, ApproximateOnly, Denied, PermanentlyDenied }

data class AttendanceUiState(
    val permission: PermissionStatus = PermissionStatus.NotRequested,
    val isLocationServiceEnabled: Boolean = true,
    val isTrackingFailed: Boolean = false,
    val office: OfficeLocation? = null,
    val currentLocation: LocationSample? = null,
    val geofence: GeofenceStatus = GeofenceStatus.NoOffice,
    val radiusMeters: Float = GeofenceEvaluator.DEFAULT_RADIUS_METERS,
    val todayRecords: List<AttendanceRecord> = emptyList(),
    val nextAttendanceType: AttendanceType = AttendanceType.CHECK_IN,
    val schedule: AttendanceSchedule = AttendanceSchedule.Default,
    val activeWindow: TimeWindow = AttendanceSchedule.Default.checkIn,
    val isWithinWindow: Boolean = false,
    val isMarkingAttendance: Boolean = false,
) {
    val canMarkAttendance: Boolean
        get() = geofence.isWithinOffice(radiusMeters) && isWithinWindow && !isMarkingAttendance
}

sealed interface AttendanceIntent {
    /** [fromUserRequest] is true when the status comes straight from the system permission dialog. */
    data class PermissionChecked(val status: PermissionStatus, val fromUserRequest: Boolean) : AttendanceIntent

    /** Office pin chosen on the map (or snapped there with the current-location button). */
    data class SaveOfficePoint(val latitude: Double, val longitude: Double) : AttendanceIntent
    data class SaveSchedule(val schedule: AttendanceSchedule) : AttendanceIntent
    data object MarkAttendance : AttendanceIntent
    data object RetryTracking : AttendanceIntent
}

sealed interface AttendanceEvent {
    data class ShowMessage(val message: AttendanceMessage) : AttendanceEvent
    data object RequestPermission : AttendanceEvent
    data object RequestEnableLocation : AttendanceEvent
}

sealed interface AttendanceMessage {
    data object OfficeSaved : AttendanceMessage
    data object ScheduleSaved : AttendanceMessage
    data object InvalidWindow : AttendanceMessage
    data object MockLocationBlocked : AttendanceMessage
    data class AttendanceMarked(val record: AttendanceRecord) : AttendanceMessage
    data class OutsideZone(val distanceMeters: Float) : AttendanceMessage
    data class OutsideWindow(val type: AttendanceType, val window: TimeWindow) : AttendanceMessage
    data class LowAccuracy(val accuracyMeters: Float) : AttendanceMessage
    data object NoOfficeSet : AttendanceMessage
    data object WaitingForGps : AttendanceMessage
    data object Failure : AttendanceMessage
}
