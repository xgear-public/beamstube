package com.awebo.ytext.news

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class NewsAnalysis(val analysis: String)

class NewsLoader {

    private val client = HttpClient(CIO) {
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
        return try {
            val response = client.get(url)
            val newsAnalysis = response.body<NewsAnalysis>()
            newsAnalysis.analysis
        } catch (e: Exception) {
            println("Error loading news: ${e.message}")
            null
        }
    }

    fun close() {
        client.close()
    }
}