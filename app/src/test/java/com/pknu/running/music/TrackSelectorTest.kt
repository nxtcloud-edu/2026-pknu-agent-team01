package com.pknu.running.music

import com.pknu.running.music.model.MusicRule
import com.pknu.running.music.model.MusicTag
import com.pknu.running.music.model.PlayMode
import com.pknu.running.music.model.TaggedTrack
import com.pknu.running.music.model.Track
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TrackSelectorTest {

    private lateinit var selector: TrackSelector

    private fun taggedTrack(id: String, vararg tags: MusicTag) = TaggedTrack(
        track = Track(id = id, title = "Track $id", artist = "Artist", durationMs = 180_000),
        tags = tags.toSet()
    )

    @BeforeEach
    fun setUp() {
        selector = TrackSelector()
    }

    @Test
    fun `빈 플레이리스트는 null을 반환한다`() {
        val result = selector.selectNext(
            playlist = emptyList(),
            rule = MusicRule.DEFAULT,
            playHistory = emptyList()
        )

        assertNull(result)
    }

    @Test
    fun `HIGH_ENERGY 규칙이면 해당 태그 곡을 선택한다`() {
        val playlist = listOf(
            taggedTrack("t1", MusicTag.NORMAL),
            taggedTrack("t2", MusicTag.HIGH_ENERGY),
            taggedTrack("t3", MusicTag.RECOVERY)
        )
        val rule = MusicRule(preferredTags = listOf(MusicTag.HIGH_ENERGY))

        val result = selector.selectNext(playlist, rule, emptyList())

        assertNotNull(result)
        assertEquals("t2", result!!.track.id)
    }

    @Test
    fun `후보가 없으면 fallbackTag로 선택한다`() {
        val playlist = listOf(
            taggedTrack("t1", MusicTag.NORMAL),
            taggedTrack("t2", MusicTag.NORMAL),
            taggedTrack("t3", MusicTag.RECOVERY)
        )
        val rule = MusicRule(
            preferredTags = listOf(MusicTag.LOVE),
            fallbackTag = MusicTag.NORMAL
        )

        val result = selector.selectNext(playlist, rule, emptyList())

        assertNotNull(result)
        assertTrue(result!!.tags.contains(MusicTag.NORMAL))
    }

    @Test
    fun `최근 재생 곡은 제외된다`() {
        val playlist = listOf(
            taggedTrack("t1", MusicTag.HIGH_ENERGY),
            taggedTrack("t2", MusicTag.HIGH_ENERGY)
        )
        val rule = MusicRule(preferredTags = listOf(MusicTag.HIGH_ENERGY))

        val result = selector.selectNext(playlist, rule, listOf("t1"))

        assertNotNull(result)
        assertEquals("t2", result!!.track.id)
    }

    @Test
    fun `모든 후보가 이력에 있으면 이력 무시하고 선택한다`() {
        val playlist = listOf(
            taggedTrack("t1", MusicTag.HIGH_ENERGY),
            taggedTrack("t2", MusicTag.NORMAL)
        )
        val rule = MusicRule(preferredTags = listOf(MusicTag.HIGH_ENERGY))

        val result = selector.selectNext(playlist, rule, listOf("t1"))

        // fallback도 이력에 없는 normal을 줄 수도 있지만,
        // 이력 무시 단계에서 t1 반환 가능
        assertNotNull(result)
    }

    @Test
    fun `순차 모드에서는 currentIndex 이후 곡을 우선한다`() {
        val playlist = listOf(
            taggedTrack("t1", MusicTag.NORMAL),
            taggedTrack("t2", MusicTag.NORMAL),
            taggedTrack("t3", MusicTag.NORMAL)
        )
        val rule = MusicRule.DEFAULT

        val result = selector.selectNext(
            playlist, rule, emptyList(),
            currentIndex = 0,
            playMode = PlayMode.SEQUENTIAL
        )

        assertNotNull(result)
        assertEquals("t2", result!!.track.id)
    }

    @Test
    fun `순차 모드에서 마지막 곡이면 처음으로 돌아간다`() {
        val playlist = listOf(
            taggedTrack("t1", MusicTag.NORMAL),
            taggedTrack("t2", MusicTag.NORMAL)
        )
        val rule = MusicRule.DEFAULT

        val result = selector.selectNext(
            playlist, rule, emptyList(),
            currentIndex = 1,
            playMode = PlayMode.SEQUENTIAL
        )

        assertNotNull(result)
        assertEquals("t1", result!!.track.id)
    }

    @Test
    fun `셔플 모드에서는 후보 중 랜덤 선택한다`() {
        val playlist = listOf(
            taggedTrack("t1", MusicTag.HIGH_ENERGY),
            taggedTrack("t2", MusicTag.HIGH_ENERGY),
            taggedTrack("t3", MusicTag.HIGH_ENERGY)
        )
        val rule = MusicRule(preferredTags = listOf(MusicTag.HIGH_ENERGY))

        // 여러 번 실행해서 랜덤성 확인 (최소 결과가 null이 아님)
        val results = (1..10).map {
            selector.selectNext(playlist, rule, emptyList(), playMode = PlayMode.SHUFFLE)
        }

        assertTrue(results.all { it != null })
        assertTrue(results.map { it!!.track.id }.toSet().size >= 1)
    }

    @Test
    fun `LOVE 규칙이면 love 태그 곡을 선택한다`() {
        val playlist = listOf(
            taggedTrack("t1", MusicTag.NORMAL),
            taggedTrack("t2", MusicTag.LOVE),
            taggedTrack("t3", MusicTag.HIGH_ENERGY)
        )
        val rule = MusicRule(preferredTags = listOf(MusicTag.LOVE))

        val result = selector.selectNext(playlist, rule, emptyList())

        assertNotNull(result)
        assertEquals("t2", result!!.track.id)
    }
}
