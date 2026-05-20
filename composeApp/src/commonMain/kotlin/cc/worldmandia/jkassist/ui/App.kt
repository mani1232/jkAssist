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
import cc.worldmandia.jkassist.DataType
import cc.worldmandia.jkassist.Role
import cc.worldmandia.jkassist.network.ChatNetworkClient
import cc.worldmandia.jkassist.push.subscribeToPush
import cc.worldmandia.jkassist.storage.SessionStorage
import cc.worldmandia.jkassist.ui.components.ChatScreen
import cc.worldmandia.jkassist.viewmodels.ChatViewModel
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.launch
import org.w3c.dom.CustomEvent
import org.w3c.dom.events.Event
import org.w3c.dom.url.URLSearchParams
import web.badging.clearAppBadge
import web.badging.setAppBadge
import web.dom.DocumentVisibilityState
import web.dom.hidden
import web.dom.visible
import web.navigator.navigator
import web.notifications.Notification
import web.notifications.NotificationPermission
import web.notifications.default
import web.notifications.granted
import web.notifications.requestPermission
import kotlin.js.unsafeCast

@OptIn(ExperimentalWasmJsInterop::class)
@Composable
fun App() = MaterialExpressiveTheme(if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {

    val scope = rememberCoroutineScope()
    val viewModel = remember { ChatViewModel(scope, ChatNetworkClient(SessionStorage())) }
    val state by viewModel.state.collectAsState()

    var showUpdateDialog by remember { mutableStateOf(false) }
    var showInstallButton by remember { mutableStateOf(false) }
    var isOnline by remember { mutableStateOf(window.navigator.onLine) }

    var shortcutExecuted by remember { mutableStateOf(false) }

    var unreadCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        document.addEventListener("visibilitychange") {
            if (web.dom.document.visibilityState == DocumentVisibilityState.visible) {
                unreadCount = 0
                scope.launch {
                    try {
                        navigator.clearAppBadge()
                    } catch (e: Exception) {
                        println("App Badging (clear) error: ${e.message}")
                    }
                }
            }
        }

        window.addEventListener("app-update-available") { showUpdateDialog = true }

        window.addEventListener("app-install-available") { showInstallButton = true }
        window.addEventListener("app-installed-success") { showInstallButton = false }

        window.addEventListener("offline") { isOnline = false }
        window.addEventListener("online") {
            isOnline = true
            viewModel.connect()
        }

        window.addEventListener("push-subscription") { event ->
            val customEvent = event.unsafeCast<CustomEvent>()
            val subscriptionJson = customEvent.detail.toString()

            scope.launch {
                viewModel.networkClient.sendPushToken(subscriptionJson)
            }
        }
    }

    LaunchedEffect(state.messages.size) {
        if (web.dom.document.visibilityState == DocumentVisibilityState.hidden && state.messages.isNotEmpty()) {
            val lastMessage = state.messages.values.last()

            if (lastMessage.role != Role.USER) {
                unreadCount++

                scope.launch {
                    try {
                        navigator.setAppBadge(unreadCount.toDouble())
                    } catch (e: Exception) {
                        println("App Badging (set) error: ${e.message}")
                    }
                }
            }
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

    LaunchedEffect(state.isConnected) {
        if (state.isConnected) {
            scope.launch {
                try {
                    if (Notification.permission == NotificationPermission.default) {
                        val permission = Notification.requestPermission()
                        if (permission == NotificationPermission.granted) {
                            subscribeToPush("BGPrMtUDx7_ZC7nYCyInlei_0gDhutY3dsjTCbeWs8by9SuDnvgQgk3Ry1PLB-71g_VyRzg-lkuLdtKmiYFg3S0")
                        }
                    } else if (Notification.permission == NotificationPermission.granted) {
                        subscribeToPush("BGPrMtUDx7_ZC7nYCyInlei_0gDhutY3dsjTCbeWs8by9SuDnvgQgk3Ry1PLB-71g_VyRzg-lkuLdtKmiYFg3S0")
                    }
                } catch (e: Exception) {
                    println("Push error: ${e.message}")
                }
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
            onSendMessage = { text, audio -> viewModel.sendMessage(text, audio?.let { DataType.AUDIO to audio }) },
            onReconnect = viewModel::connect,
            onToggleWelcome = viewModel::setShowWelcome,
            onCancelOperator = { viewModel.sendMessage("/cancel_operator") },
            showInstallButton = showInstallButton,
            onInstallClick = { window.dispatchEvent(Event("prompt-app-install")) })
    }
}