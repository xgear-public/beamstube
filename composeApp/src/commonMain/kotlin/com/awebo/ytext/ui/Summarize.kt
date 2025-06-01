package com.awebo.ytext.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.text.Regex

@Composable
fun Summarize(
    modifier: Modifier = Modifier,
    summarizeText: String,
    videoTitle: String,
    onDismiss: () -> Unit,
) {
    Column(
        modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = videoTitle,
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
        )
        Text(
            modifier = Modifier.widthIn(min = 600.dp, max = 800.dp),
            text = buildAnnotatedString {
                val pattern = Regex("\\*\\*(.*?)\\*\\*")
                var lastIndex = 0

                pattern.findAll(summarizeText).forEach { matchResult ->
                    // Add text before the match
                    append(summarizeText.substring(lastIndex, matchResult.range.first))

                    // Add the matched text (without **) with bold style
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(matchResult.groupValues[1])
                    pop()

                    lastIndex = matchResult.range.last + 1
                }
                // Add remaining text
                if (lastIndex < summarizeText.length) {
                    append(summarizeText.substring(lastIndex))
                }
            },
            style = TextStyle(
                fontSize = 19.sp,
            )

        )
        Button(
            onClick = { onDismiss() }) {
            Text("Close")
        }
    }
}