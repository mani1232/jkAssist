package cc.worldmandia.jkassist.service

import cc.worldmandia.jkassist.Apartment
import cc.worldmandia.jkassist.MockDatabase
import cc.worldmandia.jkassist.TicketCategory
import cc.worldmandia.jkassist.UserTicket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.time.Clock

interface CrmService {
    suspend fun registerEmergency(
        category: TicketCategory,
        apartment: Apartment,
        description: String,
        priority: String,
        urgent: Boolean,
        userId: String
    ): UserTicket

    suspend fun cancelTicket(ticketId: String): Boolean
}

class CrmServiceImpl : CrmService {
    private val logger = LoggerFactory.getLogger(CrmServiceImpl::class.java)

    override suspend fun registerEmergency(
        category: TicketCategory,
        apartment: Apartment,
        description: String,
        priority: String,
        urgent: Boolean,
        userId: String
    ): UserTicket = withContext(Dispatchers.IO) {

        UserTicket(
            id = "REQ-${(1000..9999).random()}",
            userId = userId,
            category = category,
            description = description,
            priority = priority,
            status = "Очікування",
            date = Clock.System.now()
        ).also {
            MockDatabase.tickets.add(it)
            logger.info("🎟️ Заявка створена: $it | Категорія: $category | Квартира: $apartment | Терміново: $urgent")
        }
    }

    override suspend fun cancelTicket(ticketId: String): Boolean = withContext(Dispatchers.IO) {
        val index = MockDatabase.tickets.indexOfFirst { it.id == ticketId }
        if (index != -1) {
            val ticket = MockDatabase.tickets[index]

            MockDatabase.tickets[index] = ticket.copy(status = "Скасовано")
            logger.info("🚫 Заявка скасована: $ticketId")
            return@withContext true
        }
        return@withContext false
    }

}