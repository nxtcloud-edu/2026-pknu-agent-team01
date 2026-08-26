package com.pknu.running.tracking.model

/**
 * GPS 위치 입력 1건.
 *
 * @property latitude 위도 (degrees)
 * @property longitude 경도 (degrees)
 * @property timestampMs 샘플 시각 (epoch millis)
 * @property accuracyMeter GPS 오차 반경 (meter). 클수록 부정확.
 * @property speedMps 기기가 제공하는 순간 속도 (m/s). 없으면 null.
 */
data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long,
    val accuracyMeter: Float,
    val speedMps: Float? = null,
)
