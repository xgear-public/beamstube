package com.awebo.ytext

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.awebo.ytext.ui.Alert
import org.jetbrains.compose.resources.stringResource
import ytext.composeapp.generated.resources.Res
import ytext.composeapp.generated.resources.alert

@Composable
fun AlertWindow(visible: Boolean, alertText: String, onCloseRequest: () -> Unit) {
    if (visible) DialogWindow(
        state = rememberDialogState(width = Dp.Unspecified, height = Dp.Unspecified),
        onCloseRequest = onCloseRequest,
        resizable = false,
        title = stringResource(Res.string.alert)
    ) {
        Alert(modifier = Modifier.wrapContentSize(), alertText = alertText, onDismiss = onCloseRequest)
    }
}