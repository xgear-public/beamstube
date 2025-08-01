package com.awebo.ytext.ytapi

import YTExt.composeApp.BuildConfig
import com.awebo.ytext.data.MiscDataStore
import com.awebo.ytext.util.Logger
import com.awebo.ytext.util.createLogger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.compose.koinInject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern


/**
 * A class for summarizing YouTube video transcripts using yt-dlp and Google's Gemini API.
 */
class YouTubeTranscriptSummarizer(val miscDataStore: MiscDataStore, private val logger: Logger) : AutoCloseable {

    companion object {
        private const val FFMPEG_PATH = "/opt/homebrew/bin/ffmpeg"
        private const val YTDLP_COMMAND = "/opt/homebrew/bin/yt-dlp"
        private const val DEFAULT_LANGUAGE = "en"
    }

    private val ffmpegPath = System.getenv("FFMPEG_PATH") ?: FFMPEG_PATH

    // Ktor HTTP Client for Gemini
    private val ktorHttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }

    /**
     * Main entry point to summarize a YouTube video transcript
     * @param videoUrl The URL of the YouTube video
     * @return The summarized transcript, or null if summarization failed
     */
    suspend fun summarizeVideo(videoUrl: String): String? {
        if (!isYtDlpAvailable()) {
            logger.error("yt-dlp command not found or not executable. Please ensure it's installed and in your PATH.")
            return null
        }

        val videoId = extractVideoId(videoUrl)
        if (videoId == null) {
            logger.warn("Could not extract a valid video ID pattern from the URL: {}", videoUrl)
        } else {
            logger.debug("Extracted Video ID: {}", videoId)
        }

        val preferredLang = getPreferredCaptionLanguage(videoUrl)
        logger.info("Preferred language determined by yt-dlp: {}", preferredLang)

        val transcriptText = fetchTranscript(videoUrl, preferredLang) ?: return null

        logger.debug("Fetched Transcript (First 500 chars): {}",
            transcriptText.take(500) + if (transcriptText.length > 500) "..." else ""
        )

        return summarizeTranscript(transcriptText)
    }

    /**
     * Checks if yt-dlp is available on the system
     */
    private fun isYtDlpAvailable(): Boolean {
        return try {
            val process = ProcessBuilder(YTDLP_COMMAND, "--version").start()
            val exited = process.waitFor(5, TimeUnit.SECONDS)
            exited && process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extracts the YouTube video ID from various URL formats
     */
    private fun extractVideoId(url: String): String? {
        val patterns = arrayOf(
            "(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%\u200C\u200B2F|youtu.be%2F|%2Fv%2F)[^#\\&\\?\\n]*",
            "(?<=\\/shorts\\/)[^#\\&\\?\\n]*"
        )
        patterns.forEach { patternString ->
            val compiledPattern = Pattern.compile(patternString)
            val matcher = compiledPattern.matcher(url)
            if (matcher.find()) {
                return matcher.group()
            }
        }
        return null
    }

    /**
     * Determines the preferred caption language for a video using yt-dlp.
     * This method implements a priority system for selecting the best language:
     * 1.  Captions explicitly marked as "original" (e.g., lang code "en-orig" or name containing "(Original)").
     * 2.  The first available manually created caption.
     * 3.  Defaults to "en" if no suitable caption is found.
     *
     * @param videoUrl The URL of the YouTube video.
     * @return The preferred language code.
     */
    private suspend fun getPreferredCaptionLanguage(videoUrl: String): String = withContext(Dispatchers.IO) {
        val command = listOf(YTDLP_COMMAND, "--no-warnings", "--list-subs", videoUrl)
        logger.debug("Executing yt-dlp command to list subtitles: {}", command.joinToString(" "))

        try {
            val process = ProcessBuilder(command).start()
            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

            // Read all output at once
            val outputLines = stdoutReader.readLines()
            val errors = stderrReader.readText()

            if (errors.isNotBlank()) {
                logger.warn("yt-dlp errors while listing subs:\n{}", errors)
            }

            val exited = process.waitFor(30, TimeUnit.SECONDS)
            if (!exited || process.exitValue() != 0) {
                logger.error(
                    "yt-dlp --list-subs command failed or timed out. Exit code: {}",
                    if (exited) process.exitValue() else "TIMEOUT"
                )
                return@withContext DEFAULT_LANGUAGE // Default to English on failure
            }

            if (logger.isDebugEnabled()) {
                logger.debug("yt-dlp --list-subs output:\n{}", outputLines.joinToString("\n"))
            }

            // --- Start Parsing Logic (Single Pass) ---
            var inManualSubsSection = false
            var firstFoundManualLang: String? = null
            val langCodeOriginalMarker = "-orig"
            val langNameOriginalMarker = "(original)"

            for (line in outputLines) {
                val trimmedLine = line.trim()

                // Update context based on section headers
                if (trimmedLine.startsWith("[info] Available subtitles for", ignoreCase = true)) {
                    inManualSubsSection = true
                    continue
                } else if (trimmedLine.startsWith("[info] Available automatic captions for", ignoreCase = true)) {
                    inManualSubsSection = false
                    continue
                }

                // Skip irrelevant lines
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("Language Name") || trimmedLine.startsWith("[")) {
                    continue
                }

                val parts = trimmedLine.split(Regex("\\s+"), limit = 2)
                if (parts.size < 2) continue

                val langCode = parts[0]
                val restOfLine = parts[1]

                // Heuristic to extract language name
                val formatKeywords = listOf("vtt", "ttml", "srv3", "srv2", "srv1", "json3")
                var namePartEndIndex = restOfLine.length
                for (keyword in formatKeywords) {
                    val keywordIndex = restOfLine.indexOf(keyword, ignoreCase = true)
                    if (keywordIndex != -1) {
                        namePartEndIndex = minOf(namePartEndIndex, keywordIndex)
                    }
                }
                val langName = restOfLine.substring(0, namePartEndIndex).trim()

                // Priority 1: Check for "original" marker. If found, return immediately.
                if (langCode.endsWith(langCodeOriginalMarker, ignoreCase = true) ||
                    langName.contains(langNameOriginalMarker, ignoreCase = true)
                ) {
                    logger.debug("Found explicitly marked original language: Code='{}', Name='{}'. Using this.", langCode, langName)
                    return@withContext langCode
                }

                // Priority 2: Store the first valid manual language found.
                if (inManualSubsSection && firstFoundManualLang == null && isLanguageCode(langCode)) {
                    firstFoundManualLang = langCode
                }
            }

            // If an original language was found, we would have already returned.
            // Now, return the first manual language if we found one.
            firstFoundManualLang?.let {
                logger.debug("Found manual subtitle language: '{}'. Using it.", it)
                return@withContext it
            }

            // Priority 3: Default to English if no better option was found.
            logger.debug("No explicitly marked original or manual captions found. Defaulting to '{}'.", DEFAULT_LANGUAGE)
            return@withContext DEFAULT_LANGUAGE

        } catch (e: Exception) {
            logger.error("Error executing or processing yt-dlp --list-subs: ${e.message}", e)
            return@withContext DEFAULT_LANGUAGE // Default to English on error
        }
    }

    /**
     * Fetches the transcript for a YouTube video using yt-dlp.
     *
     * @param videoUrl The URL of the YouTube video
     * @param lang The preferred language code (default: "en")
     * @return The transcript text, or null if fetching failed
     */
    private suspend fun fetchTranscript(videoUrl: String, lang: String = DEFAULT_LANGUAGE): String? =
        withContext(Dispatchers.IO) {
            // ... (implementation remains the same as previous version with --convert-subs srt)
            val tempFileBase = "subtitle_temp_${System.currentTimeMillis()}"
            val tempPlaceholderFile = File.createTempFile(tempFileBase, ".tmp")
            val outputTemplate = tempPlaceholderFile.absolutePath.replaceFirst(Regex("\\.tmp$"), ".%(ext)s")
            tempPlaceholderFile.delete()

            val command = mutableListOf(
                YTDLP_COMMAND,
                "--no-warnings",
                "--write-subs",
                "--write-auto-subs",
                "--sub-langs", lang,
                "--convert-subs", "srt",
                "--ffmpeg-location", ffmpegPath,
                "--skip-download",
                "-o", outputTemplate,
                videoUrl
            )

            logger.debug("Executing yt-dlp command to download/convert subtitles: ${command.joinToString(" ")}")
            val actualExpectedFileWithLang = File(outputTemplate.replace("%(ext)s", "$lang.srt"))
            val actualExpectedFileGeneric = File(outputTemplate.replace("%(ext)s", "srt"))

            try {
                val processBuilder = ProcessBuilder(command)
                val process = processBuilder.start()

                val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
                val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

                var line: String?
                logger.debug("yt-dlp download stdout:") // Differentiate from list-subs stdout
                while (stdoutReader.readLine().also { line = it } != null) {
                    println(line)
                }

                val errors = StringBuilder()
                logger.debug("yt-dlp download stderr:") // Differentiate
                while (stderrReader.readLine().also { line = it } != null) {
                    println(line)
                    errors.append(line).append("\n")
                }

                val exited = process.waitFor(60, TimeUnit.SECONDS)

                if (!exited || process.exitValue() != 0) {
                    System.err.println("yt-dlp download process failed or timed out. Exit code: ${if (exited) process.exitValue() else "TIMEOUT"}")
                    System.err.println("yt-dlp download errors:\n$errors")
                    logger.error("yt-dlp download errors:\n$errors")
                    actualExpectedFileWithLang.delete()
                    actualExpectedFileGeneric.delete()
                    File(outputTemplate.replace("%(ext)s", "$lang.vtt")).delete()
                    File(outputTemplate.replace("%(ext)s", "vtt")).delete()
                    return@withContext null
                }

                val fileToRead = when {
                    actualExpectedFileWithLang.exists() && actualExpectedFileWithLang.length() > 0 -> actualExpectedFileWithLang
                    actualExpectedFileGeneric.exists() && actualExpectedFileGeneric.length() > 0 -> actualExpectedFileGeneric
                    else -> {
                        logger.debug("Expected SRT subtitle file not found or is empty after yt-dlp execution.")
                        logger.debug("Checked for: ${actualExpectedFileWithLang.absolutePath}")
                        logger.debug("Checked for: ${actualExpectedFileGeneric.absolutePath}")
                        File(outputTemplate.replace("%(ext)s", "$lang.vtt")).delete()
                        File(outputTemplate.replace("%(ext)s", "vtt")).delete()
                        return@withContext null
                    }
                }

                logger.debug("Downloaded captions to: {}", fileToRead.absolutePath)
                val srtContent = fileToRead.readText(Charsets.UTF_8)
                fileToRead.delete()

                return@withContext parseSrtContent(srtContent)

            } catch (e: Exception) {
                logger.error("Error downloading captions: {}", e.message ?: "Unknown error", e)
                return@withContext null
            } finally {
                actualExpectedFileWithLang.delete()
                actualExpectedFileGeneric.delete()
                File(outputTemplate.replace("%(ext)s", "$lang.vtt")).delete()
                File(outputTemplate.replace("%(ext)s", "vtt")).delete()
            }
        }

    /**
     * Parses SRT content to extract plain text.
     *
     * @param srtContent The SRT formatted subtitle content
     * @return Plain text extracted from the SRT content
     */
    private fun parseSrtContent(srtContent: String): String {
        val transcriptBuilder = StringBuilder()
        srtContent.lines().forEach { line ->
            if (line.matches(Regex("^\\d+$")) ||
                line.contains("-->") ||
                line.trim().isEmpty() && transcriptBuilder.endsWith(" ")
            ) {
                // Skip
            } else {
                transcriptBuilder.append(line.trim()).append(" ")
            }
        }
        return transcriptBuilder.toString().trim()
    }

    /**
     * Summarizes the given transcript text using Google's Gemini API.
     *
     * @param transcript The transcript text to summarize
     * @return The summarized text, or null if summarization failed
     */
    private suspend fun summarizeTranscript(transcript: String): String? {
        if (transcript.isBlank()) {
            logger.debug("Transcript is empty, cannot summarize.")
            return null
        }
        val summarizationLanguageText = miscDataStore.getLanguage().summarizationLanguageText

        val modelName = "gemini-2.0-flash"
        val apiUrl =
            "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=${BuildConfig.GOOGLE_AI_STUDIO_API_KEY}"
        val promptText = """
            Please provide a concise and informative summary of the following video transcript.
            Focus on the main topics, key arguments, and any significant conclusions.
            The output should be plain text in $summarizationLanguageText language.

            Transcript:
            "$transcript"
        """.trimIndent()

        val requestBody = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = promptText))))
        )

        return try {
            logger.debug("trace 1")
            val response = ktorHttpClient.post(apiUrl) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            logger.debug("trace 2")
            if (!response.status.isSuccess()) {
                val errorBody = response.body<String>()
                logger.error("Error from Gemini API: Status ${response.status}. Response: $errorBody")
                return null
            }

            logger.debug("trace 3")
            val geminiResponse = response.body<GeminiResponse>()
            geminiResponse.candidates?.firstOrNull()?.extractText()?.trim()
                ?: run {
                    logger.debug("No summary content found in Gemini API response. Full: $geminiResponse")
                    null
                }
        } catch (e: Exception) {
            logger.error("Error during Ktor call to Gemini API: ${e.message}", e)
            null
        }
    }

    fun isLanguageCode(code: String): Boolean {
        // Get all 2-letter ISO language codes.
        val languageCodes = Locale.getISOLanguages()
        return languageCodes.contains(code.lowercase())
    }

    /**
     * Closes the HTTP client when the summarizer is no longer needed
     */
    override fun close() {
        ktorHttpClient.close()
    }

}

