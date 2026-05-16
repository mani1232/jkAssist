package cc.worldmandia.jkassist

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class User(
    val id: String,
    val username: String,
    val surname: String,
    val apartments: List<Apartment>,
    val phoneNumber: String,
    val email: String
)

@Serializable
data class Apartment(
    val number: Int, val countOfRoom: Int, val address: String, val balancePerApartment: Double = 0.0
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
        User(
            "user_123", "Иван", "Иванов", listOf(
                Apartment(
                    101, 3, "13 street", 250.0
                ), Apartment(
                    62, 1, "3 street", -100.0
                )
            ), "+380001112233", "ivan@mail.ru"
        ), User(
            "user_456", "Анна", "Петрова", listOf(
                Apartment(
                    55, 3, "55 street", -100.0
                ), Apartment(
                    18, 6, "100 street"
                )
            ), "+380004445566", "anna@mail.ru"
        )
    )

    val outages = listOf(
        Outage("вода", "Плановый ремонт труб", "15.05 10:00", "15.05 20:00"),
        Outage("свет", "Авария на подстанции", "15.05 14:00", "Неизвестно", true)
    )
}