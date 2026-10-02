package com.munjuralam.geocheck.data.local

import com.munjuralam.geocheck.domain.model.AttendanceRecord
import com.munjuralam.geocheck.domain.model.AttendanceType
import com.munjuralam.geocheck.domain.model.GeoPoint

/**
 * Compact line-based encoding for attendance records stored in DataStore.
 * Format per line: `timestamp,type,distance,latitude,longitude`.
 */
internal object AttendanceRecordCodec {
    private const val FIELD_SEPARATOR = ","
    private const val RECORD_SEPARATOR = "\n"

    fun encode(records: List<AttendanceRecord>): String =
        records.joinToString(RECORD_SEPARATOR) { r ->
            listOf(r.timestampMillis, r.type.name, r.distanceMeters, r.point.latitude, r.point.longitude)
                .joinToString(FIELD_SEPARATOR)
        }

    /** Malformed lines are skipped rather than failing the whole history. */
    fun decode(raw: String?): List<AttendanceRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(RECORD_SEPARATOR).mapNotNull { line ->
            val parts = line.split(FIELD_SEPARATOR)
            if (parts.size != 5) return@mapNotNull null
            runCatching {
                AttendanceRecord(
                    timestampMillis = parts[0].toLong(),
                    type = AttendanceType.valueOf(parts[1]),
                    distanceMeters = parts[2].toFloat(),
                    point = GeoPoint(parts[3].toDouble(), parts[4].toDouble()),
                )
            }.getOrNull()
        }
    }
}
