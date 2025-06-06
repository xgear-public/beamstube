package com.awebo.ytext.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.awebo.ytext.model.Topic
import com.awebo.ytext.model.Video
import com.awebo.ytext.util.toFormattedString
import com.awebo.ytext.ytapi.YTViewModel
import org.jetbrains.compose.ui.tooling.preview.Preview
import java.time.Duration
import java.time.Instant

@Composable
fun TopicsScreen(viewModel: YTViewModel) {
    val topics by viewModel.uiState.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
    ) {
        topics.topics.forEach {
            Topic(
                topic = it,
                onVideoClick = { videoId ->
                    viewModel.onVideoClick(videoId)
                },
                onVideoRemove = { topic, video ->
                    viewModel.onVideoRemove(topic, video)
                },
            )
        }
    }
}

@Composable
fun Topic(topic: Topic, onVideoClick: (String) -> Unit, onVideoRemove: (Topic, Video) -> Unit) {
    LazyRow(
        modifier = Modifier
            .background(topic.color)
            .padding(16.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = topic.videos, key = { video -> video.id }) { video ->
            Column(
                modifier = Modifier.width(250.dp).clickable { onVideoClick(video.id) },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                        model = video.thumbnailUrl,
                        contentDescription = null
                    )
                    IconButton(
                        modifier = Modifier
                            .align(Alignment.TopEnd),
                        onClick = { onVideoRemove(topic, video) }) {
                        Icon(
                            rememberVectorPainter(image = Icons.Filled.Delete),
                            contentDescription = "Remove video",
                            tint = Color.White
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        modifier = Modifier.align(Alignment.TopStart),
                        text = video.publishedAt.toFormattedString(),
                        style = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        textAlign = TextAlign.Start
                    )
                    Text(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(Color.Red)
                            .padding(horizontal = 4.dp),
                        text = video.duration.toFormattedString(),
                        style = TextStyle(
                            fontSize = 22.sp,
                            color = Color.White,
                        ),
                        textAlign = TextAlign.Start
                    )
                }
                Text(
                    text = video.title,
                    style = TextStyle(
                        lineHeight = 16.sp,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Preview
@Composable
fun TopicPreview() {
    Topic(
        topic = Topic(
            0L,
            "TEST",
            buildList {
                Video(
                    "sd",
                    "Test Video",
                    "Test Desc",
                    "https://i.ytimg.com/vi/4Ld-b_SPzUs/mqdefault.jpg",
                    Instant.now(),
                    Duration.ofHours(1),
                    false,
                )
            },
            Color.Red,
            0
        ),
        onVideoClick = {},
        onVideoRemove = { _, _ -> })
}