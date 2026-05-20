package cc.worldmandia.jkassist

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
enum class Role { USER, BOT, SYSTEM }
@Serializable
enum class DataType { AUDIO, IMAGE }

@Serializable
enum class TicketCategory { PLUMBING, ELECTRICAL, GAS, CARPENTRY, CLEANING, OTHER }

@Serializable
sealed interface WsEvent {
    @Serializable
    data class Message(
        val id: String,
        val role: Role,
        val text: String,
        val timestampMs: Instant,
        val data: Pair<DataType, String>? = null
    ) : WsEvent

    @Serializable
    data object TypingStarted : WsEvent
    @Serializable
    data object TypingStopped : WsEvent
    @Serializable
    data class Error(val message: String) : WsEvent

    @Serializable
    data object TransferToSupport : WsEvent

    @Serializable
    @SerialName("session_created")
    data class SessionCreated(val sessionId: String) : WsEvent
}