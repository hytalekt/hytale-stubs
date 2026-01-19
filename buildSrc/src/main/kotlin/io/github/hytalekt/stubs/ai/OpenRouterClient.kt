package io.github.hytalekt.stubs.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

/**
 * OpenRouter API client for accessing Gemini 2.5 Flash model.
 * Includes disk-based caching to avoid redundant API calls.
 */
class OpenRouterClient(
    private val apiKey: String,
    private val cacheDir: File,
    private val model: String = "google/gemini-2.5-flash-lite-preview-09-2025",
    private val temperature: Double = 0.1, // Low temperature for consistency
) {
    private val httpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build()

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    init {
        cacheDir.mkdirs()
    }

    /**
     * Send a prompt to the model and get a response.
     * Responses are cached based on prompt hash.
     */
    fun complete(
        prompt: String,
        systemPrompt: String? = null,
    ): String {
        val cacheKey = computeCacheKey(prompt, systemPrompt)
        val cacheFile = File(cacheDir, "$cacheKey.txt")

        // Check cache first
        if (cacheFile.exists()) {
            return cacheFile.readText()
        }

        // Build messages
        val messages =
            buildList {
                if (systemPrompt != null) {
                    add(Message(role = "system", content = systemPrompt))
                }
                add(Message(role = "user", content = prompt))
            }

        val request =
            ChatRequest(
                model = model,
                messages = messages,
                temperature = temperature,
                maxTokens = 8192,
            )

        val requestBody = json.encodeToString(request)

        val httpRequest =
            HttpRequest
                .newBuilder()
                .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .header("HTTP-Referer", "https://github.com/hytalekt/hytale-stubs")
                .header("X-Title", "Hytale Stubs Generator")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofMinutes(5))
                .build()

        val response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            throw OpenRouterException("API request failed with status ${response.statusCode()}: ${response.body()}")
        }

        val chatResponse = json.decodeFromString<ChatResponse>(response.body())
        val content =
            chatResponse.choices
                .firstOrNull()
                ?.message
                ?.content
                ?: throw OpenRouterException("No content in response")

        // Cache the response
        cacheFile.writeText(content)

        return content
    }

    /**
     * Send multiple prompts to the model in parallel and get responses.
     * Responses are cached based on prompt hash.
     *
     * @param requests List of pairs of (prompt, systemPrompt)
     * @param maxConcurrency Maximum number of concurrent requests (default 10)
     * @return List of responses in the same order as the requests
     */
    fun completeAll(
        requests: List<Pair<String, String?>>,
        maxConcurrency: Int = 10,
    ): List<String> {
        if (requests.isEmpty()) return emptyList()

        val executor = Executors.newFixedThreadPool(maxConcurrency)
        try {
            val futures =
                requests.map { (prompt, systemPrompt) ->
                    CompletableFuture.supplyAsync(
                        { completeWithAsyncHttp(prompt, systemPrompt) },
                        executor,
                    )
                }

            // Wait for all futures to complete and collect results
            return futures.map { it.join() }
        } finally {
            executor.shutdown()
        }
    }

    /**
     * Internal method that performs the HTTP request asynchronously.
     */
    private fun completeWithAsyncHttp(
        prompt: String,
        systemPrompt: String?,
    ): String {
        val cacheKey = computeCacheKey(prompt, systemPrompt)
        val cacheFile = File(cacheDir, "$cacheKey.txt")

        // Check cache first
        if (cacheFile.exists()) {
            return cacheFile.readText()
        }

        // Build messages
        val messages =
            buildList {
                if (systemPrompt != null) {
                    add(Message(role = "system", content = systemPrompt))
                }
                add(Message(role = "user", content = prompt))
            }

        val request =
            ChatRequest(
                model = model,
                messages = messages,
                temperature = temperature,
                maxTokens = 8192,
            )

        val requestBody = json.encodeToString(request)

        val httpRequest =
            HttpRequest
                .newBuilder()
                .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .header("HTTP-Referer", "https://github.com/hytalekt/hytale-stubs")
                .header("X-Title", "Hytale Stubs Generator")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofMinutes(5))
                .build()

        val responseFuture = httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
        val response = responseFuture.join()

        if (response.statusCode() != 200) {
            throw OpenRouterException("API request failed with status ${response.statusCode()}: ${response.body()}")
        }

        val chatResponse = json.decodeFromString<ChatResponse>(response.body())
        val content =
            chatResponse.choices
                .firstOrNull()
                ?.message
                ?.content
                ?: throw OpenRouterException("No content in response")

        // Cache the response (synchronized to prevent race conditions)
        synchronized(this) {
            if (!cacheFile.exists()) {
                cacheFile.writeText(content)
            }
        }

        return content
    }

    /**
     * Compute the cache key for a given prompt and system prompt.
     * Useful for checking if a response is cached before making a request.
     */
    fun computeCacheKey(
        prompt: String,
        systemPrompt: String?,
    ): String {
        val combined = "${systemPrompt ?: ""}|$model|$temperature|$prompt"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(combined.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(32)
    }
}

class OpenRouterException(
    message: String,
) : RuntimeException(message)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.1,
    @SerialName("max_tokens")
    val maxTokens: Int = 8192,
)

@Serializable
data class Message(
    val role: String,
    val content: String,
)

@Serializable
data class ChatResponse(
    val choices: List<Choice>,
)

@Serializable
data class Choice(
    val message: Message,
)
