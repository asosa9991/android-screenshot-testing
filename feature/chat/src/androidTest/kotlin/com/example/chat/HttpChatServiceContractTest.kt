package com.example.chat

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

/**
 * Contract test against the mock backend on the host (`./run backend`).
 * Skipped, not failed, when the backend isn't running. Never part of the screenshot lane.
 */
class HttpChatServiceContractTest {
    private val baseUrl = "http://10.0.2.2:8080"
    private val service = HttpChatService(baseUrl, timeoutMillis = 5_000)

    @Before fun backendIsUp() {
        val up = runCatching {
            (URL("$baseUrl/health").openConnection() as HttpURLConnection).run {
                connectTimeout = 1_000
                responseCode == 200
            }
        }.getOrDefault(false)
        assumeTrue("Mock backend not running on the host", up)
    }

    @Test fun password_question_returns_card_with_both_action_types() = runBlocking {
        val blocks = service.ask("contract", "How do I reset my password?")
        val card = blocks.filterIsInstance<Block.Card>().single()
        assertTrue(card.buttons.any { it.action is Action.Reply })
        assertTrue(card.buttons.any { it.action is Action.OpenUrl })
        assertTrue(blocks.any { it is Block.QuickReplies })
    }

    @Test fun unknown_block_types_degrade() = runBlocking {
        assertTrue(service.ask("contract", "/unknown").any { it is Block.Unsupported })
    }

    @Test fun backend_errors_surface_the_message() = runBlocking {
        val error = runCatching { service.ask("contract", "/error") }.exceptionOrNull()
        assertEquals("The assistant is unavailable. Try again in a moment.", error?.message)
    }
}
