package com.mapbox.hackathon.shared

import kotlinx.serialization.Serializable


@Serializable
sealed class BroUpdate {

    abstract val userId: String
    @Serializable
    data class LocationUpdate(override val userId: String, val userLocation: SharedLocation): BroUpdate()

    @Serializable
    data class RouteSet(override val userId: String, val serializedNavigationRoute: String): BroUpdate()

    @Serializable
    data class RouteCleared(override val userId: String): BroUpdate()
}

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