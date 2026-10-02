package com.munjuralam.geocheck.ui.attendance.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.munjuralam.geocheck.R
import com.munjuralam.geocheck.domain.model.AttendanceRecord
import com.munjuralam.geocheck.domain.model.AttendanceType
import com.munjuralam.geocheck.ui.attendance.formatDistance
import com.munjuralam.geocheck.ui.attendance.formatTime

@Composable
fun TodayActivityCard(
    records: List<AttendanceRecord>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                text = stringResource(R.string.today_title),
                color = Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(8.dp))
            if (records.isEmpty()) {
                Text(
                    text = stringResource(R.string.today_empty),
                    color = Muted,
                    fontSize = 14.sp,
                )
            }
            records.forEachIndexed { index, record ->
                if (index > 0) HorizontalDivider(color = Color(0xFFE6EAF0))
                val isCheckIn = record.type == AttendanceType.CHECK_IN
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isCheckIn) Icons.Filled.CheckCircle else Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = if (isCheckIn) RangeGreen else AccentBlue,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(if (isCheckIn) R.string.check_in else R.string.check_out),
                            color = Ink,
                            fontSize = 15.sp,
                        )
                        Text(
                            text = stringResource(R.string.record_detail, context.formatDistance(record.distanceMeters)),
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        text = context.formatTime(record.timestampMillis),
                        color = Ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}