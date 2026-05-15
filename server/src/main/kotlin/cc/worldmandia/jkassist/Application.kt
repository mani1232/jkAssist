package cc.worldmandia.jkassist

import cc.worldmandia.jkassist.routing.chatRoutes
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

val jsonFormat = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

fun Application.module() {
    installPlugins()

    routing {
        chatRoutes()
    }
}

private fun Application.installPlugins() {
    install(XForwardedHeaders)

    install(ContentNegotiation) {
        json(jsonFormat)
    }

    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        anyHost()
    }

    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
}

fun main() {
    embeddedServer(CIO, configure = {
        connectors.add(EngineConnectorBuilder().apply {
            host = "0.0.0.0"
            port = System.getenv("PORT")?.toIntOrNull() ?: 8800
        })
        connectionGroupSize = 2
        workerGroupSize = 5
        callGroupSize = 10
    }, module = Application::module).start(wait = true)
}