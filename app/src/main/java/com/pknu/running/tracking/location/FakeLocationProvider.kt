package com.pknu.running.tracking.location

import com.pknu.running.tracking.model.LocationSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 시뮬레이션용 위치 제공자. 실제 달리기 없이 페이스 시나리오로 위치를 생성한다.
 * 테스트/데모에 사용한다 (application-design.md Simulation Test).
 *
 * 시작 좌표에서 정북(위도 증가) 방향으로 직진한다고 가정하고, 구간별 페이스에 맞는
 * 위치 샘플을 생성한다.
 */
class FakeLocationProvider(
    private val segments: List<Segment>,
    private val startLat: Double = 35.1341,   // 부경대 부근
    private val startLon: Double = 129.1055,
    private val emitIntervalMs: Long = 1_000,
    private val startTimeMs: Long = 0L,
    private val accuracyMeter: Float = 5f,
    /** true면 각 방출 사이에 실제 delay를 준다 (데모용). false면 즉시 순차 방출. */
    private val realDelay: Boolean = false,
) : LocationProvider {

    /**
     * 하나의 러닝 구간.
     * @param durationSec 구간 지속 시간
     * @param paceSecPerKm 구간 페이스 (sec/km). null이면 정지(속도 0).
     */
    data class Segment(val durationSec: Int, val paceSecPerKm: Double?)

    private val _samples = MutableSharedFlow<LocationSample>(
        replay = 0,
        extraBufferCapacity = 1024,
    )
    override val samples: Flow<LocationSample> = _samples.asSharedFlow()

    /** 위도 1도 당 미터 (대략). 위경도 직진 이동에 사용. */
    private val metersPerDegLat = 111_320.0

    @Volatile
    private var running = false

    override suspend fun start() {
        running = true
        var lat = startLat
        val lon = startLon
        var timeMs = startTimeMs
        // 첫 앵커 샘플
        _samples.emit(LocationSample(lat, lon, timeMs, accuracyMeter))

        val intervalSec = emitIntervalMs / 1_000.0
        for (segment in segments) {
            if (!running) break
            val ticks = (segment.durationSec / intervalSec).toInt()
            var i = 0
            while (i < ticks && running) {
                timeMs += emitIntervalMs
                val distanceThisTick =
                    if (segment.paceSecPerKm == null || segment.paceSecPerKm <= 0.0) {
                        0.0
                    } else {
                        // speed(m/s) = 1000 / paceSecPerKm ; distance = speed * intervalSec
                        (1_000.0 / segment.paceSecPerKm) * intervalSec
                    }
                lat += distanceThisTick / metersPerDegLat
                _samples.emit(LocationSample(lat, lon, timeMs, accuracyMeter))
                if (realDelay) delay(emitIntervalMs)
                i++
            }
        }
    }

    override fun stop() {
        running = false
    }
}
