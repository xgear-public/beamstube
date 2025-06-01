package com.awebo.ytext

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.awebo.ytext.ui.Settings
import com.awebo.ytext.ui.vm.SettingsViewModel
import org.jetbrains.compose.resources.stringResource
import ytext.composeapp.generated.resources.Res
import ytext.composeapp.generated.resources.settings

@Composable
fun SettingsWindow(visible: Boolean, settingsViewModel: SettingsViewModel, onCloseRequest: () -> Unit) {
    if (visible) DialogWindow(
        state = rememberDialogState(height = 600.dp, width = 600.dp),
        onCloseRequest = onCloseRequest,
        resizable = false,
        title = stringResource(Res.string.settings)
    ) {
        Settings(modifier = Modifier.wrapContentSize(), settingsViewModel, onDismiss = onCloseRequest)
    }
}