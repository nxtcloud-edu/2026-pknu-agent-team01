package music

import music.model.MusicTag
import music.model.Track
import music.model.TrackMetadata
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TrackTaggerTest {

    private lateinit var tagger: TrackTagger

    @BeforeEach
    fun setUp() {
        tagger = TrackTagger()
    }

    private fun track(id: String = "t1", isFavorite: Boolean = false) = Track(
        id = id,
        title = "Test Track",
        artist = "Artist",
        durationMs = 180_000,
        isFavorite = isFavorite
    )

    private fun metadata(bpm: Int? = null, genre: String? = null) = TrackMetadata(
        trackId = "t1",
        bpm = bpm,
        genre = genre
    )

    @Test
    fun `BPM 150 트랙은 HIGH_ENERGY 태그를 받는다`() {
        val result = tagger.tagTrack(track(), metadata(bpm = 150))

        assertTrue(result.tags.contains(MusicTag.HIGH_ENERGY))
        assertFalse(result.tags.contains(MusicTag.NORMAL))
    }

    @Test
    fun `BPM 115 트랙은 NORMAL 태그를 받는다`() {
        val result = tagger.tagTrack(track(), metadata(bpm = 115))

        assertTrue(result.tags.contains(MusicTag.NORMAL))
    }

    @Test
    fun `BPM 85 트랙은 RECOVERY 태그를 받는다`() {
        val result = tagger.tagTrack(track(), metadata(bpm = 85))

        assertTrue(result.tags.contains(MusicTag.RECOVERY))
    }

    @Test
    fun `BPM null 트랙은 NORMAL 기본 태그를 받는다`() {
        val result = tagger.tagTrack(track(), metadata(bpm = null))

        assertTrue(result.tags.contains(MusicTag.NORMAL))
    }

    @Test
    fun `메타데이터 자체가 null이면 NORMAL 태그를 받는다`() {
        val result = tagger.tagTrack(track(), null)

        assertTrue(result.tags.contains(MusicTag.NORMAL))
    }

    @Test
    fun `장르 ballad + BPM 80 트랙은 RECOVERY와 LOVE 태그를 받는다`() {
        val result = tagger.tagTrack(track(), metadata(bpm = 80, genre = "ballad"))

        assertTrue(result.tags.contains(MusicTag.RECOVERY))
        assertTrue(result.tags.contains(MusicTag.LOVE))
    }

    @Test
    fun `장르 cinematic + BPM 135 트랙은 HIGH_ENERGY와 DRAMATIC 태그를 받는다`() {
        val result = tagger.tagTrack(track(), metadata(bpm = 135, genre = "cinematic"))

        assertTrue(result.tags.contains(MusicTag.HIGH_ENERGY))
        assertTrue(result.tags.contains(MusicTag.DRAMATIC))
    }

    @Test
    fun `즐겨찾기 트랙은 FAVORITE 태그가 추가된다`() {
        val result = tagger.tagTrack(track(isFavorite = true), metadata(bpm = 120))

        assertTrue(result.tags.contains(MusicTag.NORMAL))
        assertTrue(result.tags.contains(MusicTag.FAVORITE))
    }

    @Test
    fun `tagPlaylist는 모든 트랙을 태깅한다`() {
        val tracks = listOf(
            track(id = "t1"),
            track(id = "t2"),
            track(id = "t3")
        )
        val metadataMap = mapOf(
            "t1" to metadata(bpm = 140),
            "t2" to metadata(bpm = 90, genre = "love"),
            "t3" to null
        )

        val result = tagger.tagPlaylist(tracks, metadataMap)

        assertEquals(3, result.size)
        assertTrue(result[0].tags.contains(MusicTag.HIGH_ENERGY))
        assertTrue(result[1].tags.contains(MusicTag.RECOVERY))
        assertTrue(result[1].tags.contains(MusicTag.LOVE))
        assertTrue(result[2].tags.contains(MusicTag.NORMAL))
    }

    @Test
    fun `장르가 대소문자 섞여도 정상 분류된다`() {
        val result = tagger.tagTrack(track(), metadata(bpm = 110, genre = "Ballad"))

        assertTrue(result.tags.contains(MusicTag.LOVE))
    }
}
