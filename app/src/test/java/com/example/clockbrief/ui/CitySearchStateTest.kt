package com.example.clockbrief.ui

import com.example.clockbrief.data.GeocodingResult
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class CitySearchStateTest {
    private val match = GeocodingResult("Sample City", null, null, 0.0, 0.0)

    private fun successfulSearch(): CitySearchState {
        val state = CitySearchState().editQuery("Sample").startSearch()
        return state.finishSearch(state.requestVersion, Result.success(listOf(match)))
    }

    @Test fun editingQueryImmediatelyClearsOldMatches() {
        val state = successfulSearch().editQuery("Other")
        assertTrue(state.results.isEmpty())
        assertNull(state.error)
        assertFalse(state.isSearching)
    }

    @Test fun repeatingSearchClearsMatchesWhileRequestIsRunning() {
        val state = successfulSearch().startSearch()
        assertTrue(state.results.isEmpty())
        assertTrue(state.isSearching)
    }

    @Test fun emptyResponseLeavesNoPreviousMatches() {
        val pending = successfulSearch().startSearch()
        val state = pending.finishSearch(pending.requestVersion, Result.success(emptyList()))
        assertTrue(state.results.isEmpty())
        assertEquals("No matching locations found.", state.error)
        assertFalse(state.isSearching)
    }

    @Test fun failedResponseLeavesNoPreviousMatchesOrRawExceptionDetails() {
        val pending = successfulSearch().startSearch()
        val state = pending.finishSearch(pending.requestVersion, Result.failure(IOException("private URL")))
        assertTrue(state.results.isEmpty())
        assertTrue(state.error!!.contains("try again"))
        assertFalse(state.error.contains("private URL"))
        assertFalse(state.isSearching)
    }

    @Test fun delayedResponseAfterEditingQueryIsIgnoredEvenIfTextMatchesAgain() {
        val old = CitySearchState().editQuery("A").startSearch()
        val current = old.editQuery("B").editQuery("A").startSearch()
        assertEquals(current, current.finishSearch(old.requestVersion, Result.success(listOf(match))))
        assertTrue(current.isSearching)
    }

    @Test fun staleFailureCannotOverwriteNewSuccessfulResults() {
        val old = CitySearchState().editQuery("A").startSearch()
        val pending = old.editQuery("B").startSearch()
        val current = pending.finishSearch(pending.requestVersion, Result.success(listOf(match)))
        assertEquals(current, current.finishSearch(old.requestVersion, Result.failure(IOException())))
        assertEquals(listOf(match), current.results)
        assertNull(current.error)
    }

    @Test fun clearingQueryInvalidatesPendingResponse() {
        val old = CitySearchState().editQuery("A").startSearch()
        val current = old.editQuery("")
        assertEquals(current, current.finishSearch(old.requestVersion, Result.success(listOf(match))))
        assertTrue(current.results.isEmpty())
    }
}
