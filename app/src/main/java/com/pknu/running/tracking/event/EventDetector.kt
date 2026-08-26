package com.pknu.running.tracking.event

import com.pknu.running.tracking.model.RunningEvent
import com.pknu.running.tracking.model.RunningEventType
import com.pknu.running.tracking.model.RunningMetrics
import com.pknu.running.tracking.model.RunningTarget
import com.pknu.running.tracking.model.TrackingConfig
import kotlin.math.floor

/**
 * 러닝 이벤트 감지기 (Event Engine의 Time/Distance/Pace 부분).
 *
 * 매 tick마다 [RunningMetrics]를 받아 발생한 이벤트 목록을 반환한다.
 * 상태(마지막 발생 마일스톤, 쿨다운 등)를 가지므로 세션당 하나의 인스턴스를 사용한다.
 */
class EventDetector(
    private val config: TrackingConfig,
    private val target: RunningTarget,
) {

    private var lastFiredMinuteMark: Long = 0
    private var lastFiredKmMark: Int = 0
    private var lastStretchFired: Boolean = false

    // 페이스 저하 추적
    private var paceDropSustainStartSec: Long? = null
    private var lastPaceDropFiredSec: Long? = null

    /**
     * 현재 지표로부터 발생한 이벤트를 판정한다.
     * PAUSED/FINISHED 등 러닝 중이 아닐 때는 호출하지 않는 것을 전제로 한다.
     */
    fun detect(metrics: RunningMetrics): List<RunningEvent> {
        val events = mutableListOf<RunningEvent>()

        detectTimeMilestone(metrics)?.let { events.add(it) }
        detectDistanceMilestone(metrics)?.let { events.add(it) }
        detectPaceDrop(metrics)?.let { events.add(it) }
        detectLastStretch(metrics)?.let { events.add(it) }

        return events
    }

    private fun detectTimeMilestone(m: RunningMetrics): RunningEvent? {
        val interval = config.timeEventIntervalSec
        if (interval <= 0) return null
        val currentMark = m.elapsedTimeSec / interval * interval // interval 배수 중 가장 큰 값
        if (currentMark >= interval && currentMark > lastFiredMinuteMark) {
            lastFiredMinuteMark = currentMark
            return RunningEvent(
                type = RunningEventType.TIME_MILESTONE,
                occurredAtMs = m.timestampMs,
                elapsedTimeSec = m.elapsedTimeSec,
                totalDistanceMeter = m.totalDistanceMeter,
                metadata = buildMap {
                    put("minute", (currentMark / 60).toInt())
                    (m.smoothedPaceSecPerKm ?: m.currentPaceSecPerKm)?.let { put("currentPace", it) }
                },
            )
        }
        return null
    }

    private fun detectDistanceMilestone(m: RunningMetrics): RunningEvent? {
        val interval = config.distanceEventIntervalMeter
        if (interval <= 0) return null
        val currentKmMark = floor(m.totalDistanceMeter / interval).toInt()
        if (currentKmMark >= 1 && currentKmMark > lastFiredKmMark) {
            lastFiredKmMark = currentKmMark
            return RunningEvent(
                type = RunningEventType.DISTANCE_MILESTONE,
                occurredAtMs = m.timestampMs,
                elapsedTimeSec = m.elapsedTimeSec,
                totalDistanceMeter = m.totalDistanceMeter,
                metadata = buildMap {
                    put("km", currentKmMark)
                    m.averagePaceSecPerKm?.let { put("averagePace", it) }
                },
            )
        }
        return null
    }

    private fun detectPaceDrop(m: RunningMetrics): RunningEvent? {
        val targetPace = target.paceSecPerKm ?: return null
        val pace = m.smoothedPaceSecPerKm ?: run {
            paceDropSustainStartSec = null
            return null
        }

        val threshold = targetPace * (1 + config.paceDropRatio)
        val isSlow = pace > threshold

        if (!isSlow) {
            paceDropSustainStartSec = null
            return null
        }

        // 저하 시작 시각 기록
        val start = paceDropSustainStartSec ?: run {
            paceDropSustainStartSec = m.elapsedTimeSec
            m.elapsedTimeSec
        }

        // 지속 시간 미달이면 아직 발생 안 함
        if (m.elapsedTimeSec - start < config.paceDropSustainSec) return null

        // 쿨다운 검사
        val lastFired = lastPaceDropFiredSec
        if (lastFired != null && m.elapsedTimeSec - lastFired < config.paceDropCooldownSec) {
            return null
        }

        lastPaceDropFiredSec = m.elapsedTimeSec
        // 다음 발생을 위해 지속 타이머 리셋 (쿨다운 이후 다시 지속되어야 재발생)
        paceDropSustainStartSec = null

        return RunningEvent(
            type = RunningEventType.PACE_DROP,
            occurredAtMs = m.timestampMs,
            elapsedTimeSec = m.elapsedTimeSec,
            totalDistanceMeter = m.totalDistanceMeter,
            metadata = mapOf(
                "currentPace" to pace,
                "targetPace" to targetPace,
            ),
        )
    }

    private fun detectLastStretch(m: RunningMetrics): RunningEvent? {
        if (lastStretchFired) return null
        val targetDistance = target.distanceMeter ?: return null
        val enterThreshold = targetDistance - config.lastStretchMeter
        if (m.totalDistanceMeter >= enterThreshold) {
            lastStretchFired = true
            return RunningEvent(
                type = RunningEventType.LAST_STRETCH,
                occurredAtMs = m.timestampMs,
                elapsedTimeSec = m.elapsedTimeSec,
                totalDistanceMeter = m.totalDistanceMeter,
                metadata = mapOf(
                    "remainingMeter" to (targetDistance - m.totalDistanceMeter).coerceAtLeast(0.0),
                ),
            )
        }
        return null
    }

    fun reset() {
        lastFiredMinuteMark = 0
        lastFiredKmMark = 0
        lastStretchFired = false
        paceDropSustainStartSec = null
        lastPaceDropFiredSec = null
    }
}
