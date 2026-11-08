package com.example.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyParserTest {
    @Test fun `parses every block type and both action types`() {
        val blocks = ReplyParser.parse(
            """
            {"blocks": [
              {"type": "text", "text": "Hi"},
              {"type": "card", "title": "T", "body": "B", "buttons": [
                {"label": "Go", "action": {"type": "reply", "text": "Go on"}},
                {"label": "Read", "action": {"type": "open_url", "url": "https://example.com"}}
              ]},
              {"type": "steps", "title": "S", "items": ["one", "two"]},
              {"type": "quick_replies", "options": ["Yes", "No"]}
            ]}
            """,
        )
        assertEquals(
            listOf(
                Block.Text("Hi"),
                Block.Card("T", "B", listOf(ActionButton("Go", Action.Reply("Go on")), ActionButton("Read", Action.OpenUrl("https://example.com")))),
                Block.Steps("S", listOf("one", "two")),
                Block.QuickReplies(listOf("Yes", "No")),
            ),
            blocks,
        )
    }

    @Test fun `unknown blocks degrade and unknown actions are dropped`() {
        val blocks = ReplyParser.parse(
            """{"blocks": [{"type": "order_carousel"}, {"type": "card", "title": "T", "buttons": [{"label": "Pay", "action": {"type": "pay"}}]}]}""",
        )
        assertEquals(listOf(Block.Unsupported("order_carousel"), Block.Card("T", "", emptyList())), blocks)
    }

    @Test fun `legacy plain-text replies still work`() {
        assertEquals(listOf(Block.Text("Hello")), ReplyParser.parse("""{"reply": "Hello"}"""))
    }
}
