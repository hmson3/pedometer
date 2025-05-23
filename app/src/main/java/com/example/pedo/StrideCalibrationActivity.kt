package com.example.pedo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
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
import kotlin.math.*

@RequiresApi(Build.VERSION_CODES.Q)
class StrideCalibrationActivity : AppCompatActivity(), SensorEventListener, LocationListener {

    companion object {
        private const val STRIDE_DISTANCE_METERS = 5.0f // 참고용, GPS 거리 사용
    }

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

    // 위치 권한 체크 및 요청
    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }

    // 보정 시작
    private fun startCalibration() {
        startSteps = null
        startLocation = null
        isMeasuring = true
        tvResult.text = ""
        tvStepCount.text = "걸음 수 측정 중..."
        btnStart.isEnabled = false
        btnFinish.isEnabled = false

        // 현재 걸음 수 저장
        startSteps = currentSteps

        // 위치 업데이트 요청
        requestSingleLocation { location ->
            startLocation = location
            tvStepCount.text = "시작 걸음 수: ${startSteps ?: "측정 불가"}\n시작 위치: ${location.latitude}, ${location.longitude}"
            btnFinish.isEnabled = true
        }
    }

    // 보정 완료
    private fun finishCalibration() {
        endSteps = null
        endLocation = null
        isMeasuring = false
        btnFinish.isEnabled = false

        // 현재 걸음 수 저장
        endSteps = currentSteps

        // 위치 업데이트 요청
        requestSingleLocation { location ->
            endLocation = location
            val stepDelta = if (startSteps != null && endSteps != null) endSteps!! - startSteps!! else null
            val gpsDistance = if (startLocation != null && endLocation != null) {
                startLocation!!.distanceTo(endLocation!!)
            } else null

            if (stepDelta != null && gpsDistance != null && stepDelta > 0) {
                val stride = gpsDistance / stepDelta
                tvResult.text = "걸음 수 차이: $stepDelta\nGPS 거리: ${"%.2f".format(gpsDistance)} m\n보폭: ${"%.2f".format(stride)} m/걸음"
            } else {
                tvResult.text = "걸음 수 또는 위치 측정에 실패했습니다."
            }
            btnStart.isEnabled = true
        }
    }

    private fun requestSingleLocation(callback: (Location) -> Unit) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // 권한 없을 때 처리
            Toast.makeText(this@StrideCalibrationActivity, "위치 권한이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 최근 위치가 있으면 바로 사용
        val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (lastKnown != null) {
            callback(lastKnown)
            return
        }

        // 새 위치 요청
        locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, object : LocationListener {
            override fun onLocationChanged(location: Location) {
                callback(location)
                locationManager.removeUpdates(this) // 위치 받은 후 업데이트 중지
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {
                Toast.makeText(
                    this@StrideCalibrationActivity,
                    "GPS가 꺼져 있습니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, null)
    }


    // 만보기 센서
    private var currentSteps: Int? = null
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

    // LocationListener 구현(필수지만 여기선 사용 안 함)
    override fun onLocationChanged(location: Location) {}
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
