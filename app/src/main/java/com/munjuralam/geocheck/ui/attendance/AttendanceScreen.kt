package com.munjuralam.geocheck.ui.attendance

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.munjuralam.geocheck.R
import com.munjuralam.geocheck.domain.geofence.GeofenceStatus
import com.munjuralam.geocheck.domain.model.AttendanceSchedule
import com.munjuralam.geocheck.domain.model.GeoPoint
import com.munjuralam.geocheck.domain.model.LocationSample
import com.munjuralam.geocheck.domain.model.OfficeLocation
import com.munjuralam.geocheck.ui.attendance.components.AttendanceMarkSection
import com.munjuralam.geocheck.ui.attendance.components.OfficeContextCard
import com.munjuralam.geocheck.ui.attendance.components.OfficeMapPicker
import com.munjuralam.geocheck.ui.attendance.components.ScheduleDialog
import com.munjuralam.geocheck.ui.attendance.components.ScreenBackground
import com.munjuralam.geocheck.ui.attendance.components.TodayActivityCard
import com.munjuralam.geocheck.ui.theme.GeoCheckTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AttendanceRoute(
    viewModel: AttendanceViewModel = viewModel(factory = AttendanceViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentState by rememberUpdatedState(state)
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var hasAutoRequested by rememberSaveable { mutableStateOf(false) }

    val requestPermission = rememberLocationPermissionRequester { status ->
        viewModel.onIntent(AttendanceIntent.PermissionChecked(status, fromUserRequest = true))
    }
    val openAppSettings = rememberOpenAppSettings()
    val requestEnableLocation = rememberEnableLocationRequester {
        scope.launch {
            snackbarHostState.showSnackbar(context.getString(R.string.msg_location_settings_unavailable))
        }
    }
    val onPermissionAction: () -> Unit = {
        if (currentState.permission == PermissionStatus.PermanentlyDenied) openAppSettings() else requestPermission()
    }

    LifecycleResumeEffect(activity) {
        activity?.locationPermissionStatus(afterRequest = false)?.let { status ->
            viewModel.onIntent(AttendanceIntent.PermissionChecked(status, fromUserRequest = false))
            if (status != PermissionStatus.Granted && !hasAutoRequested) {
                hasAutoRequested = true
                requestPermission()
            }
        }
        onPauseOrDispose { }
    }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    is AttendanceEvent.ShowMessage -> launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(
                            context.resolve(event.message, currentState.radiusMeters),
                        )
                    }
                    AttendanceEvent.RequestPermission -> onPermissionAction()
                    AttendanceEvent.RequestEnableLocation -> requestEnableLocation()
                }
            }
        }
    }

    AttendanceScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onSaveOffice = { point ->
            viewModel.onIntent(AttendanceIntent.SaveOfficePoint(point.latitude, point.longitude))
        },
        onMarkAttendance = { viewModel.onIntent(AttendanceIntent.MarkAttendance) },
        onSaveSchedule = { viewModel.onIntent(AttendanceIntent.SaveSchedule(it)) },
        onPermissionAction = onPermissionAction,
        onEnableLocation = requestEnableLocation,
        onRetryTracking = { viewModel.onIntent(AttendanceIntent.RetryTracking) },
        onCurrentLocationMissing = {
            when {
                currentState.permission != PermissionStatus.Granted -> onPermissionAction()
                !currentState.isLocationServiceEnabled -> requestEnableLocation()
                else -> scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.msg_current_location_unavailable))
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    state: AttendanceUiState,
    snackbarHostState: SnackbarHostState,
    onSaveOffice: (GeoPoint) -> Unit,
    onMarkAttendance: () -> Unit,
    onSaveSchedule: (AttendanceSchedule) -> Unit,
    onPermissionAction: () -> Unit,
    onEnableLocation: () -> Unit,
    onRetryTracking: () -> Unit,
    onCurrentLocationMissing: () -> Unit,
) {
    var pickingOffice by rememberSaveable { mutableStateOf(false) }
    var showSchedule by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = pickingOffice) { pickingOffice = false }

    Scaffold(
        containerColor = ScreenBackground,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (pickingOffice) {
            OfficeMapPicker(
                initialPoint = state.office?.point,
                currentLocation = state.currentLocation?.point,
                showMyLocation = state.permission == PermissionStatus.Granted,
                radiusMeters = state.radiusMeters,
                onSave = { point ->
                    pickingOffice = false
                    onSaveOffice(point)
                },
                onCancel = { pickingOffice = false },
                onCurrentLocationMissing = onCurrentLocationMissing,
                modifier = Modifier.padding(padding),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                LocationBanner(state, onPermissionAction, onEnableLocation, onRetryTracking)
                OfficeContextCard(
                    office = state.office?.point,
                    onSetOffice = { pickingOffice = true },
                )
                Spacer(Modifier.padding(top = 28.dp))
                AttendanceMarkSection(
                    state = state,
                    onMarkAttendance = onMarkAttendance,
                    onEditHours = { showSchedule = true },
                )
                Spacer(Modifier.padding(top = 20.dp))
                TodayActivityCard(records = state.todayRecords)
            }
        }
    }

    if (showSchedule) {
        ScheduleDialog(
            schedule = state.schedule,
            onDismiss = { showSchedule = false },
            onSave = { schedule ->
                showSchedule = false
                onSaveSchedule(schedule)
            },
        )
    }
}

