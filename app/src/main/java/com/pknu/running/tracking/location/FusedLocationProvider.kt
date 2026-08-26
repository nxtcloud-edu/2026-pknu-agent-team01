package com.pknu.running.tracking.location

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pknu.running.tracking.model.LocationSample
import com.pknu.running.tracking.model.TrackingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Google Play Services 기반 실제 위치 제공자.
 *
 * 위치 권한(ACCESS_FINE_LOCATION)이 이미 승인되어 있어야 한다. 권한 요청/확인은
 * 호출하는 화면(기능 4) 책임이다.
 */
class FusedLocationProvider(
    context: Context,
    private val config: TrackingConfig = TrackingConfig(),
) : LocationProvider {

    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    private val _samples = MutableSharedFlow<LocationSample>(extraBufferCapacity = 256)
    override val samples: Flow<LocationSample> = _samples.asSharedFlow()

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            for (loc in result.locations) {
                _samples.tryEmit(
                    LocationSample(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        timestampMs = loc.time,
                        accuracyMeter = if (loc.hasAccuracy()) loc.accuracy else Float.MAX_VALUE,
                        speedMps = if (loc.hasSpeed()) loc.speed else null,
                    )
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun start() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            config.locationIntervalMs,
        ).setMinUpdateIntervalMillis(config.locationIntervalMs).build()

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    override fun stop() {
        client.removeLocationUpdates(callback)
    }
}
