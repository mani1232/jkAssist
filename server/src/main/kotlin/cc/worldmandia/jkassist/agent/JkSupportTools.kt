package cc.worldmandia.jkassist.agent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import cc.worldmandia.jkassist.TicketCategory
import cc.worldmandia.jkassist.service.CrmService
import cc.worldmandia.jkassist.service.InfoService
import cc.worldmandia.jkassist.service.JkWebPushService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@LLMDescription("Інструменти ШІ-диспетчера для допомоги мешканцям ЖК")
class JkSupportTools(
    private val crmService: CrmService,
    private val infoService: InfoService
) : ToolSet {

    @LLMDescription("Показати список усіх поточних заявок користувача.")
    @Tool
    fun listMyTickets(
        @LLMDescription("ID користувача з контексту [SYSTEM context]") userId: String
    ): String {
        val tickets = infoService.getUserTickets(userId)
        if (tickets.isEmpty()) return "У вас поки що немає створених заявок."

        return "Ваші заявки:\n" + tickets.joinToString("\n") {
            "- **${it.id}**: ${it.description} (Статус: ${it.status})"
        }
    }

    @OptIn(FormatStringsInDatetimeFormats::class)
    @LLMDescription("Отримати детальну інформацію про конкретну заявку за її ID (наприклад, REQ-1001).")
    @Tool
    fun getTicketDetails(
        @LLMDescription("ID заявки (формат REQ-XXXX)") ticketId: String
    ): String {
        val ticket = infoService.getTicketById(ticketId) ?: return "Заявку $ticketId не знайдено."
        val dateStr = ticket.date.toLocalDateTime(TimeZone.currentSystemDefault())
            .format(LocalDateTime.Format { byUnicodePattern("yyyy-MM-dd HH:mm") })

        return """
            Деталі заявки **${ticket.id}**:
            - Категорія: ${ticket.category}
            - Опис: ${ticket.description}
            - Пріоритет: ${ticket.priority}
            - Статус: ${ticket.status}
            - Дата створення: $dateStr
        """.trimIndent()
    }

    @LLMDescription("Очистити історію діалогу та почати нову сесію. Викликай, якщо користувач просить забути контекст, стерти історію або почати все спочатку.")
    @Tool
    fun resetSession(): String = "[RESET_REQUESTED] Історія діалогу була успішно очищена."

    @LLMDescription("Перевірити наявність планових чи аварійних відключень води, світла або інших ресурсів.")
    @Tool
    fun checkOutages(
        @LLMDescription("Тип ресурсу (наприклад: 'вода', 'світло', 'ліфт'). Якщо не вказано, поверне всі.") resource: String? = null
    ): String {
        val list = infoService.getOutages(resource)
        val queryText = resource?.let { " за запитом «$it»" } ?: ""

        if (list.isEmpty()) return "Інформації про відключення$queryText немає."

        // Возвращаем текст вместо сырого JSON для лучшего понимания моделью
        return list.joinToString("\n") { outage ->
            "Тип: ${outage.type}, Опис: ${outage.description}, Початок: ${outage.startTime}, Завершення: ${outage.estimatedEndTime}, Статус: ${outage.status}, Аварійне: ${outage.isEmergency}"
        }
    }

    @LLMDescription("Отримати повну інформацію про профіль користувача (ім'я, телефон, список його квартир та баланс).")
    @Tool
    fun getUserProfile(
        @LLMDescription("ЗАВЖДИ використовуй поточний userId з системного контексту.") userId: String
    ): String {
        val user = infoService.findUser(userId) ?: return "Користувача не знайдено."

        val apartmentsInfo = user.apartments.joinToString("; ") {
            "Квартира №${it.numberOfApartment} (${it.countOfRoom} кімн., адреса: ${it.address}, баланс: ${it.balancePerApartment})"
        }

        return "Мешканець: ${user.username} ${user.surname}. Телефон: ${user.phoneNumber}. Квартири: $apartmentsInfo"
    }

    @LLMDescription("Створити нову заявку на ремонт/обслуговування в CRM.")
    @Tool
    suspend fun createTicket(
        @LLMDescription("Поточний userId з системного контексту.") userId: String,
        @LLMDescription("Номер квартири (отримай з getUserProfile та узгодь з користувачем).") apartmentNumber: Int,
        @LLMDescription("Категорія. Допустимі значення: PLUMBING, ELECTRICAL, GAS, CARPENTRY, CLEANING, OTHER") category: TicketCategory,
        @LLMDescription("Пріоритет. Визначи сам. Допустимі: 'Низька', 'Середня', 'Висока', 'Критична'") priority: String,
        @LLMDescription("Детальний опис проблеми від користувача") description: String
    ): String {
        val user = infoService.findUser(userId) ?: return "Помилка: користувача не знайдено."

        val apartment = user.apartments.firstOrNull { it.numberOfApartment == apartmentNumber }
        if (apartment == null) {
            val validApts = user.apartments.map { it.numberOfApartment }
            return "Помилка: Користувач не є власником квартири $apartmentNumber. Доступні квартири: $validApts"
        }

        val isUrgent = priority == "Критична" || priority == "Висока"
        val ticket = crmService.registerEmergency(category, apartment, description, priority, isUrgent, userId)

        return "Заявка **${ticket.id}** успішно створена для квартири №${apartment.numberOfApartment}."
    }

    @LLMDescription("Перевести діалог на живого оператора підтримки (якщо питання складне, екстрене або користувач сам просить).")
    @Tool
    fun requestOperator(
        @LLMDescription("Коротка причина переводу для оператора") reason: String
    ): String = "[TRANSFER_REQUESTED] Причина: $reason"

    @LLMDescription("Скасувати існуючу заявку користувача. УВАГА: Можна скасувати лише ті заявки, які знаходяться в статусі 'Очікування'.")
    @Tool
    suspend fun cancelTicket(
        @LLMDescription("Поточний userId з системного контексту.") userId: String,
        @LLMDescription("ID заявки (формат REQ-XXXX), яку потрібно скасувати.") ticketId: String
    ): String {
        val ticket = infoService.getTicketById(ticketId)

        if (ticket == null || ticket.userId != userId) {
            return "Помилка: Заявку $ticketId не знайдено, або вона не належить поточному користувачу."
        }

        if (ticket.status.lowercase() != "очікування") {
            return "Відмова: Заявку $ticketId не можна скасувати, оскільки її статус вже '${ticket.status}'. Можливо, майстер вже виїхав. Порадьте користувачу зателефонувати диспетчеру або переведіть на оператора."
        }

        val isCanceled = crmService.cancelTicket(ticketId)

        return if (isCanceled) {
            "Успіх: Заявку $ticketId було успішно скасовано."
        } else {
            "Помилка: Не вдалося скасувати заявку через внутрішню помилку."
        }
    }

    @OptIn(FormatStringsInDatetimeFormats::class)
    @LLMDescription("Запланувати відкладене push-повідомлення (нагадування) для користувача на заданий час.")
    @Tool
    fun scheduleDelayedPush(
        @LLMDescription("Поточний userId з системного контексту.") userId: String,
        @LLMDescription("Текст нагадування, який отримає користувач.") customMessage: String,
        @LLMDescription("Кількість днів для затримки (якщо немає - передай 0).") days: Int = 0,
        @LLMDescription("Кількість годин для затримки (якщо немає - передай 0).") hours: Int = 0,
        @LLMDescription("Кількість хвилин для затримки (якщо немає - передай 0).") minutes: Int = 0
    ): String {
        val totalDuration = days.days + hours.hours + minutes.minutes

        if (!totalDuration.isPositive()) {
            return "Помилка: загальний час затримки має бути більшим за 0. Вкажіть дні, години або хвилини."
        }

        val scheduledInstant = Clock.System.now() + totalDuration
        val localScheduledTime = scheduledInstant.toLocalDateTime(TimeZone.currentSystemDefault())

        val formattedTime = localScheduledTime.format(LocalDateTime.Format { byUnicodePattern("yyyy-MM-dd HH:mm") })

        CoroutineScope(Dispatchers.IO).launch {
            try {
                delay(totalDuration)

                JkWebPushService.sendPushNotification(
                    userId = userId,
                    title = "🔔 Нагадування від AIHome",
                    body = customMessage
                )
            } catch (e: Exception) {
                println("❌ Помилка відправки відкладеного пуша для $userId: ${e.message}")
            }
        }

        return "✅ Нагадування успішно заплановано на $formattedTime. Користувач отримає повідомлення: '$customMessage'."
    }
}