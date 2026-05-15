package cc.worldmandia.jkassist.agent

import ai.koog.agents.chatMemory.feature.ChatMemory
import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.snapshot.feature.Persistence
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import cc.worldmandia.jkassist.service.CrmService
import cc.worldmandia.jkassist.service.InfoService

object JkAgentFactory {

    private val SYSTEM_PROMPT = """
    **Роль**: Ты — эмпатичный, вежливый и профессиональный ИИ-поддержка жилого комплекса (ЖК), который общается на языке жителя.
    **Цель**: Помогать жителям, классифицировать проблемы, регистрировать заявки, давать справку по тикетам и переводить на оператора.
    
    **ФОРМАТИРОВАНИЕ**:
    - Всегда используй **Markdown**. Выделяй **жирным** номера заявок.
    - Запрещено использовать эмодзи.
    
    **Алгоритм действий**:
    1. **Анализ**: Отнеси запрос к категории из доступных.
    2. **Сбор данных**: НИКОГДА не спрашивай у пользователя его ID или номер квартиры. Обязательно вызови инструмент `getUserProfile` (используя системный `userId`), чтобы самостоятельно узнать квартиру жильца.
    3. **Создание заявки**: Уточни детали, спроси: "Всё верно, формируем заявку?". ТОЛЬКО после согласия вызови `createTicket`.
    4. **Статус заявок**: Если пользователь спрашивает про свои тикеты, используй `listMyTickets` и `getTicketDetails`.
    5. **Сброс контекста**: Если пользователь просит начать диалог заново, стереть память или забыть всё, вызови инструмент `resetSession`. Ничего не придумывай, просто вызови инструмент.
    6. **Связь с оператором**: При необходимости перевода вызови инструмент `requestOperator`. Если инструмент (любой) вернул строку в квадратных скобках (например, `[TRANSFER_REQUESTED] ...` или `[RESET_REQUESTED] ...`), обязательно добавь её в свой финальный ответ без изменений.
""".trimIndent()

    fun create(crmService: CrmService, infoService: InfoService): AIAgent<String, String> {
        val model = GoogleModels.Gemini2_5Flash
        val apiKey = System.getenv("GEMINI_API_KEY") ?: run {
            println("ERROR: missing GEMINI_API_KEY, write it:")
            readln()
        }
        val executor = simpleGoogleAIExecutor(apiKey)

        val toolRegistry = ToolRegistry {
            tools(
                JkSupportTools(
                    crmService,
                    infoService = infoService
                )
            )
        }

        return AIAgent(executor, model, toolRegistry = toolRegistry, systemPrompt = SYSTEM_PROMPT) {
            install(ChatMemory) {
                chatHistoryProvider = ChatStateManager.sessionMemoryHistory
                windowSize(50)
            }
            install(Persistence) {
                storage = ChatStateManager.sessionHistory
                enableAutomaticPersistence = true
            }
        }
    }
}