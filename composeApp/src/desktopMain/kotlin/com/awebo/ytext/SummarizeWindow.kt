package com.awebo.ytext

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.awebo.ytext.ui.Summarize
import org.jetbrains.compose.resources.stringResource
import ytext.composeapp.generated.resources.Res
import ytext.composeapp.generated.resources.alert
import ytext.composeapp.generated.resources.video_summarization

@Composable
fun SummarizeWindow(
    visible: Boolean,
    summarizeText: String,
    videoTitle: String,
    onCloseRequest: () -> Unit
) {
    if (visible) DialogWindow(
        state = rememberDialogState(width = Dp.Unspecified, height = Dp.Unspecified),
        onCloseRequest = onCloseRequest,
        resizable = true,
        title = stringResource(Res.string.video_summarization)
    ) {
        Summarize(
            modifier = Modifier.wrapContentSize(),
            summarizeText = summarizeText,
            videoTitle = videoTitle,
            onDismiss = onCloseRequest
        )
    }
}