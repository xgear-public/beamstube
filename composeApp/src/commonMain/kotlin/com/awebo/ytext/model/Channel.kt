package com.awebo.ytext.model

import com.awebo.ytext.data.VideoPlatform
import java.time.Instant

data class Channel(
    val id: String,
    val handle: String,
    val lastUpdated: Instant,
    val sourcePlatform: VideoPlatform = VideoPlatform.YOUTUBE // Default to YouTube for backward compatibility
)