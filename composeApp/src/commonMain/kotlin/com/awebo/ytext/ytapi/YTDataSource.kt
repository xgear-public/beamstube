package com.awebo.ytext.ytapi

import YTExt.composeApp.BuildConfig
import com.awebo.ytext.data.VideoDataSource
import com.awebo.ytext.data.VideoPlatform
import com.awebo.ytext.model.Video
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.youtube.YouTube
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

const val YT_APP_NAME = BuildConfig.YT_APP_NAME
const val YT_API_KEY = BuildConfig.YT_DATA_API_V3_KEY
val WEEK_IN_SECONDS = 7 * 24 * 60 * 60L
val MAX_RESULTS_PER_PAGE_SDK = 50L

/**
 * YouTube implementation of the VideoDataSource interface.
 * Handles all YouTube API interactions.
 */
class YTDataSource : VideoDataSource {
    private val youtubeDataApiClient =
        YouTube.Builder(NetHttpTransport(), GsonFactory()) { _ -> }
            .setApplicationName(YT_APP_NAME)
            .build()

    override val videoPlatform: VideoPlatform = VideoPlatform.YOUTUBE

    override suspend fun getVideosForChannel(channelId: String): List<Video>? {
        try {
            // 1. Get the Uploads Playlist ID from the Channel ID
            println("Fetching channel details for: $channelId to find uploads playlist...")
            val channelsListResponse = youtubeDataApiClient.channels()
                .list(listOf("contentDetails"))
                .setId(listOf(channelId))
                .setKey(YT_API_KEY)
                .execute()

            val channel = channelsListResponse.items?.firstOrNull()
            val uploadsPlaylistId = channel?.contentDetails?.relatedPlaylists?.uploads
                ?: return emptyList()

            // 2. Get video IDs from the Uploads Playlist
            println("Fetching video IDs from uploads playlist: $uploadsPlaylistId")
            val playlistItemsListResponse = youtubeDataApiClient.playlistItems()
                .list(listOf("snippet"))
                .setPlaylistId(uploadsPlaylistId)
                .setKey(YT_API_KEY)
                .setMaxResults(MAX_RESULTS_PER_PAGE_SDK)
                .execute()

            val playlistItems = playlistItemsListResponse.items ?: return emptyList()
            val videoIds = playlistItems.mapNotNull { it.snippet?.resourceId?.videoId }
            if (videoIds.isEmpty()) return emptyList()

            // 3. Fetch details for the found video IDs
            val videoListResponse = youtubeDataApiClient.videos()
                .list(listOf("snippet", "contentDetails"))
                .setKey(YT_API_KEY)
                .setId(videoIds)
                .execute()

            val videoItems = videoListResponse.items ?: return emptyList()

            // 4. Filter out shorts (duration <= 3 minutes) and old videos
            val oneWeekAgo = Instant.now().minus(WEEK_IN_SECONDS, ChronoUnit.SECONDS)
            return videoItems
                .filter { isDurationLongerThan180Seconds(it.contentDetails?.duration) }
                .filter { Instant.ofEpochMilli(it.snippet.publishedAt.value).isAfter(oneWeekAgo) }
                .map {
                    Video(
                        id = it.id,
                        title = it.snippet.title,
                        description = it.snippet.description,
                        publishedAt = Instant.ofEpochMilli(it.snippet.publishedAt.value)
                            .atZone(java.time.ZoneOffset.UTC).toInstant(),
                        thumbnailUrl = it.snippet.thumbnails.medium.url,
                        duration = it.contentDetails?.let { details ->
                            Duration.parse(details.duration)
                        } ?: Duration.ZERO,
                        watched = false,
                        sourcePlatform = videoPlatform
                    )
                }

        } catch (e: Exception) {
            println("Error in YTDataSource.getVideosForChannel: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    override suspend fun getChannelId(userHandle: String): String? {
        try {
            val channelsListRequest = youtubeDataApiClient.channels().list(listOf("id"))
            channelsListRequest.forHandle = userHandle
            channelsListRequest.key = YT_API_KEY

            return channelsListRequest.execute().items?.firstOrNull()?.id
        } catch (e: Exception) {
            println("Error in YTDataSource.getChannelId: ${e.message}")
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
}
