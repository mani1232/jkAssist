package cc.worldmandia.jkassist

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Clock

class ChatViewModel(private val scope: CoroutineScope) {
    private val client = HttpClient { install(WebSockets) }
    private var webSocketSession: DefaultClientWebSocketSession? = null

    val state = MutableStateFlow(ChatState())

    init {
        connect()
    }

    fun connect() {
        scope.launch {
            state.update { it.copy(isConnecting = true, error = null) }
            try {
                client.webSocket(
                    method = HttpMethod.Get,
                    host = "127.0.0.1",
                    port = 8800,
                    path = "/ws/chat?userId=user_123"
                ) {
                    webSocketSession = this
                    state.update { it.copy(isConnecting = false, isConnected = true) }

                    incoming.consumeAsFlow().filterIsInstance<Frame.Text>().collect { frame ->
                        when (val event = Json.decodeFromString<WsEvent>(frame.readText())) {
                            is WsEvent.Message -> state.update { it.copy(messages = it.messages + (event.id to event)) }
                            is WsEvent.TypingStarted -> state.update { it.copy(isTyping = true) }
                            is WsEvent.TypingStopped -> state.update { it.copy(isTyping = false) }
                            is WsEvent.Error -> state.update { it.copy(error = event.message) }
                        }
                    }
                }
            } catch (e: Exception) {
                state.update { it.copy(isConnected = false, isConnecting = false, error = "Нет подключения к серверу") }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || !state.value.isConnected) return
        val userMsg = WsEvent.Message("u-${Clock.System.now()}", Role.USER, text, Clock.System.now())
        state.update { it.copy(messages = it.messages + (userMsg.id to userMsg)) }

        scope.launch {
            webSocketSession?.send(Frame.Text(text))
        }
    }
}

data class ChatState(
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val isTyping: Boolean = false,
    val messages: Map<String, WsEvent.Message> = emptyMap(),
    val error: String? = null
)