package com.munjuralam.geocheck.ui.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.munjuralam.geocheck.GeoCheckApp
import com.munjuralam.geocheck.domain.geofence.GeofenceEvaluator
import com.munjuralam.geocheck.domain.geofence.GeofenceStatus
import com.munjuralam.geocheck.domain.geofence.isWithinOffice
import com.munjuralam.geocheck.domain.model.AttendanceRecord
import com.munjuralam.geocheck.domain.model.AttendanceSchedule
import com.munjuralam.geocheck.domain.model.GeoPoint
import com.munjuralam.geocheck.domain.model.LocationSample
import com.munjuralam.geocheck.domain.model.OfficeLocation
import com.munjuralam.geocheck.domain.repository.AttendanceRepository
import com.munjuralam.geocheck.domain.repository.LocationTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceViewModel(
    private val repository: AttendanceRepository,
    private val locationTracker: LocationTracker,
    private val geofenceEvaluator: GeofenceEvaluator,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val permission = MutableStateFlow(PermissionStatus.NotRequested)
    private val progress = MutableStateFlow(Progress())
    private val trackingFailed = MutableStateFlow(false)
    private val trackingRetry = MutableStateFlow(0)

    private val _events = Channel<AttendanceEvent>(Channel.BUFFERED)
    val events: Flow<AttendanceEvent> = _events.receiveAsFlow()

    private val serviceEnabled = locationTracker.isLocationServiceEnabled
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    /** GPS is only active while the UI is subscribed, permission is granted and location is on. */
    private val liveLocation: Flow<LocationSample?> =
        combine(permission, serviceEnabled, trackingRetry) { p, enabled, _ ->
            p == PermissionStatus.Granted && enabled
        }
            .flatMapLatest { active ->
                if (!active) {
                    flowOf(null)
                } else {
                    locationTracker.locationUpdates()
                        .map<LocationSample, LocationSample?> { it }
                        .onStart { trackingFailed.value = false }
                        .catch { e ->
                            if (e is CancellationException) throw e
                            trackingFailed.value = true
                            emit(null)
                        }
                }
            }

    private val environment = combine(
        permission,
        serviceEnabled,
        trackingFailed,
        repository.officeLocation,
        liveLocation,
    ) { perm, enabled, failed, office, sample ->
        Environment(perm, enabled, failed, office, sample)
    }

    val uiState: StateFlow<AttendanceUiState> = combine(
        environment,
        repository.attendanceRecords,
        repository.schedule,
        progress,
    ) { env, records, schedule, prog ->
        val today = records.filter { it.timestampMillis >= startOfToday() }
        val slot = schedule.slotAt(minuteOfDay())
        AttendanceUiState(
            permission = env.permission,
            isLocationServiceEnabled = env.serviceEnabled,
            isTrackingFailed = env.trackingFailed,
            office = env.office,
            currentLocation = env.sample,
            geofence = geofenceEvaluator.evaluate(env.office, env.sample),
            radiusMeters = geofenceEvaluator.radiusMeters,
            todayRecords = today,
            nextAttendanceType = slot.type,
            schedule = schedule,
            activeWindow = slot.window,
            isWithinWindow = slot.isOpen,
            isMarkingAttendance = prog.isMarkingAttendance,
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), AttendanceUiState())

    fun onIntent(intent: AttendanceIntent) {
        when (intent) {
            is AttendanceIntent.PermissionChecked -> onPermissionChecked(intent)
            is AttendanceIntent.SaveOfficePoint -> saveOfficePoint(intent)
            is AttendanceIntent.SaveSchedule -> saveSchedule(intent.schedule)
            AttendanceIntent.MarkAttendance -> markAttendance()
            AttendanceIntent.RetryTracking -> trackingRetry.update { it + 1 }
        }
    }

    private fun onPermissionChecked(intent: AttendanceIntent.PermissionChecked) {
        // A passive re-check can't tell "denied" from "permanently denied", so keep the stronger state.
        val keepPermanent = !intent.fromUserRequest &&
            intent.status == PermissionStatus.Denied &&
            permission.value == PermissionStatus.PermanentlyDenied
        if (!keepPermanent) permission.value = intent.status
    }

    private fun saveOfficePoint(intent: AttendanceIntent.SaveOfficePoint) {
        viewModelScope.launch {
            try {
                repository.saveOfficeLocation(
                    OfficeLocation(
                        point = GeoPoint(intent.latitude, intent.longitude),
                        accuracyMeters = 0f,
                        savedAtMillis = clock(),
                    ),
                )
                send(AttendanceMessage.OfficeSaved)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                send(AttendanceMessage.Failure)
            }
        }
    }

    private fun saveSchedule(schedule: AttendanceSchedule) {
        if (!schedule.checkIn.isValid() || !schedule.checkOut.isValid()) {
            send(AttendanceMessage.InvalidWindow)
            return
        }
        viewModelScope.launch {
            try {
                repository.saveSchedule(schedule)
                send(AttendanceMessage.ScheduleSaved)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                send(AttendanceMessage.Failure)
            }
        }
    }

    private fun markAttendance() {
        if (progress.value.isMarkingAttendance || !ensureLocationReady()) return
        val state = uiState.value
        when (val status = state.geofence) {
            GeofenceStatus.NoOffice -> send(AttendanceMessage.NoOfficeSet)
            GeofenceStatus.AwaitingFix -> send(AttendanceMessage.WaitingForGps)
            is GeofenceStatus.Measured -> {
                if (!status.isWithinOffice(state.radiusMeters)) {
                    return send(AttendanceMessage.OutsideZone(status.distanceMeters))
                }
                if (!state.isWithinWindow) {
                    return send(AttendanceMessage.OutsideWindow(state.nextAttendanceType, state.activeWindow))
                }
                val sample = state.currentLocation ?: return send(AttendanceMessage.WaitingForGps)
                viewModelScope.launch {
                    progress.update { it.copy(isMarkingAttendance = true) }
                    try {
                        val record = AttendanceRecord(
                            timestampMillis = clock(),
                            type = state.nextAttendanceType,
                            distanceMeters = status.distanceMeters,
                            point = sample.point,
                        )
                        repository.addAttendanceRecord(record)
                        send(AttendanceMessage.AttendanceMarked(record))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        send(AttendanceMessage.Failure)
                    } finally {
                        progress.update { it.copy(isMarkingAttendance = false) }
                    }
                }
            }
        }
    }

    private fun ensureLocationReady(): Boolean {
        val event = when {
            permission.value != PermissionStatus.Granted -> AttendanceEvent.RequestPermission
            !uiState.value.isLocationServiceEnabled -> AttendanceEvent.RequestEnableLocation
            else -> return true
        }
        _events.trySend(event)
        return false
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        timeInMillis = clock()
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun minuteOfDay(): Int = Calendar.getInstance().apply { timeInMillis = clock() }.let {
        it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
    }

    private fun send(message: AttendanceMessage) {
        _events.trySend(AttendanceEvent.ShowMessage(message))
    }

    private data class Progress(
        val isMarkingAttendance: Boolean = false,
    )

    private data class Environment(
        val permission: PermissionStatus,
        val serviceEnabled: Boolean,
        val trackingFailed: Boolean,
        val office: OfficeLocation?,
        val sample: LocationSample?,
    )

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as GeoCheckApp
                AttendanceViewModel(
                    repository = app.container.attendanceRepository,
                    locationTracker = app.container.locationTracker,
                    geofenceEvaluator = app.container.geofenceEvaluator,
                )
            }
        }
    }
}
