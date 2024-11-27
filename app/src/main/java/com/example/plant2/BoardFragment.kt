package com.example.plant2

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.plant2.adapters.PostAdapter
import com.example.plant2.models.Post
import okhttp3.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.content.Intent

class BoardFragment : Fragment() {

    private val posts = mutableListOf<Post>()  // 게시글을 저장할 리스트
    private val client = OkHttpClient()  // OkHttpClient 객체 생성 (네트워크 통신을 위해 사용)

    private lateinit var recyclerView: RecyclerView  // RecyclerView 객체 (게시글 목록을 표시)
    private lateinit var tabAll: TextView  // "전체보기" 탭
    private lateinit var tabPride: TextView  // "자랑할래요" 탭
    private lateinit var tabQuestion: TextView  // "질문있어요" 탭

    private var currentPage = 1  // 현재 페이지 번호 (기본 값은 1)
    private val pageSize = 5  // 한 번에 가져올 게시글 수
    private var isLoading = false  // 로딩 상태 추적 (중복 요청 방지)

    // 화면을 생성할 때 호출되는 메서드 (Fragment에서 UI를 설정)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_board, container, false)

        // UI 초기화 (RecyclerView와 탭들 연결)
        recyclerView = root.findViewById(R.id.recyclerView)
        tabAll = root.findViewById(R.id.tab_all)
        tabPride = root.findViewById(R.id.tab_pride)
        tabQuestion = root.findViewById(R.id.tab_question)

        // FloatingActionButton 참조
        val fabUploadPost = root.findViewById<FloatingActionButton>(R.id.fab_upload_post)

        // RecyclerView 설정
        recyclerView.layoutManager = LinearLayoutManager(requireContext())  // 세로 스크롤로 설정
        recyclerView.adapter = PostAdapter(posts) { post ->  // 게시글 클릭 시 동작 설정
            // 게시글 클릭 시 할 작업
        }

        // 기본 탭 데이터 로드 (전체보기 탭을 기본으로 선택)
        selectTab("all")  // 전체보기 탭을 기본으로 활성화

        // 탭 클릭 이벤트 설정
        tabAll.setOnClickListener {
            selectTab("all")  // "전체보기" 클릭 시
        }
        tabPride.setOnClickListener {
            selectTab("brag")  // "자랑할래요" 클릭 시
        }
        tabQuestion.setOnClickListener {
            selectTab("question")  // "질문있어요" 클릭 시
        }

        // 게시글 업로드 버튼 클릭 시 PostCreateActivity로 이동
        fabUploadPost.setOnClickListener {
            // 게시글 작성 화면으로 이동
            val intent = Intent(requireContext(), PostCreateActivity::class.java)
            startActivity(intent)
        }

        // 스크롤 리스너 추가 (끝에 도달하면 다음 페이지를 로드)
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (!isLoading && !recyclerView.canScrollVertically(1)) {
                    loadNextPage()  // 페이지네이션을 위한 다음 페이지 로드
                }
            }
        })

        return root
    }

    // 탭 클릭 시 동작하는 메서드 (각 카테고리 활성화 및 게시글 로드)
    private fun selectTab(category: String) {
        // 모든 탭을 비활성화한 후 클릭한 탭만 활성화
        tabAll.setTextColor(resources.getColor(R.color.unselected_tab_color)) // 비활성화
        tabPride.setTextColor(resources.getColor(R.color.unselected_tab_color)) // 비활성화
        tabQuestion.setTextColor(resources.getColor(R.color.unselected_tab_color)) // 비활성화

        // 클릭한 탭은 활성화
        when (category) {
            "all" -> {
                tabAll.setTextColor(resources.getColor(R.color.black)) // 활성화
                tabAll.setTypeface(null, android.graphics.Typeface.BOLD) // Bold 적용
            }

            "brag" -> {
                tabPride.setTextColor(resources.getColor(R.color.black)) // 활성화
                tabPride.setTypeface(null, android.graphics.Typeface.BOLD) // Bold 적용
            }

            "question" -> {
                tabQuestion.setTextColor(resources.getColor(R.color.black)) // 활성화
                tabQuestion.setTypeface(null, android.graphics.Typeface.BOLD) // Bold 적용
            }
        }

        // 게시물 로드 (카테고리별로 데이터 로드)
        loadPosts(category, true)  // 새로운 카테고리 선택 시 데이터를 초기화하고 로드
    }

    // 게시글을 로드하는 메서드
    private fun loadPosts(category: String, reset: Boolean) {
        if (reset) {
            currentPage = 1  // 페이지를 1로 초기화
            posts.clear()  // 기존 게시글 데이터를 비움
            recyclerView.adapter?.notifyDataSetChanged() // 리사이클러뷰 갱신
        }

        val url = when (category) {
            "brag" -> "http://43.200.61.147:5001/posts?category=brag&page=$currentPage&page_size=$pageSize"
            "question" -> "http://43.200.61.147:5001/posts?category=question&page=$currentPage&page_size=$pageSize"
            else -> "http://43.200.61.147:5001/posts?page=$currentPage&page_size=$pageSize"
        }

        val request = Request.Builder()
            .url(url)
            .build()

        isLoading = true

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                isLoading = false
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    response.close()
                    isLoading = false
                    return
                }

                val responseData = response.body?.string()
                if (responseData != null) {
                    try {
                        val jsonObject = JSONObject(responseData)
                        val postsArray = jsonObject.getJSONArray("posts")

                        for (i in 0 until postsArray.length()) {
                            val json = postsArray.getJSONObject(i)
                            val postCategory = json.getString("Category")

                            if (category == "all" || postCategory == category) {
                                posts.add(
                                    Post(
                                        postId = json.getInt("Post_ID"),
                                        userName = json.getString("User_Nickname"),
                                        postDate = json.getString("Created_date"),
                                        content = json.getString("Content"),
                                        likesCount = json.getInt("Like_count"),
                                        commentsCount = json.optInt("Comment_count", 0),
                                        imageUrl = json.optString("Image_url", "") // Image_url 추가
                                    )
                                )
                            }
                        }

                        requireActivity().runOnUiThread {
                            recyclerView.adapter?.notifyDataSetChanged()
                        }
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
                isLoading = false
            }
        })
    }

    // 추가 페이지를 로드하는 메서드 (스크롤을 내려서 더 많은 게시글을 가져오는 기능)
    private fun loadNextPage() {
        currentPage += 1  // 페이지 번호 증가

        val currentCategory = when {
            tabAll.currentTextColor == resources.getColor(R.color.black) -> "all"
            tabPride.currentTextColor == resources.getColor(R.color.black) -> "brag"
            tabQuestion.currentTextColor == resources.getColor(R.color.black) -> "question"
            else -> "all"
        }

        loadPosts(currentCategory, reset = false)  // 페이지를 이동하며 게시글을 이어서 로드
    }

}
