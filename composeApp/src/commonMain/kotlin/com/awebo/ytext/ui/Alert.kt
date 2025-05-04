package com.awebo.ytext.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun Alert(modifier: Modifier = Modifier, alertText: String, onDismiss: () -> Unit) {
    Column(modifier.padding(8.dp)) {
        Text(alertText)
        Button(modifier = Modifier.padding(8.dp), onClick = { onDismiss() }) {
            Text("Close")
        }
    }
}