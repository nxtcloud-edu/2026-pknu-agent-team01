package com.pknu.running.tracking.model

/**
 * 러닝 목표. 모든 값은 선택 사항이다 (FR-02).
 *
 * @property distanceMeter 목표 거리 (meter)
 * @property durationSec 목표 시간 (second)
 * @property paceSecPerKm 목표 페이스 (second per km)
 */
data class RunningTarget(
    val distanceMeter: Double? = null,
    val durationSec: Long? = null,
    val paceSecPerKm: Double? = null,
)
