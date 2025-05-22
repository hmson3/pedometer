package com.example.pedo

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

@RequiresApi(Build.VERSION_CODES.Q)
class StrideCalibrationActivity : AppCompatActivity(), SensorEventListener {

    companion object {
        private const val STRIDE_DISTANCE_METERS = 5.0f
    }

    private lateinit var tvStepCount: TextView
    private lateinit var tvResult: TextView
    private lateinit var btnStart: Button
    private lateinit var btnFinish: Button

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null

    private var startSteps: Int? = null
    private var currentSteps: Int? = null
    private var isMeasuring = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stride_calibration)

        tvStepCount = findViewById(R.id.tvStepCount)
        tvResult    = findViewById(R.id.tvResult)
        btnStart    = findViewById(R.id.btnStart)
        btnFinish   = findViewById(R.id.btnFinish)

        btnStart.isEnabled = true
        btnFinish.isEnabled = false

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        if (stepCounterSensor == null) {
            Toast.makeText(this, "이 기기는 만보계 센서를 지원하지 않습니다.", Toast.LENGTH_LONG).show()
            btnStart.isEnabled = false
            btnFinish.isEnabled = false
            return
        }

        btnStart.setOnClickListener {
            startSteps = currentSteps
            tvStepCount.text = "시작 걸음 수: ${startSteps ?: "측정 불가"}"
            btnStart.isEnabled = false
            btnFinish.isEnabled = true
            isMeasuring = true
        }

        btnFinish.setOnClickListener {
            if (startSteps != null && currentSteps != null) {
                val delta = currentSteps!! - startSteps!!
                val stride = if (delta > 0) STRIDE_DISTANCE_METERS / delta else 0f
                tvResult.text = "걸음 수 차이: $delta\n보폭: ${"%.2f".format(stride)} m/걸음"
            } else {
                tvResult.text = "걸음 수 측정에 실패했습니다."
            }
            btnStart.isEnabled = true
            btnFinish.isEnabled = false
            isMeasuring = false
        }
    }

    override fun onResume() {
        super.onResume()
        // 센서 리스너 등록
        stepCounterSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        // 센서 리스너 해제
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            currentSteps = event.values[0].toInt()
            if (isMeasuring) {
                tvStepCount.text = "현재 걸음 수: $currentSteps"
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 사용하지 않음
    }
}
