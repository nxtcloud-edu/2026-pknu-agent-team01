package com.pknu.running.music.model

/**
 * 음악 태그 — 트랙의 분위기/용도를 분류한다.
 */
enum class MusicTag {
    NORMAL,
    HIGH_ENERGY,
    RECOVERY,
    LOVE,
    DRAMATIC,
    FAVORITE
}

/**
 * 재생 상태
 */
enum class PlaybackState {
    IDLE,
    PLAYING,
    PAUSED,
    LOADING
}

/**
 * 재생 모드
 */
enum class PlayMode {
    SEQUENTIAL,
    SHUFFLE
}

/**
 * 트랙 기본 정보
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val albumArt: String? = null,
    val isFavorite: Boolean = false
)

/**
 * 트랙 메타데이터 (외부 API에서 획득)
 */
data class TrackMetadata(
    val trackId: String,
    val bpm: Int? = null,
    val genre: String? = null,
    val energy: Float? = null,
    val valence: Float? = null
)

/**
 * 태그가 부여된 트랙
 */
data class TaggedTrack(
    val track: Track,
    val tags: Set<MusicTag>,
    val metadata: TrackMetadata? = null
)

/**
 * 플레이리스트
 */
data class Playlist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val imageUrl: String? = null
)

/**
 * 음악 선곡 규칙 — 이벤트/페이스에 따라 변경된다.
 */
data class MusicRule(
    val preferredTags: List<MusicTag>,
    val fallbackTag: MusicTag = MusicTag.NORMAL,
    val source: String = "default"
) {
    companion object {
        val DEFAULT = MusicRule(
            preferredTags = listOf(MusicTag.NORMAL),
            fallbackTag = MusicTag.NORMAL,
            source = "default"
        )
    }
}

/**
 * 곡 재생 로그 (리포트용)
 */
data class TrackPlayLog(
    val trackId: String,
    val startedAt: Long,
    val endedAt: Long,
    val musicRule: MusicRule,
    val wasEventTrack: Boolean = false,
    val averagePaceDuringTrack: Float? = null
)
