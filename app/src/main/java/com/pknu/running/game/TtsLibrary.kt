package com.pknu.running.game

import com.pknu.running.game.model.Narration
import com.pknu.running.game.model.NarrationTone
import com.pknu.running.game.model.RandomEventType
import com.pknu.running.game.model.RunningMode
import com.pknu.running.ui.Format
import kotlin.random.Random

/**
 * 사전 제작 TTS 문구 라이브러리 (FR-06).
 *
 * 모드/이벤트/톤별로 여러 문구 후보를 관리하고, 매번 난수로 하나를 선택한다.
 * 동적 수치(페이스/거리/분)는 문구 생성 시 채운다.
 */
object TtsLibrary {

    private val rng = Random.Default

    private fun pick(vararg options: String): String = options[rng.nextInt(options.size)]

    // ---------------------------------------------------------------- 시간/거리 안내

    fun timeMilestone(minute: Int, paceSecPerKm: Double?, mode: RunningMode): Narration {
        val pace = Format.pace(paceSecPerKm)
        val text = when (mode) {
            RunningMode.NATIONAL_TEAM -> pick(
                "경기 시작 ${minute}분 경과! 선수, 현재 페이스 $pace 로 순항 중입니다!",
                "${minute}분 경과! 선수의 페이스가 $pace, 아주 좋습니다!",
                "중계석입니다. ${minute}분이 지났고 페이스는 $pace 입니다. 대단합니다!",
            )
            RunningMode.MARATHON -> pick(
                "${minute}분 경과. 페이스 $pace 유지하고 있습니다. 호흡을 고르게 가져가세요.",
                "${minute}분째, 현재 $pace. 무리하지 말고 이 페이스를 지켜봐요.",
                "${minute}분 지났어요. $pace 페이스, 장거리엔 꾸준함이 답입니다.",
            )
            else -> pick(
                "${minute}분째 달리고 있어요. 현재 페이스는 $pace 입니다.",
                "벌써 ${minute}분! 페이스 $pace, 잘 달리고 있어요.",
                "${minute}분 경과했어요. 지금 페이스는 $pace 예요.",
            )
        }
        val tone = if (mode == RunningMode.NATIONAL_TEAM) NarrationTone.DRAMATIC else NarrationTone.INFO
        return Narration(text, tone, priority = 3, cooldownKey = "time")
    }

    fun distanceMilestone(km: Int, mode: RunningMode): Narration {
        val text = when (mode) {
            RunningMode.NATIONAL_TEAM -> pick(
                "${km}킬로미터 지점 통과! 선수 여전히 좋은 흐름입니다!",
                "${km}km 돌파! 관중들의 함성이 들리는 듯합니다!",
                "${km}킬로미터 통과, 선수 페이스가 살아있습니다!",
            )
            RunningMode.MARATHON -> pick(
                "${km}km 통과. 잘하고 있어요. 페이스 유지가 핵심입니다.",
                "${km}킬로미터 지났어요. 리듬을 유지해 봅시다.",
                "${km}km 완료. 물 한 모금 생각날 때죠? 페이스 지켜요.",
            )
            else -> pick(
                "${km}킬로미터를 지났어요. 좋아요!",
                "${km}km 돌파! 이 기세 그대로!",
                "벌써 ${km}킬로미터! 잘하고 있어요.",
            )
        }
        return Narration(text, NarrationTone.INFO, priority = 4, cooldownKey = "dist")
    }

    fun paceDrop(mode: RunningMode): Narration {
        val text = when (mode) {
            RunningMode.MARATHON -> pick(
                "페이스가 조금 느려졌어요. 목표 페이스로 다시 끌어올려 볼까요?",
                "속도가 살짝 떨어졌어요. 팔을 조금 더 흔들어봐요.",
                "페이스 관리, 지금이 중요해요. 조금만 힘내요.",
            )
            RunningMode.NATIONAL_TEAM -> pick(
                "선수 페이스가 떨어지고 있습니다! 추격을 허용하면 안 됩니다!",
                "위기입니다! 페이스가 느려졌어요, 정신 차려야 합니다!",
                "뒤에서 선수들이 좁혀옵니다! 속도를 올려야 해요!",
            )
            else -> pick(
                "조금 느려졌어요. 힘내서 다시 속도를 올려봐요!",
                "살짝 처졌어요. 다시 파이팅!",
                "페이스가 내려갔어요. 할 수 있어요, 조금만 더!",
            )
        }
        return Narration(text, NarrationTone.ENCOURAGE, priority = 5, cooldownKey = "pace_drop")
    }

    fun lastStretch(mode: RunningMode): Narration {
        val text = when (mode) {
            RunningMode.NATIONAL_TEAM -> pick(
                "마지막 500미터! 선수, 마지막 스퍼트를 올립니다!",
                "결승선이 보입니다! 남은 500미터, 전력 질주!",
            )
            else -> pick(
                "마지막 500미터예요! 끝까지 힘내세요!",
                "이제 500미터 남았어요! 유종의 미를 거둬요!",
                "거의 다 왔어요, 마지막 500미터 화이팅!",
            )
        }
        return Narration(text, NarrationTone.DRAMATIC, priority = 8, cooldownKey = "last_stretch")
    }

