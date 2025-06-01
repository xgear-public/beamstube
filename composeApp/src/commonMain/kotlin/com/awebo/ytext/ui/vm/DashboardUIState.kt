package com.awebo.ytext.ui.vm

import com.awebo.ytext.model.Topic
import java.time.Instant

data class DashboardUIState(
    val topics: List<Topic>,
    val lastReload: Instant? = null,
    val uiState: UiState? = null
)

sealed class UiState{
    data object Reorder : UiState()
    data object AddTopic : UiState()
    data object Settings : UiState()
    data class Toast(val message: String) : UiState()
    data class Summarize(val text: String, val videoTitle: String) : UiState()
}
