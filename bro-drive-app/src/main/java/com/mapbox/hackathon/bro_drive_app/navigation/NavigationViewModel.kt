package com.mapbox.hackathon.bro_drive_app.navigation

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.api.directions.v5.models.Bearing
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.geojson.Point
import com.mapbox.hackathon.bro_drive_app.sync.LocationSync
import com.mapbox.hackathon.shared.SharedUserLocation
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.core.internal.extensions.flowLocationMatcherResult
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineApiOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class NavigationViewModel : ViewModel() {

    private val locationSync = LocationSync()

    private val mapboxNavigation = MapboxNavigationApp.current()!!

    private val routeLineApi = MapboxRouteLineApi(MapboxRouteLineApiOptions.Builder().build())

    private val _location = MutableStateFlow<LocationState>(LocationState.Unknown)
    val location: StateFlow<LocationState> = _location

    private val _broLocation = MutableStateFlow<SharedUserLocation?>(null)
    val broLocation: StateFlow<SharedUserLocation?> = _broLocation

    val routeLineRoutesUpdates = mapboxNavigation.routesUpdates()
        .map {
            suspendCoroutine { continuation ->
                routeLineApi.setNavigationRoutes(
                    it.navigationRoutes,
                    mapboxNavigation.getAlternativeMetadataFor(it.navigationRoutes)
                ) {
                    continuation.resume(it)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val routeLineProgressUpdates = mapboxNavigation.routeProgressUpdates()
        .map {
            suspendCoroutine { cont ->
                routeLineApi.updateWithRouteProgress(it) {
                    cont.resume(it)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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

    fun mapLongClick(point: Point) {
        val currentLocation = (location.value as? LocationState.Known)?.value ?: return
        viewModelScope.launch {
            val result = mapboxNavigation.requestRoutes(
                RouteOptions.builder()
                    .applyDefaultNavigationOptions()
                    .coordinatesList(
                        listOf(
                            Point.fromLngLat(
                                currentLocation.enhancedLocation.longitude,
                                currentLocation.enhancedLocation.latitude,
                            ),
                            point
                        )
                    )
                    .bearingsList(
                        listOf(
                            Bearing.builder()
                                .angle(currentLocation.enhancedLocation.bearing ?: 0.0)
                                .degrees(45.0)
                                .build(),
                            null
                        )
                    )
                    .build()
            )
            when (result) {
                is RouteRequestResult.Failure -> {
                    Log.e("route request", "route request failure: ${result.reasons}")
                }
                is RouteRequestResult.Success -> mapboxNavigation.setNavigationRoutes(result.routes)
            }
        }
    }
}

sealed class LocationState {
    object Unknown : LocationState()
    data class Known(
        val firstUpdate: Boolean,
        val value: LocationMatcherResult
    ) : LocationState()
}