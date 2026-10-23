package com.example.clockbrief.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.Duration
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class WeatherData(
    val currentTempF: Int?,
    val condition: String,
    val highTempF: Int?,
    val lowTempF: Int?,
    val lastUpdated: String,
    val observedAtMillis: Long? = null,
    val observationStation: String? = null
) {
    fun displayLine(): String = buildString {
        append(currentTempF?.let { "$it°" } ?: "Current unavailable")
        if (currentTempF != null && condition.isNotBlank()) append(" · $condition")
        if (highTempF != null || lowTempF != null) {
            val high = highTempF?.let { "$it°" } ?: "—"
            val low = lowTempF?.let { "$it°" } ?: "—"
            append("  ($high / $low)")
        }
    }
}

data class GeocodingResult(
    val name: String,
    val adminArea: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double
) {
    val displayLabel: String
        get() = buildString {
            append(name)
            if (!adminArea.isNullOrBlank()) append(", $adminArea")
            if (!country.isNullOrBlank()) append(", $country")
        }
}

class WeatherRepository(
    private val request: (String) -> String = ::weatherHttpGet,
    private val now: () -> Instant = Instant::now,
    private val readHigh: (String, String) -> Int? = { _, _ -> null },
    private val writeHigh: (String, String, Int) -> Unit = { _, _, _ -> }
) {
    private data class Station(val id: String, val distance: Double)
    private data class Point(
        val zone: ZoneId,
        val forecastUrl: String,
        val gridUrl: String,
        val stations: List<Station>,
        val expires: Instant
    )

    private val points = ConcurrentHashMap<String, Point>()
    private val rememberedHighs = ConcurrentHashMap<String, Int>()

    suspend fun searchCity(query: String): Result<List<GeocodingResult>> = withContext(Dispatchers.IO) {
        cancellableResult {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            val urlString =
                "https://geocoding-api.open-meteo.com/v1/search?name=$encodedQuery&count=5&language=en&format=json"
            val responseString = request(urlString)
            val json = JSONObject(responseString)

            val results = mutableListOf<GeocodingResult>()
            if (json.has("results")) {
                val array = json.getJSONArray("results")
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    results.add(
                        GeocodingResult(
                            name = item.optString("name"),
                            adminArea = item.optString("admin1").takeIf { it.isNotBlank() },
                            country = item.optString("country").takeIf { it.isNotBlank() },
                            latitude = item.getDouble("latitude"),
                            longitude = item.getDouble("longitude")
                        )
                    )
                }
            }
            results
        }
    }

    suspend fun fetchWeather(latitude: Double, longitude: Double): Result<WeatherData> =
        withContext(Dispatchers.IO) {
            cancellableResult {
                val locationKey = "$latitude,$longitude"
                val point = resolvePoint(locationKey, latitude, longitude)
                val today = now().atZone(point.zone).toLocalDate()
                currentCoroutineContext().ensureActive()
                val periodsJson = nwsJson(point.forecastUrl).getJSONObject("properties")
                    .getJSONArray("periods")
                val periods = (0 until periodsJson.length()).mapNotNull { index ->
                    optionalData {
                        val period = periodsJson.getJSONObject(index)
                        ForecastPeriod(
                            start = OffsetDateTime.parse(period.getString("startTime")).toInstant(),
                            isDaytime = period.getBoolean("isDaytime"),
                            temperatureF = temperatureF(period.optDouble("temperature"), period.optString("temperatureUnit"))
                        )
                    }
                }
                val maxima = optionalData {
                    val grid = nwsJson(point.gridUrl).getJSONObject("properties").getJSONObject("maxTemperature")
                    val values = grid.getJSONArray("values")
                    (0 until values.length()).mapNotNull { index ->
                        optionalData {
                            val value = values.getJSONObject(index)
                            ForecastMaximum(
                                OffsetDateTime.parse(value.getString("validTime").substringBefore('/')).toInstant(),
                                temperatureF(value.optDouble("value"), grid.optString("uom"))
                            )
                        }
                    }
                }.orEmpty()
                val highKey = "$locationKey|$today"
                val freshHigh = todaysHighF(maxima, periods, today, point.zone)
                if (freshHigh != null) {
                    rememberedHighs[highKey] = freshHigh
                    writeHigh(locationKey, today.toString(), freshHigh)
                }
                val high = freshHigh ?: rememberedHighs[highKey] ?: readHigh(locationKey, today.toString())
                var observation: StationObservation? = null
                for (station in point.stations) {
                    currentCoroutineContext().ensureActive()
                    val candidate = optionalData {
                        val properties = nwsJson("https://api.weather.gov/stations/${station.id}/observations/latest")
                            .getJSONObject("properties")
                        val quantity = properties.getJSONObject("temperature")
                        val temp = temperatureF(quantity.optDouble("value"), quantity.optString("unitCode"))
                        temp?.let {
                            StationObservation(
                                temperatureF = it,
                                description = properties.optString("textDescription").takeUnless { text -> text == "null" }.orEmpty(),
                                observedAt = OffsetDateTime.parse(properties.getString("timestamp")).toInstant(),
                                stationId = station.id
                            )
                        }
                    }
                    if (candidate != null && candidate.isRecent(now())) {
                        observation = candidate
                        break
                    }
                }
                if (observation == null) {
                    currentCoroutineContext().ensureActive()
                    observation = aviationObservation(point.stations)
                }
                WeatherData(
                    currentTempF = observation?.temperatureF,
                    condition = observation?.description.orEmpty(),
                    highTempF = high,
                    lowTempF = overnightLowF(periods, today, point.zone),
                    lastUpdated = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()),
                    observedAtMillis = observation?.observedAt?.toEpochMilli(),
                    observationStation = observation?.stationId
                )
            }
        }

    private fun resolvePoint(key: String, latitude: Double, longitude: Double): Point {
        points[key]?.takeIf { now() < it.expires }?.let { return it }
        val properties = nwsJson("https://api.weather.gov/points/$latitude,$longitude").getJSONObject("properties")
        val stations = optionalData {
            val features = nwsJson(properties.getString("observationStations")).getJSONArray("features")
            (0 until features.length()).mapNotNull { index ->
                optionalData {
                    val feature = features.getJSONObject(index)
                    val coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates")
                    val id = feature.getJSONObject("properties").getString("stationIdentifier")
                    require(id.matches(Regex("[A-Za-z0-9_-]+")))
                    Station(id, distanceKm(latitude, longitude, coordinates.getDouble(1), coordinates.getDouble(0)))
                }
            }.filter { it.distance <= 50.0 }.sortedBy { it.distance }.take(5)
        }
        val point = Point(
            zone = ZoneId.of(properties.getString("timeZone")),
            forecastUrl = properties.getString("forecast"),
            gridUrl = properties.getString("forecastGridData"),
            stations = stations.orEmpty(),
            expires = now().plus(Duration.ofHours(6))
        )
        // Retry station discovery next refresh when the station endpoint failed.
        if (stations != null) points[key] = point
        return point
    }

    private fun nwsJson(url: String): JSONObject {
        val target = URL(url)
        require(target.protocol == "https" && target.host == "api.weather.gov")
        return JSONObject(request(url))
    }

    private fun aviationObservation(stations: List<Station>): StationObservation? {
        val airportIds = stations.map { it.id }.filter { it.matches(Regex("[A-Z0-9]{4}")) }
        if (airportIds.isEmpty()) return null
        val observations = optionalData {
            JSONArray(request("https://aviationweather.gov/api/data/metar?ids=${airportIds.joinToString(",")}&format=json"))
        } ?: return null
        val usable = (0 until observations.length()).mapNotNull { index ->
            optionalData {
                val item = observations.getJSONObject(index)
                val id = item.getString("icaoId")
                val temp = temperatureF(item.optDouble("temp"), "C")
                if (id !in airportIds || temp == null) return@optionalData null
                StationObservation(temp, aviationCondition(item.optString("wxString"), item.optString("cover")),
                    Instant.ofEpochSecond(item.getLong("obsTime")), id).takeIf { it.isRecent(now()) }
            }
        }
        // Keep the station-distance order, independent of response array order.
        return airportIds.firstNotNullOfOrNull { id -> usable.filter { it.stationId == id }.maxByOrNull { it.observedAt } }
    }

    private inline fun <T> optionalData(block: () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private inline fun <T> cancellableResult(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}

private fun weatherHttpGet(urlString: String): String {
    val connection = URL(urlString).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 10_000
    connection.readTimeout = 10_000
    connection.setRequestProperty("User-Agent", "ClockBrief/1.0 (Android)")
    connection.setRequestProperty("Accept", if (URL(urlString).host == "api.weather.gov") "application/geo+json" else "application/json")
    return try {
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw WeatherHttpException(responseCode)
        }
        BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
            reader.readText()
        }
    } finally {
        connection.disconnect()
    }
}
