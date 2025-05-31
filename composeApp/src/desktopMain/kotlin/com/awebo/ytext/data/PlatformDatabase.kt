package com.awebo.ytext.data

import YTExt.composeApp.BuildConfig
import androidx.room.Room
import androidx.room.RoomDatabase
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbPath = getDBPath()
    return Room.databaseBuilder<AppDatabase>(
        name = dbPath.toAbsolutePath().toString(),
    )
}

private fun getDBPath(): Path {
    val userHome = System.getProperty("user.home")
    val appName = "YouTubeams" // Replace with your app's name
    val appSupportDir = Paths.get(userHome, "Library", "Application Support", appName)
    val databaseDir = appSupportDir.resolve("database") // Or directly use appSupportDir if you prefer

    if (!Files.exists(databaseDir)) {
        try {
            Files.createDirectories(databaseDir)
            println("Database directory created at: $databaseDir")
        } catch (e: Exception) {
            println("Error creating database directory: ${e.message}")
            // Handle the error appropriately
        }
    }

    @Suppress("KotlinConstantConditions")
    val dbFileName =  if (BuildConfig.IS_RELEASE_MODE) "beam_tube_release.db" else "beam_tube.db"

    val databaseFile = databaseDir.resolve(dbFileName) // Or your preferred DB file name
    println("Database file path: $databaseFile")
    return databaseFile
}