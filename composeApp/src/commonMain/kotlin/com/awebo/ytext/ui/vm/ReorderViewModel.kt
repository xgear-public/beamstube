package com.awebo.ytext.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awebo.ytext.data.VideosRepository
import com.awebo.ytext.model.Topic
import com.awebo.ytext.model.TopicChangeRequest
import com.awebo.ytext.model.TopicManagable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReorderViewModel(
    private val videosRepository: VideosRepository
) : ViewModel() {

    private val _topics = MutableStateFlow<List<TopicManagable>>(emptyList())
    val topics: StateFlow<List<TopicManagable>> = _topics.asStateFlow()

    init {
        loadTopics()
    }

    fun loadTopics() {
        viewModelScope.launch {
            val allTopics = videosRepository.getAllTopics()
            _topics.value = allTopics
        }
    }

    fun manageTopic(topicChangeRequest: TopicChangeRequest) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                videosRepository.manageTopic(topicChangeRequest)
            }
        }
    }

}