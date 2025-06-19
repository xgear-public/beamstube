package com.awebo.ytext.news

import com.awebo.ytext.util.Logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class NewsAnalysis(val analysis: String)

class NewsLoader(private val logger: Logger) {

    private val client = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 25000 // 25 seconds
            connectTimeoutMillis = 5000  // 5 seconds
            socketTimeoutMillis = 10000  // 10 seconds
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    /**
     * Fetches news analysis from the newser service.
     * @return A string containing the analysis text, or null if an error occurs.
     */
    suspend fun loadNews(): String? {
        val url = "https://newser-service-272335850871.us-central1.run.app/analyze"
        logger.info("Fetching news analysis from: {}", url)
        return try {
            val response = client.get(url)
            val newsAnalysis = response.body<NewsAnalysis>()
            logger.info("Successfully fetched and parsed news analysis.")
            newsAnalysis.analysis
        } catch (e: Exception) {
            logger.error("Error loading news", error = e)
            null
        }
    }

    fun close() {
        client.close()
    }
}