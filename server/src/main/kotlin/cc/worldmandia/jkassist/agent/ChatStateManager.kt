package cc.worldmandia.jkassist.agent

import ai.koog.agents.chatMemory.feature.InMemoryChatHistoryProvider
import ai.koog.agents.snapshot.providers.file.JVMFilePersistenceStorageProvider
import ai.koog.prompt.message.*
import cc.worldmandia.jkassist.DataType
import cc.worldmandia.jkassist.Role
import cc.worldmandia.jkassist.WsEvent
import cc.worldmandia.jkassist.jsonFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.encoding.Base64
import kotlin.io.path.Path
import kotlin.time.Instant

object ChatStateManager {
    val sessionHistory = JVMFilePersistenceStorageProvider(Path("ai-history"))
    val sessionMemoryHistory = InMemoryChatHistoryProvider()

    private val waitingChats = mutableMapOf<String, Instant>()

    private val historyDir = File("ui_history").apply { mkdirs() }

    private val uiHistory = ConcurrentHashMap<String, MutableList<WsEvent.Message>>()

    fun getWaitingSessions(): Map<String, Instant> = waitingChats.toMap()

    fun saveUiMessage(sessionId: String, message: WsEvent.Message) {
        val history = uiHistory.computeIfAbsent(sessionId) { CopyOnWriteArrayList() }
        history.add(message)

        CoroutineScope(Dispatchers.IO).launch {
            val sessionFile = File(historyDir, "$sessionId.json")
            sessionFile.writeText(jsonFormat.encodeToString(history.toList()))
        }
    }

    init {
        val files = historyDir.listFiles { _, name -> name.endsWith(".json") }
        if (files != null) {
            for (file in files) {
                try {
                    val sessionId = file.nameWithoutExtension
                    val messages = jsonFormat.decodeFromString<List<WsEvent.Message>>(file.readText())

                    runBlocking {
                        uiHistory[sessionId] = messages.toMutableList()

                        val agentMessages = messages.map { msg ->
                            when (msg.role) {
                                Role.USER -> {
                                    if (msg.data != null) {
                                        when (msg.data!!.first) {
                                            DataType.AUDIO -> {
                                                val dataString = msg.data!!.second
                                                val mimeType = dataString.substringAfter("data:audio/").substringBefore(";")
                                                val format = mimeType.ifBlank { "webm" }

                                                val base64Str = dataString.substringAfter("base64,")
                                                val audioBytes = Base64.decode(base64Str)

                                                Message.User(
                                                    parts = listOf(
                                                        MessagePart.Text(msg.text),
                                                        MessagePart.Attachment(
                                                            AttachmentSource.Audio(
                                                                content = AttachmentContent.Binary.Bytes(audioBytes),
                                                                format = format,
                                                                fileName = "voice_message.$format"
                                                            )
                                                        )
                                                    ),
                                                    metaInfo = RequestMetaInfo(timestamp = msg.timestampMs)
                                                )
                                            }
                                            DataType.IMAGE -> TODO()
                                        }
                                    } else {
                                        Message.User(
                                            content = msg.text,
                                            metaInfo = RequestMetaInfo(timestamp = msg.timestampMs)
                                        )
                                    }
                                }

                                Role.BOT -> Message.Assistant(
                                    content = msg.text,
                                    metaInfo = ResponseMetaInfo(timestamp = msg.timestampMs)
                                )

                                Role.SYSTEM -> Message.System(
                                    content = msg.text,
                                    metaInfo = RequestMetaInfo(timestamp = msg.timestampMs)
                                )
                            }
                        }

                        sessionMemoryHistory.store(sessionId, agentMessages)

                        val lastTransfer = messages.indexOfLast { it.text.contains("Переключаю вас на фахівця") }
                        val lastEnd = messages.indexOfLast { it.text.contains("Оператор завершив діалог") }
                        if (lastTransfer > lastEnd) {
                            waitingChats[sessionId] = messages[lastTransfer].timestampMs
                        }
                    }
                } catch (e: Exception) {
                    println("Помилка під час відновлення історії з файлу ${file.name}: ${e.message}")
                }
            }
        }
    }

    suspend fun clearSession(sessionId: String) {
        uiHistory.remove(sessionId)
        sessionMemoryHistory.store(sessionId, emptyList())
        waitingChats.remove(sessionId)

        val sessionFile = File(historyDir, "$sessionId.json")
        if (sessionFile.exists()) {
            sessionFile.delete()
        }

        // File("ai-history/$sessionId.json").delete() // keep for analytics
    }

    fun getUiHistory(sessionId: String): List<WsEvent.Message> {
        return uiHistory[sessionId] ?: emptyList()
    }

    fun isWaitingForOperator(sessionId: String): Boolean {
        return waitingChats.containsKey(sessionId)
    }

    fun markWaitingForOperator(sessionId: String, timestamp: Instant) {
        waitingChats[sessionId] = timestamp
    }

    fun removeWaitingStatus(sessionId: String) {
        waitingChats.remove(sessionId)
    }

    private val operatorChannels = ConcurrentHashMap<String, Channel<String>>()

    fun getOperatorChannel(sessionId: String): Channel<String> {
        return operatorChannels.computeIfAbsent(sessionId) { Channel(Channel.BUFFERED) }
    }

    suspend fun sendOperatorMessage(sessionId: String, text: String) {
        if (isWaitingForOperator(sessionId)) {
            getOperatorChannel(sessionId).send(text)
        }
    }
}