@Composable
private fun LocationBanner(
    state: AttendanceUiState,
    onPermissionAction: () -> Unit,
    onEnableLocation: () -> Unit,
    onRetryTracking: () -> Unit,
) {
    val radius = state.radiusMeters.roundToInt()
    val banner = when {
        state.permission == PermissionStatus.ApproximateOnly -> Banner(
            stringResource(R.string.banner_permission_approx_title),
            stringResource(R.string.banner_permission_approx_body, radius),
            stringResource(R.string.action_allow),
            onPermissionAction,
        )
        state.permission == PermissionStatus.PermanentlyDenied -> Banner(
            stringResource(R.string.banner_permission_title),
            stringResource(R.string.banner_permission_blocked_body),
            stringResource(R.string.action_open_settings),
            onPermissionAction,
        )
        state.permission == PermissionStatus.Denied -> Banner(
            stringResource(R.string.banner_permission_title),
            stringResource(R.string.banner_permission_body),
            stringResource(R.string.action_allow),
            onPermissionAction,
        )
        state.permission == PermissionStatus.Granted && !state.isLocationServiceEnabled -> Banner(
            stringResource(R.string.banner_gps_off_title),
            stringResource(R.string.banner_gps_off_body),
            stringResource(R.string.action_turn_on),
            onEnableLocation,
        )
        state.isTrackingFailed -> Banner(
            stringResource(R.string.banner_tracking_failed_title),
            stringResource(R.string.banner_tracking_failed_body),
            stringResource(R.string.action_retry),
            onRetryTracking,
        )
        else -> null
    }
    if (banner != null) {
        StatusBanner(banner.title, banner.body, banner.action, banner.onAction)
        Spacer(Modifier.padding(top = 12.dp))
    }
}

private data class Banner(val title: String, val body: String, val action: String, val onAction: () -> Unit)

@Composable
private fun StatusBanner(title: String, body: String, actionLabel: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = onAction, modifier = Modifier.align(androidx.compose.ui.Alignment.End)) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun AttendanceScreenOutsidePreview() {
    GeoCheckTheme {
        AttendanceScreen(
            state = AttendanceUiState(
                permission = PermissionStatus.Granted,
                office = OfficeLocation(GeoPoint(40.7128, -74.0060), 0f, 0L),
                currentLocation = LocationSample(GeoPoint(40.7138, -74.0068), 8f, 0L, isMock = false),
                geofence = GeofenceStatus.Outside(120f),
                isWithinWindow = true,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onSaveOffice = {},
            onMarkAttendance = {},
            onSaveSchedule = {},
            onPermissionAction = {},
            onEnableLocation = {},
            onRetryTracking = {},
            onCurrentLocationMissing = {},
        )
    }
}
