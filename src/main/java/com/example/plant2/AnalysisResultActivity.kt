package com.example.plant2

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.widget.ImageView
import com.bumptech.glide.Glide
import java.text.DecimalFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy

class AnalysisResultActivity : AppCompatActivity() {

    private lateinit var textViewWateringInterval: TextView
    private lateinit var textViewGrowthData: TextView
    private lateinit var textViewPlantSpecies: TextView
    private lateinit var textViewConfidence: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis_result)

        // UI 요소 연결
        textViewWateringInterval = findViewById(R.id.textViewWateringInterval)
        textViewGrowthData = findViewById(R.id.textViewGrowthData)
        textViewPlantSpecies = findViewById(R.id.textViewPlantSpecies)
        textViewConfidence = findViewById(R.id.textViewConfidence)

        // ImageView 연결
        val imageViewPlant = findViewById<ImageView>(R.id.imageViewPlant)

        // Intent로 전달된 데이터 가져오기
        val imageUrl = intent.getStringExtra("image_url")

        // 이미지 로드 (Glide 사용)
        if (imageUrl != null) {
            Glide.with(this)
                .load(imageUrl) // URL로 이미지 로드
                .skipMemoryCache(true) // 메모리 캐시 사용 안 함
                .diskCacheStrategy(DiskCacheStrategy.NONE) // 디스크 캐시 사용 안 함
                .into(imageViewPlant)
        }

        // 분석 결과 받기
        val wateringInterval = intent.getDoubleExtra("watering_interval", -1.0)
        val growthHeight = intent.getDoubleExtra("growth_height", -1.0)
        val identifiedClass = intent.getStringExtra("identified_class") // 식물 품종 데이터 가져오기
        val confidence = intent.getDoubleExtra("confidence", -1.0) // 신뢰도 데이터 가져오기
        val nickname = intent.getStringExtra("nickname") // 닉네임 데이터 가져오기
        val textViewPlantName = findViewById<TextView>(R.id.textViewPlantName)

        // DecimalFormat으로 소수점 제한
        val decimalFormat = DecimalFormat("#.##") // 소수점 둘째 자리까지만 표시

        // 분석 결과 표시
        if (wateringInterval != -1.0 && growthHeight != -1.0 && identifiedClass != null && confidence != -1.0) {
            val formattedWateringInterval = decimalFormat.format(wateringInterval)
            val formattedGrowthHeight = decimalFormat.format(growthHeight)
            val formattedConfidence = decimalFormat.format(confidence * 100) // 신뢰도 퍼센트로 표시

            textViewPlantName.text = "식물 이름: $nickname"
            textViewWateringInterval.text = "물주기: $formattedWateringInterval 일"
            textViewGrowthData.text = "신장: $formattedGrowthHeight cm"
            textViewPlantSpecies.text = "식물 품종: $identifiedClass"
            textViewConfidence.text = "식별 신뢰도: $formattedConfidence%"
        } else {
            textViewPlantName.text = "식물 이름: $nickname"
            textViewWateringInterval.text = "분석 결과를 가져오지 못했습니다."
            textViewGrowthData.text = ""
            textViewPlantSpecies.text = "식물 품종: 식별 실패"
            textViewConfidence.text = ""
        }
    }
}
