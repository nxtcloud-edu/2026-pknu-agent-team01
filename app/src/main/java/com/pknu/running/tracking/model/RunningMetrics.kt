package com.pknu.running.tracking.model

/**
 * 실시간 러닝 지표 (출력). application-design.md의 RunningMetrics와 정합.
 *
 * 페이스 단위는 sec/km. 멈춰 있거나 표본이 부족하면 null (0 나눗셈 방지).
 *
 * @property timestampMs 지표 생성 시각 (epoch millis)
 * @property elapsedTimeSec 러닝 시작 후 경과 시간 (일시정지 제외, second)
 * @property totalDistanceMeter 누적 이동 거리 (meter)
 * @property currentPaceSecPerKm 최근 구간 기준 현재 페이스 (sec/km) 또는 null
 * @property smoothedPaceSecPerKm 스무딩된 현재 페이스 (sec/km) 또는 null
 * @property averagePaceSecPerKm 전체 평균 페이스 (sec/km) 또는 null
 * @property gpsAccuracyMeter 최근 GPS 정확도 (meter)
 * @property state 현재 세션 상태
 */
data class RunningMetrics(
    val timestampMs: Long = 0,
    val elapsedTimeSec: Long = 0,
    val totalDistanceMeter: Double = 0.0,
    val currentPaceSecPerKm: Double? = null,
    val smoothedPaceSecPerKm: Double? = null,
    val averagePaceSecPerKm: Double? = null,
    val gpsAccuracyMeter: Float = 0f,
    val state: RunningState = RunningState.READY,
)
