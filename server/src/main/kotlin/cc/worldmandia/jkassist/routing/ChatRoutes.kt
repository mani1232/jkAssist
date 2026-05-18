package cc.worldmandia.jkassist.routing

import cc.worldmandia.jkassist.Role
import cc.worldmandia.jkassist.WsEvent
import cc.worldmandia.jkassist.agent.ChatStateManager
import cc.worldmandia.jkassist.agent.JkAgentFactory
import cc.worldmandia.jkassist.jsonFormat
import cc.worldmandia.jkassist.service.CrmServiceImpl
import cc.worldmandia.jkassist.service.InfoService
import cc.worldmandia.jkassist.service.OperatorConsole
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val logger = LoggerFactory.getLogger("ChatRoutes")

@OptIn(ExperimentalUuidApi::class)
fun Routing.chatRoutes() {
    val crmService = CrmServiceImpl()
    val infoService = InfoService()

    val agent = JkAgentFactory.create(
        crmService,
        infoService = infoService
    )

    OperatorConsole.start()

    get("/api/chat/{sessionId}/history") {
        val sessionId = call.parameters["sessionId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val userId = call.request.queryParameters["userId"]

        if (userId.isNullOrBlank()) {
            return@get call.respond(HttpStatusCode.Unauthorized, "User ID required")
        }

        val history = ChatStateManager.getUiHistory(sessionId)
        call.respond(history)
    }

    webSocket("/ws/chat") {
        val userId = call.request.queryParameters["userId"] ?: "anonymous"
        val sessionUuid = call.request.queryParameters["sessionId"] ?: Uuid.generateV7().toHexDashString()

        logger.info("🟢 Чат підключено: userId=$userId, session=$sessionUuid")
        val session = agent.createSession(sessionUuid)

        var operatorListenerJob: Job? = null

        fun startOperatorListener() {
            operatorListenerJob?.cancel()
            operatorListenerJob = launch(Dispatchers.IO) {
                val channel = ChatStateManager.getOperatorChannel(sessionUuid)

                for (operatorText in channel) {
                    if (operatorText.trim().lowercase() == "end") {
                        ChatStateManager.removeWaitingStatus(sessionUuid)
                        val endMsg = WsEvent.Message(
                            id = "sys-${Uuid.generateV7().toHexDashString()}",
                            role = Role.SYSTEM,
                            text = "*(Системне повідомлення)* Оператор завершив діалог. Бот знову з вами.",
                            timestampMs = Clock.System.now()
                        )
                        sendEvent(endMsg)
                        break
                    }

                    if (operatorText.isNotBlank()) {
                        val opMsg = WsEvent.Message(
                            id = "op-${Uuid.generateV7().toHexDashString()}",
                            role = Role.SYSTEM,
                            text = operatorText,
                            timestampMs = Clock.System.now()
                        )
                        ChatStateManager.saveUiMessage(sessionUuid, opMsg)
                        sendEvent(opMsg)
                    }
                }
            }
        }

        sendEvent(WsEvent.SessionCreated(sessionUuid))

        if (ChatStateManager.isWaitingForOperator(sessionUuid)) {
            sendEvent(WsEvent.TransferToSupport)
            startOperatorListener()
        }

        try {
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val userText = frame.readText().trim()
                if (userText.isBlank()) continue

                if (userText == "/cancel_operator" && ChatStateManager.isWaitingForOperator(sessionUuid)) {
                    ChatStateManager.removeWaitingStatus(sessionUuid)

                    val cancelMsg = WsEvent.Message(
                        id = "sys-${Uuid.generateV7().toHexDashString()}",
                        role = Role.SYSTEM,
                        text = "*(Системне повідомлення)* Оператор завершив діалог (виклик скасовано). Бот знову з вами.",
                        timestampMs = Clock.System.now()
                    )
                    ChatStateManager.saveUiMessage(sessionUuid, cancelMsg)
                    sendEvent(cancelMsg)

                    println("\n👨‍💻 [ОПЕРАТОР] Користувач $sessionUuid скасував виклик.")
                    continue
                }

                val userMsg = WsEvent.Message(
                    id = Uuid.generateV7().toHexDashString(),
                    role = Role.USER,
                    text = userText,
                    timestampMs = Clock.System.now()
                )
                ChatStateManager.saveUiMessage(sessionUuid, userMsg)
                sendEvent(userMsg)

                if (ChatStateManager.isWaitingForOperator(sessionUuid)) {
                    println("\n💬 [КОРИСТУВАЧ $sessionUuid]: $userText")
                    continue
                }

                sendEvent(WsEvent.TypingStarted)

                val currentTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val enrichedText = "[SYSTEM context: поточний userId = $userId, поточний час = $currentTime]\n$userText"
                val botResponse = try {
                    session.run(enrichedText)
                } catch (e: Exception) {
                    logger.error("Помилка AI: ${e.message}")
                    "Вибачте, сталася технічна помилка при зверненні до ШІ. Спробуйте ще раз."
                }

                when {
                    botResponse.contains("[TRANSFER_REQUESTED]") -> {
                        val reason = botResponse.substringAfter("[TRANSFER_REQUESTED]").trim()
                        ChatStateManager.markWaitingForOperator(sessionUuid, Clock.System.now())

                        val transferMsg = WsEvent.Message(
                            id = "bot-${Uuid.generateV7().toHexDashString()}",
                            role = Role.BOT,
                            text = "Переключаю вас на фахівця. Будь ласка, зачекайте...",
                            timestampMs = Clock.System.now()
                        )
                        ChatStateManager.saveUiMessage(sessionUuid, transferMsg)
                        sendEvent(transferMsg)
                        sendEvent(WsEvent.TransferToSupport)

                        println("\n👨‍💻 [ОПЕРАТОР] Новий запит! Сесія: $sessionUuid | Причина: $reason")
                        startOperatorListener()
                    }

                    botResponse.contains("[RESET_REQUESTED]") -> {
                        ChatStateManager.clearSession(sessionUuid)
                        val newSessionUuid = Uuid.generateV7().toHexDashString()

                        val resetMsg = WsEvent.Message(
                            id = "sys-${Uuid.generateV7().toHexDashString()}",
                            role = Role.SYSTEM,
                            text = "*(Системне повідомлення)* Сесія оновлена. Історія очищена.",
                            timestampMs = Clock.System.now()
                        )
                        ChatStateManager.saveUiMessage(newSessionUuid, resetMsg)

                        sendEvent(WsEvent.SessionCreated(newSessionUuid))
                        sendEvent(resetMsg)

                        return@webSocket
                    }

                    else -> {
                        val responseText = botResponse.ifBlank {
                            "Вибачте, сталася затримка при обробці запиту або я не зміг згенерувати відповідь. Будь ласка, перефразуйте питання."
                        }

                        val normalMsg = WsEvent.Message(
                            id = "bot-${Uuid.generateV7().toHexDashString()}",
                            role = Role.BOT,
                            text = responseText,
                            timestampMs = Clock.System.now()
                        )
                        ChatStateManager.saveUiMessage(sessionUuid, normalMsg)
                        sendEvent(normalMsg)
                    }
                }
                sendEvent(WsEvent.TypingStopped)
            }
        } catch (e: Exception) {
            handleDisconnect(e, userId, sessionUuid)
        } finally {
            operatorListenerJob?.cancel()
        }
    }
}

private fun handleDisconnect(e: Exception, userId: String, sessionUuid: String) {
    when (e) {
        is ClosedReceiveChannelException -> logger.info("⚪ Чат закрыт: $userId ($sessionUuid)")
        is CancellationException -> logger.info("⚪ Соединение отменено: $userId")
        else -> logger.error("🔴 Ошибка $userId: ${e.message}", e)
    }
}

private suspend fun DefaultWebSocketServerSession.sendEvent(event: WsEvent) {
    send(Frame.Text(jsonFormat.encodeToString(event)))
}