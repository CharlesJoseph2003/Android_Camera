package com.example.myapplication
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.view.Surface
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import java.util.*
data class LocationOrientation(
    val latitude: Double?,
    val longitude: Double?,
    val address: String?,
    val compassDirection: String,
    val deviceOrientation: String
)

class LocationOrientationManager(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val geocoder = Geocoder(context, Locale.getDefault())

    suspend fun getCurrentLocationAndOrientation(): LocationOrientation {
        val location = getCurrentLocation()
        val address = location?.let { getAddressFromLocation(it) }
        val compassDirection = getCompassDirection()
        val deviceOrientation = getDeviceOrientation()

        return LocationOrientation(
            latitude = location?.latitude,
            longitude = location?.longitude,
            address = address,
            compassDirection = compassDirection,
            deviceOrientation = deviceOrientation
        )
    }

    private suspend fun getCurrentLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        return withTimeoutOrNull(5000L) {
            suspendCancellableCoroutine { continuation ->
                fusedLocationClient.lastLocation.addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        continuation.resume(task.result)
                    } else {
                        continuation.resume(null)
                    }
                }
            }
        }
    }

    private suspend fun getAddressFromLocation(location: Location): String? {
        return try {
            withTimeoutOrNull(5000L) {
                val addresses: List<Address>? =
                    geocoder.getFromLocation(location.latitude, location.longitude, 1)
                addresses?.firstOrNull()?.let { address ->
                    listOfNotNull(
                        address.locality,
                        address.adminArea,
                        address.countryName
                    ).joinToString(", ")
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getCompassDirection(): String {
        return withTimeoutOrNull(2000L) {
            suspendCancellableCoroutine { continuation ->
                val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
                val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

                if (magnetometer == null || accelerometer == null) {
                    continuation.resume("Unknown")
                    return@suspendCancellableCoroutine
                }

                val magneticField = FloatArray(3)
                val gravity = FloatArray(3)
                var magneticFieldSet = false
                var gravitySet = false

                val sensorListener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent?) {
                        event?.let {
                            when (it.sensor.type) {
                                Sensor.TYPE_MAGNETIC_FIELD -> {
                                    System.arraycopy(
                                        it.values,
                                        0,
                                        magneticField,
                                        0,
                                        magneticField.size
                                    )
                                    magneticFieldSet = true
                                }

                                Sensor.TYPE_ACCELEROMETER -> {
                                    System.arraycopy(it.values, 0, gravity, 0, gravity.size)
                                    gravitySet = true
                                }
                            }

                            if (magneticFieldSet && gravitySet) {
                                val rotationMatrix = FloatArray(9)
                                val orientation = FloatArray(3)

                                if (SensorManager.getRotationMatrix(
                                        rotationMatrix,
                                        null,
                                        gravity,
                                        magneticField
                                    )
                                ) {
                                    SensorManager.getOrientation(rotationMatrix, orientation)
                                    val azimuthRadians = orientation[0]
                                    val azimuthDegrees =
                                        Math.toDegrees(azimuthRadians.toDouble()).toFloat()
                                    val normalizedAzimuth =
                                        ((azimuthDegrees + 360) % 360).roundToInt()

                                    sensorManager.unregisterListener(this)
                                    continuation.resume(formatCompassDirection(normalizedAzimuth))
                                }
                            }
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }

                sensorManager.registerListener(
                    sensorListener,
                    magnetometer,
                    SensorManager.SENSOR_DELAY_NORMAL
                )
                sensorManager.registerListener(
                    sensorListener,
                    accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL
                )

                continuation.invokeOnCancellation {
                    sensorManager.unregisterListener(sensorListener)
                }
            }
        } ?: "Unknown"
    }

    private fun formatCompassDirection(degrees: Int): String {
        val directions = arrayOf(
            "North",
            "Northeast",
            "East",
            "Southeast",
            "South",
            "Southwest",
            "West",
            "Northwest"
        )
        val index = ((degrees + 22.5) / 45).toInt() % 8
        val primaryDirection = directions[index]

        val deviationFromCardinal = when (index) {
            0 -> degrees // North
            2 -> degrees - 90 // East
            4 -> degrees - 180 // South
            6 -> degrees - 270 // West
            else -> return primaryDirection
        }

        return if (kotlin.math.abs(deviationFromCardinal) <= 5) {
            primaryDirection
        } else {
            "${kotlin.math.abs(deviationFromCardinal)}° from $primaryDirection"
        }
    }

    private fun getDeviceOrientation(): String {
        return try {
            val activity = context as? android.app.Activity
            val display =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    activity?.display
                } else {
                    @Suppress("DEPRECATION")
                    activity?.windowManager?.defaultDisplay
                }

            when (display?.rotation) {
                Surface.ROTATION_0 -> "Portrait"
                Surface.ROTATION_90 -> "Landscape Left"
                Surface.ROTATION_180 -> "Portrait Upside Down"
                Surface.ROTATION_270 -> "Landscape Right"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
}