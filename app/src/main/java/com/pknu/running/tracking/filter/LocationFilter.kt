package com.pknu.running.tracking.filter

import com.pknu.running.tracking.math.GeoDistance
import com.pknu.running.tracking.model.LocationSample
import com.pknu.running.tracking.model.TrackingConfig

/**
 * GPS 샘플 품질 필터 (FR-04).
 *
 * 새 샘플을 직전 유효 샘플과 비교하여 거리 누적에 반영할지 결정한다.
 * 상태를 가지므로 하나의 러닝 세션당 하나의 인스턴스를 사용한다.
 */
class LocationFilter(private val config: TrackingConfig) {

    private var lastAccepted: LocationSample? = null

    /** 필터 판정 결과. */
    sealed interface Result {
        /** 첫 샘플. 기준점으로만 사용하고 거리 누적은 없음. */
        data object Anchor : Result

        /** 정확도가 낮아 거리 누적에서 제외. 기준점도 갱신하지 않는다. */
        data object RejectedLowAccuracy : Result

        /** 순간 속도가 비현실적이라 GPS 점프로 간주하여 폐기. */
        data object RejectedJump : Result

        /** 유효한 이동. deltaMeter만큼 거리에 누적한다. */
        data class Accepted(val deltaMeter: Double, val speedMps: Double) : Result
    }

    /**
     * 새 샘플을 판정한다.
     */
    fun accept(sample: LocationSample): Result {
        // 1. 정확도 필터: 부정확한 샘플은 거리 누적 제외 (기준점 갱신 안 함).
        if (sample.accuracyMeter > config.maxAccuracyMeter) {
            return Result.RejectedLowAccuracy
        }

        val prev = lastAccepted
        if (prev == null) {
            lastAccepted = sample
            return Result.Anchor
        }

        val delta = GeoDistance.haversineMeter(
            prev.latitude, prev.longitude,
            sample.latitude, sample.longitude,
        )
        val dtSec = (sample.timestampMs - prev.timestampMs) / 1_000.0

        // dt가 0 이하이면 판정 불가 → 점프 취급하여 폐기.
        if (dtSec <= 0.0) {
            return Result.RejectedJump
        }

        val speed = delta / dtSec
        // 2. 속도 이상치(점프) 제거.
        if (speed > config.maxSpeedMps) {
            return Result.RejectedJump
        }

        lastAccepted = sample
        return Result.Accepted(deltaMeter = delta, speedMps = speed)
    }

    /** 세션 재시작 시 상태 초기화. */
    fun reset() {
        lastAccepted = null
    }
}
