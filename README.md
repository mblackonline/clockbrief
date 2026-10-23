# ClockBrief

A full-screen display clock with date, live weather, and automatic assistant overlay dismissal.

Developed and tested on a Samsung Galaxy Tab A11+ tablet running Android 14+. There is no guarantee it will work as expected on other devices, and no warranties are implied.

This software was developed with the assistance of AI tools.

See [CHANGELOG.md](CHANGELOG.md) for changes and validation history.

## Features

- Full-screen digital clock with tabular figures
- Abbreviated day of the week and date
- Measured current temperature, conditions, today's forecast high, and tonight's forecast low via the National Weather Service
- Weather refresh every five minutes while the app is visible, with an immediate refresh on return
- Last successful update age and automatic retries when weather is unavailable
- In-app city search with private, local on-device preference storage
- Saved location preferences excluded from Android cloud backup and device transfer
- Display kept awake continuously while foregrounded
- Optional accessibility service to automatically dismiss assistant overlays after 30 seconds

## Weather sources and behavior

Weather uses the free National Weather Service API and its Aviation Weather Center.
City lookup uses Open-Meteo's Geocoding API. These integrations currently require
no account, API key, or secret token in the app.

The switch from Open-Meteo followed noticeable temperature differences during
local testing. The National Weather Service was chosen for measured current
conditions and published local forecasts, with the goal of better local accuracy.
Comparative accuracy has not been independently verified.

| Function | Service | Role |
| --- | --- | --- |
| City search | Open-Meteo Geocoding API | Converts a city name into latitude, longitude, and a readable location label. |
| Forecast location lookup | National Weather Service | Uses those coordinates to find the forecast grid, nearby stations, and location time zone. |
| Current temperature and conditions | National Weather Service station observations | Supplies measured conditions and the observation timestamp. |
| Current observations fallback | National Weather Service Aviation Weather Center | Supplies observations from the same nearby airports when the main feed has no usable recent reading. |
| Today's high and tonight's low | National Weather Service forecast grid and published periods | Supplies forecast temperatures for the selected location and date. |

### City search and coordinates

The National Weather Service's public API accepts coordinates rather than city
names and does not provide city-name geocoding. Open-Meteo supplies this lookup:
the user searches for a city, selects a result, and the app saves its name,
latitude, and longitude locally. The coordinates are then passed to the National
Weather Service for all weather requests.

Open-Meteo supplies none of the displayed temperatures, conditions, or forecasts.
City geocoding does not use Open-Meteo's weather models. Weather coverage is limited
to locations supported by the National Weather Service, primarily the United
States and supported territories. A city appearing in search does not guarantee
weather coverage.

### Measured current conditions

The app checks up to five stations within 50 km of the selected coordinates,
ordered by distance. It uses the nearest station with a usable temperature and
an observation no older than 90 minutes. Readings more than five minutes in the
future are rejected; this tolerance allows small differences in device clocks.
Missing temperatures, unsupported units, and invalid timestamps are rejected.
Temperatures are converted to Fahrenheit when needed.

If none of those stations provides a usable recent reading through the main feed,
the same nearby airports are queried through the Aviation Weather Center. The
same timestamp checks apply, and the nearest usable airport is selected regardless
of the order of records in the response.

"Updated" shows the time since the app last fetched weather successfully.
"Observed" shows the age of the actual station measurement. Fetching an older
measurement does not reset its observation age. When no recent measurement is
available, the app displays "Current unavailable" and retains available forecasts.
It does not substitute a model estimate for a measured current temperature.

### Today's high and tonight's low

The left forecast temperature is today's high. The app first uses today's maximum
from the forecast grid, then today's published daytime period if the grid value
is unavailable. It saves the high locally for the same coordinates and local date
so it can still be displayed after the daytime forecast disappears or the app
restarts. A high saved for another location or date is never substituted.

The right forecast temperature is the published low for the night beginning today
in the selected location's time zone. The preceding night's early-morning period
is excluded. The provider's dated daytime and nighttime periods handle seasonal
clock changes; the app no longer calculates a custom sunrise or morning cutoff.

Missing high or low values appear as a dash when the other value is available.
The pair is omitted when both values are unavailable. Station observations can
differ from conditions at your home, and forecasts can differ from another
website using a different location, forecast product, or update time. This
integration does not establish that one provider is more accurate than another.

### Refreshing, caching, and local storage

- Refresh weather every five minutes while visible and immediately on return.
- Preserve the existing automatic retry behavior when a complete weather request fails.
- Cache forecast-location and nearby-station metadata for six hours; fetch weather again on each refresh.
- Store the selected city, coordinates, and latest saved forecast high in private on-device preferences, excluded from cloud backup and device transfer.
- Identify service requests with the `ClockBrief/1.0 (Android)` User-Agent header.

Service references: [National Weather Service API](https://www.weather.gov/documentation/services-web-api),
[National Weather Service geocoding FAQ](https://weather-gov.github.io/api/general-faqs),
[Aviation Weather Center API](https://aviationweather.gov/data/api/), and
[Open-Meteo Geocoding API](https://open-meteo.com/en/docs/geocoding-api).

## Assistant dismissal

The accessibility service checks the focused window's owner every half-second and
dismisses the Google app after 30 seconds when it follows ClockBrief. This follows
Assistant across changing windows. Switching to another app cancels the session.
Android requires the window-content retrieval capability for these APIs; ClockBrief
reads only the root node's package name, without reading text or traversing child views.

## Prerequisites

- Android Studio or Android SDK with command-line tools
- JDK 11 or later
- Android device running Android 8.0 (API 26) or higher

## Build and Install

Clone the repository:
```bash
git clone <repo-url>
cd ClockBrief
```

Build the debug APK:
```bash
# Windows
.\gradlew.bat assembleDebug

# Linux / macOS
./gradlew assembleDebug
```

The APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

Install via ADB:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Tests

Run the local unit tests and debug build on Windows:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Run Android integration tests on a dedicated test device or emulator:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

The Gradle device test runner can uninstall the target app and clear its data.
After reinstalling on a personal device, the saved location and optional
accessibility service may need to be configured again.

Weather tests cover temperature conversion, observation freshness and age display,
station fallback, unavailable values, today's high and tonight's low selection,
location time zones, seasonal clock changes, forecast-high persistence, metadata
caching, and request cancellation. Integration tests exercise real Android JSON
parsing with controlled service responses, without depending on live forecast
values. Tests validate application behavior, not comparative forecast accuracy.

## Setup on Device

1. Open ClockBrief.
2. Tap the gear icon in the top-right corner to search for and select your city.
3. To enable hands-free overlay dismissal, open device Settings -> Accessibility -> Installed apps -> toggle ClockBrief ON.

## License

This project is licensed under the MIT License. See the LICENSE file for details.
