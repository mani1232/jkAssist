package cc.worldmandia.jkassist.agent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import cc.worldmandia.jkassist.TicketCategory
import cc.worldmandia.jkassist.service.CrmService
import cc.worldmandia.jkassist.service.InfoService

@LLMDescription("Системные инструменты ЖК")
class JkSupportTools(
    private val crmService: CrmService,
    private val infoService: InfoService
) : ToolSet {

    @LLMDescription("Показать список всех заявок пользователя.")
    @Tool
    fun listMyTickets(
        @LLMDescription("ID пользователя из контекста") userId: String
    ): String {
        val tickets = infoService.getUserTickets(userId)
        if (tickets.isEmpty()) return "У вас пока нет созданных заявок."

        return "Ваши заявки:\n" + tickets.joinToString("\n") {
            "- **${it.id}**: ${it.description} (Статус: ${it.status})"
        }
    }

    @LLMDescription("Получить подробную информацию о конкретной заявке по её ID (например, REQ-1001).")
    @Tool
    fun getTicketDetails(
        @LLMDescription("ID заявки") ticketId: String
    ): String {
        val ticket = infoService.getTicketById(ticketId) ?: return "Заявка $ticketId не найдена."
        return """
            Детали заявки **${ticket.id}**:
            - Категория: ${ticket.category}
            - Описание: ${ticket.description}
            - Статус: ${ticket.status}
            - Дата создания: ${ticket.date}
        """.trimIndent()
    }

    @LLMDescription("Очистить историю диалога и начать новую сессию. Вызывай, если пользователь просит забыть контекст, стереть историю или начать всё сначала.")
    @Tool
    fun resetSession(): String = "[RESET_REQUESTED] История диалога была успешно очищена."

    @LLMDescription("Проверить наличие отключений воды, света или других ресурсов.")
    @Tool
    fun checkOutages(
        @LLMDescription("Тип ресурса (вода, свет, лифт)") resource: String? = null
    ): String {
        val list = infoService.getOutages(resource)
        if (list.isEmpty()) return "Информации об отключениях по запросу '$resource' нет."

        return list.joinToString("\n") {
            "${if (it.isEmergency) "🚨" else "📅"} ${it.type.uppercase()}: ${it.description}. Окончание: ${it.estimatedEndTime}"
        }
    }

    @LLMDescription("Получить информацию о пользователе (баланс, квартира) по его ID.")
    @Tool
    fun getUserProfile(
        @LLMDescription("ВСЕГДА используй системный ID пользователя, переданный тебе в начале диалога.") userId: String
    ): String {
        val user = infoService.findUser(userId) ?: return "Пользователь не найден."
        return "Жилец: ${user.surname} ${user.username}, кв. ${user.apartment}. Баланс: ${user.balance} руб."
    }

    @LLMDescription("Создать заявку в CRM. Требует ID пользователя.")
    @Tool
    suspend fun createTicket(
        @LLMDescription("ВСЕГДА используй системный ID пользователя, переданный тебе в начале диалога.") userId: String,
        @LLMDescription("Категория") category: TicketCategory,
        @LLMDescription("Описание") description: String
    ): String {
        val user = infoService.findUser(userId) ?: return "Ошибка: пользователь не найден."
        val ticket = crmService.registerEmergency(category, user.apartment, description, false, userId)
        return "Заявка **${ticket.id}** создана для квартиры ${user.apartment}."
    }

    @Tool
    fun requestOperator(reason: String): String = "[TRANSFER_REQUESTED] Причина: $reason"
}