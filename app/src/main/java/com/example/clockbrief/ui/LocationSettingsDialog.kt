package com.example.clockbrief.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.clockbrief.data.GeocodingResult
import com.example.clockbrief.data.SavedLocation
import com.example.clockbrief.data.WeatherRepository
import kotlinx.coroutines.launch

@Composable
fun LocationSettingsDialog(
    currentLocation: SavedLocation?,
    onLocationSelected: (SavedLocation) -> Unit,
    onDismiss: () -> Unit,
    weatherRepository: WeatherRepository = remember { WeatherRepository() }
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<GeocodingResult>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Weather Location")
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (currentLocation != null) {
                    Text(
                        text = "Current: ${currentLocation.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            errorMessage = null
                        },
                        label = { Text("City name") },
                        placeholder = { Text("e.g. City, State or Country") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (searchQuery.isNotBlank()) {
                                isSearching = true
                                errorMessage = null
                                coroutineScope.launch {
                                    val result = weatherRepository.searchCity(searchQuery)
                                    isSearching = false
                                    result.onSuccess { list ->
                                        if (list.isEmpty()) {
                                            errorMessage = "No matching locations found."
                                        } else {
                                            searchResults = list
                                        }
                                    }.onFailure {
                                        errorMessage = "Failed to search location: ${it.message}"
                                    }
                                }
                            }
                        },
                        enabled = searchQuery.isNotBlank() && !isSearching
                    ) {
                        Text("Search")
                    }
                }

                if (isSearching) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (searchResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Select a match:",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        items(searchResults) { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onLocationSelected(
                                            SavedLocation(
                                                name = item.displayLabel,
                                                latitude = item.latitude,
                                                longitude = item.longitude
                                            )
                                        )
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                            ) {
                                Text(
                                    text = item.displayLabel,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Lat: ${item.latitude}, Lon: ${item.longitude}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
