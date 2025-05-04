package com.awebo.ytext.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AddTopic(modifier: Modifier = Modifier, addTopicAction: (String, String) -> Unit) {
    var topicTitle by remember { mutableStateOf("") }
    var searchInput by remember { mutableStateOf("") }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextField(
            value = topicTitle,
            onValueChange = { topicTitle = it },
            label = { Text("Topic name") }
        )
        TextField(
            value = searchInput,
            onValueChange = { searchInput = it },
            label = { Text("Topic channels") }
        )
        Button(
            onClick = {
                addTopicAction(topicTitle, searchInput)
                topicTitle = ""
                searchInput = ""
            }) {
            Text("ADD TOPIC")
        }
    }
}
