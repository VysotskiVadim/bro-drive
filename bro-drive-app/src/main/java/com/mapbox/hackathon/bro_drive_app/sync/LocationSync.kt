package com.mapbox.hackathon.bro_drive_app.sync

import android.util.Log
import com.mapbox.common.location.Location
import com.mapbox.hackathon.shared.IdMessage
import com.mapbox.hackathon.shared.SharedLocation
import com.mapbox.hackathon.shared.SharedUserLocation
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.UUID

class LocationSync {

    private val userId = UUID.randomUUID().toString()

    private val myLocation = MutableStateFlow<SharedLocation?>(null)

    suspend fun updateMyLocation(location: Location) {
        myLocation.emit(
            SharedLocation(
                longitude = location.longitude,
                latitude = location.latitude,
                bearing = location.bearing ?: 0.0
            )
        )
    }

    fun observerLocations(): Flow<SharedUserLocation> = flow {
        val client = HttpClient {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }
        }
        while (true) {
            try {
                client.webSocket(
                    method = HttpMethod.Get,
                    host = "10.0.2.2",
                    port = 8080,
                    path = "/locations"
                ) {
                    sendSerialized(IdMessage(userId))
                    launch {
                        myLocation.filterNotNull().collect {
                            sendSerialized(SharedUserLocation(userId, it))
                        }
                    }
                    while (true) {
                        val sharedLocation = receiveDeserialized<SharedUserLocation>()
                        this@flow.emit(sharedLocation)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.e("location sync", "location sync error, retrying in 3 seconds")
                delay(3000)
            }
        }
    }
}

