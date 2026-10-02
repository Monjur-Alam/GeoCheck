package com.munjuralam.geocheck.ui.attendance

import com.munjuralam.geocheck.domain.geofence.GeofenceEvaluator
import com.munjuralam.geocheck.domain.geofence.GeofenceStatus
import com.munjuralam.geocheck.domain.model.AttendanceRecord
import com.munjuralam.geocheck.domain.model.AttendanceSchedule
import com.munjuralam.geocheck.domain.model.AttendanceType
import com.munjuralam.geocheck.domain.model.GeoPoint
import com.munjuralam.geocheck.domain.model.LocationSample
import com.munjuralam.geocheck.domain.model.OfficeLocation
import com.munjuralam.geocheck.domain.model.TimeWindow
import com.munjuralam.geocheck.domain.repository.AttendanceRepository
import com.munjuralam.geocheck.domain.repository.LocationTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModelTest {

    private val officePoint = GeoPoint(23.8103, 90.4125)
    private val now = 1_700_000_000_000L

    private lateinit var repository: FakeRepository
    private lateinit var tracker: FakeTracker
    private lateinit var viewModel: AttendanceViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = FakeRepository()
        tracker = FakeTracker()
        viewModel = AttendanceViewModel(repository, tracker, GeofenceEvaluator(), clock = { now })
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun northOfOffice(meters: Double) =
        GeoPoint(officePoint.latitude + meters / 111_195.0, officePoint.longitude)

    private fun sample(point: GeoPoint, accuracy: Float = 5f) = LocationSample(point, accuracy, now, isMock = false)

    private fun grantPermission() =
        viewModel.onIntent(AttendanceIntent.PermissionChecked(PermissionStatus.Granted, fromUserRequest = true))

    @Test
    fun `map point is saved as the office`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.onIntent(AttendanceIntent.SaveOfficePoint(officePoint.latitude, officePoint.longitude))
        advanceUntilIdle()

        assertEquals(officePoint, repository.office.value?.point)
    }

    @Test
    fun `invalid schedule is rejected`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val original = repository.scheduleState.value

        viewModel.onIntent(
            AttendanceIntent.SaveSchedule(
                AttendanceSchedule(TimeWindow(10 * 60, 9 * 60), TimeWindow(17 * 60, 18 * 60)),
            ),
        )

        assertEquals(original, repository.scheduleState.value)
        val event = viewModel.events.first()
        assertTrue((event as AttendanceEvent.ShowMessage).message is AttendanceMessage.InvalidWindow)
    }

    @Test
    fun `attendance disabled when outside radius`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        repository.office.value = OfficeLocation(officePoint, 5f, now)
        grantPermission()
        tracker.updates.emit(sample(northOfOffice(120.0)))

        val state = viewModel.uiState.value
        assertTrue(state.geofence is GeofenceStatus.Outside)
        assertFalse(state.canMarkAttendance)

        viewModel.onIntent(AttendanceIntent.MarkAttendance)
        assertTrue(repository.records.value.isEmpty())
    }

    @Test
    fun `check-in hours only record check-in even after one is saved`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val minute = minuteOf(now)
        repository.scheduleState.value = AttendanceSchedule(
            checkIn = windowAround(minute),
            checkOut = windowAwayFrom(minute),
        )
        repository.office.value = OfficeLocation(officePoint, 5f, now)
        grantPermission()
        tracker.updates.emit(sample(northOfOffice(20.0)))

        assertEquals(AttendanceType.CHECK_IN, viewModel.uiState.value.nextAttendanceType)
        assertTrue(viewModel.uiState.value.canMarkAttendance)

        viewModel.onIntent(AttendanceIntent.MarkAttendance)
        advanceUntilIdle()
        assertEquals(AttendanceType.CHECK_IN, repository.records.value.first().type)
        assertEquals(AttendanceType.CHECK_IN, viewModel.uiState.value.nextAttendanceType)
        assertTrue(viewModel.uiState.value.canMarkAttendance)

        viewModel.onIntent(AttendanceIntent.MarkAttendance)
        advanceUntilIdle()
        assertEquals(AttendanceType.CHECK_IN, repository.records.value.first().type)
    }

    @Test
    fun `check-out hours record check-out and not check-in`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val minute = minuteOf(now)
        repository.scheduleState.value = AttendanceSchedule(
            checkIn = windowAwayFrom(minute),
            checkOut = windowAround(minute),
        )
        repository.office.value = OfficeLocation(officePoint, 5f, now)
        grantPermission()
        tracker.updates.emit(sample(northOfOffice(20.0)))

        assertEquals(AttendanceType.CHECK_OUT, viewModel.uiState.value.nextAttendanceType)
        assertTrue(viewModel.uiState.value.canMarkAttendance)

        viewModel.onIntent(AttendanceIntent.MarkAttendance)
        advanceUntilIdle()
        assertEquals(AttendanceType.CHECK_OUT, repository.records.value.first().type)
    }

    @Test
    fun `location services off requests enabling`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        tracker.enabled.value = false
        grantPermission()

        viewModel.onIntent(AttendanceIntent.MarkAttendance)

        assertEquals(AttendanceEvent.RequestEnableLocation, viewModel.events.first())
    }

    @Test
    fun `weak or mocked fix inside 50 meters can mark during open hours`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        repository.scheduleState.value = allDay()
        repository.office.value = OfficeLocation(officePoint, 5f, now)
        grantPermission()
        tracker.updates.emit(sample(northOfOffice(20.0), accuracy = 80f))

        assertTrue(viewModel.uiState.value.canMarkAttendance)

        tracker.updates.emit(sample(northOfOffice(10.0)).copy(isMock = true))
        assertTrue(viewModel.uiState.value.canMarkAttendance)
    }

    @Test
    fun `changed check-in hours become the applicable window`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val minute = Calendar.getInstance().apply { timeInMillis = now }.let {
            it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
        }
        val start = (minute - 15).coerceAtLeast(0)
        val end = (minute + 15).coerceAtMost(23 * 60 + 59).let { if (it > start) it else start + 1 }
        val checkIn = TimeWindow(start, end)

        viewModel.onIntent(
            AttendanceIntent.SaveSchedule(AttendanceSchedule(checkIn, TimeWindow(17 * 60, 18 * 60))),
        )
        advanceUntilIdle()

        assertEquals(checkIn, viewModel.uiState.value.activeWindow)
        assertTrue(viewModel.uiState.value.isWithinWindow)
    }

    @Test
    fun `inside radius but outside hours cannot mark`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        val minute = Calendar.getInstance().apply { timeInMillis = now }.let {
            it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
        }
        val blocked = if (minute < 12 * 60) TimeWindow(12 * 60, 13 * 60) else TimeWindow(0, 60)
        repository.scheduleState.value = AttendanceSchedule(blocked, blocked)
        repository.office.value = OfficeLocation(officePoint, 5f, now)
        grantPermission()
        tracker.updates.emit(sample(northOfOffice(20.0)))

        assertTrue(viewModel.uiState.value.geofence is GeofenceStatus.Inside)
        assertFalse(viewModel.uiState.value.canMarkAttendance)

        viewModel.onIntent(AttendanceIntent.MarkAttendance)
        advanceUntilIdle()
        assertTrue(repository.records.value.isEmpty())
        val event = viewModel.events.first()
        assertTrue((event as AttendanceEvent.ShowMessage).message is AttendanceMessage.OutsideWindow)
    }

    @Test
    fun `passive recheck keeps permanently denied state`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.onIntent(AttendanceIntent.PermissionChecked(PermissionStatus.PermanentlyDenied, true))
        viewModel.onIntent(AttendanceIntent.PermissionChecked(PermissionStatus.Denied, false))

        assertEquals(PermissionStatus.PermanentlyDenied, viewModel.uiState.value.permission)
    }

    private fun minuteOf(millis: Long): Int = Calendar.getInstance().apply { timeInMillis = millis }.let {
        it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
    }

    private fun windowAround(minute: Int): TimeWindow {
        val start = (minute - 15).coerceAtLeast(0)
        val end = (minute + 15).coerceAtMost(23 * 60 + 59).let { if (it > start) it else start + 1 }
        return TimeWindow(start, end)
    }

    private fun windowAwayFrom(minute: Int): TimeWindow =
        if (minute < 12 * 60) TimeWindow(12 * 60, 13 * 60) else TimeWindow(0, 60)

    private fun allDay(): AttendanceSchedule {
        val window = TimeWindow(0, 23 * 60 + 59)
        return AttendanceSchedule(window, window)
    }

    private class FakeRepository : AttendanceRepository {
        val office = MutableStateFlow<OfficeLocation?>(null)
        val records = MutableStateFlow<List<AttendanceRecord>>(emptyList())
        val scheduleState = MutableStateFlow(AttendanceSchedule.Default)

        override val officeLocation: Flow<OfficeLocation?> = office
        override val attendanceRecords: Flow<List<AttendanceRecord>> = records.map { it }
        override val schedule: Flow<AttendanceSchedule> = scheduleState

        override suspend fun saveOfficeLocation(office: OfficeLocation) {
            this.office.value = office
        }

        override suspend fun addAttendanceRecord(record: AttendanceRecord) {
            records.value = listOf(record) + records.value
        }

        override suspend fun saveSchedule(schedule: AttendanceSchedule) {
            scheduleState.value = schedule
        }
    }

    private class FakeTracker : LocationTracker {
        val enabled = MutableStateFlow(true)
        val updates = MutableSharedFlow<LocationSample>(replay = 1)
        var current: LocationSample? = null

        override val isLocationServiceEnabled: Flow<Boolean> = enabled
        override fun locationUpdates(): Flow<LocationSample> = updates
        override suspend fun currentLocation(): LocationSample? = current
    }
}
