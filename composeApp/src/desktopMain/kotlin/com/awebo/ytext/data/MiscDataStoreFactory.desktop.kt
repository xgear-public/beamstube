package com.awebo.ytext.data

import java.io.File

actual class MiscDataStoreFactory actual constructor() {
    actual fun createMiscDataStore(): MiscDataStore {
        val file = File(System.getProperty("java.io.tmpdir"), "misc.txt")
        val miscDataStore = MiscDataStore { file.absolutePath }
        return miscDataStore
    }
}