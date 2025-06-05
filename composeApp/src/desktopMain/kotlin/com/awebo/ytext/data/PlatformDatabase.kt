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
    val os = System.getProperty("os.name").lowercase()
    val appName = "YouTubeams"
    
    val appDataDir = when {
        os.contains("win") -> {
            // Windows: %APPDATA%\appName
            val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
            Paths.get(appData, appName)
        }
        os.contains("nix") || os.contains("nux") || os.contains("aix") -> {
            // Linux: $XDG_DATA_HOME/appName or ~/.local/share/appName
            val dataHome = System.getenv("XDG_DATA_HOME") 
                ?: Paths.get(System.getProperty("user.home"), ".local", "share").toString()
            Paths.get(dataHome, appName)
        }
        else -> {
            // macOS: ~/Library/Application Support/appName
            Paths.get(System.getProperty("user.home"), "Library", "Application Support", appName)
        }
    }
    
    val databaseDir = appDataDir.resolve("database")

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