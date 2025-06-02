package com.awebo.ytext

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.awebo.ytext.ui.History
import com.awebo.ytext.ui.vm.HistoryViewModel
import org.jetbrains.compose.resources.stringResource
import ytext.composeapp.generated.resources.Res
import ytext.composeapp.generated.resources.history

@Composable
fun HistoryWindow(
    visible: Boolean,
    historyViewModel: HistoryViewModel,
    onVideoClick: (String) -> Unit,
    onCloseRequest: () -> Unit,
) {
    if (visible) DialogWindow(
        state = rememberDialogState(width = Dp.Unspecified, height = Dp.Unspecified),
        onCloseRequest = onCloseRequest,
        resizable = false,
        title = stringResource(Res.string.history)
    ) {
        History(
            modifier = Modifier.wrapContentSize(),
            historyViewModel = historyViewModel,
            onVideoClick = onVideoClick
        )
    }
}