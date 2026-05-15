package cc.worldmandia.jkassist

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class User(
    val id: String,
    val username: String,
    val surname: String,
    val apartment: String,
    val phoneNumber: String,
    val email: String,
    val balance: Double = 0.0
)

@Serializable
data class Outage(
    val type: String,
    val description: String,
    val startTime: String,
    val estimatedEndTime: String,
    val isEmergency: Boolean = false
)

@Serializable
data class UserTicket(
    val id: String,
    val userId: String,
    val category: TicketCategory,
    val description: String,
    val status: String,
    val date: Instant
)


object MockDatabase {
    val tickets = mutableListOf(
        UserTicket(
            id = "REQ-1001",
            userId = "user_123",
            category = TicketCategory.PLUMBING,
            description = "Капает кран на кухне",
            status = "Назначен мастер",
            date = Instant.parse("2026-04-16T18:43:00Z")
        )
    )

    val users = listOf(
        User("user_123", "Иван", "Иванов", "101", "+380001112233", "ivan@mail.ru", -1250.0),
        User("user_456", "Анна", "Петрова", "205", "+380004445566", "anna@mail.ru", 500.0)
    )

    val outages = listOf(
        Outage("вода", "Плановый ремонт труб", "15.05 10:00", "15.05 20:00"),
        Outage("свет", "Авария на подстанции", "15.05 14:00", "Неизвестно", true)
    )
}