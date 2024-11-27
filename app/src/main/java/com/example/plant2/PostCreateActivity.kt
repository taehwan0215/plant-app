package com.example.plant2

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import java.io.File
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import android.net.Uri

class PostCreateActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var selectPhotosButton: Button
    private lateinit var contentEditText: EditText
    private lateinit var postTypeSpinner: Spinner
    private lateinit var uploadPostButton: Button

    private val PICK_IMAGE_REQUEST = 1  // 이미지 선택 요청 코드
    private var selectedImageUri: String? = null  // 선택한 이미지 URI

    // OkHttpClient 설정
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_create)

        // UI 요소 연결
        imageView = findViewById(R.id.imageView)
        selectPhotosButton = findViewById(R.id.selectPhotosButton)
        contentEditText = findViewById(R.id.contentEditText)
        postTypeSpinner = findViewById(R.id.postTypeSpinner)
        uploadPostButton = findViewById(R.id.uploadPostButton)

        // Spinner 설정
        val adapter = ArrayAdapter.createFromResource(
            this,
            R.array.post_types,  // strings.xml에 정의된 배열
            android.R.layout.simple_spinner_item  // 항목을 보여줄 레이아웃
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        postTypeSpinner.adapter = adapter

        // 사진 선택 버튼 클릭 시
        selectPhotosButton.setOnClickListener {
            // 이미지 선택 처리 (이미지 선택 인텐트 실행)
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.type = "image/*"
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        // 게시글 올리기 버튼 클릭 시
        uploadPostButton.setOnClickListener {
            // 내용 가져오기
            val content = contentEditText.text.toString()

            // 게시글 유형 선택 가져오기
            val selectedType = postTypeSpinner.selectedItem.toString()

            if (selectedType == "게시글 유형을 선택하세요") {
                // 게시글 유형을 선택하지 않으면 경고 메시지 표시
                Toast.makeText(this, "게시글 유형을 선택해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 선택된 게시글 유형에 따라 카테고리 값을 지정
            val postCategory = when (selectedType) {
                "자랑할래요" -> "brag"
                "질문있어요" -> "question"
                else -> ""
            }

            if (selectedImageUri != null) {
                // 이미지가 선택된 경우 서버로 전송
                uploadPostToServer(content, postCategory, selectedImageUri!!)
            } else {
                // 이미지가 선택되지 않은 경우
                Toast.makeText(this, "이미지를 선택해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 이미지 선택 후 처리
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            val selectedImageUri = data.data
            this.selectedImageUri = selectedImageUri.toString() // 선택된 이미지 URI 저장
            imageView.setImageURI(selectedImageUri)  // 선택된 이미지를 이미지 뷰에 표시
        }
    }

    // Uri를 실제 파일 경로로 변환
    private fun getFileFromUri(uri: String): File? {
        val context = applicationContext
        val contentUri = Uri.parse(uri)  // String을 Uri로 변환

        // ContentResolver를 통해 파일 경로 찾기
        val cursor = context.contentResolver.query(contentUri, null, null, null, null)

        cursor?.let {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndex(MediaStore.Images.Media.DATA)
                val filePath = it.getString(columnIndex)
                it.close()
                return File(filePath)
            }
        }

        return null
    }

    // 서버에 게시글 전송
    private fun uploadPostToServer(content: String, postType: String, imageUri: String) {
        val url = "http://43.200.61.147:5001/posts"

        // Uri에서 실제 파일 경로 가져오기
        val file = getFileFromUri(imageUri)  // 실제 경로로 변환된 파일
        if (file == null) {
            Toast.makeText(this, "파일을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val requestFile = RequestBody.create("image/*".toMediaType(), file)  // MediaType.toMediaType() 사용

        val userId: Int = intent.getIntExtra("user_id", -1)  // 로그인 시 전달한 user_id 받기

        val formBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("title", "Sample Title")  // 제목을 추가할 경우 제목을 받거나 설정
            .addFormDataPart("content", content)
            .addFormDataPart("category", postType)
            .addFormDataPart("file", file.name, requestFile)
            .addFormDataPart("user_id", userId.toString())  // 요청에 user_id가 포함되도록 추가

            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        // 요청 전송
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@PostCreateActivity, "게시글 업로드 실패", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    runOnUiThread {
                        Toast.makeText(this@PostCreateActivity, "게시글 업로드 성공", Toast.LENGTH_SHORT).show()
                        // 성공 시 추가 작업 (예: 게시글 목록으로 이동 등)
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@PostCreateActivity, "게시글 업로드 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}
