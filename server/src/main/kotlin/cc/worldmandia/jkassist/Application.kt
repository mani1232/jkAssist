package cc.worldmandia.jkassist

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.chatMemory.feature.InMemoryChatHistoryProvider
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.streaming.StreamFrame
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.util.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

interface CrmService {
    suspend fun registerEmergency(apartment: String, description: String, urgent: Boolean): TicketId
}

class CrmServiceImpl : CrmService {
    override suspend fun registerEmergency(apartment: String, description: String, urgent: Boolean): TicketId {
        delay(800.milliseconds)
        return TicketId("REQ-${(1000..9999).random()}").also {
            println(it)
            println(apartment)
            println(description)
            println(urgent)
        }
    }
}

class JkSupportTools(private val crmService: CrmService) : ToolSet {
    @LLMDescription("Выдать жителю первичную инструкцию по безопасности при аварии (вода, свет, газ). Вызывать ДО оформления заявки.")
    @Tool
    fun getEmergencyAdvice(
        @LLMDescription("вода, свет, газ")
        issueType: String
    ): String = when {
        "вода" in issueType.lowercase() -> "Срочно перекройте вводные вентили на стояке (обычно в санузле) и уберите воду с пола."
        "газ" in issueType.lowercase() -> "Немедленно откройте окна, не включайте электроприборы и вызовите службу 104!"
        else -> "Пожалуйста, минимизируйте использование неисправного оборудования до прихода мастера."
    }

    @LLMDescription("Создать заявку на вызов мастера. Обязательно требует номер квартиры.")
    @Tool
    suspend fun createTicket(
        @LLMDescription("Номер квартиры")
        apartmentNumber: String,
        @LLMDescription("Более подробное описание с цитированием")
        issueDescription: String,
        @LLMDescription("Это срочно?")
        isUrgent: Boolean): String {
        val ticket = crmService.registerEmergency(apartmentNumber, issueDescription, isUrgent)
        return "Заявка успешно зарегистрирована. Внутренний номер: ${ticket.value}"
    }
}

fun Application.module() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    val model = GoogleModels.Gemini2_5FlashLite
    val executor = simpleGoogleAIExecutor("token")
    val toolRegistry = ToolRegistry {
        tools(JkSupportTools(CrmServiceImpl()))
    }

    val agent = AIAgent(
        executor, model, toolRegistry = toolRegistry, systemPrompt = """
                        Ты — эмпатичный ИИ-диспетчер ЖК "Светлый". Твой алгоритм:
                        1. Если авария — дай совет (getEmergencyAdvice).
                        2. Узнай номер квартиры.
                        3. Оформить заявку с помощью инструмента createTicket и сообщить пользователю её внутренний номер.""".trimIndent(),
        //temperature = 0.6
    ) {
        install(ChatMemory) {
            chatHistoryProvider = InMemoryChatHistoryProvider()
            windowSize(50)
        }
    }



    routing {
        webSocket("/ws/chat") {
            val userId = call.request.queryParameters["userId"] ?: "anonymous"

            val session = agent.createSession(userId)

            session.run("Пользователя зовут $userId")

            session.context().llm.writeSession {
                try {
                    for (frame in incoming) {
                        if (frame !is Frame.Text) continue
                        val userText = frame.readText()

                        sendSerializedEvent(WsEvent.TypingStarted)

                        try {
                            val messageId = "bot-${UUID.randomUUID()}"

                            appendPrompt {
                                user(userText)
                            }

                            val flow = requestLLMStreaming()

                            val sb = StringBuilder()

                            flow.collect { chunk ->
                                if (chunk is StreamFrame.TextDelta) {
                                    sb.append(chunk.text)
                                    val responseMsg = WsEvent.Message(
                                        id = messageId,
                                        role = Role.BOT,
                                        text = sb.toString(),
                                        timestamp = Clock.System.now()
                                    )
                                    sendSerializedEvent(responseMsg)
                                }
                            }

                            sendSerializedEvent(WsEvent.TypingStopped)

                        } catch (e: Exception) {
                            sendSerializedEvent(WsEvent.TypingStopped)
                            sendSerializedEvent(WsEvent.Error("AI Error: ${e.localizedMessage}"))
                        }
                    }
                } catch (e: Exception) {
                    log.error("Connection for $userId closed or error occurred: ${e.message}")
                }
            }
        }
    }
}

private suspend fun DefaultWebSocketServerSession.sendSerializedEvent(event: WsEvent) {
    val jsonString = Json.encodeToString(event)
    send(Frame.Text(jsonString))
}

fun main() {
    embeddedServer(CIO, configure = {
        connectors.add(EngineConnectorBuilder().apply {
            host = "127.0.0.1"
            port = 8800
        })
        connectionGroupSize = 2
        workerGroupSize = 5
        callGroupSize = 10
        shutdownGracePeriod = 2000
        shutdownTimeout = 3000
    }) {
        module()
    }.start(wait = true)
}