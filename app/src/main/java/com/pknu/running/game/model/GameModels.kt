package com.pknu.running.game.model

/**
 * 러닝 모드. 각 모드는 서로 다른 이벤트 규칙, TTS 세트, 연출을 가진다 (FR-03).
 */
enum class RunningMode {
    /** 기본 — 최소한의 시간/거리/페이스 안내. */
    BASIC,

    /** 마라톤 — 장거리 페이스 유지 가이드. */
    MARATHON,

    /** 인터벌 — 빠른 구간/회복 구간 반복. */
    INTERVAL,

    /** 국가대표 — 경기 중계 나레이션 + 가상 순위/추월. */
    NATIONAL_TEAM,
}

/**
 * 인터벌 모드 설정.
 *
 * @property workSec 빠른 구간 길이 (초)
 * @property recoverySec 회복 구간 길이 (초)
 * @property sets 세트 수
 */
data class IntervalConfig(
    val workSec: Int = 60,
    val recoverySec: Int = 60,
    val sets: Int = 5,
)

/** 인터벌 구간 종류. */
enum class IntervalPhase { WORK, RECOVERY, DONE }

/**
 * 모드 실행 설정.
 *
 * @property mode 선택된 모드
 * @property intervalConfig 인터벌 모드일 때만 사용
 * @property randomEventEnabled 랜덤 이벤트 사용 여부
 */
data class ModeConfig(
    val mode: RunningMode,
    val intervalConfig: IntervalConfig = IntervalConfig(),
    val randomEventEnabled: Boolean = true,
)

/**
 * 랜덤 이벤트 종류 (FR-07).
 */
enum class RandomEventType {
    /** "앞에 잘생긴 사람이 나타났습니다." */
    IDEAL_TYPE_APPEARED,

    /** "라이벌이 따라오고 있습니다." */
    RIVAL_CHASING,

    /** "지금부터 30초 스퍼트!" */
    SPRINT_30S,

    /** "이번 노래가 끝날 때까지 달려볼까요?" */
    LAST_SONG_CHALLENGE,
}

/**
 * 나레이션 톤. TTS 음색/연출 선택에 사용.
 */
enum class NarrationTone { INFO, ENCOURAGE, DRAMATIC, FUN, MISSION }

/**
 * 재생할 나레이션 1건.
 *
 * @property text 화면 표시 및 TTS로 읽을 문구
 * @property tone 톤
 * @property priority 동시 발생 시 우선순위 (높을수록 우선)
 * @property cooldownKey 반복 억제용 키 (같은 키는 쿨다운 동안 재생 제한)
 */
data class Narration(
    val text: String,
    val tone: NarrationTone = NarrationTone.INFO,
    val priority: Int = 0,
    val cooldownKey: String? = null,
)

/**
 * 게임/연출 이벤트 (출력). UI·음악 등이 구독할 수 있다.
 */
sealed interface GameEvent {
    /** 나레이션 재생 요청. */
    data class Narrate(val narration: Narration) : GameEvent

    /** 인터벌 구간 전환. */
    data class IntervalChanged(val phase: IntervalPhase, val setIndex: Int, val totalSets: Int) : GameEvent

    /** 랜덤 이벤트 발생. */
    data class Random(val type: RandomEventType) : GameEvent

    /** 국가대표 모드 가상 순위 갱신. */
    data class RankUpdate(val rank: Int, val totalRunners: Int, val overtook: Boolean) : GameEvent
}
