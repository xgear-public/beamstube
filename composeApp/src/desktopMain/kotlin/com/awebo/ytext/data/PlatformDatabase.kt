package com.awebo.ytext.data

import YTExt.composeApp.BuildConfig
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    @Suppress("KotlinConstantConditions")
    val dbFileName =  if (BuildConfig.IS_RELEASE_MODE) "beam_tube_release.db" else "beam_tube.db"
    val dbFile = File(System.getProperty("java.io.tmpdir"), dbFileName)
    println(dbFile.absolutePath)
    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePath,
    )
}