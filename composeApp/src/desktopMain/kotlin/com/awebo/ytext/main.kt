package com.awebo.ytext

import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.awebo.ytext.di.initKoin
import com.awebo.ytext.ytapi.ReorderViewModel
import com.awebo.ytext.ui.UiState
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import ytext.composeapp.generated.resources.Res
import ytext.composeapp.generated.resources.tray_icon_64

fun main() = application {
    initKoin {}

    val trayIcon = painterResource(Res.drawable.tray_icon_64)
    Tray(
        icon = trayIcon,
        menu = {
            Item("Quit", onClick = ::exitApplication)
        }
    )
    Window(
        title = "YTExt",
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(width = 2000.dp, height = 1000.dp),
    ) {
        App { viewModel ->
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            MenuBar {
                Menu("Actions", mnemonic = 'A') {
                    Item(
                        "Add Topic",
                        onClick = { viewModel.onAddTopicClick() },
                        shortcut = KeyShortcut(Key.T, meta = true)
                    )
                    Item(
                        "Refresh",
                        onClick = { viewModel.reloadAllTopics() },
                        shortcut = KeyShortcut(Key.R, meta = true)
                    )
                    Item(
                        "Reorder Topics",
                        onClick = { viewModel.startReorderTopics() },
                        shortcut = KeyShortcut(Key.O, meta = true)
                    )
                }
            }
            AlertWindow(
                visible = uiState.uiState?.let { it is UiState.Toast } == true,
                alertText =
                    if (uiState.uiState != null && uiState.uiState is UiState.Toast)
                        (uiState.uiState as UiState.Toast).message
                    else
                        "",
                onCloseRequest = {
                    viewModel.closeDialog()
                }
            )
            AddTopicWindow(
                visible = uiState.uiState?.let { it is UiState.AddTopic } == true,
                addTopicAction = { title, channels ->
                    viewModel.closeDialog()
                    viewModel.addTopic(title, channels)
                },
                onCloseRequest = {
                    viewModel.closeDialog()
                }
            )
            ReorderWindow(
                visible = uiState.uiState?.let { it is UiState.Reorder } == true,
                reorderViewModel = koinViewModel<ReorderViewModel>(),
                reorderAction = { list, list2 ->
                    viewModel.onTopicsUpdated(list, list2)
                },
                onCloseRequest = {
                    viewModel.closeDialog()
                }
            )
        }
    }
}