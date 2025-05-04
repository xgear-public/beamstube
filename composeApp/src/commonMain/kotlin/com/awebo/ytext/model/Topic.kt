package com.awebo.ytext.model

import androidx.compose.ui.graphics.Color

data class Topic(
    val id: Long,
    val title: String,
    val videos: List<Video>,
    val color: Color,
    val order: Int
)
