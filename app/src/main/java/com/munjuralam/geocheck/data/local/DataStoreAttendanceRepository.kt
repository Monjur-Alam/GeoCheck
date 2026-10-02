package com.munjuralam.geocheck.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.munjuralam.geocheck.domain.model.AttendanceRecord
import com.munjuralam.geocheck.domain.model.AttendanceSchedule
import com.munjuralam.geocheck.domain.model.GeoPoint
import com.munjuralam.geocheck.domain.model.OfficeLocation
import com.munjuralam.geocheck.domain.model.TimeWindow
import com.munjuralam.geocheck.domain.repository.AttendanceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

class DataStoreAttendanceRepository(
    private val dataStore: DataStore<Preferences>,
) : AttendanceRepository {

    private val preferences: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val officeLocation: Flow<OfficeLocation?> = preferences
        .map { prefs ->
            val lat = prefs[Keys.OFFICE_LAT] ?: return@map null
            val lng = prefs[Keys.OFFICE_LNG] ?: return@map null
            OfficeLocation(
                point = GeoPoint(lat, lng),
                accuracyMeters = prefs[Keys.OFFICE_ACCURACY] ?: 0f,
                savedAtMillis = prefs[Keys.OFFICE_SAVED_AT] ?: 0L,
            )
        }
        .distinctUntilChanged()

    override val attendanceRecords: Flow<List<AttendanceRecord>> = preferences
        .map { AttendanceRecordCodec.decode(it[Keys.RECORDS]) }
        .distinctUntilChanged()

    override suspend fun saveOfficeLocation(office: OfficeLocation) {
        dataStore.edit { prefs ->
            prefs[Keys.OFFICE_LAT] = office.point.latitude
            prefs[Keys.OFFICE_LNG] = office.point.longitude
            prefs[Keys.OFFICE_ACCURACY] = office.accuracyMeters
            prefs[Keys.OFFICE_SAVED_AT] = office.savedAtMillis
        }
    }

    override val schedule: Flow<AttendanceSchedule> = preferences
        .map { prefs ->
            val fallback = AttendanceSchedule.Default
            AttendanceSchedule(
                checkIn = windowOr(
                    prefs[Keys.CHECK_IN_START],
                    prefs[Keys.CHECK_IN_END],
                    fallback.checkIn,
                ),
                checkOut = windowOr(
                    prefs[Keys.CHECK_OUT_START],
                    prefs[Keys.CHECK_OUT_END],
                    fallback.checkOut,
                ),
            )
        }
        .distinctUntilChanged()

    override suspend fun saveSchedule(schedule: AttendanceSchedule) {
        dataStore.edit { prefs ->
            prefs[Keys.CHECK_IN_START] = schedule.checkIn.startMinutes
            prefs[Keys.CHECK_IN_END] = schedule.checkIn.endMinutes
            prefs[Keys.CHECK_OUT_START] = schedule.checkOut.startMinutes
            prefs[Keys.CHECK_OUT_END] = schedule.checkOut.endMinutes
        }
    }

    override suspend fun addAttendanceRecord(record: AttendanceRecord) {
        dataStore.edit { prefs ->
            val updated = (listOf(record) + AttendanceRecordCodec.decode(prefs[Keys.RECORDS]))
                .take(MAX_STORED_RECORDS)
            prefs[Keys.RECORDS] = AttendanceRecordCodec.encode(updated)
        }
    }

    private object Keys {
        val OFFICE_LAT = doublePreferencesKey("office_lat")
        val OFFICE_LNG = doublePreferencesKey("office_lng")
        val OFFICE_ACCURACY = floatPreferencesKey("office_accuracy")
        val OFFICE_SAVED_AT = longPreferencesKey("office_saved_at")
        val RECORDS = stringPreferencesKey("attendance_records")
        val CHECK_IN_START = intPreferencesKey("check_in_start")
        val CHECK_IN_END = intPreferencesKey("check_in_end")
        val CHECK_OUT_START = intPreferencesKey("check_out_start")
        val CHECK_OUT_END = intPreferencesKey("check_out_end")
    }

    private fun windowOr(start: Int?, end: Int?, fallback: TimeWindow): TimeWindow {
        if (start == null || end == null) return fallback
        val window = TimeWindow(start, end)
        return if (window.isValid()) window else fallback
    }

    private companion object {
        const val MAX_STORED_RECORDS = 100
    }
}
