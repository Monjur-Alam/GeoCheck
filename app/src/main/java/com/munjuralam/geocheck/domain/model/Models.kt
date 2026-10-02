package com.munjuralam.geocheck.domain.model

data class GeoPoint(val latitude: Double, val longitude: Double)

/** A single GPS fix as reported by the device. */
data class LocationSample(
    val point: GeoPoint,
    val accuracyMeters: Float,
    val timestampMillis: Long,
    val isMock: Boolean,
)

data class OfficeLocation(
    val point: GeoPoint,
    val accuracyMeters: Float,
    val savedAtMillis: Long,
)

enum class AttendanceType { CHECK_IN, CHECK_OUT }

data class AttendanceRecord(
    val timestampMillis: Long,
    val type: AttendanceType,
    val distanceMeters: Float,
    val point: GeoPoint,
)

/** Minutes from midnight, both inclusive. The end must be later than the start. */
data class TimeWindow(val startMinutes: Int, val endMinutes: Int) {
    fun contains(minuteOfDay: Int): Boolean = minuteOfDay in startMinutes..endMinutes

    fun isValid(): Boolean =
        startMinutes in DAY_MINUTES && endMinutes in DAY_MINUTES && endMinutes > startMinutes

    private companion object {
        val DAY_MINUTES = 0..1_439
    }
}

data class AttendanceSchedule(
    val checkIn: TimeWindow,
    val checkOut: TimeWindow,
) {
    /**
     * The open window decides the action. Check-in hours always mark check-in,
     * even if a check-in was already saved. Check-out hours mark check-out only
     * when check-in hours are closed.
     */
    fun slotAt(minuteOfDay: Int): AttendanceSlot {
        val checkInOpen = checkIn.contains(minuteOfDay)
        val checkOutOpen = checkOut.contains(minuteOfDay)
        return when {
            checkInOpen -> AttendanceSlot(AttendanceType.CHECK_IN, checkIn, isOpen = true)
            checkOutOpen -> AttendanceSlot(AttendanceType.CHECK_OUT, checkOut, isOpen = true)
            minuteOfDay < checkIn.startMinutes -> AttendanceSlot(AttendanceType.CHECK_IN, checkIn, isOpen = false)
            minuteOfDay < checkOut.startMinutes -> AttendanceSlot(AttendanceType.CHECK_OUT, checkOut, isOpen = false)
            else -> AttendanceSlot(AttendanceType.CHECK_IN, checkIn, isOpen = false)
        }
    }

    companion object {
        val Default = AttendanceSchedule(
            checkIn = TimeWindow(startMinutes = 9 * 60, endMinutes = 10 * 60 + 30),
            checkOut = TimeWindow(startMinutes = 17 * 60, endMinutes = 19 * 60),
        )
    }
}

data class AttendanceSlot(
    val type: AttendanceType,
    val window: TimeWindow,
    val isOpen: Boolean,
)
