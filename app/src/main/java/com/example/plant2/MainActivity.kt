package com.example.plant2

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.plant2.databinding.ActivityMainBinding
import com.example.plant2.BoardFragment
import com.example.plant2.HomeFragment
import com.example.plant2.PlantFragment
import com.example.plant2.BookFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // BottomNavigationView 클릭 이벤트 처리
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.navigation_plant -> {
                    loadFragment(PlantFragment())
                    true
                }
                R.id.navigation_community -> {
                    // BoardFragment로 전환
                    loadFragment(BoardFragment())
                    true
                }
                R.id.navigation_book -> {
                    loadFragment(BookFragment())
                    true
                }
                else -> false
            }
        }

        // 기본 선택된 화면 설정 (홈 화면)
        binding.bottomNavigation.selectedItemId = R.id.navigation_home
    }

    private fun loadFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
