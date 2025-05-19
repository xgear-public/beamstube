package com.awebo.ytext.ytapi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awebo.ytext.model.Topic
import com.awebo.ytext.model.Video
import com.awebo.ytext.openUrl
import com.awebo.ytext.ytapi.UiState.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import ytext.composeapp.generated.resources.Res
import ytext.composeapp.generated.resources.topics_updating

class YTViewModel(private val ytRepository: YTRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUIState(emptyList()))
    val uiState: StateFlow<DashboardUIState> = _uiState.asStateFlow()

    init {
        loadAllTopics(true)
    }

    fun loadAllTopics(cleanUp: Boolean = false) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (cleanUp) {
                    ytRepository.deleteOldVideos()
                }
                val topics = ytRepository.loadAllTopics()
                _uiState.value = DashboardUIState(topics)
            }
        }
    }

    /**
     * @param topics - new topics order
     * @param topicsToRemove - topics to remove
     */
    fun addTopic(title: String, topicChannel: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val channels = topicChannel.split(",")
                ytRepository.createTopic(title, channels)
                loadAllTopics()
            }
        }
    }

    fun onVideoClick(videoId: String) {
        openUrl("https://www.youtube.com/watch?v=$videoId")
    }

    fun reloadAllTopics() {
        viewModelScope.launch {
            val message = getString(Res.string.topics_updating)
            _uiState.update { state ->
                state.copy(uiState = Toast(message))
            }
            withContext(Dispatchers.IO) {
                val topics = ytRepository.reloadAllTopics()
                _uiState.value = DashboardUIState(topics)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun startReorderTopics() {
        _uiState.value = DashboardUIState(_uiState.value.topics, uiState = UiState.Reorder)
    }

    fun onAddTopicClick() {
        _uiState.value = DashboardUIState(_uiState.value.topics, uiState = UiState.AddTopic)
    }

    fun closeDialog() {
        _uiState.update { state ->
            state.copy(uiState = null)
        }
    }

    /**
     * @param topics - new topics order
     * @param topicsToRemove - topics to remove
     */
    fun onTopicsUpdated(topics: List<Topic>, topicsToRemove: List<Topic>) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ytRepository.deleteTopics(topicsToRemove)
                ytRepository.updateTopicsOrder(topics, topicsToRemove)
                loadAllTopics()
            }
        }
    }

    fun onVideoRemove(topic: Topic, videoToRemove: Video) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                ytRepository.markVideoWatched(videoToRemove.copy(watched = true))
                _uiState.update { state ->
                    val updatedTopic = state.topics.find { it.id == topic.id }?.copy(
                        videos = topic.videos.filter { video ->
                            video.id != videoToRemove.id
                        }
                    )
                    state.copy(
                        topics =
                        if (updatedTopic?.videos?.isNotEmpty() == true) {
                            state.topics.map {
                                if (it.id == topic.id) {
                                    updatedTopic
                                } else {
                                    it
                                }
                            }
                        } else {
                            state.topics.filter { it.id != topic.id }
                        }
                    )
                }
            }
        }
    }

}
