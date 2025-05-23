package com.example.pedo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

@RequiresApi(Build.VERSION_CODES.Q)
class StrideCalibrationActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var tvStepCount: TextView
    private lateinit var tvResult: TextView
    private lateinit var btnStart: Button
    private lateinit var btnFinish: Button

    private lateinit var sensorManager: SensorManager
    private var stepCounterSensor: Sensor? = null

    private lateinit var locationManager: LocationManager

    private var startSteps: Int? = null
    private var endSteps: Int? = null
    private var startLocation: Location? = null
    private var endLocation: Location? = null
    private var isMeasuring = false
    private var currentSteps: Int? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (!granted) {
            Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            btnStart.isEnabled = false
            btnFinish.isEnabled = false
        }
    }

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
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (stepCounterSensor == null) {
            Toast.makeText(this, "이 기기는 만보계 센서를 지원하지 않습니다.", Toast.LENGTH_LONG).show()
            btnStart.isEnabled = false
            btnFinish.isEnabled = false
            return
        }

        btnStart.setOnClickListener {
            if (checkLocationPermission()) {
                startCalibration()
            } else {
                requestLocationPermission()
            }
        }

        btnFinish.setOnClickListener {
            if (checkLocationPermission()) {
                finishCalibration()
            } else {
                requestLocationPermission()
            }
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    // 위치를 한 번만 받아오는 함수 (타임아웃 포함)
    private fun requestSingleLocation(callback: (Location?) -> Unit) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "위치 권한이 없습니다.", Toast.LENGTH_SHORT).show()
            callback(null)
            return
        }
        tvStepCount.text = "GPS 위치를 찾는 중입니다...\n(실외에서 측정해 주세요)"
        var isCalled = false
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!isCalled) {
                    isCalled = true
                    callback(location)
                    locationManager.removeUpdates(this)
                }
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                if (!isCalled) {
                    isCalled = true
                    Toast.makeText(this@StrideCalibrationActivity, "GPS가 꺼져 있습니다.", Toast.LENGTH_SHORT).show()
                    callback(null)
                    locationManager.removeUpdates(this)
                }
            }
        }
        try {
            locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper())
        } catch (e: Exception) {
            // 일부 기기에서 requestSingleUpdate가 deprecated일 수 있어 fallback
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener)
        }
        // 타임아웃: 안내만, 상태 유지
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isCalled) {
                isCalled = true
                locationManager.removeUpdates(listener)
                Toast.makeText(this, "GPS 위치를 아직 찾지 못했습니다. 움직이지 말고 기다려 주세요.", Toast.LENGTH_LONG).show()
                tvStepCount.text = "GPS 위치를 아직 찾지 못했습니다. 움직이지 말고 기다려 주세요."
                callback(null)
            }
        }, 30000L)
    }

    // 보정 시작: 모든 값 초기화 후 현재 걸음수와 최신 GPS 위치를 저장
    private fun startCalibration() {
        // 모든 값 초기화
        startSteps = null
        endSteps = null
        startLocation = null
        endLocation = null
        isMeasuring = true
        tvResult.text = ""
        tvStepCount.text = ""
        btnStart.isEnabled = false
        btnFinish.isEnabled = false

        requestSingleLocation { location ->
            if (location != null) {
                startLocation = location
                startSteps = currentSteps
                tvStepCount.text = "시작 걸음 수: ${startSteps ?: "측정 불가"}\n시작 위치: ${location.latitude}, ${location.longitude}"
                btnFinish.isEnabled = true
            }
            // 위치 못 찾으면 안내 문구 유지(상태 유지)
        }
    }

    // 보정 완료: 현재 걸음수와 최신 GPS 위치를 저장, 보폭 계산
    private fun finishCalibration() {
        endSteps = null
        endLocation = null
        isMeasuring = false
        btnFinish.isEnabled = false

        requestSingleLocation { location ->
            if (location != null) {
                endLocation = location
                endSteps = currentSteps
                val stepDelta = if (startSteps != null && endSteps != null) endSteps!! - startSteps!! else null
                val gpsDistance = if (startLocation != null && endLocation != null) {
                    startLocation!!.distanceTo(endLocation!!)
                } else null

                if (stepDelta != null && gpsDistance != null && stepDelta > 0) {
                    val stride = gpsDistance / stepDelta
                    tvStepCount.text = ""
                    tvResult.text = "걸음 수 차이: $stepDelta\nGPS 거리: ${"%.2f".format(gpsDistance)} m\n보폭: ${"%.2f".format(stride)} m/걸음"
                } else {
                    tvResult.text = "걸음 수 또는 위치 측정에 실패했습니다."
                }
                btnStart.isEnabled = true
            } else {
                tvResult.text = ""
                Toast.makeText(this, "GPS 위치를 아직 찾지 못했습니다. 움직이지 말고 기다려 주세요.", Toast.LENGTH_LONG).show()
                tvStepCount.text = "GPS 위치를 아직 찾지 못했습니다. 움직이지 말고 기다려 주세요."
            }
        }
    }

    // 센서 콜백: 누적 걸음수 갱신
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            currentSteps = event.values[0].toInt()
            if (isMeasuring) {
                tvStepCount.text = "현재 걸음 수: $currentSteps"
            }
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        stepCounterSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }
}
