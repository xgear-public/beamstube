package com.awebo.ytext.news

import com.awebo.ytext.util.Logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.ConnectException // Import specific exceptions for clarity
import java.net.SocketTimeoutException

@Serializable
private data class NewsAnalysis(val analysis: String)

class NewsLoader(private val logger: Logger) {

    // Make URL a constant or load from config
    private val NEWS_ANALYSIS_URL = "https://newser-service-272335850871.us-central1.run.app/analyze"

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            // Give the entire request up to 90 seconds to complete.
            requestTimeoutMillis = 90000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 80000
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(HttpRequestRetry) {
            maxRetries = 3
            exponentialDelay()
            // Retry on IOException (includes EOFException) and common network issues
            retryOnExceptionIf { _, cause ->
                cause is IOException || cause is ConnectException || cause is SocketTimeoutException
            }
            // Optionally, also retry on specific server errors if they are known to be transient
            // retryOnServerErrors(RetryStatus.SERVER_ERRORS) // Retries on 5xx status codes
        }
    }

    /**
     * Fetches news analysis from the newser service.
     * @return A string containing the analysis text, or null if an error occurs.
     */
    suspend fun loadNews(): String? {
        // Use the constant URL
        val url = NEWS_ANALYSIS_URL
        logger.info("Fetching news analysis from: {}", url)
        return try {
            val response = client.get(url)
            val newsAnalysis = response.body<NewsAnalysis>()
            logger.info("Successfully fetched and parsed news analysis.")
            newsAnalysis.analysis
        } catch (e: Exception) {
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