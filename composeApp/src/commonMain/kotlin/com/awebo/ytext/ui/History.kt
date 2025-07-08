package com.awebo.ytext.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.awebo.ytext.ui.vm.HistoryViewModel

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun History(
    historyViewModel: HistoryViewModel,
    modifier: Modifier = Modifier,
    onVideoClick: (String) -> Unit,
) {
    val videoList by historyViewModel.videos.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = modifier.height(600.dp).width(300.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(videoList) { video ->
            VideoItem(video = video, onVideoClick = onVideoClick)
            Divider()
        }
    }
}
