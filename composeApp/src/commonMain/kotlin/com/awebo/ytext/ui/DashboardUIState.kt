package com.awebo.ytext.ui

import com.awebo.ytext.model.Topic

data class DashboardUIState(
    val topics: List<Topic>,
    val uiState: UiState? = null
)

sealed class UiState{
    data object Reorder : UiState()
    data object AddTopic : UiState()
    data class Toast(val message: String) : UiState()
}
