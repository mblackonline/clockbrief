# ClockBrief

A full-screen display clock with date, live weather, and automatic assistant overlay dismissal.

Developed and tested on a Samsung Galaxy Tab A11+ tablet running Android 14+. There is no guarantee it will work as expected on other devices, and no warranties are implied.

This software was developed with the assistance of AI tools.

## Features

- Full-screen digital clock with tabular figures
- Abbreviated day of the week and date
- Live weather conditions, temperature, and daily highs/lows via Open-Meteo
- Weather refresh every five minutes while the app is visible, with an immediate refresh on return
- Last successful update age and automatic retries when weather is unavailable
- In-app city search with private, local on-device preference storage
- Display kept awake continuously while foregrounded
- Optional accessibility service to automatically dismiss assistant overlays after 30 seconds

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

## Setup on Device

1. Open ClockBrief.
2. Tap the gear icon in the top-right corner to search for and select your city.
3. To enable hands-free overlay dismissal, open device Settings -> Accessibility -> Installed apps -> toggle ClockBrief ON.

## License

This project is licensed under the MIT License. See the LICENSE file for details.
