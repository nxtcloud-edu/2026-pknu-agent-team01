package com.pknu.running.tracking.model

/**
 * 러닝 이벤트 종류. 다른 기능(음악/TTS)은 이 타입을 구독하여 자기 규칙을 실행한다.
 */
enum class RunningEventType {
    /** 시간 마일스톤 (5분마다 반복). */
    TIME_MILESTONE,

    /** 거리 마일스톤 (1km마다 반복). */
    DISTANCE_MILESTONE,

    /** 목표 페이스보다 느려짐. */
    PACE_DROP,

    /** 마지막 500m 구간 진입. */
    LAST_STRETCH,
}

/**
 * 러닝 이벤트 (출력).
 *
 * @property type 이벤트 종류
 * @property occurredAtMs 발생 시각 (epoch millis)
 * @property elapsedTimeSec 발생 시점 경과 시간 (second)
 * @property totalDistanceMeter 발생 시점 누적 거리 (meter)
 * @property metadata 부가 정보. 예: {"km": 2}, {"minute": 5}, {"currentPace":..., "targetPace":...}
 */
data class RunningEvent(
    val type: RunningEventType,
    val occurredAtMs: Long,
    val elapsedTimeSec: Long,
    val totalDistanceMeter: Double,
    val metadata: Map<String, Any> = emptyMap(),
)

/**
 * finish() 결과 요약. 리포트/기록 담당(기능 4)이 소비한다. DB 저장은 하지 않는다.
 */
data class RunRecordSummary(
    val totalDistanceMeter: Double,
    val elapsedTimeSec: Long,
    val averagePaceSecPerKm: Double?,
    val bestPaceSecPerKm: Double?,
    val events: List<RunningEvent>,
)
