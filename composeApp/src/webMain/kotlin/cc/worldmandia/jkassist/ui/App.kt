package cc.worldmandia.jkassist.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import cc.worldmandia.jkassist.WebSessionStorage
import cc.worldmandia.jkassist.network.ChatNetworkClient
import cc.worldmandia.jkassist.ui.components.ChatScreen
import cc.worldmandia.jkassist.viewmodels.ChatViewModel

@Composable
fun App() = MaterialExpressiveTheme(if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {

    val scope = rememberCoroutineScope()
    val viewModel = remember { ChatViewModel(scope, ChatNetworkClient(WebSessionStorage())) }
    val state by viewModel.state.collectAsState()

    ChatScreen(
        state = state, onSendMessage = viewModel::sendMessage, onReconnect = viewModel::connect,
        onToggleWelcome = viewModel::setShowWelcome, onCancelOperator = { viewModel.sendMessage("/cancel_operator") }
    )
}