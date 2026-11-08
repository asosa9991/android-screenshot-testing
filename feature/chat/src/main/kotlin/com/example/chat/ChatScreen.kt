package com.example.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Static brand colors. No dynamic color: it follows the wallpaper and would break goldens. */
@Composable
fun AppTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFF8FD3FF), onPrimary = Color(0xFF00344F),
            primaryContainer = Color(0xFF0B4A6E), onPrimaryContainer = Color(0xFFD6EEFF),
            secondaryContainer = Color(0xFF263845), onSecondaryContainer = Color(0xFFD3E5F3),
            background = Color(0xFF0E1419), surface = Color(0xFF0E1419),
            surfaceVariant = Color(0xFF1E2830), onSurfaceVariant = Color(0xFFC3CDD5),
            outline = Color(0xFF5A6873), outlineVariant = Color(0xFF34414B),
            errorContainer = Color(0xFF5C1A1A), onErrorContainer = Color(0xFFFFDAD6),
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0B5F8A), onPrimary = Color.White,
            primaryContainer = Color(0xFF0B5F8A), onPrimaryContainer = Color.White,
            secondaryContainer = Color(0xFFD3E5F3), onSecondaryContainer = Color(0xFF0E2A3B),
            background = Color(0xFFF6F8FA), surface = Color(0xFFF6F8FA),
            surfaceVariant = Color(0xFFE3E9EE), onSurfaceVariant = Color(0xFF39444D),
            outline = Color(0xFF8795A0), outlineVariant = Color(0xFFC9D3DA),
            errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Wires the stateless screen to a [ChatViewModel]. */
@Composable
fun ChatRoute(viewModel: ChatViewModel) {
    val state by viewModel.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(viewModel) { viewModel.links.collect { uriHandler.openUri(it) } }
    ChatScreen(
        state,
        onDraftChange = viewModel::onDraftChange,
        onSend = viewModel::send,
        onAction = viewModel::onAction,
        onQuickReply = viewModel::onQuickReply,
        onRetry = viewModel::retry,
    )
}

/**
 * Header drawn behind the status bar, conversation in the middle, composer kept
 * above the navigation bar and the keyboard. These insets are why Lane B exists.
 */
@Composable
fun ChatScreen(
    state: ChatUiState,
    modifier: Modifier = Modifier,
    onDraftChange: (String) -> Unit = {},
    onSend: () -> Unit = {},
    onAction: (Action) -> Unit = {},
    onQuickReply: (String) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Header()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.messages.isEmpty() && !state.isWaitingForReply) {
                    EmptyState(Modifier.align(Alignment.Center))
                } else {
                    Conversation(state, onAction)
                }
            }
            if (state.quickReplies.isNotEmpty()) QuickReplyRow(state.quickReplies, onQuickReply)
            state.error?.let { ErrorBanner(it, onRetry) }
            Composer(state, onDraftChange, onSend)
        }
    }
}

@Composable
private fun Header() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("Ask Support", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text("Answers from the help center", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Ask a question to get started", style = MaterialTheme.typography.titleMedium)
        Text("For example: \"How do I reset my password?\"", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Conversation(state: ChatUiState, onAction: (Action) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("conversation"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(state.messages, key = { it.id }) { message ->
            if (message.author == Author.User) {
                UserBubble(message.blocks.filterIsInstance<Block.Text>().joinToString("\n") { it.text })
            } else {
                AssistantMessage(message, onAction)
            }
        }
        if (state.isWaitingForReply) {
            item(key = "typing") { AssistantBubble("Thinking…") }
        }
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun AssistantMessage(message: ChatMessage, onAction: (Action) -> Unit) {
    Column(Modifier.widthIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        message.blocks.forEach { block ->
            when (block) {
                is Block.Text -> AssistantBubble(block.text)
                is Block.Card -> CardBlock(block, onAction)
                is Block.Steps -> StepsBlock(block)
                is Block.QuickReplies -> Unit // shown above the composer, see QuickReplyRow
                is Block.Unsupported -> AssistantBubble("This message needs a newer version of the app.")
            }
        }
    }
}

@Composable
private fun AssistantBubble(text: String) {
    Surface(
        shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CardBlock(card: Block.Card, onAction: (Action) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(card.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (card.body.isNotEmpty()) {
                Text(card.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (card.buttons.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    card.buttons.forEachIndexed { index, button ->
                        if (index == 0) {
                            FilledTonalButton(onClick = { onAction(button.action) }) { Text(button.label) }
                        } else {
                            OutlinedButton(onClick = { onAction(button.action) }) { Text(button.label) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepsBlock(steps: Block.Steps) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (steps.title.isNotEmpty()) {
                Text(steps.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            steps.items.forEachIndexed { index, item ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${index + 1}.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Text(item, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun QuickReplyRow(options: List<String>, onQuickReply: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            SuggestionChip(onClick = { onQuickReply(option) }, label = { Text(option) })
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun Composer(state: ChatUiState, onDraftChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f).testTag("composer"),
            placeholder = { Text("Ask a question") },
            maxLines = 4,
        )
        Button(onClick = onSend, enabled = state.canSend) { Text("Send") }
    }
}
