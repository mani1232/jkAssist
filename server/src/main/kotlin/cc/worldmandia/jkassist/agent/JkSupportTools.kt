package cc.worldmandia.jkassist.agent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import cc.worldmandia.jkassist.TicketCategory
import cc.worldmandia.jkassist.jsonFormat
import cc.worldmandia.jkassist.service.CrmService
import cc.worldmandia.jkassist.service.InfoService
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

@LLMDescription("Інструменти ШІ-диспетчера")
class JkSupportTools(
    private val crmService: CrmService,
    private val infoService: InfoService
) : ToolSet {

    @LLMDescription("Показати список усіх поточних заявок користувача.")
    @Tool
    fun listMyTickets(
        @LLMDescription("ID користувача з контексту") userId: String
    ): String {
        val tickets = infoService.getUserTickets(userId)
        if (tickets.isEmpty()) return "У вас поки що немає створених заявок."

        return "Ваші заявки:\n" + tickets.joinToString("\n") {
            "- **${it.id}**: ${it.description} (Статус: ${it.status})"
        }
    }

    @LLMDescription("Отримати детальну інформацію про конкретну заявку за її ID (наприклад, REQ-1001).")
    @Tool
    fun getTicketDetails(
        @LLMDescription("ID заявки") ticketId: String
    ): String {
        val ticket = infoService.getTicketById(ticketId) ?: return "Заявку $ticketId не знайдено."
        return """
            Деталі заявки **${ticket.id}**:
            - Категорія: ${ticket.category}
            - Опис: ${ticket.description}
            - Статус: ${ticket.status}
            - Дата створення: ${ticket.date}
        """.trimIndent()
    }

    @LLMDescription("Очистити історію діалогу та почати нову сесію. Викликай, якщо користувач просить забути контекст, стерти історію або почати все спочатку.")
    @Tool
    fun resetSession(): String = "[RESET_REQUESTED] Історія діалогу була успішно очищена."

    @LLMDescription("Перевірити наявність планових чи аварійних відключень води, світла або інших ресурсів.")
    @Tool
    fun checkOutages(
        @LLMDescription("Тип ресурсу (наприклад: вода, світло, ліфт)") resource: String? = null
    ): String {
        val list = infoService.getOutages(resource)
        val queryText = resource?.let { " за запитом «$it»" } ?: ""

        if (list.isEmpty()) return "Інформації про відключення$queryText немає."

        return jsonFormat.encodeToString(list)
    }

    @LLMDescription("Отримати повну інформацію про профіль користувача за його ID.")
    @Tool
    fun getUserProfile(
        @LLMDescription("ЗАВЖДИ використовуй системний ID користувача, переданий тобі на початку діалогу.") userId: String
    ): String {
        val user = infoService.findUser(userId) ?: return "Користувача не знайдено."
        return "Info мешканець: ${jsonFormat.encodeToString(user)}"
    }

    @LLMDescription("Створити нову заявку в CRM. Вимагає ID користувача.")
    @Tool
    suspend fun createTicket(
        @LLMDescription("ЗАВЖДИ використовуй системний ID користувача, переданий тобі на початку діалогу.") userId: String,
        @LLMDescription("ApartmentNumber from can get from `getUserProfile`") apartmentNumber: Int,
        @LLMDescription("Категорія проблеми") category: TicketCategory,
        @LLMDescription("Priority проблеми") priority: String,
        @LLMDescription("Детальний опис проблеми") description: String
    ): String {
        val user = infoService.findUser(userId) ?: return "Помилка: користувача не знайдено."
        user.apartments.first { it.number == apartmentNumber }.let {
            val ticket = crmService.registerEmergency(category, it, description, priority, false, userId)
            return "Заявка **${ticket.id}** успішно створена для квартири ${it}."
        }
    }

    @LLMDescription("Перевести діалог на живого оператора підтримки.")
    @Tool
    fun requestOperator(
        @LLMDescription("Коротка причина переводу") reason: String
    ): String = "[TRANSFER_REQUESTED] Причина: $reason"

    @OptIn(FormatStringsInDatetimeFormats::class)
    @Tool
    @LLMDescription("Отримання поточної актуальної дати та часу.")
    fun requestDate(): String = """
        Поточна дата та час: ${
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).format(LocalDateTime.Format {
            byUnicodePattern("yyyy-MM-dd'T'HH:mm:ss[.SSS]")
        })
    }
    """.trimIndent()
}