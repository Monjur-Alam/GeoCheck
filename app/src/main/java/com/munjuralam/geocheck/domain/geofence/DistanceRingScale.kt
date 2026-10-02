package com.munjuralam.geocheck.domain.geofence

/**
 * One distance ring for every reading. The scale always runs to 300 m.
 * Colour is chosen separately: green under 50 m, red beyond that.
 */
object DistanceRingScale {
    const val MAX_METERS = 300f

    fun fillFraction(distanceMeters: Float): Float =
        (distanceMeters / MAX_METERS).coerceIn(0f, 1f)
}