package com.awebo.ytext.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import org.koin.java.KoinJavaComponent.inject


actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val context: Context by inject(Context::class.java)
    return Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "database"
    )

}