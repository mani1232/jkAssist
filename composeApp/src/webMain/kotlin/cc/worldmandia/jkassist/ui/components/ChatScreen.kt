package cc.worldmandia.jkassist.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.worldmandia.jkassist.Role
import cc.worldmandia.jkassist.WsEvent
import cc.worldmandia.jkassist.viewmodels.ChatState
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime

@Composable
fun ChatScreen(
    state: ChatState,
    onSendMessage: (String) -> Unit,
    onReconnect: () -> Unit,
    onToggleWelcome: (Boolean) -> Unit,
    onCancelOperator: () -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val messageList = remember(state.messages) {
        state.messages.values.toList()
    }

    val showScrollToBottom by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    LaunchedEffect(messageList.size) {
        if (messageList.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(state.isHistoryLoaded, messageList.isEmpty()) {
        if (state.isHistoryLoaded) {
            if (messageList.isEmpty()) {
                onToggleWelcome(true)
            } else {
                onToggleWelcome(false)
            }
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                isOperatorMode = state.isOperatorMode,
                isConnecting = state.isConnecting,
                isConnected = state.isConnected,
                isShowingWelcome = state.isShowingWelcome,
                onReconnect = onReconnect,
                onToggleWelcome = onToggleWelcome,
                onCancelOperator = onCancelOperator
            )
        }) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background)
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Crossfade(
                    targetState = state.isShowingWelcome, animationSpec = tween(500), label = "Chat Content Crossfade"
                ) { showWelcome ->
                    if (showWelcome) {
                        WelcomeInfo(onSuggestionClick = { text ->
                            onToggleWelcome(false)
                            onSendMessage(text)
                        })
                    } else {
                        MessageList(
                            messages = messageList,
                            isTyping = state.isTyping,
                            isOperatorMode = state.isOperatorMode,
                            listState = listState
                        )
                    }
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showScrollToBottom && !state.isShowingWelcome,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 16.dp, end = 16.dp),
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            coroutineScope.launch {
                                listState.animateScrollToItem(0)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Прокрутити вниз"
                        )
                    }
                }
            }

            ChatInputBar(
                isConnected = state.isConnected,
                isHistoryLoaded = state.isHistoryLoaded,
                isTyping = state.isTyping,
                onSendMessage = onSendMessage,
                onToggleWelcome
            )
        }
    }
}

