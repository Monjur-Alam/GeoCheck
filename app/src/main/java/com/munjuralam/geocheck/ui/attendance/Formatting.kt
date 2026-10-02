package com.munjuralam.geocheck.ui.attendance

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import com.munjuralam.geocheck.R
import com.munjuralam.geocheck.domain.model.AttendanceType
import com.munjuralam.geocheck.domain.model.GeoPoint
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

fun Context.formatDistance(meters: Float): String =
    if (meters < 1_000f) {
        getString(R.string.unit_meters, meters.roundToInt())
    } else {
        getString(R.string.unit_kilometers, meters / 1_000f)
    }

fun Context.formatTime(millis: Long): String = DateFormat.getTimeFormat(this).format(Date(millis))

fun Context.formatMinutesOfDay(minutes: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minutes / 60)
        set(Calendar.MINUTE, minutes % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return DateFormat.getTimeFormat(this).format(calendar.time)
}

fun formatLatLon(point: GeoPoint): String =
    String.format(Locale.US, "Lat: %.4f, Lon: %.4f", point.latitude, point.longitude)

/** Compact label used inside the distance ring, e.g. "120m". */
fun formatRingDistance(meters: Float): String {
    val rounded = meters.roundToInt()
    return if (rounded < 1_000) "${rounded}m" else String.format(Locale.US, "%.1fkm", meters / 1_000f)
}

fun Context.formatDateTime(millis: Long): String = DateUtils.formatDateTime(
    this,
    millis,
    DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
)

fun Context.resolve(message: AttendanceMessage, radiusMeters: Float): String = when (message) {
    AttendanceMessage.OfficeSaved -> getString(R.string.msg_office_saved)
    AttendanceMessage.ScheduleSaved -> getString(R.string.msg_schedule_saved)
    AttendanceMessage.InvalidWindow -> getString(R.string.msg_invalid_window)
    AttendanceMessage.MockLocationBlocked -> getString(R.string.msg_mock_blocked)
    is AttendanceMessage.AttendanceMarked -> getString(
        if (message.record.type == AttendanceType.CHECK_IN) R.string.msg_checked_in else R.string.msg_checked_out,
        formatTime(message.record.timestampMillis),
        formatDistance(message.record.distanceMeters),
    )
    is AttendanceMessage.OutsideZone -> getString(
        R.string.msg_outside_zone,
        formatDistance(message.distanceMeters),
        radiusMeters.roundToInt(),
    )
    is AttendanceMessage.OutsideWindow -> getString(
        if (message.type == AttendanceType.CHECK_IN) R.string.msg_outside_check_in else R.string.msg_outside_check_out,
        formatMinutesOfDay(message.window.startMinutes),
        formatMinutesOfDay(message.window.endMinutes),
    )
    is AttendanceMessage.LowAccuracy ->
        getString(R.string.msg_low_accuracy, formatDistance(message.accuracyMeters))
    AttendanceMessage.NoOfficeSet -> getString(R.string.msg_no_office)
    AttendanceMessage.WaitingForGps -> getString(R.string.msg_waiting_gps)
    AttendanceMessage.Failure -> getString(R.string.msg_failure)
}
