package com.pknu.running.tracking.math

/**
 * 페이스 계산 및 스무딩 유틸.
 *
 * 페이스 단위는 sec/km. 거리 또는 시간이 0이면 null을 반환한다 (0 나눗셈 방지).
 */
object PaceCalculator {

    private const val METERS_PER_KM = 1_000.0

    /**
     * 주어진 거리(meter)와 시간(second)으로부터 페이스(sec/km)를 계산한다.
     * 거리가 0 이하이거나 시간이 0 이하이면 null.
     */
    fun paceSecPerKm(distanceMeter: Double, elapsedSec: Double): Double? {
        if (distanceMeter <= 0.0 || elapsedSec <= 0.0) return null
        return elapsedSec / (distanceMeter / METERS_PER_KM)
    }

    /**
     * 지수이동평균(EMA)을 적용한다.
     *
     * @param current 이번 표본 값 (없으면 null)
     * @param previousSmoothed 직전 스무딩 값 (없으면 null)
     * @param alpha EMA 계수 (0~1)
     * @return 새 스무딩 값. current가 null이면 previousSmoothed를 유지한다.
     */
    fun ema(current: Double?, previousSmoothed: Double?, alpha: Double): Double? {
        if (current == null) return previousSmoothed
        if (previousSmoothed == null) return current
        return alpha * current + (1 - alpha) * previousSmoothed
    }
}
