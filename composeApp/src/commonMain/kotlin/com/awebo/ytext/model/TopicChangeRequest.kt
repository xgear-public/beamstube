package com.awebo.ytext.model

sealed class TopicChangeRequest
class TopicUpdateRequest(val topicId: Long, val channels: String) : TopicChangeRequest()
class TopicAddRequest(val title: String, val channels: String) : TopicChangeRequest()
class TopicDeleteRequest(val topicId: Long) : TopicChangeRequest()