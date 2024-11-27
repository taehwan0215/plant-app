package com.example.plant2

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import okhttp3.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class AddPlantActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var selectPhotosButton: Button
    private lateinit var plantNicknameEditText: EditText
    private lateinit var buttonSavePlant: Button

    private val PICK_IMAGE_REQUEST = 1
    private var selectedImageUri: Uri? = null

    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_plant)

        // UI 요소 연결
        imageView = findViewById(R.id.imageView)
        selectPhotosButton = findViewById(R.id.selectPhotosButton)
        plantNicknameEditText = findViewById(R.id.editTextNickname)
        buttonSavePlant = findViewById(R.id.buttonSavePlant)

        // 사진 선택 버튼 클릭 시
        selectPhotosButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        // 추가 버튼 클릭 시
        buttonSavePlant.setOnClickListener {
            val nickname = plantNicknameEditText.text.toString().trim()

            if (nickname.isEmpty()) {
                Toast.makeText(this, "식물 닉네임을 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedImageUri != null) {
                uploadImageToS3(nickname)
            } else {
                Toast.makeText(this, "사진을 선택해주세요.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 이미지 선택 후 처리
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            selectedImageUri = data.data
            imageView.setImageURI(selectedImageUri)
        }
    }

    // S3에 이미지 업로드
    private fun uploadImageToS3(nickname: String) {
        val preSignedUrlRequestUrl = "http://43.200.61.147:5001/get-presigned-url"

        val jsonObject = JSONObject()
        jsonObject.put("filename", "plant_image.jpg")
        val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), jsonObject.toString())

        val request = Request.Builder()
            .url(preSignedUrlRequestUrl)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@AddPlantActivity, "Pre-signed URL 요청 실패", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val preSignedUrl = JSONObject(responseBody).getString("url")

                        // Pre-signed URL을 사용해 이미지 업로드
                        selectedImageUri?.let { uri ->
                            val bitmap = MediaStore.Images.Media.getBitmap(this@AddPlantActivity.contentResolver, uri)
                            val outputStream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                            val byteArray = outputStream.toByteArray()

                            val uploadRequest = Request.Builder()
                                .url(preSignedUrl)
                                .put(RequestBody.create("image/jpeg".toMediaTypeOrNull(), byteArray))
                                .build()

                            client.newCall(uploadRequest).enqueue(object : Callback {
                                override fun onFailure(call: Call, e: IOException) {
                                    e.printStackTrace()
                                    runOnUiThread {
                                        Toast.makeText(this@AddPlantActivity, "S3 업로드 실패", Toast.LENGTH_SHORT).show()
                                    }
                                }

                                override fun onResponse(call: Call, response: Response) {
                                    if (response.isSuccessful) {
                                        val s3ImageUrl = preSignedUrl.split('?')[0]
                                        identifyPlant(nickname, s3ImageUrl)
                                    } else {
                                        runOnUiThread {
                                            Toast.makeText(this@AddPlantActivity, "S3 업로드 실패", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            })
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@AddPlantActivity, "Pre-signed URL 요청 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // 백엔드에 식물 식별 요청
    private fun identifyPlant(nickname: String, imageUrl: String) {
        val url = "http://43.200.61.147:5001/identify_plant"
        val jsonObject = JSONObject()
        jsonObject.put("image_url", imageUrl)

        val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), jsonObject.toString())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@AddPlantActivity, "식물 식별 요청 실패", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    runOnUiThread {
                        try {
                            if (responseBody != null) {
                                // 응답 로그로 출력하여 확인
                                Log.d("IdentifyPlantResponse", responseBody)

                                // 식별 결과를 JSON 객체로 변환하여 처리
                                val jsonResponse = JSONObject(responseBody)
                                val identifiedClass = jsonResponse.optString("identified_class", "Unknown")
                                val confidence = jsonResponse.optDouble("confidence", 0.0)

                                // 식별 성공 후 분석 요청
                                analyzePlant(nickname, imageUrl, identifiedClass, confidence)
                            } else {
                                Toast.makeText(this@AddPlantActivity, "식별 서버 응답이 없습니다.", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(this@AddPlantActivity, "식별 결과 처리 중 오류 발생", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@AddPlantActivity, "식물 식별 요청 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    // 백엔드에 분석 요청
    private fun analyzePlant(nickname: String, imageUrl: String, identifiedClass: String, confidence: Double) {
        val url = "http://43.200.61.147:5001/analyze"
        val jsonObject = JSONObject()
        jsonObject.put("nickname", nickname)
        jsonObject.put("image_url", imageUrl)

        val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), jsonObject.toString())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@AddPlantActivity, "분석 서버 요청 실패", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    runOnUiThread {
                        try {
                            if (responseBody != null) {
                                // 응답 로그로 출력하여 확인
                                Log.d("AnalyzePlantResponse", responseBody)

                                // 분석 결과를 JSON 객체로 변환하여 처리
                                val jsonResponse = JSONObject(responseBody)
                                val wateringInterval = jsonResponse.optDouble("watering_interval", -1.0)
                                val growthHeight = jsonResponse.optDouble("growth_height", -1.0)

                                if (wateringInterval != -1.0 && growthHeight != -1.0) {
                                    // AnalysisResultActivity로 이동하며 결과 전달
                                    val resultIntent = Intent(this@AddPlantActivity, AnalysisResultActivity::class.java)
                                    resultIntent.putExtra("identified_class", identifiedClass)
                                    resultIntent.putExtra("confidence", confidence)
                                    resultIntent.putExtra("watering_interval", wateringInterval)
                                    resultIntent.putExtra("growth_height", growthHeight)
                                    resultIntent.putExtra("image_url", imageUrl) // 업로드된 이미지 URL 전달
                                    resultIntent.putExtra("nickname", nickname) // 닉네임 전달
                                    startActivity(resultIntent)
                                } else {
                                    Toast.makeText(this@AddPlantActivity, "분석 서버 응답 데이터 오류: 필요한 키가 없습니다.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(this@AddPlantActivity, "분석 서버 응답이 없습니다.", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(this@AddPlantActivity, "분석 결과 처리 중 오류 발생", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(this@AddPlantActivity, "분석 서버 요청 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}