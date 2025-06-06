@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.awebo.ytext.data

import androidx.compose.ui.graphics.Color
import androidx.room.*
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.time.Duration
import java.time.Instant

@Database(
    entities = [VideoEntity::class, ChannelEntity::class, TopicEntity::class],
    version = 1
)
@TypeConverters(InstantTypeConverter::class, ColorTypeConverter::class, DurationTypeConverter::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getDao(): VideoDao
}

// The Room compiler generates the `actual` implementations.
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

@Dao
interface VideoDao {

    // video methods

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: VideoEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertList(items: List<VideoEntity>)

    @Query("DELETE FROM video WHERE publishedAt < :timestamp")
    suspend fun deleteOldVideos(timestamp: Long)

    @Query("DELETE FROM video WHERE id = :videoId")
    suspend fun deleteVideo(videoId: String)

    @Query("UPDATE video SET watched = :watched WHERE id = :videoId")
    suspend fun markVideoWatched(videoId: String, watched: Boolean)



    // channel methods

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ChannelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: List<ChannelEntity>)

    @Transaction
    @Query("SELECT * FROM channel WHERE handle = :handle")
    suspend fun getChannelVideos(handle: String): ChannelVideos?

    @Transaction
    @Query("SELECT * FROM channel WHERE id = :channelId")
    suspend fun getChannelVideosByChannelId(channelId: String): ChannelVideos?



    // topic methods

    @Query("SELECT * FROM topic ORDER BY `order` DESC LIMIT 1")
    suspend fun getTopicWithHighestOrder(): TopicEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TopicEntity): Long

    @Update
    suspend fun updateTopics(topics: List<TopicEntity>)

//    @Query("DELETE FROM topic WHERE id = :topicId")
//    suspend fun deleteTopic(topicId: Long)

    @Transaction
    @Query("SELECT * FROM topic WHERE id = :topicId")
    suspend fun getTopicChannelsWithVideos(topicId: Long): TopicChannelsWithVideos?

    @Transaction
    @Query("SELECT * FROM topic")
    suspend fun getAllTopicsWithChannelsAndVideos(): List<TopicChannelsWithVideos>

    @Query("DELETE FROM topic WHERE id IN (:topicsIds)")
    suspend fun deleteTopics(topicsIds: List<Long>)

    @Query("SELECT * FROM topic ORDER BY `order` ASC")
    suspend fun getAllTopics(): List<TopicEntity>

}


@Entity(
    tableName = "video",
    foreignKeys = [
        ForeignKey(
            entity = ChannelEntity::class,
            parentColumns = ["id"],
            childColumns = ["channelEntityId"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
data class VideoEntity(
    @PrimaryKey(autoGenerate = false) val id: String,
    val title: String,
    val channelEntityId: String,
    val description: String,
    val thumbnailUrl: String,
    val publishedAt: Instant,
    val duration: Duration,
    val watched: Boolean
)

@Entity(
    tableName = "channel",
    foreignKeys = [
        ForeignKey(
            entity = TopicEntity::class,
            parentColumns = ["id"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.CASCADE,
        )
    ]
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = false) val id: String,
    val handle: String,
    val lastUpdated: Instant,
    val topicId: Long,
)

@Entity(tableName = "topic")
data class TopicEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val title: String,
    val color: Color,
    val order: Int
)


data class ChannelVideos(
    @Embedded
    val channel: ChannelEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "channelEntityId"
    )
    val videos: List<VideoEntity>
) {
    fun exists() = videos.isNotEmpty()
}

data class TopicChannelsWithVideos(
    @Embedded
    val topic: TopicEntity,
    @Relation(
        entity = ChannelEntity::class,
        parentColumn = "id",
        entityColumn = "topicId",
    )
    val channels: List<ChannelVideos>
)


fun getRoomDatabase(
    builder: RoomDatabase.Builder<AppDatabase>
): AppDatabase {
    return builder
//        .addMigrations(MIGRATIONS)
//        .fallbackToDestructiveMigrationOnDowngrade()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

expect fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>

class InstantTypeConverter {
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Instant?): Long? {
        return date?.toEpochMilli()
    }
}

class ColorTypeConverter {
    @TypeConverter
    fun fromColor(color: Color): String {
        return color.value.toString()
    }

    @TypeConverter
    fun toColor(value: String): Color {
        return Color(value.toULong(10))
    }
}

class DurationTypeConverter {
    @TypeConverter
    fun fromDuration(duration: Duration): Long {
        return duration.toMillis()
    }

    @TypeConverter
    fun toDuration(value: Long): Duration {
        return Duration.ofMillis(value)
    }
}
