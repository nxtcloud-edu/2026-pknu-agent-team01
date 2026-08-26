package com.pknu.running.tracking.model

/**
 * 러닝 트래킹 튜닝 파라미터. 기본값은 docs/features/01-running-tracking/01-inception.md 참고.
 */
data class TrackingConfig(
    /** GPS 요청 주기 (ms). */
    val locationIntervalMs: Long = 1_000,
    /** metrics 방출 주기 (ms). 정지 중에도 시간은 흐른다. */
    val metricsTickMs: Long = 1_000,
    /** 현재 페이스 계산 창 (second). */
    val currentPaceWindowSec: Int = 10,
    /** 페이스 스무딩 EMA 계수 (0~1). 클수록 반응이 빠르다. */
    val emaAlpha: Double = 0.2,
    /** 이 값보다 부정확한 샘플은 거리 계산에서 제외 (meter). */
    val maxAccuracyMeter: Double = 30.0,
    /** 이 값을 초과하는 이동은 GPS 점프로 간주하여 폐기 (m/s). */
    val maxSpeedMps: Double = 12.0,
    /** 시간 이벤트 반복 주기 (second). 기본 5분. */
    val timeEventIntervalSec: Long = 300,
    /** 거리 이벤트 반복 주기 (meter). 기본 1km. */
    val distanceEventIntervalMeter: Double = 1_000.0,
    /** 목표 대비 이 비율 이상 느리면 페이스 저하로 간주. */
    val paceDropRatio: Double = 0.10,
    /** 페이스 저하가 이만큼 지속되어야 이벤트 발생 (second). */
    val paceDropSustainSec: Long = 5,
    /** 페이스 저하 이벤트 재발생 억제 시간 (second). */
    val paceDropCooldownSec: Long = 30,
    /** 마지막 구간 진입 거리 (meter). */
    val lastStretchMeter: Double = 500.0,
)
