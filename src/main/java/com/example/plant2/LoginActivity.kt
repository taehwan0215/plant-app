package com.example.plant2

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException
import android.util.Log //로그관련



class LoginActivity : AppCompatActivity() {

    // UI 요소 선언
    private lateinit var usernameEditText: EditText  // 사용자 이름 입력 필드
    private lateinit var passwordEditText: EditText  // 비밀번호 입력 필드
    private lateinit var loginButton: Button         // 로그인 버튼

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login) // XML 레이아웃 파일 연결 (activity_login.xml 파일을 사용 중)

        // UI 요소 초기화
        usernameEditText = findViewById(R.id.usernameEditText)  // username 입력 필드와 연결
        passwordEditText = findViewById(R.id.passwordEditText)  // password 입력 필드와 연결
        loginButton = findViewById(R.id.loginButton)            // login 버튼과 연결

        // 로그인 버튼 클릭 이벤트 설정
        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString() // 입력된 사용자 이름
            val password = passwordEditText.text.toString() // 입력된 비밀번호
            loginUser(username, password) // 로그인 함수 호출
        }
    }

    // 로그인 요청을 서버로 전송하는 함수
    private fun loginUser(username: String, password: String) {
        val client = OkHttpClient()  // OkHttp 클라이언트 생성

        // JSON 데이터 생성 (username과 password를 JSON 객체에 포함)
        val jsonObject = JSONObject()
        jsonObject.put("username", username)
        jsonObject.put("password", password)
        val jsonBody = jsonObject.toString()
        val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        // 서버 요청 생성
        val request = Request.Builder()
            .url("http://43.200.61.147:5001/login")  // 최신 백엔드 서버 주소와 엔드포인트
            .post(requestBody)                             // POST 요청으로 JSON 데이터 전송
            .build()

        // 요청을 Logcat에 출력하여 확인
        Log.d("Login Request", "Request to server: $jsonBody")  // 요청 내용을 로그에 출력

        // 서버에 비동기 요청
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // 요청 실패 시 실행 (네트워크 오류 등)
                runOnUiThread {
                    Toast.makeText(this@LoginActivity, "Failed to connect to server", Toast.LENGTH_SHORT).show()
                }
                Log.e("Login Error", "Request failed: ${e.message}")  // 요청 실패 시 로그
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()  // 서버 응답 데이터를 문자열로 받음
                Log.d("Response Data", responseData ?: "No response")  // 서버 응답 로그
                val jsonResponse = JSONObject(responseData ?: "{}") // JSON 형식으로 파싱

                runOnUiThread {
                    if (response.isSuccessful && jsonResponse.optString("status") == "success") {
                        // 로그인 성공 처리
                        Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_SHORT).show()
                        // 서버에서 받은 user_id
                        val userId = jsonResponse.optInt("user_id")
                        // PostCreateActivity로 유저 ID와 함께 이동
                        val postCreateIntent = Intent(this@LoginActivity, PostCreateActivity::class.java)
                        postCreateIntent.putExtra("user_id", userId)  // user_id를 Intent에 담아서 전달
                        startActivity(postCreateIntent)
                        // 로그인 성공 시 다음 화면으로 이동 (MainActivity로 이동)
                        val intent = Intent(this@LoginActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        // 로그인 실패 처리 (서버에서 받은 오류 메시지 표시)
                        val message = jsonResponse.optString("message", "Login failed")
                        Toast.makeText(this@LoginActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}
