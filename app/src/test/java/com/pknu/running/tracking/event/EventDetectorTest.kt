package com.pknu.running.tracking.event

import com.google.common.truth.Truth.assertThat
import com.pknu.running.tracking.model.RunningEventType
import com.pknu.running.tracking.model.RunningMetrics
import com.pknu.running.tracking.model.RunningState
import com.pknu.running.tracking.model.RunningTarget
import com.pknu.running.tracking.model.TrackingConfig
import org.junit.Test

class EventDetectorTest {

    private val config = TrackingConfig()

    private fun metrics(
        elapsedSec: Long = 0,
        distance: Double = 0.0,
        smoothedPace: Double? = null,
    ) = RunningMetrics(
        timestampMs = elapsedSec * 1000,
        elapsedTimeSec = elapsedSec,
        totalDistanceMeter = distance,
        currentPaceSecPerKm = smoothedPace,
        smoothedPaceSecPerKm = smoothedPace,
        averagePaceSecPerKm = smoothedPace,
        gpsAccuracyMeter = 5f,
        state = RunningState.RUNNING,
    )

    @Test
    fun `time milestone fires at 5 minutes`() {
        val detector = EventDetector(config, RunningTarget())
        assertThat(detector.detect(metrics(elapsedSec = 299)).map { it.type })
            .doesNotContain(RunningEventType.TIME_MILESTONE)
        val events = detector.detect(metrics(elapsedSec = 300))
        assertThat(events.map { it.type }).contains(RunningEventType.TIME_MILESTONE)
        assertThat(events.first { it.type == RunningEventType.TIME_MILESTONE }.metadata["minute"])
            .isEqualTo(5)
    }

    @Test
    fun `time milestone repeats every 5 minutes without duplicates`() {
        val detector = EventDetector(config, RunningTarget())
        detector.detect(metrics(elapsedSec = 300))
        // 6분 시점에는 다시 발생하지 않음 (다음은 10분)
        assertThat(detector.detect(metrics(elapsedSec = 360)).map { it.type })
            .doesNotContain(RunningEventType.TIME_MILESTONE)
        // 10분 시점 재발생
        val at10 = detector.detect(metrics(elapsedSec = 600))
        assertThat(at10.map { it.type }).contains(RunningEventType.TIME_MILESTONE)
        assertThat(at10.first { it.type == RunningEventType.TIME_MILESTONE }.metadata["minute"])
            .isEqualTo(10)
    }

    @Test
    fun `distance milestone fires each km and repeats`() {
        val detector = EventDetector(config, RunningTarget())
        assertThat(detector.detect(metrics(distance = 999.0)).map { it.type })
            .doesNotContain(RunningEventType.DISTANCE_MILESTONE)
        val at1km = detector.detect(metrics(distance = 1000.0))
        assertThat(at1km.first { it.type == RunningEventType.DISTANCE_MILESTONE }.metadata["km"])
            .isEqualTo(1)
        // 1.5km 에서는 재발생 없음
        assertThat(detector.detect(metrics(distance = 1500.0)).map { it.type })
            .doesNotContain(RunningEventType.DISTANCE_MILESTONE)
        val at2km = detector.detect(metrics(distance = 2000.0))
        assertThat(at2km.first { it.type == RunningEventType.DISTANCE_MILESTONE }.metadata["km"])
            .isEqualTo(2)
    }

    @Test
    fun `pace drop fires after sustained slowness and respects cooldown`() {
        // 목표 300s/km. 10% 임계 → 330 초과가 5초 지속되어야 발생.
        val detector = EventDetector(config, RunningTarget(paceSecPerKm = 300.0))
        val slow = 360.0 // 20% 느림

        // 저하 시작
        assertThat(detector.detect(metrics(elapsedSec = 10, smoothedPace = slow))).isEmpty()
        // 4초 경과 (아직 5초 미만)
        assertThat(detector.detect(metrics(elapsedSec = 14, smoothedPace = slow))).isEmpty()
        // 5초 지속 → 발생
        val fired = detector.detect(metrics(elapsedSec = 15, smoothedPace = slow))
        assertThat(fired.map { it.type }).contains(RunningEventType.PACE_DROP)

        // 쿨다운(30s) 내에는 재발생 안 함
        assertThat(detector.detect(metrics(elapsedSec = 40, smoothedPace = slow)).map { it.type })
            .doesNotContain(RunningEventType.PACE_DROP)
    }

    @Test
    fun `pace drop resets when pace recovers`() {
        val detector = EventDetector(config, RunningTarget(paceSecPerKm = 300.0))
        detector.detect(metrics(elapsedSec = 10, smoothedPace = 360.0)) // 저하 시작
        // 회복
        detector.detect(metrics(elapsedSec = 12, smoothedPace = 290.0))
        // 다시 느려짐 → 지속 타이머 재시작, 13초에는 아직 발생 안 함
        assertThat(detector.detect(metrics(elapsedSec = 13, smoothedPace = 360.0))).isEmpty()
    }

    @Test
    fun `no pace drop when target pace absent`() {
        val detector = EventDetector(config, RunningTarget())
        repeat(10) { i ->
            assertThat(detector.detect(metrics(elapsedSec = i.toLong(), smoothedPace = 500.0)).map { it.type })
                .doesNotContain(RunningEventType.PACE_DROP)
        }
    }

    @Test
    fun `last stretch fires once when entering final 500m`() {
        val detector = EventDetector(config, RunningTarget(distanceMeter = 5000.0))
        // 4499m: 아직 진입 전
        assertThat(detector.detect(metrics(distance = 4499.0)).map { it.type })
            .doesNotContain(RunningEventType.LAST_STRETCH)
        // 4500m: 진입 (5000 - 500)
        val fired = detector.detect(metrics(distance = 4500.0))
        assertThat(fired.map { it.type }).contains(RunningEventType.LAST_STRETCH)
        // 이후 재발생 없음
        assertThat(detector.detect(metrics(distance = 4800.0)).map { it.type })
            .doesNotContain(RunningEventType.LAST_STRETCH)
    }

    @Test
    fun `no last stretch when target distance absent`() {
        val detector = EventDetector(config, RunningTarget())
        assertThat(detector.detect(metrics(distance = 10_000.0)).map { it.type })
            .doesNotContain(RunningEventType.LAST_STRETCH)
    }
}
