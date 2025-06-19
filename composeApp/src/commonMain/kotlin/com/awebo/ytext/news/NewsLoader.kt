package com.awebo.ytext.news

import com.awebo.ytext.util.Logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpRequestRetry // Import HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException // Import IOException for specific retry condition

@Serializable
private data class NewsAnalysis(val analysis: String)

class NewsLoader(private val logger: Logger) {

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 25000 // 25 seconds
            connectTimeoutMillis = 25000  // 5 seconds
            socketTimeoutMillis = 25000  // 10 seconds
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        // Install HttpRequestRetry plugin
        install(HttpRequestRetry) {
            // Retry up to 3 times on exceptions
            // By default, it retries on 5xx server errors and network issues.
            // We can customize which exceptions to retry on.
            // EOFException is a subclass of IOException.
            retryOnExceptionIf { _, cause ->
                cause is IOException // Retry on any IOException, including EOFException
            }
            // Configure the number of retries
            maxRetries = 3
            // Configure delay strategy (e.g., exponential backoff)
            exponentialDelay() // e.g., 1s, 2s, 4s
            // You can also log retry attempts
            // modifyRequest { request -> logger.info("Retrying request ${request.url}, attempt ${this.retryCount}") }
        }
    }

    /**
     * Fetches news analysis from the newser service.
     * @return A string containing the analysis text, or null if an error occurs.
     */
    suspend fun loadNews(): String? {
        val url = "https://newser-service-272335850871.us-central1.run.app/analyze"
        // Ensure your Logger implementation correctly substitutes the placeholder
        logger.info("Fetching news analysis from: {}", url)
        return try {
            val response = client.get(url)
            val newsAnalysis = response.body<NewsAnalysis>()
            logger.info("Successfully fetched and parsed news analysis.")
            newsAnalysis.analysis
        } catch (e: Exception) { // Catching general Exception will also catch specific ones like EOFException or HttpRequestTimeoutException
            // Include the URL in the error log for better diagnostics
            logger.error("Error loading news from $url", error = e)
            null
        }
    }

    fun close() {
        client.close()
        logger.info("NewsLoader HttpClient closed.")
    }
}
