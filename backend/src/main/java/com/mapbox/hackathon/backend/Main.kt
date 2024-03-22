package com.mapbox.hackathon.backend

import io.ktor.network.sockets.Connection
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.websocket.*
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import java.time.Duration
import java.util.Collections

fun main() {
    embeddedServer(Netty, port = 8080, host = "127.0.0.1") {
        install(WebSockets) {
            pingPeriod = Duration.ofSeconds(15)
            timeout = Duration.ofSeconds(15)
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
        routing {
            get("/") {
                call.respondText("Hello, world!")
            }
            webSocket("/chat") {
                println("Adding user!")
                try {
                    send(Frame.Text("You are connected!"))
                    for (frame in incoming) {
                        frame as? Frame.Text ?: continue
                        val receivedText = frame.readText()
                        println("received $receivedText")
                    }
                } catch (e: Exception) {
                    println(e.localizedMessage)
                } finally {
                    //println("Removing $thisConnection!")
                }
            }

        }
    }.start(wait = true)
}