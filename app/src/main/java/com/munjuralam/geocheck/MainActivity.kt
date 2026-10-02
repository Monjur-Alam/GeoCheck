package com.munjuralam.geocheck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.munjuralam.geocheck.ui.attendance.AttendanceRoute
import com.munjuralam.geocheck.ui.theme.GeoCheckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GeoCheckTheme {
                AttendanceRoute()
            }
        }
    }
}
