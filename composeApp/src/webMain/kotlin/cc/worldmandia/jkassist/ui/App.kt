package cc.worldmandia.jkassist.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.worldmandia.jkassist.WebSessionStorage
import cc.worldmandia.jkassist.network.ChatNetworkClient
import cc.worldmandia.jkassist.ui.components.ChatScreen
import cc.worldmandia.jkassist.viewmodels.ChatViewModel
import kotlinx.browser.window
import org.w3c.dom.events.Event
import org.w3c.dom.url.URLSearchParams

@OptIn(ExperimentalWasmJsInterop::class)
@Composable
fun App() = MaterialExpressiveTheme(if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {

    val scope = rememberCoroutineScope()
    val viewModel = remember { ChatViewModel(scope, ChatNetworkClient(WebSessionStorage())) }
    val state by viewModel.state.collectAsState()

    var showUpdateDialog by remember { mutableStateOf(false) }
    var showInstallButton by remember { mutableStateOf(false) }
    var isOnline by remember { mutableStateOf(window.navigator.onLine) }

    var shortcutExecuted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        window.addEventListener("app-update-available") { showUpdateDialog = true }

        window.addEventListener("app-install-available") { showInstallButton = true }
        window.addEventListener("app-installed-success") { showInstallButton = false }

        window.addEventListener("offline") { isOnline = false }
        window.addEventListener("online") {
            isOnline = true
            viewModel.connect()
        }
    }

    if (state.isConnected && state.isHistoryLoaded && !shortcutExecuted) {
        LaunchedEffect(Unit) {
            shortcutExecuted = true

            val urlParams = URLSearchParams(window.location.search.toJsString())
            val action = urlParams.get("action")

            if (action != null) {
                viewModel.setShowWelcome(false)

                when (action) {
                    "new_ticket" -> viewModel.sendMessage("Викликати сантехніка: протікає труба")
                    "outages" -> viewModel.sendMessage("Коли сьогодні вимикатимуть світло?")
                }

                window.history.replaceState(null, "", window.location.pathname)
            }
        }
    }

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text(text = "Доступне оновлення", fontWeight = FontWeight.Bold) },
            text = { Text("Вийшла нова версія AIHome. Оновіть сторінку, щоб отримати нові функції та виправлення.") },
            confirmButton = {
                Button(onClick = {
                    showUpdateDialog = false
                    window.dispatchEvent(Event("apply-app-update"))
                }) { Text("Оновити зараз") }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) { Text("Пізніше") }
            })
    }

    Column {
        AnimatedVisibility(
            visible = !isOnline, enter = expandVertically(), exit = shrinkVertically()
        ) {
            Text(
                text = "Відсутнє підключення до інтернету",
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.error).padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.onError,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        ChatScreen(
            state = state,
            onSendMessage = viewModel::sendMessage,
            onReconnect = viewModel::connect,
            onToggleWelcome = viewModel::setShowWelcome,
            onCancelOperator = { viewModel.sendMessage("/cancel_operator") },
            showInstallButton = showInstallButton,
            onInstallClick = { window.dispatchEvent(Event("prompt-app-install")) })
    }
}