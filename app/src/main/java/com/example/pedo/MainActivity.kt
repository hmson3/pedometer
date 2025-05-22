package com.example.pedo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnCalibrate = findViewById<Button>(R.id.btnCalibrate)
        btnCalibrate.setOnClickListener {
            val intent = Intent(this, StrideCalibrationActivity::class.java)
            startActivity(intent)
        }
    }
}