/**
 * Example usage of the YouTubeTranscriptSummarizer
 */
//fun main() = runBlocking {
//    YouTubeTranscriptSummarizer().use { summarizer ->
//        println("Enter YouTube video URL:")
//        val videoUrl = "https://www.youtube.com/watch?v=ohyK_2XoYOw"
//
//        if (videoUrl.isBlank()) {
//            println("No URL provided. Exiting.")
//            return@use
//        }
//
//        val summary = summarizer.summarizeVideo(videoUrl)
//        if (summary != null) {
//            println("\n--- Summarized Transcript ---")
//            println(summary)
//        } else {
//            println("Failed to generate summary for the video.")
//        }
//    }
//}

// --- Gemini API Data Classes ---
@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GenerationConfig? = null
)

@Serializable
data class GeminiContent(val parts: List<GeminiPart>)

@Serializable
data class GeminiPart(val text: String)

@Serializable
data class GenerationConfig(
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    @SerialName("promptFeedback")
    val promptFeedback: GeminiPromptFeedback? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
    val index: Int? = null,
    val safetyRatings: List<GeminiSafetyRating>? = null
) {
    fun extractText(): String? = this.content?.parts?.firstOrNull()?.text
}

@Serializable
data class GeminiSafetyRating(
    val category: String = "",
    val probability: String = ""
)

@Serializable
data class GeminiPromptFeedback(
    val safetyRatings: List<GeminiSafetyRating>? = null
)