@Composable
fun WelcomeInfo(onSuggestionClick: (String) -> Unit) {
    val suggestions = listOf(
        "Коли сьогодні вимикатимуть світло?",
        "Чому немає гарячої води?",
        "Викликати сантехніка: протікає труба",
        "Який статус моєї останньої заявки?",
        "З'єднати з живим оператором",
        "Почати новий чат",
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.Home,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Ласкаво просимо!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = """
            Привіт! Я — ваш розумний AI для вашого дому.
            
            Я тут, щоб миттєво допомогти вам із такими питаннями:
            • Графіки відключення світла та води
            • Оформлення заявок на ремонт (електрик, сантехнік тощо)
            • Отримання посилань на оплату рахунків
            • Перевірка статусу ваших поточних звернень
            • Відповіді на часті запитання (тарифи, послуги ЖЕКу)
            
            Якщо питання заскладне — я одразу покличу людину-оператора. 
            Чим можу допомогти сьогодні?
            """.trimIndent(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2
        ) {
            suggestions.forEach { suggestion ->
                SuggestionChip(
                    text = suggestion, onClick = { onSuggestionClick(suggestion) })
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

@Composable
fun SuggestionChip(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ChatTopBar(
    isOperatorMode: Boolean,
    isConnecting: Boolean,
    isConnected: Boolean,
    isShowingWelcome: Boolean,
    onReconnect: () -> Unit,
    onToggleWelcome: (Boolean) -> Unit,
    onCancelOperator: () -> Unit
) {
    TopAppBar(
        title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(10.dp).clip(CircleShape).background(
                        when {
                            isConnected -> MaterialTheme.colorScheme.primary
                            isConnecting -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = if (isOperatorMode) "Чат із оператором" else "AIHome", fontWeight = FontWeight.Bold
            )

            if (isConnected) {
                IconButton(onClick = { onToggleWelcome(!isShowingWelcome) }) {
                    Icon(
                        imageVector = if (isShowingWelcome) Icons.Outlined.ChatBubbleOutline else Icons.Outlined.Info,
                        contentDescription = "Показати інформацію",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }, actions = {
        when {
            isConnecting -> {
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            !isConnected -> {
                IconButton(onClick = onReconnect) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Перепідключитися",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            isOperatorMode -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCancelOperator) {
                        Text("Скасувати", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(imageVector = Icons.Outlined.Face, contentDescription = "Оператор")
                    }
                }
            }
        }
    }, colors = TopAppBarDefaults.topAppBarColors(
        containerColor = if (isOperatorMode) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.primaryContainer,
        titleContentColor = if (isOperatorMode) MaterialTheme.colorScheme.onTertiaryContainer
        else MaterialTheme.colorScheme.onPrimaryContainer
    )
    )
}

@Composable
fun MessageList(
    modifier: Modifier = Modifier,
    messages: List<WsEvent.Message>,
    isTyping: Boolean,
    isOperatorMode: Boolean,
    listState: LazyListState
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item(key = "typing_indicator") {
            AnimatedVisibility(
                visible = isTyping,
                modifier = Modifier.animateItem(),
                enter = fadeIn() + slideInVertically { it / 2 } + expandVertically(),
                exit = fadeOut() + slideOutVertically { it / 2 } + shrinkVertically()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(start = 8.dp).size(12.dp), strokeWidth = 2.dp
                    )
                    Text(
                        text = if (isOperatorMode) " Оператор друкує..." else " AI аналізує...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 4.dp)
                    )
                }
            }
        }

        items(items = messages.reversed(), key = { it.id }) { msg ->
            val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

            AnimatedVisibility(
                visibleState = visibleState,
                modifier = Modifier.animateItem(),
                enter = fadeIn(animationSpec = tween(300)) + slideInVertically(
                    initialOffsetY = { it / 2 },
                    animationSpec = tween(300)
                ) + expandVertically(animationSpec = tween(300))
            ) {
                Column {
                    ChatBubble(msg)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    isConnected: Boolean,
    isHistoryLoaded: Boolean,
    isTyping: Boolean,
    onSendMessage: (String) -> Unit,
    onToggleWelcome: (Boolean) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var showDevAlert by remember { mutableStateOf(false) }

    val isSendEnabled = isConnected && isHistoryLoaded && !isTyping && inputText.isNotBlank()
    val isMediaEnabled = isConnected && isHistoryLoaded

    if (showDevAlert) {
        AlertDialog(
            onDismissRequest = { showDevAlert = false },
            title = { Text(text = "У розробці", fontWeight = FontWeight.Bold) },
            text = { Text(text = "Функції надсилання медіафайлів та запису голосових повідомлень з’являться в найближчих оновленнях.") },
            confirmButton = {
                TextButton(onClick = { showDevAlert = false }) { Text("Зрозуміло") }
            })
    }

    Surface(
        color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp).navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = { showDevAlert = true }, modifier = Modifier.padding(bottom = 2.dp), enabled = isMediaEnabled
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddCircle,
                    contentDescription = "Додати фото",
                    tint = if (isMediaEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                    if (event.key == Key.Enter && !event.isShiftPressed && event.type == KeyEventType.KeyDown) {
                        if (isSendEnabled) {
                            onToggleWelcome(false)
                            onSendMessage(inputText.trim())
                            inputText = ""
                        }
                        return@onPreviewKeyEvent true
                    }
                    false
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (isSendEnabled) {
                        onToggleWelcome(false)
                        onSendMessage(inputText.trim())
                        inputText = ""
                    }
                }),
                placeholder = {
                    Text(
                        if (!isHistoryLoaded) "Завантаження..."
                        else if (!isConnected) "Підключення..."
                        else "Повідомлення..."
                    )
                },
                enabled = true,
                shape = RoundedCornerShape(20.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )

            Spacer(Modifier.width(8.dp))

            Crossfade(
                targetState = inputText.isNotBlank(),
                label = "Input Action Crossfade",
                modifier = Modifier.padding(bottom = 2.dp)
            ) { hasText ->
                if (hasText) {
                    FilledIconButton(
                        onClick = {
                            onToggleWelcome(false)
                            onSendMessage(inputText)
                            inputText = ""
                        }, enabled = isSendEnabled, modifier = Modifier.size(52.dp), shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Надіслати")
                    }
                } else {
                    FilledIconButton(
                        onClick = { showDevAlert = true },
                        enabled = isMediaEnabled,
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Outlined.Mic, contentDescription = "Записати аудіо")
                    }
                }
            }
        }
    }
}

@OptIn(FormatStringsInDatetimeFormats::class)
@Composable
fun ChatBubble(msg: WsEvent.Message) {
    val isUser = msg.role == Role.USER

    val isSysNotice = msg.role == Role.SYSTEM && msg.text.startsWith("*")
    val isOperator = msg.role == Role.SYSTEM && !msg.text.startsWith("*")

    val senderName = when {
        isUser -> "Ви"
        isSysNotice -> "Система"
        isOperator -> "Оператор"
        else -> "AI"
    }

    Column(
        modifier = Modifier.fillMaxWidth(), horizontalAlignment = when {
            isUser -> Alignment.End
            isSysNotice -> Alignment.CenterHorizontally
            else -> Alignment.Start
        }
    ) {
        if (!isSysNotice) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isUser) {
                    Text(
                        text = senderName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isOperator) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                val formattedTime = msg.timestampMs.toLocalDateTime(TimeZone.currentSystemDefault())
                    .format(LocalDateTime.Format { byUnicodePattern("dd.MM HH:mm") })

                Text(
                    text = formattedTime,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                if (isUser) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = senderName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        SelectionContainer {
            Surface(
                color = when {
                    isSysNotice -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    isUser -> MaterialTheme.colorScheme.primaryContainer
                    isOperator -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }, shape = RoundedCornerShape(16.dp).copy(
                    bottomEnd = CornerSize(if (isUser && !isSysNotice) 4.dp else 16.dp),
                    bottomStart = CornerSize(if (!isUser && !isSysNotice) 4.dp else 16.dp)
                ), tonalElevation = if (isSysNotice) 0.dp else 1.dp
            ) {
                Markdown(
                    content = msg.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    colors = markdownColor(
                        text = when {
                            isSysNotice -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            isUser -> MaterialTheme.colorScheme.onPrimaryContainer
                            isOperator -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                )
            }
        }
    }
}