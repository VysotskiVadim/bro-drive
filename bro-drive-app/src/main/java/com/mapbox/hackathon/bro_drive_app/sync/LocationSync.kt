package com.mapbox.hackathon.bro_drive_app.sync

import android.util.Log
import com.mapbox.common.location.Location
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class LocationSync {
    fun updateMyLocation(location: Location) {

    }

    fun observerLocations(): Flow<SharedLocations> = flow {
        val client = HttpClient {
            install(WebSockets)
        }
        client.webSocket(method = HttpMethod.Get, host = "10.0.2.2", port = 8080, path = "/chat") {
            while(true) {
                val othersMessage = incoming.receive() as? Frame.Text ?: continue
                Log.d("vadzim-test", othersMessage.readText())
                val myMessage = "test message"
                send(Frame.Text(myMessage))
            }
        }
    }
}

data class SharedLocations(
    val locations: List<SharedLocation>
)

data class SharedLocation(
    val userId: String,
    val location: Location
)