    // ---------------------------------------------------------------- 모드 시작/인터벌

    fun modeStart(mode: RunningMode): Narration {
        val text = when (mode) {
            RunningMode.BASIC -> pick(
                "기본 모드로 러닝을 시작합니다. 가볍게 출발해요!",
                "자, 러닝 시작할게요. 오늘도 즐겁게 달려봐요!",
            )
            RunningMode.MARATHON -> pick(
                "마라톤 모드입니다. 페이스를 꾸준히 유지해 볼까요?",
                "장거리 모드 시작! 서두르지 말고 리듬을 잡아요.",
            )
            RunningMode.INTERVAL -> pick(
                "인터벌 모드입니다. 빠른 구간과 회복 구간을 반복합니다. 준비됐죠?",
                "인터벌 시작! 전력과 회복을 번갈아 갑니다. 화이팅!",
            )
            RunningMode.NATIONAL_TEAM -> pick(
                "국가대표 모드! 지금부터 당신은 국가대표 선수입니다. 경기를 시작합니다!",
                "관중들의 함성과 함께 경기가 시작됩니다! 대한민국 대표 선수, 출발!",
            )
        }
        return Narration(text, NarrationTone.INFO, priority = 6, cooldownKey = "mode_start")
    }

    fun intervalWork(setIndex: Int, totalSets: Int): Narration =
        Narration(
            pick(
                "$setIndex/$totalSets 세트, 빠른 구간 시작! 속도를 올리세요!",
                "$setIndex/$totalSets 세트, 전력 질주 구간입니다! 폭발적으로!",
                "지금부터 $setIndex 세트 스피드 구간! 최대한 빠르게!",
            ),
            NarrationTone.MISSION, priority = 7, cooldownKey = "interval",
        )

    fun intervalRecovery(setIndex: Int, totalSets: Int): Narration =
        Narration(
            pick(
                "$setIndex/$totalSets 세트, 회복 구간입니다. 호흡을 고르세요.",
                "$setIndex/$totalSets 세트, 이제 회복 구간! 천천히 숨을 돌려요.",
                "회복 구간입니다. 페이스를 낮추고 몸을 회복시켜요.",
            ),
            NarrationTone.INFO, priority = 7, cooldownKey = "interval",
        )

    fun intervalDone(): Narration =
        Narration(
            pick(
                "모든 인터벌 세트를 완료했어요! 정말 잘했어요!",
                "인터벌 완주! 오늘 정말 대단했어요!",
            ),
            NarrationTone.ENCOURAGE, priority = 7,
        )

    // ---------------------------------------------------------------- 국가대표 가상 순위

    fun rankUpdate(rank: Int, total: Int, overtook: Boolean): Narration {
        val text = if (overtook) {
            pick(
                "선수, 한 명을 추월했습니다! 현재 $total 명 중 ${rank}위입니다!",
                "추월 성공! 순위가 올라 $total 명 중 ${rank}위입니다!",
                "앞 선수를 제쳤습니다! 지금 ${rank}위!",
            )
        } else {
            pick(
                "현재 순위 $total 명 중 ${rank}위입니다. 앞 선수를 노려봅시다!",
                "지금 ${rank}위입니다. 조금만 더 힘내면 순위를 올릴 수 있어요!",
                "${rank}위 유지 중입니다. 추격의 고삐를 늦추지 마세요!",
            )
        }
        return Narration(text, NarrationTone.DRAMATIC, priority = 5, cooldownKey = "rank")
    }

    // ---------------------------------------------------------------- 랜덤 이벤트

    fun randomEvent(type: RandomEventType): Narration = when (type) {
        RandomEventType.IDEAL_TYPE_APPEARED -> Narration(
            pick(
                "앞에 잘생긴 사람이 나타났습니다! 멋진 모습 보여주세요!",
                "이상형이 지나갑니다! 자세 바로잡고 폼 나게 달려요!",
                "저기 훈훈한 사람 발견! 지금이 폼 잡을 타이밍이에요!",
            ),
            NarrationTone.FUN, priority = 6,
        )
        RandomEventType.RIVAL_CHASING -> Narration(
            pick(
                "라이벌이 따라오고 있습니다! 따라잡히지 마세요!",
                "뒤에서 라이벌이 바짝 붙었어요! 속도를 유지해요!",
                "경쟁자가 추격 중입니다! 격차를 벌려봅시다!",
            ),
            NarrationTone.DRAMATIC, priority = 6,
        )
        RandomEventType.SPRINT_30S -> Narration(
            pick(
                "지금부터 30초 스퍼트! 전력으로 달려요!",
                "30초 전력 질주 미션! 가진 힘을 다 써봐요!",
                "스퍼트 타임! 30초만 폭발적으로 달려볼까요?",
            ),
            NarrationTone.MISSION, priority = 7,
        )
        RandomEventType.LAST_SONG_CHALLENGE -> Narration(
            pick(
                "이번 노래가 끝날 때까지 달려볼까요?",
                "지금 나오는 곡이 끝날 때까지 멈추지 말고 달려요!",
                "한 곡만 더! 이 노래 끝까지 완주 도전!",
            ),
            NarrationTone.FUN, priority = 6,
        )
    }
}
