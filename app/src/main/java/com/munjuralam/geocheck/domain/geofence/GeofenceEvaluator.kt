package com.munjuralam.geocheck.domain.geofence

import com.munjuralam.geocheck.domain.model.LocationSample
import com.munjuralam.geocheck.domain.model.OfficeLocation

sealed interface GeofenceStatus {
    data object NoOffice : GeofenceStatus
    data object AwaitingFix : GeofenceStatus

    sealed interface Measured : GeofenceStatus {
        val distanceMeters: Float
    }

    data class Inside(override val distanceMeters: Float) : Measured
    data class Outside(override val distanceMeters: Float) : Measured

    /** Inside the radius on paper, but the fix is too imprecise to trust. */
    data class LowAccuracy(override val distanceMeters: Float, val accuracyMeters: Float) : Measured

    data class MockLocation(override val distanceMeters: Float) : Measured
}

/** True when the fix is inside the office radius, including a weak or mocked reading. */
fun GeofenceStatus.isWithinOffice(radiusMeters: Float): Boolean = when (this) {
    is GeofenceStatus.Inside, is GeofenceStatus.LowAccuracy -> true
    is GeofenceStatus.MockLocation -> distanceMeters <= radiusMeters
    else -> false
}

class GeofenceEvaluator(
    val radiusMeters: Float = DEFAULT_RADIUS_METERS,
    private val maxAccuracyMeters: Float = DEFAULT_MAX_ACCURACY_METERS,
) {
    fun evaluate(office: OfficeLocation?, sample: LocationSample?): GeofenceStatus {
        if (office == null) return GeofenceStatus.NoOffice
        if (sample == null) return GeofenceStatus.AwaitingFix

        val distance = GeoDistance.metersBetween(office.point, sample.point).toFloat()
        return when {
            sample.isMock -> GeofenceStatus.MockLocation(distance)
            distance > radiusMeters -> GeofenceStatus.Outside(distance)
            sample.accuracyMeters > maxAccuracyMeters ->
                GeofenceStatus.LowAccuracy(distance, sample.accuracyMeters)
            else -> GeofenceStatus.Inside(distance)
        }
    }

    fun isAccurateEnough(sample: LocationSample): Boolean = sample.accuracyMeters <= maxAccuracyMeters

    companion object {
        const val DEFAULT_RADIUS_METERS = 50f
        const val DEFAULT_MAX_ACCURACY_METERS = 30f
    }
}
