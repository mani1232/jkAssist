package cc.worldmandia.jkassist.routing

import ai.koog.agents.core.agent.context.AIAgentContext
import ai.koog.agents.core.agent.session.AIAgentRunSession
import cc.worldmandia.jkassist.Role
import cc.worldmandia.jkassist.WsEvent
import cc.worldmandia.jkassist.agent.ChatStateManager
import cc.worldmandia.jkassist.agent.JkAgentFactory
import cc.worldmandia.jkassist.jsonFormat
import cc.worldmandia.jkassist.service.CrmServiceImpl
import cc.worldmandia.jkassist.service.InfoService
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val logger = LoggerFactory.getLogger("ChatRoutes")

@OptIn(ExperimentalUuidApi::class, FormatStringsInDatetimeFormats::class)
fun Routing.chatRoutes() {
    val crmService = CrmServiceImpl()
    val infoService = InfoService()

    val agent = JkAgentFactory.create(
        crmService,
        infoService = infoService
    )

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

        logger.info("🟢 Чат подключен: userId=$userId, session=$sessionUuid")
        val session = agent.createSession(sessionUuid)
        session.run(
            """
            [СИСТЕМНОЕ СООБЩЕНИЕ] Установлено соединение. ID текущего собеседника: $userId. Сохрани этот ID в контексте и ВСЕГДА используй его как аргумент `userId` для вызова инструментов. КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО просить пользователя назвать свой ID.
            Используй эту дату что бы давать актуальную информацию, сегодня ${
                Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).format(LocalDateTime.Format {
                    byUnicodePattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]")
                })
            }
            """.trimIndent()
        )

        sendEvent(WsEvent.SessionCreated(sessionUuid))

        if (ChatStateManager.isWaitingForOperator(sessionUuid)) {
            sendEvent(WsEvent.TransferToSupport)
            startOperatorConsole(sessionUuid, "Восстановление сессии после переподключения")
        }

        try {
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val userText = frame.readText().trim()
                if (userText.isBlank()) continue

                val userMsg = WsEvent.Message(
                    id = Uuid.generateV7().toHexDashString(),
                    role = Role.USER,
                    text = userText,
                    timestampMs = Clock.System.now()
                )
                ChatStateManager.saveUiMessage(sessionUuid, userMsg)
                sendEvent(userMsg)

                if (ChatStateManager.isWaitingForOperator(sessionUuid)) {
                    handleOperatorSimulation(userText)
                    continue
                }

                sendEvent(WsEvent.TypingStarted)
                handleAiResponse(session, sessionUuid, userText, userId)
                sendEvent(WsEvent.TypingStopped)
            }
        } catch (e: Exception) {
            handleDisconnect(e, userId, sessionUuid)
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun DefaultWebSocketServerSession.startOperatorConsole(sessionUuid: String, reason: String? = null) {
    launch(Dispatchers.IO) {
        println("\n=======================================================")
        println("👨‍💻 [ОПЕРАТОР] Активная сессия: $sessionUuid")
        if (reason != null) println("👨‍💻 $reason")
        println("👨‍💻 Введите ответ в консоль и нажмите Enter.")
        println("👨‍💻 (Для возврата к ИИ введите слово 'end')")
        println("=======================================================\n")

        while (ChatStateManager.isWaitingForOperator(sessionUuid)) {
            val operatorText = readlnOrNull() ?: continue

            if (operatorText.trim().lowercase() == "end") {
                ChatStateManager.removeWaitingStatus(sessionUuid)
                val endMsg = WsEvent.Message(
                    id = "sys-${Uuid.generateV7().toHexDashString()}",
                    role = Role.SYSTEM,
                    text = "*(Системное сообщение)* Оператор завершил диалог. Бот снова с вами.",
                    timestampMs = Clock.System.now()
                )
                ChatStateManager.saveUiMessage(sessionUuid, endMsg)
                sendEvent(endMsg)
                println("👨‍💻 Вы вышли из режима оператора для сессии $sessionUuid.\n")
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

private fun DefaultWebSocketServerSession.handleOperatorSimulation(userText: String) {
    println("👤 [ПОЛЬЗОВАТЕЛЬ]: $userText")
}

@OptIn(ExperimentalUuidApi::class)
private suspend fun DefaultWebSocketServerSession.handleAiResponse(
    session: AIAgentRunSession<String, String, out AIAgentContext>,
    sessionUuid: String,
    userText: String,
    userId: String
) {
    try {
        val botResponse = session.run(userText)

        when {
            botResponse.contains("[TRANSFER_REQUESTED]") -> {
                val reason = botResponse.substringAfter("[TRANSFER_REQUESTED]").trim()
                ChatStateManager.markWaitingForOperator(sessionUuid, Clock.System.now())

                val transferMsg = WsEvent.Message(
                    id = "bot-${Uuid.generateV7().toHexDashString()}",
                    role = Role.BOT,
                    text = "Переключаю вас на специалиста. Пожалуйста, подождите...",
                    timestampMs = Clock.System.now()
                )
                ChatStateManager.saveUiMessage(sessionUuid, transferMsg)
                sendEvent(transferMsg)
                sendEvent(WsEvent.TransferToSupport)

                startOperatorConsole(sessionUuid, "Причина: $reason")
            }

            botResponse.contains("[RESET_REQUESTED]") -> {
                ChatStateManager.clearSession(sessionUuid)

                val newSessionUuid = Uuid.generateV7().toHexDashString()

                val resetMsg = WsEvent.Message(
                    id = "sys-${Uuid.generateV7().toHexDashString()}",
                    role = Role.BOT,
                    text = "Сессия обновлена. История очищена.",
                    timestampMs = Clock.System.now()
                )

                ChatStateManager.saveUiMessage(newSessionUuid, resetMsg)
                sendEvent(resetMsg)
                sendEvent(WsEvent.SessionCreated(newSessionUuid))

                session.run("[СИСТЕМНОЕ СООБЩЕНИЕ] Установлено новое соединение после очистки истории. ID текущего собеседника: $userId. Сохрани этот ID в контексте и ВСЕГДА используй его как аргумент `userId` для вызова инструментов. КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО просить пользователя назвать свой ID.")
            }

            else -> {
                val normalMsg = WsEvent.Message(
                    id = "bot-${Uuid.generateV7().toHexDashString()}",
                    role = Role.BOT,
                    text = botResponse,
                    timestampMs = Clock.System.now()
                )
                ChatStateManager.saveUiMessage(sessionUuid, normalMsg)
                sendEvent(normalMsg)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.error("🔴 Ошибка LLM: ${e.message}", e)
        sendEvent(WsEvent.Error("Внутренняя ошибка. Попробуйте еще раз."))
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