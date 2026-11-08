package com.example.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.example.testing.ShotHostActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Behavior, not pixels: the buttons and chips in a rich reply do what they say. */
class ChatInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ShotHostActivity>()

    private val service = FakeChatService(
        mapOf(
            "How do I reset my password?" to ChatFixtures.loaded.messages[1].blocks,
            "Send me a reset link" to listOf(Block.Text("Done. Check your email.")),
        ),
    )

    private fun launch() {
        val vm = ChatViewModel(service, "conv-test")
        compose.setContent { AppTheme { ChatRoute(vm) } }
    }

    @Test fun asking_a_question_shows_the_rich_reply() {
        launch()
        compose.onNodeWithTag("composer").performTextInput("How do I reset my password?")
        compose.onNodeWithText("Send").performClick()

        compose.onNodeWithText("Reset your password").assertIsDisplayed()
        compose.onNodeWithText("That worked").assertIsDisplayed()
    }

    @Test fun a_card_button_sends_its_reply() {
        launch()
        compose.onNodeWithTag("composer").performTextInput("How do I reset my password?")
        compose.onNodeWithText("Send").performClick()
        compose.onNodeWithText("Send reset link").performClick()

        assertEquals(listOf("How do I reset my password?", "Send me a reset link"), service.questions)
        compose.onNodeWithTag("conversation").performScrollToNode(hasText("Done. Check your email."))
        compose.onNodeWithText("Done. Check your email.").assertIsDisplayed()
    }

    @Test fun a_quick_reply_is_sent_and_then_hidden() {
        launch()
        compose.onNodeWithTag("composer").performTextInput("How do I reset my password?")
        compose.onNodeWithText("Send").performClick()
        compose.onNodeWithText("That worked").performClick()

        assertEquals("That worked", service.questions.last())
        compose.onNodeWithText("I didn't get the email").assertDoesNotExist()
    }
}
