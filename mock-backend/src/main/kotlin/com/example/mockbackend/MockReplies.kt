package com.example.mockbackend

import com.example.mockbackend.Json.arr
import com.example.mockbackend.Json.obj
import com.example.mockbackend.Json.str

data class MockReply(val status: Int, val json: String, val delayMillis: Long? = null)

/**
 * Canned answers, matched on keywords in the question. Every block and action type
 * the app supports is reachable from here, plus the failure scenarios:
 *
 *   "password"          text + card with reply / open_url buttons + quick replies
 *   "reset link"        plain text confirmation
 *   "two-factor", "2fa" text + numbered steps
 *   "/slow"             answers after 2.5 s (loading state)
 *   "/error"            HTTP 503 with an error message
 *   "/broken"           HTTP 200 with a body the app can't parse
 *   "/unknown"          a block type the app doesn't know (forward compatibility)
 *   anything else       help text + quick replies
 */
object MockReplies {
    fun replyTo(question: String): MockReply {
        val q = question.lowercase()
        return when {
            q.startsWith("/error") -> MockReply(503, obj("error" to str("The assistant is unavailable. Try again in a moment.")))
            q.startsWith("/broken") -> MockReply(200, "{not json")
            q.startsWith("/slow") -> ok(text("Sorry for the wait. That took 2.5 seconds on purpose.")).copy(delayMillis = 2_500)
            q.startsWith("/unknown") -> ok(text("Here are your recent orders:"), obj("type" to str("order_carousel")))
            "reset link" in q -> ok(text("Done. We sent a reset link to a•••@example.com. It expires in 30 minutes."))
            "password" in q -> ok(
                text("I can send you a reset link right now."),
                obj(
                    "type" to str("card"),
                    "title" to str("Reset your password"),
                    "body" to str("We'll email a reset link to a•••@example.com. It expires after 30 minutes."),
                    "buttons" to arr(
                        button("Send reset link", obj("type" to str("reply"), "text" to str("Send me a reset link"))),
                        button("Help article", obj("type" to str("open_url"), "url" to str("https://example.com/help/reset-password"))),
                    ),
                ),
                quickReplies("That worked", "I didn't get the email"),
            )
            "two-factor" in q || "2fa" in q -> ok(
                text("Yes. Here's how to turn it on:"),
                obj(
                    "type" to str("steps"),
                    "title" to str("Turn on two-factor authentication"),
                    "items" to arr(str("Open Settings → Security"), str("Tap Two-factor authentication"), str("Scan the QR code with your authenticator app")),
                ),
            )
            else -> ok(
                text("I'm the mock backend. Ask about passwords or two-factor authentication, or try /slow, /error, /broken and /unknown."),
                quickReplies("How do I reset my password?", "Is two-factor available?"),
            )
        }
    }

    private var counter = 0
    private fun ok(vararg blocks: String) = MockReply(200, obj("id" to str("mock-${++counter}"), "blocks" to arr(*blocks)))
    private fun text(text: String) = obj("type" to str("text"), "text" to str(text))
    private fun button(label: String, action: String) = obj("label" to str(label), "action" to action)
    private fun quickReplies(vararg options: String) =
        obj("type" to str("quick_replies"), "options" to arr(*options.map(::str).toTypedArray()))
}

/** Just enough JSON for a mock: building objects and reading one string field. */
object Json {
    fun str(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }

    fun obj(vararg fields: Pair<String, String>) = fields.joinToString(",", "{", "}") { (k, v) -> "${str(k)}:$v" }
    fun arr(vararg items: String) = items.joinToString(",", "[", "]")

    /** Reads a top-level string field such as "message", decoding every JSON escape. */
    fun stringField(json: String, name: String): String? {
        val match = Regex(""""${Regex.escape(name)}"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(json) ?: return null
        return unescape(match.groupValues[1])
    }

    // Android's org.json writes "/" as "\/", so all escapes from RFC 8259 must be handled.
    private fun unescape(raw: String): String = buildString {
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c != '\\' || i + 1 >= raw.length) { append(c); i++; continue }
            when (val e = raw[i + 1]) {
                'u' -> { append(raw.substring(i + 2, i + 6).toInt(16).toChar()); i += 6; continue }
                'n' -> append('\n')
                't' -> append('\t')
                'r' -> append('\r')
                'b' -> append('\b')
                'f' -> append('\u000C')
                else -> append(e) // \" \\ \/
            }
            i += 2
        }
    }
}
