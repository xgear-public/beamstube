package com.awebo.ytext

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import com.awebo.ytext.ui.TopicsScreen
import com.awebo.ytext.ytapi.YTViewModel
import org.koin.compose.KoinContext
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App(platformContent: @Composable (YTViewModel) -> Unit = {}) {
    KoinContext {
        val viewModel: YTViewModel = koinViewModel()
        MaterialTheme {
            platformContent(viewModel)
            TopicsScreen(viewModel)
        }
    }
}