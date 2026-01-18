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

/**
 * OpenRouter API client for accessing Gemini 2.5 Flash model.
 * Includes disk-based caching to avoid redundant API calls.
 */
class OpenRouterClient(
    private val apiKey: String,
    private val cacheDir: File,
    private val model: String = "google/gemini-2.5-flash-preview",
    private val temperature: Double = 0.1, // Low temperature for consistency
) {
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    private val json = Json {
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
    fun complete(prompt: String, systemPrompt: String? = null): String {
        val cacheKey = computeCacheKey(prompt, systemPrompt)
        val cacheFile = File(cacheDir, "$cacheKey.txt")

        // Check cache first
        if (cacheFile.exists()) {
            return cacheFile.readText()
        }

        // Build messages
        val messages = buildList {
            if (systemPrompt != null) {
                add(Message(role = "system", content = systemPrompt))
            }
            add(Message(role = "user", content = prompt))
        }

        val request = ChatRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            maxTokens = 8192,
        )

        val requestBody = json.encodeToString(request)

        val httpRequest = HttpRequest.newBuilder()
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
        val content = chatResponse.choices.firstOrNull()?.message?.content
            ?: throw OpenRouterException("No content in response")

        // Cache the response
        cacheFile.writeText(content)

        return content
    }

    private fun computeCacheKey(prompt: String, systemPrompt: String?): String {
        val combined = "${systemPrompt ?: ""}|$model|$temperature|$prompt"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(combined.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(32)
    }
}

class OpenRouterException(message: String) : RuntimeException(message)

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
