package com.munjuralam.geocheck.data.local

import com.munjuralam.geocheck.domain.model.AttendanceRecord
import com.munjuralam.geocheck.domain.model.AttendanceType
import com.munjuralam.geocheck.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceRecordCodecTest {

    @Test
    fun `round trip preserves records`() {
        val records = listOf(
            AttendanceRecord(1_700_000_000_000, AttendanceType.CHECK_OUT, 12.5f, GeoPoint(23.81, 90.41)),
            AttendanceRecord(1_699_999_000_000, AttendanceType.CHECK_IN, 3f, GeoPoint(-33.86, 151.21)),
        )
        assertEquals(records, AttendanceRecordCodec.decode(AttendanceRecordCodec.encode(records)))
    }

    @Test
    fun `blank input decodes to empty list`() {
        assertTrue(AttendanceRecordCodec.decode(null).isEmpty())
        assertTrue(AttendanceRecordCodec.decode("").isEmpty())
    }

    @Test
    fun `malformed lines are skipped`() {
        val raw = "garbage\n1700000000000,CHECK_IN,4.0,23.81,90.41\n1,UNKNOWN,1,2,3"
        val decoded = AttendanceRecordCodec.decode(raw)
        assertEquals(1, decoded.size)
        assertEquals(AttendanceType.CHECK_IN, decoded.single().type)
    }
}
