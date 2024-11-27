package com.example.plant2.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.plant2.R
import com.example.plant2.models.Post
import com.bumptech.glide.Glide

class PostAdapter(private val posts: List<Post>, private val onPostClick: (Post) -> Unit) :
    RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

    inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val userName: TextView = itemView.findViewById(R.id.userName)
        private val postDate: TextView = itemView.findViewById(R.id.postDate)
        private val postContent: TextView = itemView.findViewById(R.id.postContent)
        private val likes: TextView = itemView.findViewById(R.id.likes)
        private val comments: TextView = itemView.findViewById(R.id.comments)
        private val postImage: ImageView = itemView.findViewById(R.id.postImage)  // 이미지 뷰 추가

        fun bind(post: Post) {
            userName.text = post.userName ?: "Unknown" // users 테이블의 nickname
            postDate.text = post.postDate ?: "Unknown" // post 테이블의 Created_date
            postContent.text = post.content.takeIf { it.isNotEmpty() } ?: "내용 없음" // post 테이블의 Content
            likes.text = post.likesCount.toString() ?: "Unknown" // post 테이블의 Like_count
            comments.text = post.commentsCount.toString() ?: "Unknown" // post 테이블의 Comment_count

            // Glide로 이미지 URL을 불러와서 ImageView에 로드
            val imageUrl = post.imageUrl ?: ""  // 이미지 URL 가져오기 (Post 모델에서 imageUrl이 null일 경우 빈 문자열 사용)
            if (imageUrl.isNotEmpty()) {
                Glide.with(itemView.context)
                    .load(imageUrl)  // 이미지 URL로 Glide로 이미지를 로드
                    .into(postImage)  // ImageView에 이미지 넣기
            }

            itemView.setOnClickListener { onPostClick(post) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.post_item, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(posts[position])
    }

    override fun getItemCount() = posts.size
}
