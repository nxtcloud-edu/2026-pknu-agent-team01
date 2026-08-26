package com.pknu.running.game

import com.pknu.running.game.model.RandomEventType
import kotlin.random.Random

/**
 * 랜덤 이벤트 엔진 (FR-07).
 *
 * 일정 주기로 호출되어, 설정 확률과 쿨다운을 기준으로 이벤트 발생 여부를 판정한다.
 * 같은 이벤트가 최근 발생했다면 쿨다운 동안 재발생을 억제한다.
 *
 * @param probability 판정마다 이벤트가 발생할 확률 (0~1)
 * @param cooldownSec 같은 이벤트 재발생 억제 시간
 * @param globalCooldownSec 이벤트 종류 무관 최소 간격
 * @param random 난수원 (테스트에서 고정 가능)
 */
class RandomEventEngine(
    private val probability: Double = 0.6,
    private val cooldownSec: Long = 30,
    private val globalCooldownSec: Long = 10,
    private val random: Random = Random.Default,
) {

    private val lastFiredAtSec = mutableMapOf<RandomEventType, Long>()
    private var lastAnyFiredAtSec: Long = Long.MIN_VALUE

    /**
     * 현재 경과 시간(초)을 기준으로 이벤트 발생을 판정한다.
     * 발생하지 않으면 null.
     */
    fun tick(elapsedSec: Long): RandomEventType? {
        // 전역 쿨다운
        if (elapsedSec - lastAnyFiredAtSec < globalCooldownSec) return null

        if (random.nextDouble() >= probability) return null

        // 쿨다운이 지난 후보들 중 하나 선택
        val candidates = RandomEventType.entries.filter { type ->
            val last = lastFiredAtSec[type]
            last == null || elapsedSec - last >= cooldownSec
        }
        if (candidates.isEmpty()) return null

        val chosen = candidates[random.nextInt(candidates.size)]
        lastFiredAtSec[chosen] = elapsedSec
        lastAnyFiredAtSec = elapsedSec
        return chosen
    }

    fun reset() {
        lastFiredAtSec.clear()
        lastAnyFiredAtSec = Long.MIN_VALUE
    }
}
