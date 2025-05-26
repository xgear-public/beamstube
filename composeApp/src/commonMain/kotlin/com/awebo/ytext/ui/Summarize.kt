package com.awebo.ytext.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
            text = videoTitle
        )
        Text(
            modifier = Modifier.widthIn(min = 600.dp, max = 800.dp),
            text = summarizeText
        )
        Button(
            onClick = { onDismiss() }) {
            Text("Close")
        }
    }
}