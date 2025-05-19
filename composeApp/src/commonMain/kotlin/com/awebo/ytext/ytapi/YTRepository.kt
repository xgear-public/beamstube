package com.awebo.ytext.ytapi

import androidx.compose.ui.graphics.Color
import com.awebo.ytext.data.ChannelEntity
import com.awebo.ytext.data.TopicEntity
import com.awebo.ytext.data.VideoDao
import com.awebo.ytext.model.Topic
import com.awebo.ytext.model.Video
import com.awebo.ytext.model.Video.Companion.fromEntity
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.youtube.YouTube
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

class YTRepository(val videoDao: VideoDao) {

    val youtubeDataApiClient = YouTube.Builder(NetHttpTransport(), GsonFactory()) { httpRequest -> }
        .setApplicationName(YT_APP_NAME) // Replace with your application name
        .build()

    suspend fun loadAllTopics(): List<Topic> =
        videoDao.getAllTopicsWithChannelsAndVideos()
            .filter {
                it.channels
                    .flatMap { it.videos }.any { it.watched.not() }
            }
            .map {
                Topic(
                    it.topic.id,
                    it.topic.title,
                    it.channels
                        .flatMap { it.videos.filter { it.watched.not() } }
                        .map(::fromEntity)
                        .sortedByDescending { it.publishedAt },
                    it.topic.color,
                    it.topic.order,
                )
            }
            .sortedBy { it.order }


    suspend fun getAllTopics(): List<Topic> =
        videoDao.getAllTopics().map {
            Topic(
                id = it.id,
                title = it.title,
                color = it.color,
                order = it.order,
                videos = emptyList()
            )
        }

    suspend fun createTopic(title: String, channelHandleList: List<String>): List<Video> {
        val loadedChannelEntities: List<ChannelEntity> = channelHandleList.map {
            val channelId = loadChannelId(it)
            if (channelId != null) {
                return@map ChannelEntity(
                    id = channelId,
                    handle = it,
                    lastUpdated = Instant.ofEpochMilli(0),
                    topicId = 0
                )
            } else {
                println("No channel found with username: $it")
                return emptyList()
            }
        }
        if (loadedChannelEntities.size == channelHandleList.size) {
            val highestOrder = videoDao.getTopicWithHighestOrder()?.order ?: 0
            val random = (0..360).random()
            val hue = random.toFloat()
            val topicEntity = TopicEntity(
                id = DUMMY_ID,
                title = title,
                color = Color.hsl(
                    hue = hue,
                    saturation = 1f,
                    lightness = 0.95f
                ),
                order = highestOrder + 1 // make new topic on the bottom in order
            )
            println("topicEntity color: ${topicEntity.color}, random: $random, hue: $hue")
            val topicDbId = videoDao.insert(topicEntity)

            val channelEntities = loadedChannelEntities.map {
                ChannelEntity(it.id, it.handle, it.lastUpdated, topicDbId)
            }
            videoDao.insert(channelEntities)

            channelEntities.forEach {
                updateChannelVideosByChannelId(it.id)
            }
            val topicChannelsWithVideos = videoDao.getTopicChannelsWithVideos(topicDbId)
            return topicChannelsWithVideos
                ?.channels
                ?.flatMap { it.videos }
                ?.map {
                    Video(
                        id = it.id,
                        title = it.title,
                        description = it.description,
                        thumbnailUrl = it.thumbnailUrl,
                        publishedAt = it.publishedAt,
                        duration = it.duration,
                        watched = it.watched,
                    )
                }
                ?.sortedByDescending { it.publishedAt }
                ?: emptyList()
        } else {
            throw RuntimeException("Something went wrong with loading channels")
        }
    }

//    suspend fun updateChannelVideos(channelHandle: String): List<Video> {
//        val channelVideos = videoDao.getChannelVideos(channelHandle)
//        val channelId = if (channelVideos?.exists() == true) {
//
//            if (channelVideos.channel.lastUpdated.isAfter(Instant.now().minusSeconds(MINUTES_TTL * 60))) {
//                // if last update less than 30 min ago
//                return channelVideos.videos.map(::fromEntity)
//            }
//            channelVideos.channel.id
//        } else {
//            val channelId = loadChannelId(channelHandle)
//            if (channelId == null) {
//                throw RuntimeException("No channel found with username: $channelHandle")
//            }
//            videoDao.insert(ChannelEntity(channelId, channelHandle, Instant.now(), 0))
//            channelId
//        }
//        val searchVideos = loadAndSaveVideosByChannelId(channelId)
//        return searchVideos
//    }


    private suspend fun updateChannelVideosByChannelId(channelId: String): List<Video> {
        println("Thread id: ${Thread.currentThread().id} updateChannelVideosByChannelId started for $channelId")

        val channelVideos = videoDao.getChannelVideosByChannelId(channelId)
        if (channelVideos?.exists() == true) {

            if (channelVideos.channel.lastUpdated.isAfter(Instant.now().minusSeconds(MINUTES_TTL * 60))) {
                // if last update less than 30 min ago
                return channelVideos.videos.map(::fromEntity)
            }
        }
        val loadedVideos = loadAndSaveVideosByChannelId(channelId)
        return loadedVideos
    }

