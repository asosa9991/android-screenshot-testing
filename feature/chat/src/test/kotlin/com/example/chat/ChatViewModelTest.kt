package com.example.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private var ids = 0
    private fun viewModel(service: ChatService) = ChatViewModel(service, "conv-1", newId = { "${++ids}" })

    @Test fun `sending a question shows it and the backend's rich reply`() {
        val reply = listOf(Block.Text("Sure."), ChatFixtures.resetCard, Block.QuickReplies(listOf("That worked")))
        val service = FakeChatService(mapOf("Reset my password" to reply))
        val vm = viewModel(service)

        vm.onDraftChange("  Reset my password ")
        vm.send()

        assertEquals(listOf("Reset my password"), service.questions)
        assertEquals(
            listOf(ChatMessage.user("1", "Reset my password"), ChatMessage("2", Author.Assistant, reply)),
            vm.state.value.messages,
        )
        assertEquals("", vm.state.value.draft)
        assertEquals(listOf("That worked"), vm.state.value.quickReplies)
    }

    @Test fun `a reply button sends its text as the next question`() {
        val service = FakeChatService()
        val vm = viewModel(service)

        vm.onAction(Action.Reply("Send me a reset link"))

        assertEquals(listOf("Send me a reset link"), service.questions)
        assertEquals("Send me a reset link", (vm.state.value.messages.first().blocks.single() as Block.Text).text)
    }

    @Test fun `a link button asks the UI to open the url`() = runTest {
        val vm = viewModel(FakeChatService())
        vm.onAction(Action.OpenUrl("https://example.com/help"))
        assertEquals("https://example.com/help", vm.links.first())
    }

    @Test fun `quick replies disappear once the user answers`() {
        val service = FakeChatService(mapOf("Hi" to listOf(Block.Text("Hello"), Block.QuickReplies(listOf("Thanks")))))
        val vm = viewModel(service)
        vm.onQuickReply("Hi")
        assertEquals(listOf("Thanks"), vm.state.value.quickReplies)
        vm.onQuickReply("Thanks")
        assertEquals(emptyList<String>(), vm.state.value.quickReplies)
    }

    @Test fun `a backend failure shows an error and retry asks again`() {
        val failing = FakeChatService(failure = ChatServiceException("The assistant is unavailable."))
        val vm = viewModel(failing)

        vm.onDraftChange("Hi")
        vm.send()
        assertEquals("The assistant is unavailable.", vm.state.value.error)
        assertFalse(vm.state.value.isWaitingForReply)

        vm.retry()
        assertEquals(listOf("Hi", "Hi"), failing.questions)
    }

    @Test fun `blank drafts are not sent`() {
        val service = FakeChatService()
        val vm = viewModel(service)
        vm.onDraftChange("   ")
        vm.send()
        assertEquals(emptyList<String>(), service.questions)
        assertNull(vm.state.value.error)
    }
}
