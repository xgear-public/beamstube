package com.awebo.ytext

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.rememberDialogState
import com.awebo.ytext.model.Topic
import com.awebo.ytext.ui.Reorder
import com.awebo.ytext.ytapi.ReorderViewModel
import org.jetbrains.compose.resources.stringResource
import ytext.composeapp.generated.resources.Res
import ytext.composeapp.generated.resources.reorder_topics

@Composable
fun FrameWindowScope.ReorderWindow(
    visible: Boolean,
    reorderViewModel: ReorderViewModel,
    reorderAction: (List<Topic>, List<Topic>) -> Unit,
    onCloseRequest: () -> Unit
) {
    if (visible) DialogWindow(
        state = rememberDialogState(height = 600.dp),
        onCloseRequest = onCloseRequest,
        resizable = false,
        title = stringResource(Res.string.reorder_topics)
    ) {
        Reorder(modifier = Modifier.fillMaxSize(), reorderViewModel = reorderViewModel, reorderAction = reorderAction)
    }
}