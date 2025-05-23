package com.awebo.ytext

import YTExt.composeApp.BuildConfig
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import com.awebo.ytext.ui.TopicsScreen
import com.awebo.ytext.ytapi.YTViewModel
import org.koin.compose.KoinContext
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App(platformContent: @Composable (YTViewModel) -> Unit = {}) {
    println("is release mode: ${BuildConfig.IS_RELEASE_MODE}")
    KoinContext {
        val viewModel: YTViewModel = koinViewModel()
        MaterialTheme {
            platformContent(viewModel)
            TopicsScreen(viewModel)
        }
    }
}