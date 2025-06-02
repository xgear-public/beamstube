package com.awebo.ytext.data

import androidx.compose.ui.graphics.Color
import com.awebo.ytext.model.*
import com.awebo.ytext.util.toFormattedString
import com.awebo.ytext.ytapi.WEEK_IN_SECONDS
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant

class VideosRepository(
    val miscDataStore: MiscDataStore,
    val videoDao: VideoDao,
    val dataSources: Map<VideoPlatform, VideoDataSource>,
) {

    private val defaultDataSource = dataSources[VideoPlatform.YOUTUBE]!!

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
                        .map(Video.Companion::fromEntity)
                        .sortedByDescending { it.publishedAt },
                    it.topic.color,
                    it.topic.order,
                )
            }
            .sortedBy { it.order }


    suspend fun getAllTopics(): List<TopicManagable> =
        videoDao.getAllTopics().map { tcv ->
            TopicManagable(
                topic = Topic(
                    id = tcv.topic.id,
                    title = tcv.topic.title,
                    color = tcv.topic.color,
                    order = tcv.topic.order,
                    videos = emptyList()
                ),
                channelList = tcv.channels.map {
                    Channel(
                        id = it.channel.id,
                        handle = it.channel.handle,
                        lastUpdated = it.channel.lastUpdated,
                        sourcePlatform = it.channel.sourcePlatform,
                    )
                }
            )
        }

    suspend fun createTopic(title: String, channels: String): List<Video> {
        val channelsHandleList = channels.split(",")
        val loadedChannelEntities: List<ChannelEntity> = channelsHandleList.map { handle ->
            // Try each data source until we find one that can handle this channel
            for (dataSource in dataSources.values) {
                val channelId = dataSource.getChannelId(handle)
                if (channelId != null) {
                    return@map ChannelEntity(
                        id = channelId,
                        handle = handle,
                        lastUpdated = Instant.ofEpochMilli(0),
                        topicId = 0,
                        sourcePlatform = dataSource.videoPlatform
                    )
                }
            }
            println("No channel found with handle: $handle")
            return emptyList()
        }
        if (loadedChannelEntities.size == channelsHandleList.size) {
            val highestOrder = videoDao.getTopicWithHighestOrder()?.order ?: 0
            val random = (0..360).random()
            val hue = random.toFloat()
            val topicEntity = TopicEntity(
                id = DUMMY_ID,
                title = title,
                color = Color.Companion.hsl(
                    hue = hue,
                    saturation = 1f,
                    lightness = 0.95f
                ),
                order = highestOrder + 1 // make new topic on the bottom in order
            )
            println("topicEntity color: ${topicEntity.color}, random: $random, hue: $hue")
            val topicDbId = videoDao.insert(topicEntity)

            val channelEntities = loadedChannelEntities.map {
                ChannelEntity(
                    id = it.id,
                    handle = it.handle,
                    lastUpdated = it.lastUpdated,
                    topicId = topicDbId,
                    sourcePlatform = it.sourcePlatform
                )
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

    private suspend fun updateChannelVideosByChannelId(
        channelId: String,
        channelHandle: String? = "null",
        forceUpdate: Boolean = false,
    ): List<Video> {
        println("updateChannelVideosByChannelId started for $channelHandle, id $channelId")

        val channelVideos = videoDao.getChannelVideosByChannelId(channelId)
        channelVideos?.let {
            if (
                !forceUpdate &&
                channelVideos.channel.lastUpdated
                    .isAfter(Instant.now().minusSeconds(MINUTES_TTL * 60))
            ) {
                println("skipping update, channel last updated less than $MINUTES_TTL min ago")
                return channelVideos.videos.map(Video.Companion::fromEntity)
            }
        }

        val dataSource = dataSources[defaultDataSource.videoPlatform] ?: defaultDataSource
        val loadedVideos = loadAndSaveVideosByChannelId(channelId, dataSource)

        videoDao.updateChannelLastUpdated(channelId, Instant.now())

        println("updateChannelVideosByChannelId finished for $channelHandle, id $channelId")

        return loadedVideos
    }

    private suspend fun loadAndSaveVideosByChannelId(
        channelId: String,
        dataSource: VideoDataSource = defaultDataSource
    ): List<Video> {
        val videos = dataSource.getVideosForChannel(channelId) ?: return emptyList()

        println("channelId: $channelId, Videos: ${videos.size}")
        // Update the channel's last updated time
        val items = videos.map { video ->
            video.toEntity(channelId)
        }
        println("items: ${items.size}")

        videoDao.insertList(items)

        return videos
    }

    suspend fun reloadAllTopics(): List<Topic> {
        println("reloadAllTopics started")

        val topicChannelsWithVideos = videoDao.getAllTopicsWithChannelsAndVideos()
        println("reloadAllTopics topicChannelsWithVideos: ${topicChannelsWithVideos.size}")
        topicChannelsWithVideos.forEach { topic ->

            topic.channels.forEach { channel ->
                updateChannelVideosByChannelId(channel.channel.id, channel.channel.handle)
            }
        }

        println("reloadAllTopics stopped 1: ${Instant.now().toFormattedString()}")
        val loadAllTopics = loadAllTopics()

        val lastReload = miscDataStore.lastReload()
        println("lastReload: ${lastReload.toFormattedString()}")
        miscDataStore.updateReloadTime(Instant.now())

        return loadAllTopics
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

    suspend fun markVideoWatched(video: Video) {
        videoDao.markVideoWatched(video.id, video.watched)
    }

    suspend fun updateTopic(topicId: Long, channels: String) {
        // Delete all channels for this topic by topicId
        val videos = videoDao.getTopicChannelsWithVideos(topicId)
        val watchedVideos = videos?.let { result ->
            result.channels
                .flatMap { it ->
                    it.videos
                }
                .filter { it.watched }
                .map {
                    it.id
                }
        }

        videoDao.deleteChannelsByTopicId(topicId)

        // Then add new channels similar to createTopic
        val channelsHandleList = channels.split(",")
        val loadedChannelEntities = channelsHandleList.mapNotNull { handle ->
            // Try each data source until we find one that can handle this channel
            for (dataSource in dataSources.values) {
                val channelId = dataSource.getChannelId(handle)
                if (channelId != null) {
                    return@mapNotNull ChannelEntity(
                        id = channelId,
                        handle = handle,
                        lastUpdated = Instant.ofEpochMilli(0),
                        topicId = topicId,
                        sourcePlatform = dataSource.videoPlatform
                    )
                }
            }
            println("No channel found with handle: $handle")
            null
        }

        if (loadedChannelEntities.isNotEmpty()) {
            // Insert new channels
            videoDao.insert(loadedChannelEntities)


            // Update videos for each channel
            loadedChannelEntities.forEach {
                updateChannelVideosByChannelId(it.id, forceUpdate = true)
            }

            watchedVideos?.let { it ->
                videoDao.markVideosAsWatched(it)
            }

        }
    }

    suspend fun manageTopic(changeRequest: TopicChangeRequest) {
        when (changeRequest) {
            is TopicAddRequest ->
                createTopic(changeRequest.title, changeRequest.channels)

            is TopicDeleteRequest ->
                videoDao.deleteTopics(listOf(changeRequest.topicId))

            is TopicUpdateRequest ->
                updateTopic(changeRequest.topicId, changeRequest.channels)
        }
    }

    fun getWatchedVideosLast3Days(): Flow<List<Video>> {
        val timestamp = Instant.now().minus(3, java.time.temporal.ChronoUnit.DAYS).toEpochMilli()

        return videoDao
            .getWatchedVideosLastNDays(timestamp)
            .map { it.map(Video.Companion::fromEntity) }
    }

    companion object {
        private const val MINUTES_TTL = 30L // 30 minutes cache TTL
        private const val DUMMY_ID = 0L
    }
}