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
    val status: String,
    val isEmergency: Boolean = false
)

@Serializable
data class UserTicket(
    val id: String,
    val userId: String,
    val category: TicketCategory,
    val description: String,
    val priority: String,
    val status: String,
    val date: Instant
)