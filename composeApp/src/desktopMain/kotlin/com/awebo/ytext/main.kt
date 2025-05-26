package com.awebo.ytext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.awebo.ytext.di.initKoin
import com.awebo.ytext.ui.vm.DashboardUIState
import com.awebo.ytext.ui.vm.ReorderViewModel
import com.awebo.ytext.ui.vm.UiState
import com.awebo.ytext.ui.vm.YTViewModel
import com.awebo.ytext.util.toFormattedString
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel
import ytext.composeapp.generated.resources.Res
import ytext.composeapp.generated.resources.tray_icon_64
import java.awt.Desktop

fun main() = application {
    initKoin {}

    setupMacAboutMenu()
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
            updateReloadedStatus(uiState)
            Menu(viewModel)
            Alert(uiState, viewModel)
            Summarize(uiState, viewModel)
            AddTopic(uiState, viewModel)
            Reorder(uiState, viewModel)
        }
    }
}

private fun FrameWindowScope.updateReloadedStatus(uiState: DashboardUIState) {
    uiState.lastReload?.let { it ->
        window.title = "YTExt - ${it.toFormattedString()}"
    }
}

@Composable
private fun FrameWindowScope.Reorder(
    uiState: DashboardUIState,
    viewModel: YTViewModel
) {
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

@Composable
private fun FrameWindowScope.AddTopic(
    uiState: DashboardUIState,
    viewModel: YTViewModel
) {
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
}


@Composable
private fun Summarize(uiState: DashboardUIState, viewModel: YTViewModel) {
    SummarizeWindow(
        visible = uiState.uiState?.let { it is UiState.Summarize } == true,
        summarizeText =
            if (uiState.uiState != null && uiState.uiState is UiState.Summarize)
                uiState.uiState.text
            else
                "",
        onCloseRequest = {
            viewModel.closeDialog()
        },
        videoTitle =
            if (uiState.uiState != null && uiState.uiState is UiState.Summarize)
                uiState.uiState.videoTitle
            else
                ""
    )
}

@Composable
private fun Alert(
    uiState: DashboardUIState,
    viewModel: YTViewModel
) {
    AlertWindow(
        visible = uiState.uiState?.let { it is UiState.Toast } == true,
        alertText =
            if (uiState.uiState != null && uiState.uiState is UiState.Toast)
                uiState.uiState.message
            else
                ""
    ) {
        viewModel.closeDialog()
    }
}


@Composable
private fun FrameWindowScope.Menu(viewModel: YTViewModel) {
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
}

fun setupMacAboutMenu() {
    if (Desktop.isDesktopSupported()) {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler {
                // Use Compose's logic or just show a Swing dialog
                javax.swing.JOptionPane.showMessageDialog(
                    null,
                    "Version 1.0",
                    "About",
                    javax.swing.JOptionPane.INFORMATION_MESSAGE
                )
            }
        }
    }
}