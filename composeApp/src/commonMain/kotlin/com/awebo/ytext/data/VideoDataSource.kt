package com.awebo.ytext.data

import com.awebo.ytext.model.Video
import java.time.Instant

/**
 * Interface defining the contract for video data sources.
 * Each platform-specific implementation (YouTube, Vimeo, etc.) should implement this interface.
 */
interface VideoDataSource {
    /**
     * Fetches videos for a specific channel
     * @param channelId The unique identifier for the channel
     * @return List of videos or null if an error occurs
     */
    suspend fun getVideosForChannel(channelId: String): List<Video>?
    
    /**
     * Resolves a channel handle to a channel ID
     * @param userHandle The channel's handle/username
     * @return Channel ID or null if not found
     */
    suspend fun getChannelId(userHandle: String): String?
    
    /**
     * The platform name that this data source represents (e.g., "YouTube", "Vimeo")
     */
    val platformName: String
}
