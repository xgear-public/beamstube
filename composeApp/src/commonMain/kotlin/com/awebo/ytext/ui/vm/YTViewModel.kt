package com.awebo.ytext.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awebo.ytext.data.MiscDataStore
import com.awebo.ytext.data.SummarizationEntity
import com.awebo.ytext.data.VideosRepository
import com.awebo.ytext.model.Topic
import com.awebo.ytext.model.TopicChangeRequest
import com.awebo.ytext.model.Video
import com.awebo.ytext.openUrl
import com.awebo.ytext.ytapi.YouTubeTranscriptSummarizer
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
import java.time.Instant

class YTViewModel(
    private val videosRepository: VideosRepository,
    private val miscDataStore: MiscDataStore,
    private val summarizer: YouTubeTranscriptSummarizer // This is a singleton
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUIState(emptyList()))
    val uiState: StateFlow<DashboardUIState> = _uiState.asStateFlow()

    init {
        loadAllTopics(true)
    }

    fun loadAllTopics(cleanUp: Boolean = false) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (cleanUp) {
                    videosRepository.deleteOldVideos()
                }
                val topics = videosRepository.loadAllTopics()
                _uiState.value = DashboardUIState(topics)
            }
        }
    }

    fun addTopic(title: String, channels: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                videosRepository.createTopic(title, channels)
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
                state.copy(uiState = UiState.Toast(message))
            }
            withContext(Dispatchers.IO) {
                val topics = videosRepository.reloadAllTopics()
                _uiState.value = DashboardUIState(topics, miscDataStore.lastReload())
            }
        }
    }

    fun startSettings() {
        _uiState.value = DashboardUIState(_uiState.value.topics, uiState = UiState.Settings)
    }

    fun startReorderTopics() {
        _uiState.value = DashboardUIState(_uiState.value.topics, uiState = UiState.Reorder)
    }

    fun onAddTopicClick() {
        _uiState.value = DashboardUIState(_uiState.value.topics, uiState = UiState.AddTopic)
    }

    fun onHistoryClick() {
        _uiState.value = DashboardUIState(_uiState.value.topics, uiState = UiState.History)
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
                videosRepository.deleteTopics(topicsToRemove)
                videosRepository.updateTopicsOrder(topics, topicsToRemove)
                loadAllTopics()
            }
        }
    }

    fun onVideoRemove(topic: Topic, videoToRemove: Video) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                videosRepository.markVideoWatched(videoToRemove.copy(watched = true))
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

    private var isSummarizing = false
    fun onSummarize(video: Video) {
        if (isSummarizing) { // Check if already summarizing
            _uiState.update { state ->
                state.copy(
                    uiState = UiState.Toast("Waiting for previous summarization to finish")
                )
            }
            return // Exit early
        }

        isSummarizing = true // Set flag before starting
        _uiState.update { state ->
            state.copy(
                uiState = UiState.Toast("Summarizing...")
            )
        }

        viewModelScope.launch {
            try {
                // Check cache first
                val languageCode = miscDataStore.getLanguage().code
                val cachedSummary = withContext(Dispatchers.IO) {
                    videosRepository.getSummarization(video.id, languageCode)
                }

                if (cachedSummary != null) {
                    _uiState.update { state ->
                        state.copy(
                            uiState = UiState.Summarize(cachedSummary.summaryText, video.title)
                        )
                    }
                } else {
                    val summarizedText = withContext(Dispatchers.IO) {
                        summarizer.summarizeVideo("https://www.youtube.com/watch?v=${video.id}")
                    }

                    if (summarizedText != null) {
                        // Save to cache
                        withContext(Dispatchers.IO) {
                            videosRepository.saveSummarization(
                                SummarizationEntity(
                                    videoId = video.id,
                                    summaryText = summarizedText,
                                    language = languageCode,
                                    timestamp = Instant.now()
                                )
                            )
                        }
                        _uiState.update { state ->
                            state.copy(
                                uiState = UiState.Summarize(summarizedText, video.title)
                            )
                        }
                    } else {
                        // Optionally handle the case where summarization returns null (e.g., failed)
                        _uiState.update { state ->
                            state.copy(
                                uiState = UiState.Toast("Summarization failed.")
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // It's good practice to log the exception and update UI accordingly
                // logger.error("Error during summarization", e) // If you have a logger in ViewModel
                _uiState.update { state ->
                    state.copy(
                        uiState = UiState.Toast("An error occurred during summarization.")
                    )
                }
            } finally {
                isSummarizing = false // Reset the flag in a finally block
            }
        }
    }
}