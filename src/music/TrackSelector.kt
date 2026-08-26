package music

import music.model.MusicRule
import music.model.MusicTag
import music.model.PlayMode
import music.model.TaggedTrack

/**
 * 현재 MusicRule과 재생 이력을 기반으로 다음 곡을 선택한다.
 */
class TrackSelector {

    companion object {
        private const val HISTORY_LIMIT = 5
    }

    /**
     * 다음 곡을 선택한다.
     *
     * @param playlist 태깅된 플레이리스트
     * @param rule 현재 활성 음악 규칙
     * @param playHistory 최근 재생된 trackId 목록
     * @param currentIndex 현재 곡의 플레이리스트 내 인덱스 (순차 재생용)
     * @param playMode 재생 모드 (순차/셔플)
     * @return 선택된 곡, 플레이리스트가 비어있으면 null
     */
    fun selectNext(
        playlist: List<TaggedTrack>,
        rule: MusicRule,
        playHistory: List<String>,
        currentIndex: Int = -1,
        playMode: PlayMode = PlayMode.SEQUENTIAL
    ): TaggedTrack? {
        if (playlist.isEmpty()) return null

        // 1. preferredTags 기준으로 후보 필터링
        val candidates = filterByTags(playlist, rule.preferredTags, playHistory)
        if (candidates.isNotEmpty()) {
            return pickFromCandidates(candidates, playMode, playlist, currentIndex)
        }

        // 2. fallbackTag로 재시도
        val fallbackCandidates = filterByTags(playlist, listOf(rule.fallbackTag), playHistory)
        if (fallbackCandidates.isNotEmpty()) {
            return pickFromCandidates(fallbackCandidates, playMode, playlist, currentIndex)
        }

        // 3. 이력 무시하고 전체에서 선택
        val allCandidates = filterByTags(playlist, rule.preferredTags, emptyList())
        if (allCandidates.isNotEmpty()) {
            return pickFromCandidates(allCandidates, playMode, playlist, currentIndex)
        }

        // 4. 최종 fallback: 순서대로 다음 곡
        return nextSequential(playlist, currentIndex)
    }

    private fun filterByTags(
        playlist: List<TaggedTrack>,
        tags: List<MusicTag>,
        playHistory: List<String>
    ): List<TaggedTrack> {
        val recentHistory = playHistory.takeLast(HISTORY_LIMIT).toSet()
        return playlist.filter { track ->
            track.tags.any { it in tags } && track.track.id !in recentHistory
        }
    }

    private fun pickFromCandidates(
        candidates: List<TaggedTrack>,
        playMode: PlayMode,
        playlist: List<TaggedTrack>,
        currentIndex: Int
    ): TaggedTrack {
        return when (playMode) {
            PlayMode.SHUFFLE -> candidates.random()
            PlayMode.SEQUENTIAL -> {
                // 순차 모드: 후보 중 currentIndex 이후에 가장 가까운 곡
                val nextInOrder = candidates
                    .filter { playlist.indexOf(it) > currentIndex }
                    .minByOrNull { playlist.indexOf(it) }
                nextInOrder ?: candidates.first()
            }
        }
    }

    private fun nextSequential(playlist: List<TaggedTrack>, currentIndex: Int): TaggedTrack {
        val nextIndex = (currentIndex + 1) % playlist.size
        return playlist[nextIndex]
    }
}
