package cc.worldmandia.jkassist

import kotlin.time.Instant

object MockDatabase {

    val pushSubscriptions = mutableMapOf<String, String>()

    val tickets = mutableListOf(
        UserTicket(
            id = "REQ-1001",
            userId = "user_123",
            category = TicketCategory.PLUMBING,
            description = "На кухні капає кран",
            status = "Призначено майстра",
            priority = "Висока",
            date = Instant.parse("2026-04-16T18:43:00Z")
        )
    )

    val users = listOf(
        User(
            "user_123", "Mykyta", "Secret", listOf(
                Apartment(
                    101, 3, "13 street", 250.0
                ), Apartment(
                    62, 1, "3 street", -100.0
                )
            ), "+380001112233", "mykyta@gmail.com"
        ), User(
            "user_456", "Anna", "Bogdanova", listOf(
                Apartment(
                    55, 3, "55 street", -100.0
                ), Apartment(
                    18, 6, "100 street"
                )
            ), "+380004445566", "anna@gmail.com"
        )
    )

    val outages = listOf(
        Outage("вода", "Плановий ремонт труб", "15.05.2026 10:00", "15.05.2026 20:00", "Completed"),
        Outage("світло", "Аварія на підстанції", "15.05.2026 14:00", "Невідомо", "Uncompleted", true)
    )
}