package com.awebo.ytext.data

import androidx.datastore.core.okio.OkioSerializer
import okio.BufferedSink
import okio.BufferedSource
import okio.use
import java.time.Instant
import java.util.prefs.Preferences

//@Serializable
data class LastReload(
    val timestamp: Instant,
)

class MiscDataStore(
    private val produceFilePath: () -> String,
) {

    private val prefs: Preferences = Preferences.userNodeForPackage(MiscDataStore::class.java)
    private val INSTANT_KEY = "last_saved_instant_epoch_millis_v2"


    fun update(
        newReloadTime: Instant,
    ) {
        prefs.putLong(INSTANT_KEY, newReloadTime.toEpochMilli())
        prefs.flush()
    }

    fun lastReload(): Instant {
        val epochMillis = prefs.getLong(INSTANT_KEY, -1L)
        return if (epochMillis != -1L) {
            Instant.ofEpochMilli(epochMillis)
        } else {
            Instant.EPOCH
        }
    }
}