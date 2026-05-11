package cc.worldmandia.jkassist

import kotlin.jvm.JvmInline
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
value class TicketId(val value: String)

enum class Role { USER, BOT, SYSTEM }

@Serializable
sealed interface WsEvent {
    @Serializable
    data class Message(
        val id: String,
        val role: Role,
        val text: String,
        val timestamp: Instant
    ) : WsEvent

    @Serializable data object TypingStarted : WsEvent
    @Serializable data object TypingStopped : WsEvent
    @Serializable data class Error(val message: String) : WsEvent
}