package com.munjuralam.geocheck.domain.geofence

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceRingScaleTest {

    @Test
    fun `the ring always fills against 300 meters`() {
        assertEquals(0f, DistanceRingScale.fillFraction(0f), 0.001f)
        assertEquals(25f / 300f, DistanceRingScale.fillFraction(25f), 0.001f)
        assertEquals(0.5f, DistanceRingScale.fillFraction(150f), 0.001f)
        assertEquals(1f, DistanceRingScale.fillFraction(300f), 0.001f)
        assertEquals(1f, DistanceRingScale.fillFraction(2_000f), 0.001f)
    }
}