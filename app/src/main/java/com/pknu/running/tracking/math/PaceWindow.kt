package com.pknu.running.tracking.math

import java.util.ArrayDeque

/**
 * 최근 N초 구간의 (거리, 경과시간)을 누적하여 현재 페이스를 계산하는 슬라이딩 윈도우.
 *
 * 경과시간(elapsedSec)은 일시정지를 제외한 러닝 시간 기준으로 넣는다.
 */
class PaceWindow(private val windowSec: Int) {

    private data class Point(val elapsedSec: Double, val cumulativeDistanceMeter: Double)

    private val points = ArrayDeque<Point>()

    /**
     * 새 표본을 추가한다.
     *
     * @param elapsedSec 러닝 시작 후 경과 시간 (일시정지 제외)
     * @param cumulativeDistanceMeter 누적 총 거리
     */
    fun add(elapsedSec: Double, cumulativeDistanceMeter: Double) {
        points.addLast(Point(elapsedSec, cumulativeDistanceMeter))
        trim(elapsedSec)
    }

    private fun trim(nowSec: Double) {
        // 윈도우 밖(오래된) 점 제거. 단, 경계 계산을 위해 창 시작 직전 점 1개는 남긴다.
        while (points.size > 1) {
            val second = points.elementAt(1)
            if (second.elapsedSec < nowSec - windowSec) {
                points.removeFirst()
            } else {
                break
            }
        }
    }

    /**
     * 윈도우 구간의 현재 페이스(sec/km)를 반환한다. 표본이 부족하거나 이동이 없으면 null.
     */
    fun currentPaceSecPerKm(): Double? {
        if (points.size < 2) return null
        val first = points.first
        val last = points.last
        val dist = last.cumulativeDistanceMeter - first.cumulativeDistanceMeter
        val dt = last.elapsedSec - first.elapsedSec
        return PaceCalculator.paceSecPerKm(dist, dt)
    }

    fun reset() {
        points.clear()
    }
}
