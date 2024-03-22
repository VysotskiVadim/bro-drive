package com.mapbox.hackathon.backend

import com.mapbox.hackathon.shared.IdMessage
import com.mapbox.hackathon.shared.SharedUserLocation
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.receiveDeserialized
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.Duration

fun main() {
    embeddedServer(Netty, port = 8080, host = "127.0.0.1") {
        install(WebSockets) {
            contentConverter = KotlinxWebsocketSerializationConverter(Json)
            pingPeriod = Duration.ofSeconds(5)
            timeout = Duration.ofSeconds(30)
        }
        val allLocations = MutableSharedFlow<SharedUserLocation>(extraBufferCapacity = 500)
        routing {
            get("/") {
                call.respondText("Hello, world!")
            }
            webSocket("/locations") {
                try {
                    val idMessage = receiveDeserialized<IdMessage>()
                    println("connect with ${idMessage.userId}")
                    launch {
                        allLocations
                            .filter { it.userId != idMessage.userId }
                            .collect {
                                println("Broadcasting location from ${it.userId} to ${idMessage.userId}")
                                sendSerialized(it)
                            }
                    }

                    while (true) {
                        val location = receiveDeserialized<SharedUserLocation>()
                        println("received location from ${location.userId}")
                        allLocations.emit(location)
                    }
                } catch (e: Exception) {
                    println("Error: ${e.message}")
                    e.printStackTrace()
                } finally {
                    //println("Removing $thisConnection!")
                }
            }

        }
    }.start(wait = true)
}