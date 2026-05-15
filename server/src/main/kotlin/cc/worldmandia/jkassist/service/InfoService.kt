package cc.worldmandia.jkassist.service

import cc.worldmandia.jkassist.MockDatabase
import cc.worldmandia.jkassist.Outage
import cc.worldmandia.jkassist.User
import cc.worldmandia.jkassist.UserTicket

class InfoService {
    fun findUser(userId: String): User? = MockDatabase.users.find { it.id == userId }

    fun getOutages(resource: String? = null): List<Outage> {
        if (resource.isNullOrBlank()) return MockDatabase.outages
        return MockDatabase.outages.filter { it.type.contains(resource.lowercase()) }
    }

    fun getUserTickets(userId: String): List<UserTicket> {
        return MockDatabase.tickets.filter { it.userId == userId }
    }

    fun getTicketById(ticketId: String): UserTicket? {
        return MockDatabase.tickets.find { it.id == ticketId }
    }
}