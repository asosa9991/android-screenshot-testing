package com.example.chat

enum class Author { User, Assistant }

/** What tapping a button does. */
sealed interface Action {
    /** Sends [text] as the user's next question. */
    data class Reply(val text: String) : Action

    /** Opens [url] in the browser. */
    data class OpenUrl(val url: String) : Action
}

data class ActionButton(val label: String, val action: Action)

/** One piece of an assistant reply. A reply is a list of blocks, so plain text is just one [Text]. */
sealed interface Block {
    data class Text(val text: String) : Block
    data class Card(val title: String, val body: String, val buttons: List<ActionButton> = emptyList()) : Block
    data class Steps(val title: String, val items: List<String>) : Block

    /** Suggested follow-ups, shown only under the latest assistant message. */
    data class QuickReplies(val options: List<String>) : Block

    /** A block type this app version doesn't know. Rendered as a safe placeholder, never a crash. */
    data class Unsupported(val type: String) : Block
}

data class ChatMessage(val id: String, val author: Author, val blocks: List<Block>) {
    companion object {
        fun user(id: String, text: String) = ChatMessage(id, Author.User, listOf(Block.Text(text)))
    }
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val isWaitingForReply: Boolean = false,
    val error: String? = null,
) {
    val canSend: Boolean get() = draft.isNotBlank() && !isWaitingForReply

    /** Quick replies are offered only while the latest message is the assistant's. */
    val quickReplies: List<String>
        get() = messages.lastOrNull()
            ?.takeIf { it.author == Author.Assistant && !isWaitingForReply }
            ?.blocks?.filterIsInstance<Block.QuickReplies>()?.flatMap { it.options }
            .orEmpty()
}
