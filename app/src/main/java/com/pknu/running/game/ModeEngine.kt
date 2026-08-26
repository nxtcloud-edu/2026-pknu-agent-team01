package com.pknu.running.game

import com.pknu.running.game.model.GameEvent
import com.pknu.running.game.model.IntervalConfig
import com.pknu.running.game.model.IntervalPhase
import com.pknu.running.game.model.ModeConfig
import com.pknu.running.game.model.RunningMode
import com.pknu.running.tracking.model.RunningMetrics
import kotlin.math.max

/**
 * 선택된 모드의 규칙을 실행하는 엔진 (C-04).
 *
 * 매 tick(경과시간+지표)마다 호출되어, 모드별 연출 이벤트를 반환한다.
 * - INTERVAL: work/recovery 구간 타이머 관리, 구간 전환 시 이벤트 방출
 * - NATIONAL_TEAM: 목표 페이스 기반 가상 순위(Ghost Runner) 계산, 추월 이벤트
 * - MARATHON/BASIC: 별도 주기 연출 없음 (공통 마일스톤/페이스 이벤트는 GameDirector가 처리)
 */
class ModeEngine(private val config: ModeConfig) {

    // 인터벌 상태
    private var intervalPhase: IntervalPhase = IntervalPhase.WORK
    private var intervalSetIndex: Int = 1
    private var phaseStartSec: Long = 0
    private var intervalStarted = false

    // 국가대표 가상 순위 상태
    private var lastRank: Int = TOTAL_RUNNERS
    private var lastRankNarrateSec: Long = Long.MIN_VALUE

    /**
     * 모드별 tick 처리. 발생한 이벤트 목록을 반환한다.
     */
    fun tick(elapsedSec: Long, metrics: RunningMetrics): List<GameEvent> = when (config.mode) {
        RunningMode.INTERVAL -> tickInterval(elapsedSec)
        RunningMode.NATIONAL_TEAM -> tickNationalTeam(elapsedSec, metrics)
        else -> emptyList()
    }

    // ---------------------------------------------------------------- 인터벌

    private fun tickInterval(elapsedSec: Long): List<GameEvent> {
        val cfg = config.intervalConfig
        val events = mutableListOf<GameEvent>()

        if (!intervalStarted) {
            intervalStarted = true
            intervalPhase = IntervalPhase.WORK
            intervalSetIndex = 1
            phaseStartSec = elapsedSec
            events.add(GameEvent.IntervalChanged(IntervalPhase.WORK, 1, cfg.sets))
            return events
        }

        if (intervalPhase == IntervalPhase.DONE) return events

        val phaseLen = if (intervalPhase == IntervalPhase.WORK) cfg.workSec else cfg.recoverySec
        if (elapsedSec - phaseStartSec >= phaseLen) {
            phaseStartSec = elapsedSec
            when (intervalPhase) {
                IntervalPhase.WORK -> {
                    // work 끝 → recovery
                    intervalPhase = IntervalPhase.RECOVERY
                    events.add(GameEvent.IntervalChanged(IntervalPhase.RECOVERY, intervalSetIndex, cfg.sets))
                }
                IntervalPhase.RECOVERY -> {
                    // recovery 끝 → 다음 세트 work 또는 종료
                    if (intervalSetIndex >= cfg.sets) {
                        intervalPhase = IntervalPhase.DONE
                        events.add(GameEvent.IntervalChanged(IntervalPhase.DONE, intervalSetIndex, cfg.sets))
                    } else {
                        intervalSetIndex++
                        intervalPhase = IntervalPhase.WORK
                        events.add(GameEvent.IntervalChanged(IntervalPhase.WORK, intervalSetIndex, cfg.sets))
                    }
                }
                IntervalPhase.DONE -> {}
            }
        }
        return events
    }

    // ---------------------------------------------------------------- 국가대표

    /**
     * Ghost Runner 개념으로 가상 순위를 계산한다.
     * 사용자의 평균 페이스가 기준보다 빠를수록 순위가 올라간다.
     */
    private fun tickNationalTeam(elapsedSec: Long, metrics: RunningMetrics): List<GameEvent> {
        // 30초마다 순위 갱신
        if (elapsedSec - lastRankNarrateSec < RANK_UPDATE_INTERVAL_SEC) return emptyList()
        if (elapsedSec < RANK_UPDATE_INTERVAL_SEC) return emptyList()

        val pace = metrics.averagePaceSecPerKm ?: return emptyList()
        // 기준 페이스(REFERENCE_PACE)보다 빠르면 순위 상승. 페이스 격차를 순위로 환산.
        val diff = REFERENCE_PACE - pace // 양수면 기준보다 빠름
        val computedRank = when {
            diff >= 60 -> 1
            diff >= 30 -> 2
            diff >= 0 -> 3
            diff >= -30 -> 5
            else -> 7
        }.coerceIn(1, TOTAL_RUNNERS)

        val overtook = computedRank < lastRank
        lastRank = computedRank
        lastRankNarrateSec = elapsedSec
        return listOf(GameEvent.RankUpdate(computedRank, TOTAL_RUNNERS, overtook))
    }

    fun reset() {
        intervalStarted = false
        intervalPhase = IntervalPhase.WORK
        intervalSetIndex = 1
        phaseStartSec = 0
        lastRank = TOTAL_RUNNERS
        lastRankNarrateSec = Long.MIN_VALUE
    }

    companion object {
        private const val TOTAL_RUNNERS = 8
        private const val RANK_UPDATE_INTERVAL_SEC = 30L

        /** 국가대표 순위 기준 페이스 (sec/km). 이보다 빠르면 상위권. */
        private const val REFERENCE_PACE = 360.0 // 6'00"/km
    }
}
