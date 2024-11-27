package com.example.plant2.models

data class Post(
    val postId: Int,
    val userName: String, // 사용자 이름 (데이터베이스에서 User_ID와 조인 필요)
    val postDate: String,
    val content: String,
    val likesCount: Int,
    val commentsCount: Int,
    val imageUrl: String?
)

