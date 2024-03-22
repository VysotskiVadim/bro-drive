package com.mapbox.hackathon.shared

import kotlinx.serialization.Serializable

@Serializable
data class SharedLocations(
    val locations: List<SharedUserLocation>
)

@Serializable
data class SharedUserLocation(
    val userId: String,
    val location: SharedLocation
)

@Serializable
data class SharedLocation(
    val longitude: Double,
    val latitude: Double,
    val bearing: Double
)

@Serializable
data class IdMessage(
    val userId: String
)