package cc.worldmandia.jkassist.service

import cc.worldmandia.jkassist.agent.ChatStateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object OperatorConsole {
    fun start() {
        CoroutineScope(Dispatchers.IO).launch {
            println("\n=======================================================")
            println("👨‍💻 Глобальна консоль оператора запущена.")
            println("👨‍💻 Доступні команди:")
            println("👨‍💻   list                      - Показати сесії, що очікують")
            println("👨‍💻   send <session_id> <текст> - Відповісти в чат")
            println("👨‍💻   end <session_id>          - Завершити діалог")
            println("=======================================================\n")

            while (true) {
                val input = readlnOrNull()?.trim() ?: continue
                if (input.isEmpty()) continue

                val parts = input.split(" ", limit = 3)
                when (parts[0].lowercase()) {
                    "list" -> {
                        val waiting = ChatStateManager.getWaitingSessions()
                        if (waiting.isEmpty()) {
                            println("👨‍💻 Немає активних запитів.")
                        } else {
                            println("👨‍💻 Очікують відповіді:")
                            waiting.forEach { (id, time) ->
                                println(" - $id (з $time)")
                            }
                        }
                    }

                    "send" -> {
                        if (parts.size < 3) {
                            println("👨‍💻 Помилка. Використання: send <session_id> <текст>")
                        } else {
                            val id = parts[1]
                            val text = parts[2]
                            ChatStateManager.sendOperatorMessage(id, text)
                            println("👨‍💻 [✓] Надіслано в $id")
                        }
                    }

                    "end" -> {
                        if (parts.size < 2) {
                            println("👨‍💻 Помилка. Використання: end <session_id>")
                        } else {
                            val id = parts[1]
                            ChatStateManager.sendOperatorMessage(id, "end")
                            println("👨‍💻 [✓] Сесія $id переведена назад на бота.")
                        }
                    }

                    else -> println("👨‍💻 Невідома команда. Доступні: list, send, end")
                }
            }
        }
    }
}