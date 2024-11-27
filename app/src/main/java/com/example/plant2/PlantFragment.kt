package com.example.plant2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.content.Intent
import android.widget.LinearLayout

class PlantFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_gallery, container, false)
        val fabAddPlant: FloatingActionButton = view.findViewById(R.id.fab_add_plant)

        fabAddPlant.setOnClickListener {
            val intent = Intent(context, AddPlantActivity::class.java)
            startActivity(intent)
        }
        // LinearLayout 연결
        val plantLayout: LinearLayout = view.findViewById(R.id.linearLayoutPlant)
        // 클릭 이벤트 추가
        plantLayout.setOnClickListener {
            // DummyPlantActivity로 화면 전환
            val intent = Intent(context, DummyPlantActivity::class.java)
            startActivity(intent)
        }

        return view
    }
}
