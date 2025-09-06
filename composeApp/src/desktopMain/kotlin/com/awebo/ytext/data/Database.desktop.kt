package com.awebo.ytext.data

actual fun getDbPath(): String {
    return getDesktopDBPath().toAbsolutePath().toString()
}