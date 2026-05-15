package cc.worldmandia.jkassist.viewmodels

import cc.worldmandia.jkassist.Role
import cc.worldmandia.jkassist.WsEvent
import cc.worldmandia.jkassist.network.ChatNetworkClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

data class ChatState(
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val isHistoryLoaded: Boolean = false,
    val isTyping: Boolean = false,
    val isOperatorMode: Boolean = false,
    val isShowingWelcome: Boolean = false,
    val messages: Map<String, WsEvent.Message> = emptyMap(),
    val error: String? = null
)

class ChatViewModel(
    private val scope: CoroutineScope,
    private val networkClient: ChatNetworkClient
) {
    private val _state = MutableStateFlow(ChatState())
    val state = _state.asStateFlow()

    private var connectionJob: Job? = null

    init {
        connect()
    }

    fun connect() {
        connectionJob?.cancel()

        connectionJob = scope.launch {
            while (isActive) {
                _state.update { it.copy(isConnecting = true, isConnected = false, error = null) }

                try {
                    val sessionId = networkClient.sessionStorage.getSessionId()
                    if (sessionId != null) {
                        val history = networkClient.getHistory(sessionId)
                        _state.update {
                            it.copy(
                                messages = history.associateBy { msg -> msg.id },
                                isHistoryLoaded = true
                            )
                        }
                    } else {
                        _state.update { it.copy(isHistoryLoaded = true) }
                    }

                    networkClient.connectWebSocket(
                        onConnected = {
                            _state.update { it.copy(isConnecting = false, isConnected = true) }
                        },
                        onEvent = ::handleIncomingEvent
                    )

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    println("Сетевая ошибка: ${e.message}")
                    networkClient.disconnect()
                    _state.update {
                        it.copy(
                            isConnecting = false,
                            isHistoryLoaded = false,
                            error = "Нет подключения к серверу. Повторяем попытку..."
                        )
                    }
                } finally {
                    _state.update { it.copy(isConnected = false) }
                    delay(3000.milliseconds)
                }
            }
        }
    }

    private fun handleIncomingEvent(event: WsEvent) {
        when (event) {
            is WsEvent.Message -> {
                _state.update { currentState ->
                    val isEndMessage = event.role == Role.SYSTEM && event.text.contains("Оператор завершил диалог")

                    currentState.copy(
                        messages = currentState.messages + (event.id to event),
                        isOperatorMode = !isEndMessage && currentState.isOperatorMode
                    )
                }
            }

            is WsEvent.TypingStarted -> _state.update { it.copy(isTyping = true) }
            is WsEvent.TypingStopped -> _state.update { it.copy(isTyping = false) }
            is WsEvent.Error -> _state.update { it.copy(error = event.message) }
            is WsEvent.TransferToSupport -> _state.update { it.copy(isOperatorMode = true) }
            is WsEvent.SessionCreated -> {
                scope.launch {
                    try {
                        val history = networkClient.getHistory(event.sessionId)
                        _state.update {
                            it.copy(
                                messages = history.associateBy { msg -> msg.id },
                                isHistoryLoaded = true
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        _state.update { it.copy(isHistoryLoaded = true, error = "Не удалось загрузить новую историю") }
                    }
                }
            }
        }
    }

    fun setShowWelcome(show: Boolean) {
        _state.update { it.copy(isShowingWelcome = show) }
    }

    fun sendMessage(text: String) {
        val currentState = _state.value
        if (text.isBlank() || !currentState.isConnected) return

        scope.launch {
            try {
                networkClient.sendMessage(text)
            } catch (_: Exception) {
                _state.update { it.copy(error = "Ошибка отправки сообщения: нет сети") }
            }
        }
    }
}