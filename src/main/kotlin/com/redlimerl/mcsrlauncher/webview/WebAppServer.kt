package com.redlimerl.mcsrlauncher.webview

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.apache.logging.log4j.LogManager
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class WebAppServer private constructor(
    private val server: HttpServer
) {
    private val logger = LogManager.getLogger("WebAppServer")

    val baseUrl: String = "http://127.0.0.1:${server.address.port}/"

    fun stop() {
        server.stop(0)
        logger.info("Stopped webapp server")
    }

    companion object {
        fun start(): WebAppServer {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val webAppServer = WebAppServer(server)

            server.createContext("/") { exchange ->
                webAppServer.handle(exchange)
            }

            server.executor = Executors.newCachedThreadPool { runnable ->
                Thread(runnable, "webapp-server").apply { isDaemon = true }
            }
            server.start()

            webAppServer.logger.info("Started webapp server at ${webAppServer.baseUrl}")
            return webAppServer
        }
    }

    private fun handle(exchange: HttpExchange) {
        try {
            if (exchange.requestMethod != "GET" && exchange.requestMethod != "HEAD") {
                exchange.sendResponseHeaders(405, -1)
                return
            }

            val rawPath = exchange.requestURI.path ?: "/"
            val path = if (rawPath == "/") "/index.html" else rawPath
            if (path.contains("..")) {
                exchange.sendResponseHeaders(400, -1)
                return
            }

            val resourcePath = "/webapp$path"
            val stream = javaClass.getResourceAsStream(resourcePath)
            if (stream == null) {
                exchange.sendResponseHeaders(404, -1)
                return
            }

            val contentType = guessContentType(path)
            exchange.responseHeaders.add("Content-Type", contentType)
            exchange.responseHeaders.add("Cache-Control", "no-store")

            if (exchange.requestMethod == "HEAD") {
                exchange.sendResponseHeaders(200, -1)
                return
            }

            val bytes = stream.use { it.readBytes() }
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        } catch (e: Exception) {
            logger.error("Failed to serve request", e)
            try {
                val msg = "Internal Server Error"
                val bytes = msg.toByteArray(StandardCharsets.UTF_8)
                exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
                exchange.sendResponseHeaders(500, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } catch (_: Exception) {
            }
        } finally {
            exchange.close()
        }
    }

    private fun guessContentType(path: String): String {
        return when (path.substringAfterLast('.', missingDelimiterValue = "").lowercase()) {
            "html" -> "text/html; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "js" -> "text/javascript; charset=utf-8"
            "json" -> "application/json; charset=utf-8"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "ico" -> "image/x-icon"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            else -> "application/octet-stream"
        }
    }
}
