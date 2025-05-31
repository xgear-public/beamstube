package com.awebo.ytext.ytapi

import YTExt.composeApp.BuildConfig
import io.ktor.client.*
import io.ktor.client.call.body
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
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern


/**
 * A class for summarizing YouTube video transcripts using yt-dlp and Google's Gemini API.
 *
 */
class YouTubeTranscriptSummarizer : AutoCloseable {
    companion object {
        private const val YTDLP_COMMAND = "/opt/homebrew/bin/yt-dlp"
        private const val DEFAULT_LANGUAGE = "en"
    }

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
            println("Error: yt-dlp command not found or not executable. Please ensure it's installed and in your PATH.")
            return null
        }

        val videoId = extractVideoId(videoUrl)
        if (videoId == null) {
            println("Could not extract a valid video ID pattern from the URL: $videoUrl")
        } else {
            println("Extracted Video ID: $videoId")
        }

        val preferredLang = getPreferredCaptionLanguage(videoUrl)
        println("Preferred language determined by yt-dlp: $preferredLang")

        val transcriptText = fetchTranscript(videoUrl, preferredLang) ?: return null

        println("\n--- Fetched Transcript (First 500 chars) ---")
        println(
            transcriptText.substring(0, minOf(transcriptText.length, 500)) +
                    if (transcriptText.length > 500) "..." else ""
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
     * Priority 1: Looks for explicitly "original" captions (e.g., lang code ends with "-orig" or name contains "(Original)").
     * Priority 2 (Default): Returns "en" if no such "original" captions are found.
     *
     * @param videoUrl The URL of the YouTube video
     * @return The preferred language code
     */
    private suspend fun getPreferredCaptionLanguage(videoUrl: String): String = withContext(Dispatchers.IO) {
        val command = listOf(
            YTDLP_COMMAND,
            "--no-warnings",
            "--list-subs",
            videoUrl
        )
        println("Executing yt-dlp command to list subtitles: ${command.joinToString(" ")}")

        try {
            val processBuilder = ProcessBuilder(command)
            val process = processBuilder.start()

            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

            val outputLines = mutableListOf<String>()
            var line: String?
            while (stdoutReader.readLine().also { line = it } != null) {
                outputLines.add(line!!)
            }
            println("yt-dlp --list-subs output:\n${outputLines.joinToString("\n")}")

            val errors = StringBuilder()
            while (stderrReader.readLine().also { line = it } != null) {
                errors.append(line).append("\n")
            }
            if (errors.isNotBlank()) {
                System.err.println("yt-dlp errors while listing subs:\n$errors")
            }

            val exited = process.waitFor(30, TimeUnit.SECONDS)
            if (!exited || process.exitValue() != 0) {
                System.err.println("yt-dlp --list-subs command failed or timed out. Exit code: ${if (exited) process.exitValue() else "TIMEOUT"}")
                return@withContext DEFAULT_LANGUAGE // Default to English on failure
            }

            // --- Start Parsing Logic ---
            var subtitlesSectionStarted = false
            val langCodeOriginalMarker = "-orig"
            val langNameOriginalMarker = "(original)" // Case-insensitive check later

            for (outputLine in outputLines) {
                val trimmedLine = outputLine.trim()

                // Detect the start of subtitle listing.
                // It could be a header like "Language Name Formats" or section start like "[info] Available ... captions"
                if (trimmedLine.startsWith("Language Name", ignoreCase = true) ||
                    trimmedLine.startsWith("[info] Available automatic captions for", ignoreCase = true) ||
                    trimmedLine.startsWith("[info] Available subtitles for", ignoreCase = true)
                ) {
                    subtitlesSectionStarted = true
                    continue // Skip this header line
                }

                if (!subtitlesSectionStarted || trimmedLine.isEmpty() || trimmedLine.startsWith("[")) {
                    continue // Skip lines before section starts, empty lines, or other [info] lines
                }

                // Attempt to parse a subtitle entry line
                // Format: <lang_code> <Language Name...> <formats...> [Auto-generated Yes/No]
                // Example 1: en       English             vtt, ttml, srv3 Yes
                // Example 2: fr       French              vtt, ttml, srv3
                // Example 3: be-orig  Belarusian (Original) vtt, ttml, srv3
                // Example 4 (from your output for automatic): ab Abkhazian vtt, ttml, srv3... (no auto-gen column here)

                val parts = trimmedLine.split(Regex("\\s+"), limit = 2) // Split lang code from the rest
                if (parts.size < 2) {
                    continue // Not a valid subtitle line
                }

                val langCode = parts[0]
                val restOfLine = parts[1] // Contains "Language Name Formats..."

                // Heuristic to extract language name: find the first sequence of format strings like "vtt," or "ttml,"
                // The name is before that. This is a bit fragile.
                val formatKeywords = listOf("vtt", "ttml", "srv3", "srv2", "srv1", "json3")
                var namePartEndIndex = restOfLine.length
                for (keyword in formatKeywords) {
                    val keywordIndex = restOfLine.indexOf(keyword, ignoreCase = true)
                    if (keywordIndex != -1) {
                        namePartEndIndex = minOf(namePartEndIndex, keywordIndex)
                    }
                }
                val langName = restOfLine.substring(0, namePartEndIndex).trim()


                // Check for explicit "original" markers (Priority 1)
                if (langCode.endsWith(langCodeOriginalMarker, ignoreCase = true) ||
                    langName.contains(langNameOriginalMarker, ignoreCase = true)
                ) {
                    println("Found explicitly marked original language: Code='$langCode', Name='$langName'. Using code: '$langCode'")
                    return@withContext langCode // Return the full code like "be-orig"
                }
            }

            // If no explicitly marked "original" caption was found after checking all lines
            println("No explicitly marked '-orig' or '(Original)' captions found. Defaulting to English ('en').")
            return@withContext DEFAULT_LANGUAGE

        } catch (e: Exception) {
            System.err.println("Error executing or processing yt-dlp --list-subs: ${e.message}")
            e.printStackTrace()
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
                "--skip-download",
                "-o", outputTemplate,
                videoUrl
            )

            println("Executing yt-dlp command to download/convert subtitles: ${command.joinToString(" ")}")
            val actualExpectedFileWithLang = File(outputTemplate.replace("%(ext)s", "$lang.srt"))
            val actualExpectedFileGeneric = File(outputTemplate.replace("%(ext)s", "srt"))

            try {
                val processBuilder = ProcessBuilder(command)
                val process = processBuilder.start()

                val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
                val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

                var line: String?
                println("yt-dlp download stdout:") // Differentiate from list-subs stdout
                while (stdoutReader.readLine().also { line = it } != null) {
                    println(line)
                }

                val errors = StringBuilder()
                println("yt-dlp download stderr:") // Differentiate
                while (stderrReader.readLine().also { line = it } != null) {
                    println(line)
                    errors.append(line).append("\n")
                }

                val exited = process.waitFor(60, TimeUnit.SECONDS)

                if (!exited || process.exitValue() != 0) {
                    System.err.println("yt-dlp download process failed or timed out. Exit code: ${if (exited) process.exitValue() else "TIMEOUT"}")
                    System.err.println("yt-dlp download errors:\n$errors")
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
                        println("Expected SRT subtitle file not found or is empty after yt-dlp execution.")
                        println("Checked for: ${actualExpectedFileWithLang.absolutePath}")
                        println("Checked for: ${actualExpectedFileGeneric.absolutePath}")
                        File(outputTemplate.replace("%(ext)s", "$lang.vtt")).delete()
                        File(outputTemplate.replace("%(ext)s", "vtt")).delete()
                        return@withContext null
                    }
                }

                println("Reading transcript from: ${fileToRead.absolutePath}")
                val srtContent = fileToRead.readText(Charsets.UTF_8)
                fileToRead.delete()

                return@withContext parseSrtContent(srtContent)

            } catch (e: Exception) {
                System.err.println("Error executing or processing yt-dlp download: ${e.message}")
                e.printStackTrace()
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
            println("Transcript is empty, cannot summarize.")
            return null
        }

        val modelName = "gemini-2.0-flash"
        val apiUrl =
            "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=${BuildConfig.GOOGLE_AI_STUDIO_API_KEY}"
        val promptText = """
            Please provide a concise and informative summary of the following video transcript.
            Focus on the main topics, key arguments, and any significant conclusions.
            The output should be plain text in russian language.

            Transcript:
            "$transcript"
        """.trimIndent()

        val requestBody = GeminiRequest(
            contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = promptText))))
        )

        return try {
            println("trace 1")
            val response = ktorHttpClient.post(apiUrl) {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            println("trace 2")
            if (!response.status.isSuccess()) {
                val errorBody = response.body<String>()
                println("Error from Gemini API: Status ${response.status}. Response: $errorBody")
                return null
            }

            println("trace 3")
            val geminiResponse = response.body<GeminiResponse>()
            geminiResponse.candidates?.firstOrNull()?.extractText()?.trim()
                ?: run {
                    println("No summary content found in Gemini API response. Full: $geminiResponse")
                    null
                }
        } catch (e: Exception) {
            println("Error during Ktor call to Gemini API: ${e.message}")
            e.printStackTrace()
            null
        }
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
fun main() = runBlocking {
    YouTubeTranscriptSummarizer().use { summarizer ->
        println("Enter YouTube video URL:")
        val videoUrl = "https://www.youtube.com/watch?v=ohyK_2XoYOw"

        if (videoUrl.isBlank()) {
            println("No URL provided. Exiting.")
            return@use
        }

        val summary = summarizer.summarizeVideo(videoUrl)
        if (summary != null) {
            println("\n--- Summarized Transcript ---")
            println(summary)
        } else {
            println("Failed to generate summary for the video.")
        }
    }
}

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