package com.awebo.ytext.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun Instant.toFormattedString(): String =
    DateTimeFormatter.ofPattern("HH:mm dd.MM")
//        .ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault()).format(this)


fun java.time.Duration.toFormattedString(): String {
    val seconds = this.seconds
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainingSeconds = seconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, remainingSeconds)
    } else {
        String.format("%02d:%02d", minutes, remainingSeconds)
    }
}
