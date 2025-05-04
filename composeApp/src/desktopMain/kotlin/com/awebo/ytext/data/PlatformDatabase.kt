package com.awebo.ytext.data

import YTExt.composeApp.BuildConfig
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
//    val dbFile = File(System.getProperty("java.io.tmpdir"), "beam_tube_release.db")
    val dbFile = File(System.getProperty("java.io.tmpdir"), BuildConfig.DB_FILE_NAME)
    println(dbFile.absolutePath)
    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePath,
    )
}