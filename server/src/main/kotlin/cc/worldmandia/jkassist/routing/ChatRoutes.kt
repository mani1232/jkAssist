package cc.worldmandia.jkassist.routing

import ai.koog.prompt.message.*
import cc.worldmandia.jkassist.*
import cc.worldmandia.jkassist.agent.ChatStateManager
import cc.worldmandia.jkassist.agent.JkAgentFactory
import cc.worldmandia.jkassist.service.CrmServiceImpl
import cc.worldmandia.jkassist.service.InfoService
import cc.worldmandia.jkassist.service.OperatorConsole
import cc.worldmandia.jkassist.service.WebPushService
import io.ktor.http.*
import io.ktor.server.request.*
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
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val logger = LoggerFactory.getLogger("ChatRoutes")

@OptIn(ExperimentalUuidApi::class)
fun Routing.chatRoutes() {
    val pushPrivateKey = System.getenv("PRIVATE_KEY") ?: run {
        println("ERROR: missing PRIVATE_KEY, write it:")
        readln()
    }

    val webPushService = WebPushService(pushPrivateKey)

    val crmService = CrmServiceImpl()
    val infoService = InfoService()

    val agent = JkAgentFactory.create(
        crmService,
        infoService = infoService, webPushService
    )

    OperatorConsole.start()

    post("/api/notifications/subscribe") {
        val userId = call.request.queryParameters["userId"]
            ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing userId")

        val subscriptionJson = call.receiveText()

        MockDatabase.pushSubscriptions[userId] = subscriptionJson
        logger.info("🔑 Push-токен збережено для $userId")

        println("Отримано Push-токен для $userId: $subscriptionJson")
        call.respond(HttpStatusCode.OK)
    }

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
                            text = "*(Системне повідомлення)* Оператор завершив діалог. Дякуємо за очікування, бот знову на зв'язку.",
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

                        launch {
                            webPushService.sendPushNotification(
                                userId = userId,
                                title = "Оператор AIHome",
                                body = operatorText
                            )
                        }
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
                val rawText = frame.readText().trim()
                if (rawText.isBlank()) continue

                val incomingMsg = try {
                    jsonFormat.decodeFromString<WsEvent.Message>(rawText)
                } catch (_: Exception) {
                    WsEvent.Message(
                        id = Uuid.generateV7().toHexDashString(),
                        role = Role.USER,
                        text = rawText,
                        timestampMs = Clock.System.now()
                    )
                }

                val userText = incomingMsg.text
                val data = incomingMsg.data

                if (userText == "/cancel_operator" && ChatStateManager.isWaitingForOperator(sessionUuid)) {
                    ChatStateManager.removeWaitingStatus(sessionUuid)

                    val cancelMsg = WsEvent.Message(
                        id = "sys-${Uuid.generateV7().toHexDashString()}",
                        role = Role.SYSTEM,
                        text = "*(Системне повідомлення)* Ви скасували виклик оператора. Бот знову з вами.",
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
                    timestampMs = Clock.System.now(),
                    data = data
                )
                ChatStateManager.saveUiMessage(sessionUuid, userMsg)
                sendEvent(userMsg)

                if (ChatStateManager.isWaitingForOperator(sessionUuid)) {
                    println("\n💬 [КОРИСТУВАЧ $sessionUuid]: $userText ${if(data != null) "[Data]" else ""}")
                    continue
                }

                sendEvent(WsEvent.TypingStarted)

                val currentTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val enrichedText = "[SYSTEM context: поточний userId = $userId, поточний час = $currentTime]\n$userText"
                val botResponse = try {
                    if (data != null) {
                        when (data.first) {
                            DataType.AUDIO -> {
                                logger.info("🎙️ Отримано аудіо від $userId, розмір Base64: ${data.second.length} символів")

                                val dataString = data.second
                                val mimeType = dataString.substringAfter("data:audio/").substringBefore(";")
                                val format = mimeType.ifBlank { "webm" }

                                val base64Str = dataString.substringAfter("base64,")
                                val audioBytes = Base64.decode(base64Str)

                                val userMessage = Message.User(
                                    parts = listOf(
                                        MessagePart.Text(enrichedText),
                                        MessagePart.Attachment(
                                            AttachmentSource.Audio(
                                                content = AttachmentContent.Binary.Bytes(audioBytes),
                                                format = format,
                                                fileName = "voice_message.$format"
                                            )
                                        )
                                    ),
                                    metaInfo = RequestMetaInfo(timestamp = Clock.System.now())
                                )

                                val currentHistory = ChatStateManager.sessionMemoryHistory.load(sessionUuid)
                                val updatedHistory = currentHistory + userMessage
                                ChatStateManager.sessionMemoryHistory.store(sessionUuid, updatedHistory)

                                session.run(" ")
                            }
                            DataType.IMAGE -> TODO()
                        }

                    } else {
                        session.run(enrichedText)
                    }
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

                        launch {
                            webPushService.sendPushNotification(
                                userId = userId,
                                title = "AIHome Асистент",
                                body = responseText.take(100) + if (responseText.length > 100) "..." else ""
                            )
                        }
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