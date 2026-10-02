package com.munjuralam.geocheck.ui.attendance.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.munjuralam.geocheck.R
import com.munjuralam.geocheck.domain.model.GeoPoint
import com.munjuralam.geocheck.ui.attendance.formatLatLon
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val Fallback = GeoPoint(23.8103, 90.4125)
private const val PICK_ZOOM = 16.5f

@Composable
fun OfficeMapPicker(
    initialPoint: GeoPoint?,
    currentLocation: GeoPoint?,
    showMyLocation: Boolean,
    radiusMeters: Float,
    onSave: (GeoPoint) -> Unit,
    onCancel: () -> Unit,
    onCurrentLocationMissing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = initialPoint ?: currentLocation ?: Fallback
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(start.toLatLng(), PICK_ZOOM)
    }
    val scope = rememberCoroutineScope()
    var centered by remember { mutableStateOf(false) }

    LaunchedEffect(initialPoint, currentLocation) {
        if (centered) return@LaunchedEffect
        val point = initialPoint ?: currentLocation ?: return@LaunchedEffect
        centered = true
        runCatching {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(point.toLatLng(), PICK_ZOOM),
                durationMs = 500,
            )
        }.onFailure { centered = false }
    }

    val target = cameraPositionState.position.target
    val currentLocationLabel = stringResource(R.string.current_location)

    Column(modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.picker_hint, radiusMeters.roundToInt()),
            color = Muted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp)),
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = showMyLocation),
                uiSettings = MapUiSettings(
                    compassEnabled = false,
                    indoorLevelPickerEnabled = false,
                    mapToolbarEnabled = false,
                    myLocationButtonEnabled = false,
                    zoomControlsEnabled = false,
                ),
            ) {
                Circle(
                    center = target,
                    radius = radiusMeters.toDouble(),
                    fillColor = AccentBlue.copy(alpha = 0.16f),
                    strokeColor = AccentBlue,
                    strokeWidth = 3f,
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(50),
                color = Color.White,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .background(AccentBlue, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(Color.White, CircleShape),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatLatLon(GeoPoint(target.latitude, target.longitude)),
                        color = Ink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .offset(y = (-18).dp),
            )

            Surface(
                onClick = {
                    val point = currentLocation
                    if (point == null) {
                        onCurrentLocationMissing()
                    } else {
                        scope.launch {
                            runCatching {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(point.toLatLng(), 17f),
                                    durationMs = 700,
                                )
                            }
                        }
                    }
                },
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(48.dp)
                    .semantics { contentDescription = currentLocationLabel },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CurrentLocationGlyph()
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_cancel), color = Muted, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {
                    val chosen = cameraPositionState.position.target
                    onSave(GeoPoint(chosen.latitude, chosen.longitude))
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = Color.White),
                modifier = Modifier
                    .weight(2f)
                    .height(50.dp),
            ) {
                Text(stringResource(R.string.picker_save), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CurrentLocationGlyph() {
    androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        drawCircle(color = AccentBlue, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawCircle(color = AccentBlue, radius = size.minDimension * 0.16f)
        val arm = size.minDimension * 0.18f
        val mid = center
        drawLine(AccentBlue, mid.copy(x = mid.x - size.minDimension / 2, y = mid.y), mid.copy(x = mid.x - arm, y = mid.y), stroke)
        drawLine(AccentBlue, mid.copy(x = mid.x + arm, y = mid.y), mid.copy(x = mid.x + size.minDimension / 2, y = mid.y), stroke)
        drawLine(AccentBlue, mid.copy(y = mid.y - size.minDimension / 2), mid.copy(y = mid.y - arm), stroke)
        drawLine(AccentBlue, mid.copy(y = mid.y + arm), mid.copy(y = mid.y + size.minDimension / 2), stroke)
    }
}

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)
