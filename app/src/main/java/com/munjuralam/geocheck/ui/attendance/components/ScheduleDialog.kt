package com.munjuralam.geocheck.ui.attendance.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.munjuralam.geocheck.R
import com.munjuralam.geocheck.domain.model.AttendanceSchedule
import com.munjuralam.geocheck.domain.model.TimeWindow
import com.munjuralam.geocheck.ui.attendance.formatMinutesOfDay

private enum class TimeSlot { CheckInStart, CheckInEnd, CheckOutStart, CheckOutEnd }

@Composable
fun ScheduleDialog(
    schedule: AttendanceSchedule,
    onDismiss: () -> Unit,
    onSave: (AttendanceSchedule) -> Unit,
) {
    val context = LocalContext.current
    var checkInStart by remember { mutableIntStateOf(schedule.checkIn.startMinutes) }
    var checkInEnd by remember { mutableIntStateOf(schedule.checkIn.endMinutes) }
    var checkOutStart by remember { mutableIntStateOf(schedule.checkOut.startMinutes) }
    var checkOutEnd by remember { mutableIntStateOf(schedule.checkOut.endMinutes) }
    var editing by remember { mutableStateOf<TimeSlot?>(null) }
    var invalid by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.schedule_title), fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.schedule_check_in), fontWeight = FontWeight.SemiBold, color = Ink)
                TimeRangeRow(
                    start = context.formatMinutesOfDay(checkInStart),
                    end = context.formatMinutesOfDay(checkInEnd),
                    onStart = { editing = TimeSlot.CheckInStart },
                    onEnd = { editing = TimeSlot.CheckInEnd },
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.schedule_check_out), fontWeight = FontWeight.SemiBold, color = Ink)
                TimeRangeRow(
                    start = context.formatMinutesOfDay(checkOutStart),
                    end = context.formatMinutesOfDay(checkOutEnd),
                    onStart = { editing = TimeSlot.CheckOutStart },
                    onEnd = { editing = TimeSlot.CheckOutEnd },
                )
                if (invalid) {
                    Text(
                        text = stringResource(R.string.schedule_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val next = AttendanceSchedule(
                    checkIn = TimeWindow(checkInStart, checkInEnd),
                    checkOut = TimeWindow(checkOutStart, checkOutEnd),
                )
                if (next.checkIn.isValid() && next.checkOut.isValid()) {
                    onSave(next)
                } else {
                    invalid = true
                }
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    val slot = editing
    if (slot != null) {
        val initial = when (slot) {
            TimeSlot.CheckInStart -> checkInStart
            TimeSlot.CheckInEnd -> checkInEnd
            TimeSlot.CheckOutStart -> checkOutStart
            TimeSlot.CheckOutEnd -> checkOutEnd
        }
        MinutesPickerDialog(
            initialMinutes = initial,
            onConfirm = { minutes ->
                when (slot) {
                    TimeSlot.CheckInStart -> checkInStart = minutes
                    TimeSlot.CheckInEnd -> checkInEnd = minutes
                    TimeSlot.CheckOutStart -> checkOutStart = minutes
                    TimeSlot.CheckOutEnd -> checkOutEnd = minutes
                }
                invalid = false
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun TimeRangeRow(
    start: String,
    end: String,
    onStart: () -> Unit,
    onEnd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onStart,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f),
        ) { Text(start) }
        Text(stringResource(R.string.schedule_to), color = Muted)
        OutlinedButton(
            onClick = onEnd,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f),
        ) { Text(end) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinutesPickerDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                // `hour` is always 0–23. is24Hour only switches the dial to AM/PM.
                onConfirm(state.hour * 60 + state.minute)
            }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        text = { TimePicker(state = state) },
    )
}
