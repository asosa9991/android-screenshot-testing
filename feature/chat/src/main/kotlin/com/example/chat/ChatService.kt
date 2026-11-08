package com.example.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The backend contract: send a question, get the assistant's reply as content blocks. */
interface ChatService {
    suspend fun ask(conversationId: String, question: String): List<Block>
}

class ChatServiceException(message: String) : IOException(message)

/**
 * Calls `POST {baseUrl}/v1/chat` with `{"conversationId", "message"}` and reads `{"blocks": [...]}`.
 * In debug builds the app points this at the mock backend (`./run backend`).
 */
class HttpChatService(
    private val baseUrl: String,
    private val timeoutMillis: Int = 15_000,
) : ChatService {
    override suspend fun ask(conversationId: String, question: String): List<Block> = withContext(Dispatchers.IO) {
        val connection = (URL("$baseUrl/v1/chat").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            val body = JSONObject().put("conversationId", conversationId).put("message", question)
            connection.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(text).optString("error") }.getOrNull()
                throw ChatServiceException(message?.ifBlank { null } ?: "The assistant is unavailable (HTTP $code).")
            }
            ReplyParser.parse(text)
        } finally {
            connection.disconnect()
        }
    }
}

/** Parses the backend's reply. Unknown block and action types degrade instead of failing. */
object ReplyParser {
    fun parse(json: String): List<Block> {
        val root = try {
            JSONObject(json)
        } catch (e: org.json.JSONException) {
            throw ChatServiceException("The assistant sent a reply this app can't read.")
        }
        // Older backends send {"reply": "..."}; treat it as one text block.
        root.optString("reply").takeIf { it.isNotEmpty() }?.let { return listOf(Block.Text(it)) }
        val blocks = root.optJSONArray("blocks") ?: throw ChatServiceException("The assistant sent an empty reply.")
        return blocks.objects().map(::block)
    }

    private fun block(o: JSONObject): Block = when (val type = o.optString("type")) {
        "text" -> Block.Text(o.getString("text"))
        "card" -> Block.Card(
            title = o.getString("title"),
            body = o.optString("body"),
            buttons = o.optJSONArray("buttons")?.objects()?.mapNotNull(::button).orEmpty(),
        )
        "steps" -> Block.Steps(o.optString("title"), o.getJSONArray("items").strings())
        "quick_replies" -> Block.QuickReplies(o.getJSONArray("options").strings())
        else -> Block.Unsupported(type)
    }

    private fun button(o: JSONObject): ActionButton? {
        val action = o.optJSONObject("action") ?: return null
        return when (action.optString("type")) {
            "reply" -> ActionButton(o.getString("label"), Action.Reply(action.getString("text")))
            "open_url" -> ActionButton(o.getString("label"), Action.OpenUrl(action.getString("url")))
            else -> null // Unknown action: hide the button rather than show one that does nothing.
        }
    }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}
