# Change log

## Unreleased

### National Weather Service integration

- Replace Open-Meteo's weather-model estimates and National Blend of Models forecasts with National Weather Service station observations and published forecasts.
- Retain Open-Meteo's Geocoding API solely to translate a user-entered city name into a selected location's name, latitude, and longitude. All displayed weather data comes from National Weather Service services.
- Resolve the selected coordinates to the forecast grid, observation stations, and location time zone. Cache this metadata for six hours.
- Retrieve measured current temperature and conditions from up to five nearby stations within 50 km. Reject missing or invalid values, observations older than 90 minutes, and timestamps more than five minutes in the future.
- Fall back to the National Weather Service's Aviation Weather Center for recent observations from the same nearby airports when the main observation feed has no usable current reading.
- Show the station observation age separately from the app's last successful refresh. Show "Current unavailable" when no usable recent observation is available while retaining available forecast temperatures.
- Use today's forecast-grid maximum, with the published daytime forecast as a fallback. Persist the high for matching coordinates and the local date so it survives forecast-period removal and app restarts.
- Use the published low for the night beginning on the current local date. Exclude the preceding night's early-morning period and remove the previous custom morning cutoff.
- Use the forecast location's time zone and dated forecast periods for seasonal clock changes, instead of the tablet's time zone or hard-coded clock-change dates.
- Display a dash for an unavailable high or low, without substituting another day's forecast. Omit the forecast pair when both values are unavailable.
- Preserve the existing foreground refresh schedule, automatic retries, private location storage, and backup exclusions. Add no runtime dependencies or API credentials.

### Documentation and validation

- Document each provider's role, coordinate lookup, weather coverage, freshness rules, fallback behavior, local caching, and service references in the README.
- Document that Android device-test cleanup can uninstall the app and clear local data; use a dedicated test installation or emulator.
- Replace the previous model-selection and hourly-low tests with tests for observation validation, published forecast selection, partial displays, and observation-age reporting.
- Add Android integration tests for JSON parsing, station and airport fallback, stale or missing observations, cached highs, metadata reuse, and cancellation propagation.
- Validation completed on October 22, 2026: 46 local unit tests and 12 Android device tests passed; the debug build passed. A live refresh and the tablet's display were checked after restoring the saved city following device-test cleanup.
- Forecast accuracy against observed weather has not been independently benchmarked. Passing tests confirm the implemented behavior, rather than proving a provider is more accurate.
