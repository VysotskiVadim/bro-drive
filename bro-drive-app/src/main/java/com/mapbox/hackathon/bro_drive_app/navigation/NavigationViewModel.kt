package com.mapbox.hackathon.bro_drive_app.navigation

import android.Manifest.permission.ACCESS_FINE_LOCATION
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.hackathon.bro_drive_app.sync.LocationSync
import com.mapbox.hackathon.shared.SharedUserLocation
import com.mapbox.navigation.core.internal.extensions.flowLocationMatcherResult
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class NavigationViewModel : ViewModel() {

    private val locationSync = LocationSync()

    private val mapboxNavigation = MapboxNavigationApp.current()!!

    private val _location = MutableStateFlow<LocationState>(LocationState.Unknown)
    val location: StateFlow<LocationState> = _location

    private val _broLocation = MutableStateFlow<SharedUserLocation?>(null)
    val broLocation: StateFlow<SharedUserLocation?> = _broLocation

    init {
        viewModelScope.launch {
            mapboxNavigation.flowLocationMatcherResult()
                .onEach {
                    locationSync.updateMyLocation(it.enhancedLocation)
                }
                .collect {
                    val firstLocation = _location.value == LocationState.Unknown
                    _location.value = LocationState.Known(firstLocation, it)
                }
        }
        viewModelScope.launch {
            locationSync.observerLocations().collect {
                _broLocation.emit(it)
            }
        }
    }

    @androidx.annotation.RequiresPermission(ACCESS_FINE_LOCATION)
    fun tripSessionCouldBeStarted() {
        mapboxNavigation.startTripSession()
    }
}

sealed class LocationState {
    object Unknown : LocationState()
    data class Known(
        val firstUpdate: Boolean,
        val value: LocationMatcherResult
    ) : LocationState()
}