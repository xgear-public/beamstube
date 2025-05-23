package com.awebo.ytext.data

import androidx.compose.ui.graphics.Color
import com.awebo.ytext.model.Topic
import com.awebo.ytext.model.Video
import com.awebo.ytext.util.toFormattedString
import com.awebo.ytext.ytapi.WEEK_IN_SECONDS
import java.time.Instant

class VideosRepository(
	val miscDataStore: MiscDataStore,
    val videoDao: VideoDao,
    val dataSources: Map<String, VideoDataSource>,
) {

    private val defaultDataSource = dataSources["youtube"]!!

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
        val loadedChannelEntities: List<ChannelEntity> = channelHandleList.map { handle ->
            // Try each data source until we find one that can handle this channel
            for (dataSource in dataSources.values) {
                val channelId = dataSource.getChannelId(handle)
                if (channelId != null) {
                    return@map ChannelEntity(
                        id = "${dataSource.platformName}:$channelId",
                        handle = handle,
                        lastUpdated = Instant.ofEpochMilli(0),
                        topicId = 0,
                        sourcePlatform = dataSource.platformName
                    )
                }
            }
            println("No channel found with handle: $handle")
            return emptyList()
        }
        if (loadedChannelEntities.size == channelHandleList.size) {
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

    private suspend fun updateChannelVideosByChannelId(channelId: String): List<Video> {
        println("updateChannelVideosByChannelId started for $channelId")

        val channelVideos = videoDao.getChannelVideosByChannelId(channelId)
        channelVideos?.let {
            if (channelVideos.channel.lastUpdated.isAfter(Instant.now().minusSeconds(MINUTES_TTL * 60))) {
                println("skipping update, channel last updated less than $MINUTES_TTL min ago")
                return channelVideos.videos.map(Video.Companion::fromEntity)
            }
        }

        // Extract platform from channel ID (format: "platform:channelId")
        val (platform, actualChannelId) = if (channelId.contains(':')) {
            val parts = channelId.split(':', limit = 2)
            parts[0] to parts[1]
        } else {
            // For backward compatibility with existing data
            defaultDataSource.platformName to channelId
        }

        val dataSource = dataSources[platform.lowercase()] ?: defaultDataSource
        val loadedVideos = loadAndSaveVideosByChannelId(actualChannelId, dataSource)

        videoDao.updateChannelLastUpdated(channelId, Instant.now())

        // Update the channel's last updated time
//        if (channelVideos != null) {
//            val updatedChannel = channelVideos.channel.copy(
//                lastUpdated = Instant.now()
//            )
//            videoDao.updateChannelLastUpdated(updatedChannel)
//        }

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
            video.toEntity("${dataSource.platformName}:$channelId")
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
                updateChannelVideosByChannelId(channel.channel.id)
            }
        }

        println("reloadAllTopics stopped 1: ${Instant.now().toFormattedString()}")
        val loadAllTopics = loadAllTopics()

        val lastReload = miscDataStore.lastReload()
        println("lastReload: ${lastReload.toFormattedString()}")
        miscDataStore.update(Instant.now())

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

    companion object {
        private const val MINUTES_TTL = 30L // 30 minutes cache TTL
        private const val DUMMY_ID = 0L

        const val YT_API_KEY = "AIzaSyBkBcs6tOHKi7Q9_GrPNCJA1TVSBtoSGvs"
        const val YT_APP_NAME = "YTExt"
        const val MAX_RESULTS_PER_PAGE_SDK = 50L // Max results per page (use Long for SDK methods)
    }
}