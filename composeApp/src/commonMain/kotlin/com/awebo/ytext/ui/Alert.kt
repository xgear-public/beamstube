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
fun Alert(modifier: Modifier = Modifier, alertText: String, onDismiss: () -> Unit) {
    Column(
        modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            modifier = Modifier.widthIn(min = 200.dp, max = 750.dp),
            text = alertText
        )
        Button(
            onClick = { onDismiss() }) {
            Text("Close")
        }
    }
}