package cc.worldmandia.jkassist.network

import cc.worldmandia.jkassist.SessionStorage
import cc.worldmandia.jkassist.WsEvent
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

class ChatNetworkClient(
    val sessionStorage: SessionStorage
) {
    companion object {
        private const val API_HOST = "hack.worldmandia.cc"
        private const val API_PORT = 443
        private const val API_URL = "https://$API_HOST:$API_PORT"

        private const val USER_TOKEN = "user_123"
    }

    private val jsonFormat = Json { ignoreUnknownKeys = true }
    private var webSocketSession: DefaultClientWebSocketSession? = null

    val httpClient = HttpClient {
        install(WebSockets) { pingInterval = 3.seconds }
        install(ContentNegotiation) { json(jsonFormat) }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 10_000
        }
    }

    suspend fun getHistory(sessionId: String): List<WsEvent.Message> {
        return httpClient.get("$API_URL/api/chat/$sessionId/history") {
            parameter("userId", USER_TOKEN)
        }.body()
    }

    suspend fun connectWebSocket(
        onConnected: () -> Unit,
        onEvent: (WsEvent) -> Unit
    ) {
        val sessionId = sessionStorage.getSessionId()
        val wsPath = buildString {
            append("/ws/chat?userId=$USER_TOKEN")
            if (sessionId != null) append("&sessionId=$sessionId")
        }

        httpClient.wss(
            method = HttpMethod.Get,
            host = API_HOST,
            port = API_PORT,
            path = wsPath,
            request = {
                url.protocol = URLProtocol.WSS
                url.port = API_PORT
            }
        ) {
            webSocketSession = this
            onConnected()

            incoming.consumeAsFlow()
                .filterIsInstance<Frame.Text>()
                .map { jsonFormat.decodeFromString<WsEvent>(it.readText()) }
                .collect { event ->
                    handleSystemEvents(event)
                    onEvent(event)
                }
        }
    }

    private fun handleSystemEvents(event: WsEvent) {
        if (event is WsEvent.SessionCreated) {
            sessionStorage.saveSessionId(event.sessionId)
        }
    }

    suspend fun sendMessage(text: String) {
        val session = webSocketSession
        if (session == null || !session.isActive) {
            throw IllegalStateException("Нет активного вебсокет-соединения")
        }
        session.send(Frame.Text(text))
    }

    fun disconnect() {
        webSocketSession?.cancel()
        webSocketSession = null
    }
}