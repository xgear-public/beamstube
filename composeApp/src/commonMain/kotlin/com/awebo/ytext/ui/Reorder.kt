package com.awebo.ytext.ui

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.awebo.ytext.model.Topic
import com.awebo.ytext.model.TopicManagable
import com.awebo.ytext.model.TopicUpdateRequest
import com.awebo.ytext.ui.vm.ReorderViewModel
import org.jetbrains.compose.resources.painterResource
import sh.calvin.reorderable.ReorderableColumn
import ytext.composeapp.generated.resources.Res
import ytext.composeapp.generated.resources.delete_24_b

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun Reorder(
    modifier: Modifier = Modifier,
    reorderViewModel: ReorderViewModel,
    reorderAction: (List<Topic>, List<Topic>) -> Unit,
) {
    LaunchedEffect(Unit) {
        reorderViewModel.loadTopics()
    }

    val vmTopics = reorderViewModel.topics.collectAsStateWithLifecycle().value
    var editableItemsList by remember { mutableStateOf<List<TopicManagable>>(emptyList()) }
    var editableChannelsStrings by remember { mutableStateOf<List<String>>(emptyList()) } // Keep as List<String> for immutability, update by creating new list
    var listOfRemoved by remember { mutableStateOf<List<TopicManagable>>(emptyList()) } // Use List for immutable state


//    val value = reorderViewModel.topics.collectAsStateWithLifecycle().value
//    var list by remember { mutableStateOf(value) }
//    var channelsList by remember { mutableStateOf(value.map { it.channelList.map{ch -> ch.handle}.joinToString(",") }.toMutableList()) }
//    var listOfRemoved by remember { mutableStateOf(mutableListOf<TopicManagable>()) }
//
//    println("list $list")
//    println("channelsList $channelsList")
//    println("listOfRemoved $listOfRemoved")

    LaunchedEffect(vmTopics) {
        editableItemsList = vmTopics
        editableChannelsStrings = vmTopics.map { topicManagable ->
            topicManagable.channelList.joinToString(",") { channel -> channel.handle }
        }
    }

    println("editableChannelsStrings $editableChannelsStrings")
    println("Opened reorder")

    Column(modifier = modifier.padding(8.dp).fillMaxSize()) {
        ReorderableColumn(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            list = editableItemsList,
            onSettle = { fromIndex, toIndex ->
                editableItemsList = editableItemsList.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
            },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { index, item, isDragging ->
            key(item.topic.id) {
                val interactionSource = remember { MutableInteractionSource() }

                Card(
                    onClick = {},
                    modifier = Modifier
                        .height(70.dp).fillMaxWidth()
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(
                                    label = "Move Up",
                                    action = {
                                        if (index > 0) {
                                            editableItemsList = editableItemsList.toMutableList().apply {
                                                add(index - 1, removeAt(index))
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                ),
                                CustomAccessibilityAction(
                                    label = "Move Down",
                                    action = {
                                        if (index < editableItemsList.size - 1) {
                                            editableItemsList = editableItemsList.toMutableList().apply {
                                                add(index + 1, removeAt(index))
                                            }
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                ),
                            )
                        },
                    interactionSource = interactionSource,
                ) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(vertical = 4.dp)
                            .draggableHandle(interactionSource = interactionSource)
                            .clearAndSetSemantics { },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            item.topic.title,
                            modifier = Modifier
                                .draggableHandle(interactionSource = interactionSource)
                                .align(Alignment.CenterVertically)
                                .width(60.dp)
                                .basicMarquee()
                                .padding(horizontal = 8.dp)
                                .clearAndSetSemantics { },
                        )
                        TextField(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 8.dp),
                            value = editableChannelsStrings[index],
                            onValueChange = { newChannelsText ->
                                editableChannelsStrings = editableChannelsStrings.toMutableList().apply {
                                    this[index] = newChannelsText
                                }
                            },
                            label = { Text("Topic channels") }
                        )
                        IconButton(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .align(Alignment.CenterVertically)
                                .size(24.dp),
                            onClick = {
                                println("Edit $index")
                                val topic = editableItemsList[index]
                                val channels = editableChannelsStrings[index]
                                reorderViewModel.manageTopic(
                                    TopicUpdateRequest(
                                        topicId = topic.topic.id,
                                        channels = channels
                                    )
                                )
                                println("Edit Element $channels")
                            },
                        ) {
                            Icon(
                                painter = rememberVectorPainter(image = Icons.Filled.Edit),
                                modifier = Modifier.size(24.dp),
                                contentDescription = "Edit topic"
                            )
                        }
                        IconButton(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .align(Alignment.CenterVertically)
                                .size(24.dp),
                            onClick = {
                                println("Delete $index")
                                val element = editableItemsList[index]
                                listOfRemoved = listOfRemoved + element
                                editableItemsList = editableItemsList.toMutableList().apply { removeAt(index) }
                                editableChannelsStrings = editableChannelsStrings.toMutableList().apply { removeAt(index) }
                            },
                        ) {
                            Icon(
                                painter = rememberVectorPainter(image = Icons.Filled.Delete),
                                modifier = Modifier.size(24.dp),
                                contentDescription = "Delete topic"
                            )
                        }
                    }

                }
            }
        }
        Button(onClick = {
            val topicsWithUpdatedOrder = editableItemsList
                .mapIndexed { index, topicManagable ->
                    topicManagable.topic.copy(order = index)
                }
            val topicsWithRemoved = listOfRemoved.map { removed -> removed.topic }
            reorderAction(topicsWithUpdatedOrder, topicsWithRemoved)
        }) {
            Text(text = "SAVE")
        }
    }
}
