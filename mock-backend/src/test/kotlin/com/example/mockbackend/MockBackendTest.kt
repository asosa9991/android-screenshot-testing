package com.example.mockbackend

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class MockBackendTest {
    companion object {
        private val server = MockBackend.start(0)
        private val base = "http://localhost:${server.address.port}"

        @BeforeClass @JvmStatic fun up() = Unit
        @AfterClass @JvmStatic fun down() = server.stop(0)
    }

    private fun post(message: String): Pair<Int, String> {
        val c = URL("$base/v1/chat").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.outputStream.use { it.write("""{"conversationId":"t","message":${Json.str(message)}}""".toByteArray()) }
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream).bufferedReader().readText()
        return code to body
    }

    @Test fun `password question returns a card with both action types`() {
        val (code, body) = post("How do I reset my \"password\"?")
        assertEquals(200, code)
        assertTrue(body.contains("\"type\":\"card\""))
        assertTrue(body.contains("\"type\":\"reply\""))
        assertTrue(body.contains("\"type\":\"open_url\""))
        assertTrue(body.contains("\"type\":\"quick_replies\""))
    }

    @Test fun `error scenario returns 503 with a message`() {
        val (code, body) = post("/error")
        assertEquals(503, code)
        assertTrue(body.contains("\"error\""))
    }

    @Test fun `escaped slashes from Android's org-json still select a scenario`() {
        // Regression: the Android client sends {"message":"\/error"}.
        val c = URL("$base/v1/chat").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.outputStream.use { it.write("""{"conversationId":"t","message":"\/error"}""".toByteArray()) }
        assertEquals(503, c.responseCode)
    }

    @Test fun `unicode and quote escapes are decoded`() {
        assertEquals("caf\u00e9 \"x\" a/b", Json.stringField("""{"message":"caf\u00e9 \"x\" a\/b"}""", "message"))
    }

    @Test fun `missing message is a 400`() {
        val c = URL("$base/v1/chat").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.outputStream.use { it.write("{}".toByteArray()) }
        assertEquals(400, c.responseCode)
    }
}
