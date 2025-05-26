package com.awebo.ytext.model

import com.awebo.ytext.data.VideoEntity
import com.awebo.ytext.data.VideoPlatform
import java.time.Duration
import java.time.Instant

data class Video(
    val id: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val publishedAt: Instant,
    val duration: Duration,
    val watched: Boolean,
    val sourcePlatform: VideoPlatform = VideoPlatform.YOUTUBE // Default to YouTube for backward compatibility
) {
    fun toEntity(channelEntityId: String = "") = VideoEntity(
        id = id,
        title = title,
        description = description,
        thumbnailUrl = thumbnailUrl,
        publishedAt = publishedAt,
        duration = duration,
        watched = watched,
        channelEntityId = channelEntityId,
        sourcePlatform = sourcePlatform
    )

    companion object {
        fun fromEntity(videoEntity: VideoEntity): Video {
            return Video(
                id = videoEntity.id,
                title = videoEntity.title,
                description = videoEntity.description,
                publishedAt = videoEntity.publishedAt,
                thumbnailUrl = videoEntity.thumbnailUrl,
                duration = videoEntity.duration,
                watched = videoEntity.watched,
                sourcePlatform = videoEntity.sourcePlatform
            )
        }
    }
}