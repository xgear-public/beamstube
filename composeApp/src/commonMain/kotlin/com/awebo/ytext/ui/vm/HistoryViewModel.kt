package com.awebo.ytext.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awebo.ytext.data.VideosRepository
import com.awebo.ytext.model.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(private val videoRepo: VideosRepository) : ViewModel() {

    private val _videos: MutableStateFlow<List<Video>> = MutableStateFlow(emptyList())
    val videos: StateFlow<List<Video>> = _videos.asStateFlow()

    init {
        viewModelScope.launch {
            videoRepo.getWatchedVideosLast3Days().collect {
                _videos.value = it
            }
        }
    }

}