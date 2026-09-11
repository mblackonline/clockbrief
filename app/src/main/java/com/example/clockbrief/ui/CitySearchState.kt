package com.example.clockbrief.ui

import com.example.clockbrief.data.GeocodingResult

internal data class CitySearchState(
    val query: String = "",
    val results: List<GeocodingResult> = emptyList(),
    val error: String? = null,
    val isSearching: Boolean = false,
    val requestVersion: Long = 0
) {
    fun editQuery(value: String) = CitySearchState(
        query = value, requestVersion = requestVersion + 1
    )

    fun startSearch() = CitySearchState(
        query = query, isSearching = true, requestVersion = requestVersion + 1
    )

    fun finishSearch(version: Long, result: Result<List<GeocodingResult>>): CitySearchState {
        // Comparing text alone would incorrectly accept an old A -> B -> A result.
        if (version != requestVersion || !isSearching) return this
        val matches = result.getOrNull().orEmpty()
        return copy(
            results = matches,
            isSearching = false,
            error = when {
                result.isFailure -> "Unable to search. Check your connection and try again."
                matches.isEmpty() -> "No matching locations found."
                else -> null
            }
        )
    }
}
