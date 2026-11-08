package com.example.mockbackend

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * Mock of the chat backend. No dependencies; JDK HTTP server only.
 *
 *   POST /v1/chat   {"conversationId": "...", "message": "..."}  ->  {"id": "...", "blocks": [...]}
 *   GET  /health    -> ok
 *
 * Scenarios are picked from the question text (see [MockReplies]); `X-Mock-Delay-Ms` adds latency.
 * The Android emulator reaches this server at http://10.0.2.2:8080.
 */
fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toIntOrNull() ?: System.getenv("PORT")?.toIntOrNull() ?: 8080
    val server = MockBackend.start(port)
    println("Mock chat backend on http://localhost:${server.address.port} (emulator: http://10.0.2.2:${server.address.port})")
    println("Try: curl -s localhost:${server.address.port}/v1/chat -d '{\"message\":\"How do I reset my password?\"}'")
}

object MockBackend {
    fun start(port: Int): HttpServer {
        val server = HttpServer.create(InetSocketAddress(port), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/health") { it.respond(200, "ok", "text/plain") }
        server.createContext("/v1/chat", ::handleChat)
        server.start()
        return server
    }

    private fun handleChat(exchange: HttpExchange) {
        if (exchange.requestMethod != "POST") {
            return exchange.respond(405, Json.obj("error" to Json.str("Use POST")))
        }
        val body = exchange.requestBody.bufferedReader().use { it.readText() }
        val question = Json.stringField(body, "message")?.trim()
        if (question.isNullOrEmpty()) {
            return exchange.respond(400, Json.obj("error" to Json.str("Missing \"message\"")))
        }
        exchange.requestHeaders.getFirst("X-Mock-Delay-Ms")?.toLongOrNull()?.let { Thread.sleep(it.coerceAtMost(30_000)) }

        val reply = MockReplies.replyTo(question)
        reply.delayMillis?.let(Thread::sleep)
        println("[${exchange.requestMethod} /v1/chat] \"$question\" -> ${reply.status}")
        exchange.respond(reply.status, reply.json)
    }

    private fun HttpExchange.respond(status: Int, body: String, type: String = "application/json") {
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", "$type; charset=utf-8")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
