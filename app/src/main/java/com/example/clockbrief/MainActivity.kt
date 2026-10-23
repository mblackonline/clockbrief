package com.example.clockbrief

import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.clockbrief.data.LocationPreferences
import com.example.clockbrief.data.SavedLocation
import com.example.clockbrief.data.WeatherData
import com.example.clockbrief.data.WeatherRepository
import com.example.clockbrief.data.WeatherRefreshController
import com.example.clockbrief.service.ClockAssistantSession
import com.example.clockbrief.ui.LocationSettingsDialog
import com.example.clockbrief.ui.theme.ClockBriefTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var clockTime by mutableStateOf("")
    private var clockDate by mutableStateOf("")
    private var currentLocation by mutableStateOf<SavedLocation?>(null)
    private var nowMillis by mutableStateOf(System.currentTimeMillis())
    private var isSettingsOpen by mutableStateOf(false)

    private val locationPreferences by lazy { LocationPreferences(this) }
    private val weatherRepository by lazy {
        WeatherRepository(
            readHigh = locationPreferences::getForecastHigh,
            writeHigh = locationPreferences::saveForecastHigh
        )
    }
    private val weatherController = WeatherRefreshController(
        fetchWeather = { location -> weatherRepository.fetchWeather(location.latitude, location.longitude) },
        log = { message -> Log.d("ClockBriefWeather", message) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        hideSystemBars()

        currentLocation = locationPreferences.getLocation()
        weatherController.selectLocation(currentLocation)

        clockTime = formattedTime()
        clockDate = formattedDate()

        // Update clock every second
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    nowMillis = System.currentTimeMillis()
                    clockTime = formattedTime()
                    clockDate = formattedDate()
                    delay(1_000)
                }
            }
        }

        // One polling owner; restarts immediately on return and on location changes.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                weatherController.runWhileStarted()
            }
        }

        setContent {
            val weatherState by weatherController.state.collectAsState()
            ClockBriefTheme(darkTheme = true, dynamicColor = false) {
                ClockScreen(
                    time = clockTime,
                    date = clockDate,
                    weather = weatherState.weather,
                    weatherStatus = weatherState.statusText(nowMillis),
                    location = currentLocation,
                    onOpenSettings = { isSettingsOpen = true }
                )

                if (isSettingsOpen) {
                    LocationSettingsDialog(
                        currentLocation = currentLocation,
                        onLocationSelected = { newLocation ->
                            currentLocation = newLocation
                            weatherController.selectLocation(newLocation)
                            locationPreferences.saveLocation(newLocation)
                            isSettingsOpen = false
                        },
                        onDismiss = {
                            isSettingsOpen = false
                            hideSystemBars()
                        }
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        ClockAssistantSession.policy.onClockFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onDestroy() {
        ClockAssistantSession.policy.cancelSession()
        super.onDestroy()
    }

    private fun hideSystemBars() {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun formattedTime(): String =
        DateFormat.getTimeFormat(this).format(System.currentTimeMillis())

    private fun formattedDate(): String =
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
}

@Composable
private fun ClockScreen(
    time: String,
    date: String,
    weather: WeatherData?,
    location: SavedLocation?,
    onOpenSettings: () -> Unit,
    weatherStatus: String = "",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .padding(24.dp)
    ) {
        // Gear / Settings trigger in top right corner
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(48.dp)
                .clickable(onClick = onOpenSettings),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⚙",
                color = Color(0x55FFFFFF),
                fontSize = 22.sp
            )
        }

        // Centered Time, Date, and Weather
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicText(
                text = time,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    color = Color.White,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Light,
                    fontFeatureSettings = "tnum",
                    letterSpacing = (-2).sp,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 32.sp,
                    maxFontSize = 210.sp
                )
            )

            if (date.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                BasicText(
                    text = date,
                    modifier = Modifier.fillMaxWidth(),
                    style = TextStyle(
                        color = Color(0xFFB0B0B0),
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 16.sp,
                        maxFontSize = 46.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (weather != null) {
                val weatherLine = weather.displayLine()
                BasicText(
                    text = weatherLine,
                    modifier = Modifier.fillMaxWidth(),
                    style = TextStyle(
                        color = Color(0xFF909090),
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 14.sp,
                        maxFontSize = 36.sp
                    )
                )
            }
            if (location != null && weatherStatus.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = weatherStatus,
                    color = Color(0xFF909090),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            } else if (location == null) {
                Text(
                    text = "Tap ⚙ to set weather location",
                    color = Color(0x66FFFFFF),
                    fontSize = 16.sp,
                    modifier = Modifier.clickable(onClick = onOpenSettings)
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 800)
@Composable
private fun ClockPreview() {
    ClockBriefTheme(darkTheme = true, dynamicColor = false) {
        ClockScreen(
            time = "10:08 PM",
            date = "Wed, Sep 9",
            weather = WeatherData(
                currentTempF = 72,
                condition = "Partly Cloudy",
                highTempF = 78,
                lowTempF = 58,
                lastUpdated = "10:00 PM"
            ),
            location = SavedLocation("Sample City", 40.7128, -74.0060),
            onOpenSettings = {}
        )
    }
}
