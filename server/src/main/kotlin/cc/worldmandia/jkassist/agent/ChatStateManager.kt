package cc.worldmandia.jkassist.agent

import ai.koog.agents.chatMemory.feature.InMemoryChatHistoryProvider
import ai.koog.agents.snapshot.providers.file.JVMFilePersistenceStorageProvider
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import cc.worldmandia.jkassist.Role
import cc.worldmandia.jkassist.WsEvent
import cc.worldmandia.jkassist.jsonFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.Path
import kotlin.time.Instant

object ChatStateManager {
    val sessionHistory = JVMFilePersistenceStorageProvider(Path("ai-history"))
    val sessionMemoryHistory = InMemoryChatHistoryProvider()

    private val waitingChats = mutableMapOf<String, Instant>()

    private val uiHistoryFile = File("ui_history.json")
    private val uiHistory = ConcurrentHashMap<String, MutableList<WsEvent.Message>>()
    private val fileMutex = Mutex()

    fun saveUiMessage(sessionId: String, message: WsEvent.Message) {
        val history = uiHistory.computeIfAbsent(sessionId) { CopyOnWriteArrayList() }
        history.add(message)

        CoroutineScope(Dispatchers.IO).launch {
            fileMutex.withLock {
                uiHistoryFile.writeText(jsonFormat.encodeToString(uiHistory.toMutableMap()))
            }
        }
    }

    init {
        if (uiHistoryFile.exists()) {
            try {
                val data =
                    jsonFormat.decodeFromString<MutableMap<String, List<WsEvent.Message>>>(uiHistoryFile.readText())

                runBlocking {
                    data.forEach { (sessionId, messages) ->
                        uiHistory[sessionId] = messages.toMutableList()

                        val agentMessages = messages.map { msg ->
                            when (msg.role) {
                                Role.USER -> Message.User(
                                    content = msg.text,
                                    metaInfo = RequestMetaInfo(timestamp = msg.timestampMs)
                                )

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

                        val lastTransfer = messages.indexOfLast { it.text.contains("Переключаю вас на специалиста") }
                        val lastEnd = messages.indexOfLast { it.text.contains("Оператор завершил диалог") }
                        if (lastTransfer > lastEnd) {
                            waitingChats[sessionId] = messages[lastTransfer].timestampMs
                        }
                    }
                }
            } catch (e: Exception) {
                println("Ошибка восстановления истории: ${e.message}")
            }
        }
    }

    suspend fun clearSession(sessionId: String) {
        uiHistory.remove(sessionId)
        sessionMemoryHistory.store(sessionId, emptyList())
        waitingChats.remove(sessionId)

        // File("ai-history/$sessionId.json").delete() Keep for history

        saveToFile()
    }

    fun getUiHistory(sessionId: String): List<WsEvent.Message> {
        return uiHistory[sessionId] ?: emptyList()
    }

    private fun saveToFile() {
        try {
            uiHistoryFile.writeText(jsonFormat.encodeToString(uiHistory.toMutableMap()))
        } catch (e: Exception) {
            println("Ошибка сохранения истории в файл: ${e.message}")
        }
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
}