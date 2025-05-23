package com.awebo.ytext.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.awebo.ytext.model.Topic
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
    reorderViewModel.loadTopics()
    val value = reorderViewModel.topics.collectAsStateWithLifecycle().value
    var list by remember { mutableStateOf(value) }
    var listOfRemoved by remember { mutableStateOf(mutableListOf<Topic>()) }

    println("listOfRemoved $listOfRemoved")

    println("Opened reorder")

    Column(modifier = modifier.padding(8.dp).fillMaxSize()) {
        ReorderableColumn(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            list = list,
            onSettle = { fromIndex, toIndex ->
                list = list.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
            },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { index, item, isDragging ->
            key(item.id) {
                val interactionSource = remember { MutableInteractionSource() }

                Card(
                    onClick = {},
                    modifier = Modifier
                        .height(32.dp).fillMaxWidth()
                        .semantics {
                            customActions = listOf(
                                CustomAccessibilityAction(
                                    label = "Move Up",
                                    action = {
                                        if (index > 0) {
                                            list = list.toMutableList().apply {
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
                                        if (index < list.size - 1) {
                                            list = list.toMutableList().apply {
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
                            .draggableHandle(interactionSource = interactionSource)
                            .clearAndSetSemantics { },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            item.title,
                            Modifier
                                .draggableHandle(interactionSource = interactionSource)
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .clearAndSetSemantics { },
                        )
                        IconButton(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(24.dp),
                            onClick = {
                                println("Delete $index")
                                val element = list[index]
                                listOfRemoved = listOfRemoved.toMutableList().apply { add(element) }
                                list = list.toMutableList().apply { removeAt(index) }
                            },
                        ) {
                            Icon(
                                modifier = Modifier.size(24.dp),
                                painter = painterResource(Res.drawable.delete_24_b),
                                contentDescription = "Delete topic"
                            )
                        }
                    }

                }
            }
        }
        Button(onClick = {
            val topicsWithUpdatedOrder = list
                .mapIndexed { index, topic ->
                    topic.copy(order = index)
                }
            reorderAction(topicsWithUpdatedOrder, listOfRemoved)
        }) {
            Text(text = "SAVE")
        }
    }
}