    private suspend fun loadAndSaveVideosByChannelId(channelId: String): List<Video> {
        val videos = getVideosForChannel(channelId)
        if (videos == null) {
            return emptyList()
        }
        videoDao.insertList(videos.map {
            it.toEntity(channelId)
        })
        return videos
    }

    @Deprecated("search.list uses 100 quota, replaced by channels.list and playlistItems.list quota 1 + 1")
    private suspend fun loadVideos(channelId: String): List<Video> {
        println("search for $channelId")

        try {
            val searchListRequest = youtubeDataApiClient.search().list(listOf("id", "snippet"))
            searchListRequest.channelId = channelId
            searchListRequest.type = listOf("video")
            searchListRequest.maxResults = 10L // You can adjust the number of results

            // Set the API key
            searchListRequest.key = YT_API_KEY

            // Calculate the date one week ago
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.add(Calendar.DAY_OF_YEAR, -7)
            val oneWeekAgo = calendar.time

            // Format the date to ISO 8601 format
            val instant = oneWeekAgo.toInstant()
            val offsetDateTime = instant.atOffset(ZoneOffset.UTC)
            val publishedAfter = offsetDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

            searchListRequest.publishedAfter = publishedAfter

            // Add sorting by date
            searchListRequest.order = "date"

            val searchResponse = searchListRequest.execute()
            val searchResults = searchResponse.items

            if (searchResults.isEmpty()) {
                println("No videos found for query: $channelId within the last week.")
            } else {
                println("Videos found for query: $channelId (within the last week):")
                return searchResults.map {
                    Video(
                        id = it.id.videoId,
                        title = it.snippet.title,
                        description = it.snippet.description,
                        publishedAt = Instant.ofEpochMilli(it.snippet.publishedAt.value),
                        thumbnailUrl = it.snippet.thumbnails.medium.url,
                        duration = Duration.ZERO,
                        watched = false
                    )
                }
            }

        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return emptyList()
    }

    private suspend fun getVideosForChannel(channelId: String): List<Video>? {
        try {
            // 1. Get the Uploads Playlist ID from the Channel ID (Cost: 1 unit)
            println("Fetching channel details for: $channelId to find uploads playlist...")
            val channelsListResponse = youtubeDataApiClient.channels()
                .list(listOf("contentDetails")) // Part: We only need contentDetails for relatedPlaylists
                .setId(listOf(channelId)) // Use listOf for setting ID
                .setKey(YT_API_KEY)
                .execute()

            val channel = channelsListResponse.items?.firstOrNull()
            val uploadsPlaylistId = channel?.contentDetails?.relatedPlaylists?.uploads

            if (uploadsPlaylistId == null) {
                println("Could not find uploads playlist ID for channel $channelId.")
                return emptyList() // Cannot proceed without uploads playlist
            }
            println("Found uploads playlist ID: $uploadsPlaylistId")

            // 2. Get video IDs from the Uploads Playlist (Cost: 1 unit per page)
            // Note: Implement pagination here if you need more than MAX_RESULTS_PER_PAGE_SDK videos
            println("Fetching video IDs from uploads playlist: $uploadsPlaylistId")
            val playlistItemsListResponse = youtubeDataApiClient.playlistItems()
                .list(listOf("snippet")) // Part: We need snippet for resourceId.videoId
                .setPlaylistId(uploadsPlaylistId)
                .setKey(YT_API_KEY)
                .setMaxResults(MAX_RESULTS_PER_PAGE_SDK)
                // Add .setPageToken(nextPageToken) for pagination
                .execute()

            val playlistItems = playlistItemsListResponse.items
            if (playlistItems == null || playlistItems.isEmpty()) {
                println("No video items found in the uploads playlist $uploadsPlaylistId.")
                return emptyList()
            }

            // Extract video IDs - ensure resourceId and videoId are not null
            val videoIds = playlistItems.mapNotNull { it.snippet?.resourceId?.videoId }
            if (videoIds.isEmpty()) {
                println("No valid video IDs extracted from playlist items.")
                return emptyList()
            }
            println("Found ${videoIds.size} video IDs via playlist.")


            // 3. Fetch details for the found video IDs (Cost: 1 unit per call of 50 videos)
            println("Fetching details for ${videoIds.size} video IDs...")
            // Note: If videoIds list is larger than 50, it needs to be chunked into multiple calls.
            val videoListResponse = youtubeDataApiClient.videos()
                .list(listOf("snippet", "contentDetails")) // Part: Get snippet and contentDetails (for duration)
                .setKey(YT_API_KEY)
                .setId(videoIds) // Provide comma-separated IDs (max 50)
                .execute()

            val videoItems = videoListResponse.items
            if (videoItems == null || videoItems.isEmpty()) {
                println("Could not fetch details for video IDs.")
                // It's possible playlistItems listed IDs for deleted/private videos
                return emptyList()
            }

            // 4. Filter out shorts (duration <= 60 seconds) (Cost: 0 units)
            val regularVideos = videoItems.filter { video ->
                isDurationLongerThan180Seconds(video.contentDetails?.duration)
            }

            // 5. Filter out videos older than a week (Cost: 0 units)
            val filteredVideos = regularVideos.filter { video ->
                val publishedAt = Instant.ofEpochMilli(video.snippet.publishedAt.value)
                publishedAt.isAfter(Instant.now().minusSeconds(WEEK_IN_SECONDS))
            }

            println("Fetched ${regularVideos.size} regular videos (>${180}s) for channel $channelId. Filtered: ${filteredVideos.size}")

            return filteredVideos.map {
                Video(
                    id = it.id,
                    title = it.snippet.title,
                    description = it.snippet.description,
                    publishedAt = Instant.ofEpochMilli(it.snippet.publishedAt.value),
                    thumbnailUrl = it.snippet.thumbnails.medium.url,
                    duration =
                        it.contentDetails?.let {
                            Duration.parse(it.duration)
                        } ?: Duration.ZERO,
                    watched = false,
                )
            }

        } catch (e: IOException) {
            // Handle network/API errors
            println("Error fetching YouTube videos (IOException): ${e.message}")
            e.printStackTrace()
            return null // Return null to indicate failure
        } catch (e: Exception) {
            // Handle other potential exceptions (e.g., NullPointers if API response structure is unexpected)
            println("An unexpected error occurred: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    private fun isDurationLongerThan180Seconds(durationString: String?): Boolean {
        if (durationString == null) return false
        return try {
            val duration = Duration.parse(durationString)
            duration.seconds > 180
        } catch (e: Exception) {
            println("Error parsing duration '$durationString': ${e.message}")
            false // Treat parse errors as not longer than 60s
        }
    }

    private suspend fun loadChannelId(userHandle: String): String? {
        println("channelId search for $userHandle")

        try {
            val channelsListRequest = youtubeDataApiClient.channels().list(listOf("id"))
            channelsListRequest.forHandle = userHandle
            channelsListRequest.key = YT_API_KEY

            val channelsResponse = channelsListRequest.execute()
            val channelResults = channelsResponse.items

            if (channelResults?.isNotEmpty() == true) {
                return channelResults.first().id
            } else {
                println("No channel found with username: $userHandle")
                return null
            }

        } catch (t: Throwable) {
            t.printStackTrace()
            return null
        }
    }


    suspend fun reloadAllTopics(): List<Topic> {
        println("reloadAllTopics started: ${DateTimeFormatter.ISO_INSTANT.format(Instant.now())}")


        val topicChannelsWithVideos = videoDao.getAllTopicsWithChannelsAndVideos()
        println("reloadAllTopics topicChannelsWithVideos: ${topicChannelsWithVideos.size}")
        topicChannelsWithVideos.forEach { topic ->

            topic.channels.forEach { channel ->
                updateChannelVideosByChannelId(channel.channel.id)
            }
        }

        println("reloadAllTopics stopped 1: ${DateTimeFormatter.ISO_INSTANT.format(Instant.now())}")
        return loadAllTopics()
    }

    suspend fun deleteOldVideos() {
        videoDao.deleteOldVideos(
            Instant.now().minusSeconds(WEEK_IN_SECONDS).toEpochMilli()
        )
    }

    suspend fun updateTopicsOrder(topics: List<Topic>, topicsToRemove: List<Topic>) {
        val topicEntities = topics.map {
            TopicEntity(
                id = it.id,
                title = it.title,
                color = it.color,
                order = it.order
            )
        }
        videoDao.updateTopics(topicEntities)
    }

    suspend fun deleteTopics(topics: List<Topic>) {
        val topicIdsToRemove = topics.map { it.id }
        videoDao.deleteTopics(topicIdsToRemove)
    }

    suspend fun deleteVideo(videoId: String) {
        videoDao.deleteVideo(videoId)
    }

    suspend fun markVideoWatched(video: Video) {
        videoDao.markVideoWatched(video.id, video.watched)
    }

    companion object {
        const val YT_API_KEY = "AIzaSyBkBcs6tOHKi7Q9_GrPNCJA1TVSBtoSGvs"
        const val YT_APP_NAME = "YTExt"
        const val MAX_RESULTS_PER_PAGE_SDK = 50L // Max results per page (use Long for SDK methods)

        const val DUMMY_ID = 0L

        const val WEEK_IN_SECONDS = 60 * 60 * 24 * 7L

        const val MINUTES_TTL = 30L
//        const val MINUTES_TTL = 1L
    }
}
