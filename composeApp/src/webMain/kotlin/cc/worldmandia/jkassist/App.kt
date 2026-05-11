package cc.worldmandia.jkassist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.model.rememberMarkdownState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val viewModel = remember { ChatViewModel(scope) }
    val state by viewModel.state.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.isTyping) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ИИ Диспетчер ЖК") },
                actions = {
                    if (state.isConnecting) CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 16.dp))
                    else if (!state.isConnected) {
                        IconButton(onClick = { viewModel.connect() }) { Icon(Icons.Default.Warning, contentDescription = "Переподключиться", tint = MaterialTheme.colorScheme.error) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {

            // Сообщения
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), state = listState) {
                items(state.messages.values.toList()) { msg -> ChatBubble(msg) }
                if (state.isTyping) {
                    item { Text("Диспетчер печатает...", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp)) }
                }
            }

            // Поле ввода
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (state.isConnected) "Напишите о проблеме..." else "Подключение...") },
                    enabled = state.isConnected && !state.isTyping,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = { viewModel.sendMessage(inputText); inputText = "" },
                    enabled = inputText.isNotBlank() && state.isConnected && !state.isTyping,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
                }
            }
        }
    }
}

@Composable
fun ChatBubble(msg: WsEvent.Message) {
    val isUser = msg.role == Role.USER
    val markdownState = rememberMarkdownState(
        msg.timestamp,
        retainState = true
    ) {
        msg.text
    }
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(16.dp).copy(bottomEnd = CornerSize(if (isUser) 0.dp else 16.dp), bottomStart = CornerSize(if (isUser) 16.dp else 0.dp))
        ) {
            Markdown(markdownState, modifier = Modifier.padding(12.dp), colors = markdownColor(text = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}