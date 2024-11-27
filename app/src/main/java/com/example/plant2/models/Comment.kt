package com.example.plant2.models

data class Comment(
    val commentId: Int,
    val content: String,
    val authorId: Int,
    val parentCommentId: Int?,
    val createdAt: String
)
