package com.awebo.ytext

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.rememberDialogState
import org.jetbrains.compose.resources.stringResource
import ytext.composeapp.generated.resources.Res
import ytext.composeapp.generated.resources.add_topic
import com.awebo.ytext.ui.AddTopic

@Composable
fun FrameWindowScope.AddTopicWindow(
    visible: Boolean,
    addTopicAction: (String, String) -> Unit,
    onCloseRequest: () -> Unit
) {
    if (visible) DialogWindow(
//        state = rememberDialogState(),
        state = rememberDialogState(width = 1000.dp, height = 200.dp),
        onCloseRequest = onCloseRequest,
        resizable = false,
        title = stringResource(Res.string.add_topic)
    ) {
        AddTopic(modifier = Modifier.fillMaxSize(), addTopicAction)
    }
}