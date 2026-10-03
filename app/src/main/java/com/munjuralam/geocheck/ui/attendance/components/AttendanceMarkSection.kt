package com.munjuralam.geocheck.ui.attendance.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.munjuralam.geocheck.R
import com.munjuralam.geocheck.domain.geofence.DistanceRingScale
import com.munjuralam.geocheck.domain.geofence.GeofenceStatus
import com.munjuralam.geocheck.domain.geofence.isWithinOffice
import com.munjuralam.geocheck.domain.model.AttendanceType
import com.munjuralam.geocheck.ui.attendance.AttendanceUiState
import com.munjuralam.geocheck.ui.attendance.formatDistance
import com.munjuralam.geocheck.ui.attendance.formatMinutesOfDay
import com.munjuralam.geocheck.ui.attendance.formatRingDistance
import kotlin.math.roundToInt

@Composable
fun AttendanceMarkSection(
    state: AttendanceUiState,
    onMarkAttendance: () -> Unit,
    onEditHours: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val radius = state.radiusMeters.roundToInt()
    val measured = state.geofence as? GeofenceStatus.Measured
    val withinOffice = state.geofence.isWithinOffice(state.radiusMeters)
    val inZone = measured != null && measured.distanceMeters <= state.radiusMeters
    val ringColor = when {
        measured == null -> RingTrack
        inZone -> RangeGreen
        else -> RangeRed
    }
    val ringTrackColor = when {
        measured == null -> RingTrack
        inZone -> RangeGreenSoft
        else -> RangeRedSoft
    }
    val sweep = measured?.let { DistanceRingScale.fillFraction(it.distanceMeters) * 360f } ?: 0f
    val status = when {
        state.geofence is GeofenceStatus.NoOffice -> null
        state.geofence is GeofenceStatus.AwaitingFix -> stringResource(R.string.status_locating) to Muted
        withinOffice -> stringResource(R.string.status_in_range) to RangeGreen
        else -> stringResource(R.string.status_out_of_range) to RangeRed
    }
    val windowStart = context.formatMinutesOfDay(state.activeWindow.startMinutes)
    val windowEnd = context.formatMinutesOfDay(state.activeWindow.endMinutes)
    val actionName = stringResource(
        if (state.nextAttendanceType == AttendanceType.CHECK_IN) R.string.check_in else R.string.check_out,
    )
    val windowLabel = stringResource(R.string.applicable_window, actionName, windowStart, windowEnd).uppercase()
    val caption = when {
        state.office == null -> stringResource(R.string.hint_set_office_first)
        state.geofence is GeofenceStatus.AwaitingFix -> stringResource(R.string.hint_locating)
        !withinOffice -> stringResource(R.string.hint_move_closer, radius)
        !state.isWithinWindow -> stringResource(R.string.hint_outside_hours, actionName, windowStart, windowEnd)
        else -> stringResource(R.string.hint_ready, radius)
    }
    val unlocked = state.canMarkAttendance || state.isMarkingAttendance

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DistanceRing(
            label = measured?.let { formatRingDistance(it.distanceMeters) }
                ?: stringResource(R.string.distance_placeholder),
            showAway = measured != null,
            color = ringColor,
            trackColor = ringTrackColor,
            sweep = sweep,
        )

        val accuracy = state.currentLocation?.accuracyMeters?.takeIf { measured != null && it != Float.MAX_VALUE }
        if (accuracy != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.gps_accuracy, context.formatDistance(accuracy)),
                color = Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(14.dp))
        ApplicableWindowText(windowLabel)

        if (status != null) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .background(status.second, CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = status.first.uppercase(),
                    color = status.second,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = caption,
            color = Muted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 28.dp),
        )

        Spacer(Modifier.height(22.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .dashedRoundedBorder(DashColor, 22.dp)
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!unlocked) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.lock_content_description),
                    tint = Color(0xFFC5CED9),
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.height(14.dp))
            }
            Button(
                onClick = onMarkAttendance,
                enabled = state.canMarkAttendance,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentBlue,
                    contentColor = Color.White,
                    disabledContainerColor = if (state.isMarkingAttendance) AccentBlue else DisabledFill,
                    disabledContentColor = Color.White,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state.isMarkingAttendance) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text(
                        text = stringResource(
                            when {
                                !state.isWithinWindow -> R.string.mark_attendance
                                state.nextAttendanceType == AttendanceType.CHECK_IN ->
                                    R.string.mark_attendance_check_in
                                else -> R.string.mark_attendance_check_out
                            },
                        ),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            ApplicableWindowText(windowLabel)
            TextButton(onClick = onEditHours) {
                Text(
                    text = stringResource(R.string.edit_hours),
                    color = AccentBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ApplicableWindowText(label: String) {
    Text(
        text = label,
        color = Muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.6.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun DistanceRing(
    label: String,
    showAway: Boolean,
    color: Color,
    trackColor: Color,
    sweep: Float,
) {
    Box(Modifier.size(156.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(4.dp)) {
            val strokeWidth = 9.dp.toPx()
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            val inset = strokeWidth / 2
            drawCircle(
                color = trackColor,
                radius = size.minDimension / 2 - inset,
            )
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke,
            )
            if (sweep > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = stroke,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = Ink,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            if (showAway) {
                Text(
                    text = stringResource(R.string.distance_away_label).uppercase(),
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.8.sp,
                )
            }
        }
    }
}

private fun Modifier.dashedRoundedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    val inset = 1.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
        cornerRadius = CornerRadius(radius.toPx(), radius.toPx()),
        style = Stroke(
            width = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
        ),
    )
}
