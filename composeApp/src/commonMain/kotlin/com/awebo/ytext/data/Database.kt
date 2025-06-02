@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.awebo.ytext.data

import androidx.compose.ui.graphics.Color
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import java.time.Duration
import java.time.Instant

/**
 *
 * db versions
 *
 * 1 - initial state
 * 2 - add sourcePlatform to video and channel tables
 * 
 */
@Database(
    entities = [VideoEntity::class, ChannelEntity::class, TopicEntity::class],
    version = 2
)
@TypeConverters(InstantTypeConverter::class, ColorTypeConverter::class, DurationTypeConverter::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun getDao(): VideoDao
}

// The Room compiler generates the `actual` implementations.
@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

fun getRoomDatabase(
    builder: RoomDatabase.Builder<AppDatabase>
): AppDatabase {
    return builder
        .addMigrations(MIGRATION_1_2)
//        .fallbackToDestructiveMigrationOnDowngrade()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

expect fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>

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

    @Query("SELECT COUNT(*) FROM video")
    suspend fun getVideosCount(): Long

    @Query("UPDATE video SET watched = 1 WHERE id IN (:videoIds)")
    suspend fun markVideosAsWatched(videoIds: List<String>)

    @Query("SELECT * FROM video WHERE watched = 1 AND publishedAt >= :timestamp ORDER BY publishedAt DESC")
    fun getWatchedVideosLastNDays(timestamp: Long): Flow<List<VideoEntity>>



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

    @Query("UPDATE channel SET lastUpdated = :lastUpdated WHERE id = :channelId")
    suspend fun updateChannelLastUpdated(channelId: String, lastUpdated: Instant)

    @Query("DELETE FROM channel WHERE id IN (:channelIds)")
    suspend fun deleteChannels(channelIds: List<String>)
    
    @Query("DELETE FROM channel WHERE topicId = :topicId")
    suspend fun deleteChannelsByTopicId(topicId: Long)



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
    suspend fun getAllTopics(): List<TopicChannelsWithVideos>

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
    val watched: Boolean,
    val sourcePlatform: VideoPlatform = VideoPlatform.YOUTUBE // Default to YouTube for backward compatibility
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
    val sourcePlatform: VideoPlatform = VideoPlatform.YOUTUBE // Default to YouTube for backward compatibility
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

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
                // Add sourcePlatform column to video table with default value 'YOUTUBE'
        connection.execSQL("""
            ALTER TABLE video 
            ADD COLUMN sourcePlatform TEXT NOT NULL DEFAULT 'YOUTUBE'
        """.trimIndent())

        // Add sourcePlatform column to channel table with default value 'YOUTUBE'
        connection.execSQL("""
            ALTER TABLE channel 
            ADD COLUMN sourcePlatform TEXT NOT NULL DEFAULT 'YOUTUBE'
        """.trimIndent())
    }

}

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
