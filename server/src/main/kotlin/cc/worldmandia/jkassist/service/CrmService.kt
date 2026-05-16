package cc.worldmandia.jkassist.service

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
        apartment: String,
        description: String,
        urgent: Boolean,
        userId: String
    ): UserTicket
}

class CrmServiceImpl : CrmService {
    private val logger = LoggerFactory.getLogger(CrmServiceImpl::class.java)

    override suspend fun registerEmergency(
        category: TicketCategory, apartment: String, description: String, urgent: Boolean, userId: String
    ): UserTicket = withContext(Dispatchers.IO) {

        UserTicket(
            id = "REQ-${(1000..9999).random()}",
            userId = userId,
            category = category,
            description = description,
            status = "Очікування",
            date = Clock.System.now()
        ).also {
            MockDatabase.tickets.add(it)
            logger.info("🎟️ Заявка створена: $it | Категорія: $category | Квартира: $apartment | Терміново: $urgent")
        }
    }
}