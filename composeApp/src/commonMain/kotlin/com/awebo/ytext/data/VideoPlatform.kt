package com.awebo.ytext.data

enum class VideoPlatform(name: String) {
    YOUTUBE("youtube"),
    UNKNOWN("unknown"),
    ;

    fun getByName(name: String): VideoPlatform {
        return VideoPlatform.entries.firstOrNull() { it.name == name } ?: UNKNOWN
    }
}