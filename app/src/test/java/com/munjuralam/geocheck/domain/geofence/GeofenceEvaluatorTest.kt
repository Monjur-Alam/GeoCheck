package com.munjuralam.geocheck.domain.geofence

import com.munjuralam.geocheck.domain.model.GeoPoint
import com.munjuralam.geocheck.domain.model.LocationSample
import com.munjuralam.geocheck.domain.model.OfficeLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceEvaluatorTest {

    private val evaluator = GeofenceEvaluator()
    private val officePoint = GeoPoint(23.8103, 90.4125)
    private val office = OfficeLocation(officePoint, accuracyMeters = 5f, savedAtMillis = 0L)

    /** Roughly [meters] north of the office (1 degree of latitude is about 111.2 km). */
    private fun northOfOffice(meters: Double) = GeoPoint(officePoint.latitude + meters / 111_195.0, officePoint.longitude)

    private fun sample(point: GeoPoint, accuracy: Float = 5f, mock: Boolean = false) =
        LocationSample(point, accuracy, timestampMillis = 0L, isMock = mock)

    @Test
    fun `haversine distance matches known value`() {
        // Dhaka to Chattogram is roughly 213 km as the crow flies.
        val meters = GeoDistance.metersBetween(GeoPoint(23.8103, 90.4125), GeoPoint(22.3569, 91.7832))
        assertEquals(213_000.0, meters, 3_000.0)
    }

    @Test
    fun `same point is zero meters`() {
        assertEquals(0.0, GeoDistance.metersBetween(officePoint, officePoint), 1e-9)
    }

    @Test
    fun `no office yields NoOffice`() {
        assertEquals(GeofenceStatus.NoOffice, evaluator.evaluate(null, sample(officePoint)))
    }

    @Test
    fun `no fix yields AwaitingFix`() {
        assertEquals(GeofenceStatus.AwaitingFix, evaluator.evaluate(office, null))
    }

    @Test
    fun `within 50 m is Inside`() {
        val status = evaluator.evaluate(office, sample(northOfOffice(30.0)))
        assertTrue(status is GeofenceStatus.Inside)
        assertEquals(30f, (status as GeofenceStatus.Inside).distanceMeters, 0.5f)
    }

    @Test
    fun `beyond 50 m is Outside`() {
        val status = evaluator.evaluate(office, sample(northOfOffice(120.0)))
        assertTrue(status is GeofenceStatus.Outside)
        assertEquals(120f, (status as GeofenceStatus.Outside).distanceMeters, 0.5f)
    }

    @Test
    fun `boundary just past radius is Outside`() {
        assertTrue(evaluator.evaluate(office, sample(northOfOffice(50.5))) is GeofenceStatus.Outside)
    }

    @Test
    fun `inside radius with poor accuracy is LowAccuracy`() {
        val status = evaluator.evaluate(office, sample(northOfOffice(10.0), accuracy = 80f))
        assertTrue(status is GeofenceStatus.LowAccuracy)
    }

    @Test
    fun `mock location is flagged even when inside`() {
        val status = evaluator.evaluate(office, sample(northOfOffice(5.0), mock = true))
        assertTrue(status is GeofenceStatus.MockLocation)
    }
}
