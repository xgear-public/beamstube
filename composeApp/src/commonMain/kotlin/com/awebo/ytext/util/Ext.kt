package com.awebo.ytext.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun Instant.toFormattedString(): String =
    DateTimeFormatter.ofPattern("HH:mm dd.MM")
//        .ofLocalizedDateTime(FormatStyle.SHORT, FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault()).format(this)