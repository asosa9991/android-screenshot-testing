package com.example.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

class ChatViewModel(
    private val service: ChatService,
    private val conversationId: String = UUID.randomUUID().toString(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val _links = Channel<String>(Channel.BUFFERED)
    /** URLs the UI should open, from [Action.OpenUrl] buttons. */
    val links: Flow<String> = _links.receiveAsFlow()

    private var lastQuestion: String? = null

    fun onDraftChange(text: String) = _state.update { it.copy(draft = text) }

    fun send() {
        val question = _state.value.draft.trim()
        if (question.isEmpty()) return
        ask(question, clearDraft = true)
    }

    fun onAction(action: Action) {
        when (action) {
            is Action.Reply -> ask(action.text, clearDraft = false)
            is Action.OpenUrl -> _links.trySend(action.url)
        }
    }

    fun onQuickReply(option: String) = ask(option, clearDraft = false)

    fun retry() {
        val question = lastQuestion ?: return
        if (_state.value.isWaitingForReply) return
        request(question)
    }

    private fun ask(question: String, clearDraft: Boolean) {
        if (_state.value.isWaitingForReply) return
        _state.update {
            it.copy(
                messages = it.messages + ChatMessage.user(newId(), question),
                draft = if (clearDraft) "" else it.draft,
            )
        }
        request(question)
    }

    private fun request(question: String) {
        lastQuestion = question
        _state.update { it.copy(isWaitingForReply = true, error = null) }
        viewModelScope.launch {
            try {
                val blocks = service.ask(conversationId, question)
                _state.update {
                    it.copy(messages = it.messages + ChatMessage(newId(), Author.Assistant, blocks), isWaitingForReply = false)
                }
            } catch (e: IOException) {
                _state.update {
                    it.copy(isWaitingForReply = false, error = e.message ?: "Couldn't reach the assistant.")
                }
            }
        }
    }
}
