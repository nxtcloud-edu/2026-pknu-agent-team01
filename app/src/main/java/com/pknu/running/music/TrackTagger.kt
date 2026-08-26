package com.pknu.running.music

import com.pknu.running.music.model.MusicTag
import com.pknu.running.music.model.TaggedTrack
import com.pknu.running.music.model.Track
import com.pknu.running.music.model.TrackMetadata

/**
 * 트랙의 BPM/장르/즐겨찾기 정보를 기반으로 MusicTag를 부여한다.
 */
class TrackTagger {

    companion object {
        private const val HIGH_ENERGY_BPM = 130
        private const val RECOVERY_BPM = 100

        private val LOVE_GENRES = setOf("romance", "ballad", "love", "r&b")
        private val DRAMATIC_GENRES = setOf("epic", "cinematic", "orchestral", "soundtrack")
    }

    fun tagTrack(track: Track, metadata: TrackMetadata?): TaggedTrack {
        val tags = mutableSetOf<MusicTag>()

        // BPM 기반 분류
        val bpm = metadata?.bpm
        when {
            bpm == null -> tags.add(MusicTag.NORMAL)
            bpm > HIGH_ENERGY_BPM -> tags.add(MusicTag.HIGH_ENERGY)
            bpm < RECOVERY_BPM -> tags.add(MusicTag.RECOVERY)
            else -> tags.add(MusicTag.NORMAL)
        }

        // 장르 기반 추가 태그
        val genre = metadata?.genre?.lowercase()
        if (genre != null) {
            if (LOVE_GENRES.any { genre.contains(it) }) {
                tags.add(MusicTag.LOVE)
            }
            if (DRAMATIC_GENRES.any { genre.contains(it) }) {
                tags.add(MusicTag.DRAMATIC)
            }
        }

        // 즐겨찾기
        if (track.isFavorite) {
            tags.add(MusicTag.FAVORITE)
        }

        return TaggedTrack(
            track = track,
            tags = tags,
            metadata = metadata
        )
    }

    fun tagPlaylist(tracks: List<Track>, metadataMap: Map<String, TrackMetadata?>): List<TaggedTrack> {
        return tracks.map { track ->
            tagTrack(track, metadataMap[track.id])
        }
    }
}
