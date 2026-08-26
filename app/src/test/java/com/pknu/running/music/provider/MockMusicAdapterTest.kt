package com.pknu.running.music.provider

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MockMusicAdapterTest {

    private lateinit var adapter: MockMusicAdapter

    @BeforeEach
    fun setUp() {
        // 테스트용으로 100ms 후 곡 종료
        adapter = MockMusicAdapter(defaultTrackDurationMs = 100)
    }

    @Test
    fun `authenticate 후 isAuthenticated는 true`() = runBlocking {
        assertFalse(adapter.isAuthenticated())
        adapter.authenticate()
        assertTrue(adapter.isAuthenticated())
    }

    @Test
    fun `getPlaylists는 목 데이터를 반환한다`() = runBlocking {
        val playlists = adapter.getPlaylists()
        assertTrue(playlists.isNotEmpty())
        assertEquals("pl1", playlists[0].id)
    }

    @Test
    fun `getPlaylistTracks는 해당 플레이리스트의 트랙을 반환한다`() = runBlocking {
        val tracks = adapter.getPlaylistTracks("pl1")
        assertEquals(5, tracks.size)
        assertEquals("m1", tracks[0].id)
    }

    @Test
    fun `존재하지 않는 플레이리스트는 빈 리스트를 반환한다`() = runBlocking {
        val tracks = adapter.getPlaylistTracks("unknown")
        assertTrue(tracks.isEmpty())
    }

    @Test
    fun `playTrack 후 재생 상태가 된다`() = runBlocking {
        adapter.playTrack("m1")
        assertTrue(adapter.isCurrentlyPlaying())
        assertEquals("m1", adapter.getCurrentTrackId())
    }

    @Test
    fun `pause 후 재생이 멈춘다`() = runBlocking {
        adapter.playTrack("m1")
        adapter.pause()
        assertFalse(adapter.isCurrentlyPlaying())
    }

    @Test
    fun `stop 후 트랙이 null이 된다`() = runBlocking {
        adapter.playTrack("m1")
        adapter.stop()
        assertFalse(adapter.isCurrentlyPlaying())
        assertNull(adapter.getCurrentTrackId())
    }

    @Test
    fun `곡이 끝나면 onTrackEnd 콜백이 호출된다`() = runBlocking {
        val latch = CountDownLatch(1)
        adapter.setOnTrackEndListener { latch.countDown() }

        adapter.playTrack("m1")

        assertTrue(latch.await(500, TimeUnit.MILLISECONDS))
        assertFalse(adapter.isCurrentlyPlaying())
    }

    @Test
    fun `setVolume은 0~1 범위로 제한된다`() = runBlocking {
        adapter.setVolume(0.5f)
        assertEquals(0.5f, adapter.getVolume())

        adapter.setVolume(1.5f)
        assertEquals(1.0f, adapter.getVolume())

        adapter.setVolume(-0.3f)
        assertEquals(0.0f, adapter.getVolume())
    }

    @Test
    fun `getTrackMetadata는 BPM과 장르를 반환한다`() = runBlocking {
        val metadata = adapter.getTrackMetadata("m1")
        assertNotNull(metadata)
        assertEquals(114, metadata!!.bpm)
        assertEquals("pop", metadata.genre)
    }

    @Test
    fun `존재하지 않는 트랙의 메타데이터는 null이다`() = runBlocking {
        val metadata = adapter.getTrackMetadata("unknown")
        assertNull(metadata)
    }

    @Test
    fun `durationMs를 지정하면 해당 시간 후 곡이 끝난다`() = runBlocking {
        val shortAdapter = MockMusicAdapter(defaultTrackDurationMs = 5000)
        val latch = CountDownLatch(1)
        shortAdapter.setOnTrackEndListener { latch.countDown() }

        // 50ms duration 지정
        shortAdapter.playTrack("m1", startPositionMs = 60_000, durationMs = 50)

        assertTrue(latch.await(200, TimeUnit.MILLISECONDS))
    }
}
