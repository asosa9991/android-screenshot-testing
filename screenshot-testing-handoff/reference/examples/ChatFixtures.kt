package com.example.chat

/**
 * Named UI states shared by both lanes. Lives in the debug source set, so Lane A
 * (screenshotTest on debug) and Lane B (androidTest on debug) render identical state.
 */
object ChatFixtures {
    val resetCard = Block.Card(
        title = "Reset your password",
        body = "We'll email a reset link to a•••@example.com. It expires after 30 minutes.",
        buttons = listOf(
            ActionButton("Send reset link", Action.Reply("Send me a reset link")),
            ActionButton("Help article", Action.OpenUrl("https://example.com/help/reset-password")),
        ),
    )

    val empty = ChatUiState()

    /** Text, a card with buttons and quick replies: the rich reply most screens need to show. */
    val loaded = ChatUiState(
        messages = listOf(
            ChatMessage.user("1", "How do I reset my password?"),
            ChatMessage(
                "2", Author.Assistant,
                listOf(
                    Block.Text("I can send you a reset link right now."),
                    resetCard,
                    Block.QuickReplies(listOf("That worked", "I didn't get the email")),
                ),
            ),
        ),
    )

    val steps = ChatUiState(
        messages = listOf(
            ChatMessage.user("1", "Is two-factor authentication available?"),
            ChatMessage(
                "2", Author.Assistant,
                listOf(
                    Block.Text("Yes. Here's how to turn it on:"),
                    Block.Steps(
                        "Turn on two-factor authentication",
                        listOf("Open Settings → Security", "Tap Two-factor authentication", "Scan the QR code with your authenticator app"),
                    ),
                ),
            ),
        ),
    )

    val waiting = steps.copy(
        messages = steps.messages + ChatMessage.user("3", "Can I use SMS instead?"),
        isWaitingForReply = true,
    )

    val error = waiting.copy(isWaitingForReply = false, error = "The assistant is unavailable. Try again in a moment.")

    val unsupported = ChatUiState(
        messages = listOf(
            ChatMessage.user("1", "Show me my recent orders"),
            ChatMessage("2", Author.Assistant, listOf(Block.Unsupported("order_carousel"))),
        ),
    )
}

/** In-memory backend for previews and tests: no network, same answer every time. */
class FakeChatService(
    private val replies: Map<String, List<Block>> = emptyMap(),
    private val failure: Exception? = null,
) : ChatService {
    val questions = mutableListOf<String>()

    override suspend fun ask(conversationId: String, question: String): List<Block> {
        questions += question
        failure?.let { throw it }
        return replies[question] ?: listOf(Block.Text("Answer to: $question"))
    }
}
