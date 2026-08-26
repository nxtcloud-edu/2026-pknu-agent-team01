package com.pknu.running.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pknu.running.BuildConfig
import com.pknu.running.music.MusicController
import com.pknu.running.music.MusicProvider
import com.pknu.running.music.TrackTagger
import com.pknu.running.music.model.MusicRule
import com.pknu.running.music.model.MusicTag
import com.pknu.running.music.model.Playlist
import com.pknu.running.music.model.PlayMode
import com.pknu.running.music.model.PlaybackState
import com.pknu.running.music.model.TaggedTrack
import com.pknu.running.music.provider.MockMusicAdapter
import com.pknu.running.music.provider.MusicProviderFactory
import com.pknu.running.music.provider.MusicProviderType
import com.pknu.running.music.provider.SpotifyAdapter
import com.pknu.running.music.provider.YouTubePlayerAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 음악 화면용 ViewModel.
 *
 * Mock 모드와 Spotify+YouTube 모드를 전환할 수 있다.
 * - Mock: 기본 모드, 오프라인 데모용
 * - Spotify+YouTube: Spotify에서 플레이리스트 조회 → YouTube 오디오 재생
 */
class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val tagger = TrackTagger()

    // 현재 활성 provider & controller
    private var provider: MusicProvider = MockMusicAdapter(defaultTrackDurationMs = 4_000)
    private var controller = MusicController(provider)
    private var youtubeEnabled = false
    private var youtubeResolver: com.pknu.running.music.provider.YouTubeStreamResolver? = null
    private var exoPlayer: androidx.media3.exoplayer.ExoPlayer? = null

    data class MusicUiState(
        val loaded: Boolean = false,
        val playbackState: PlaybackState = PlaybackState.IDLE,
        val playMode: PlayMode = PlayMode.SEQUENTIAL,
        val rule: String = "default",
        val currentTitle: String? = null,
        val currentArtist: String? = null,
        val currentTags: List<MusicTag> = emptyList(),
        val isEventTrack: Boolean = false,
        val playlist: List<TaggedTrack> = emptyList(),
        // Spotify 연동 상태
        val providerMode: MusicProviderType = MusicProviderType.MOCK,
        val spotifyAuthenticated: Boolean = false,
        val spotifyPlaylists: List<Playlist> = emptyList(),
        val statusMessage: String? = null,
    )

    private val _ui = MutableStateFlow(MusicUiState())
    val ui: StateFlow<MusicUiState> = _ui.asStateFlow()

    init {
        setupController()
        loadDefault()
    }

    private fun setupController() {
        controller.setOnTrackEndHandler {
            viewModelScope.launch {
                controller.handleTrackEnd()
                publish()
            }
        }
        controller.initialize()
    }

    private fun loadDefault() {
        viewModelScope.launch {
            provider.authenticate()
            controller.loadPlaylist("pl1")
            (provider as? MockMusicAdapter)?.let { mock ->
                controller.loadEventTracks(mock.getPlaylistTracks("pl2"))
            }

            val tracks = provider.getPlaylistTracks("pl1")
            val metaMap = tracks.associate { it.id to provider.getTrackMetadata(it.id) }
            val tagged = tagger.tagPlaylist(tracks, metaMap)

            _ui.value = _ui.value.copy(loaded = true, playlist = tagged)
            publish()
        }
    }

    // --- YouTube 직접 재생 모드 ---

    /**
     * YouTube 모드를 활성화한다. Spotify 인증 없이
     * Mock 플레이리스트의 곡명으로 YouTube 검색 → ExoPlayer 재생.
     */
    fun enableYouTubeMode() {
        youtubeEnabled = true
        if (youtubeResolver == null) {
            youtubeResolver = com.pknu.running.music.provider.YouTubeStreamResolver()
        }
        _ui.value = _ui.value.copy(
            providerMode = MusicProviderType.SPOTIFY_YOUTUBE,
            statusMessage = "YouTube 모드 활성화됨"
        )
    }

    fun disableYouTubeMode() {
        youtubeEnabled = false
        exoPlayer?.stop()
        _ui.value = _ui.value.copy(
            providerMode = MusicProviderType.MOCK,
            statusMessage = null
        )
    }

    /**
     * 현재 곡을 YouTube에서 검색해서 재생한다.
     */
    private fun playViaYouTube(title: String, artist: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(statusMessage = "YouTube 검색 중: $artist - $title")
            try {
                val resolved = withContext(Dispatchers.IO) {
                    youtubeResolver?.resolveAudioUrl("$artist $title")
                }
                if (resolved != null) {
                    withContext(Dispatchers.Main) {
                        val ctx = getApplication<Application>().applicationContext
                        if (exoPlayer == null) {
                            exoPlayer = androidx.media3.exoplayer.ExoPlayer.Builder(ctx).build()
                        }
                        val player = exoPlayer!!
                        val mediaItem = androidx.media3.common.MediaItem.fromUri(resolved.audioUrl)
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        player.playWhenReady = true
                        // 인트로 무음 구간 스킵 (5.0초)
                        player.seekTo(5000L)
                    }
                    _ui.value = _ui.value.copy(statusMessage = "♪ YouTube 재생: ${resolved.title}")
                } else {
                    _ui.value = _ui.value.copy(statusMessage = "YouTube에서 찾을 수 없음: $artist - $title")
                }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(statusMessage = "재생 실패: ${e.message}")
            }
        }
    }

    fun stopYouTube() {
        exoPlayer?.stop()
        _ui.value = _ui.value.copy(statusMessage = "정지됨")
    }

    // --- Spotify+YouTube 모드 전환 ---

    /**
     * Spotify+YouTube 모드로 전환한다.
     * @return SpotifyAdapter 인스턴스 (인증 흐름 시작 위해 Activity에서 사용)
     */
    fun switchToSpotifyYouTube(): Any? {
        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        val redirectUri = BuildConfig.SPOTIFY_REDIRECT_URI

        if (clientId.isBlank()) {
            _ui.value = _ui.value.copy(statusMessage = "SPOTIFY_CLIENT_ID가 설정되지 않음")
            return null
        }

        val ctx = getApplication<Application>().applicationContext
        val newProvider = MusicProviderFactory.create(
            context = ctx,
            type = MusicProviderType.SPOTIFY_YOUTUBE,
            clientId = clientId,
            redirectUri = redirectUri
        )

        provider = newProvider
        controller = MusicController(provider)
        setupController()

        _ui.value = _ui.value.copy(
            providerMode = MusicProviderType.SPOTIFY_YOUTUBE,
            statusMessage = "Spotify 모드로 전환됨"
        )

        return when (newProvider) {
            is YouTubePlayerAdapter -> newProvider
            else -> null
        }
    }

    /**
     * Mock 모드로 되돌린다.
     */
    fun switchToMock() {
        provider = MockMusicAdapter(defaultTrackDurationMs = 4_000)
        controller = MusicController(provider)
        setupController()
        _ui.value = MusicUiState(providerMode = MusicProviderType.MOCK)
        loadDefault()
    }

    /**
     * Spotify 인증 완료 후 호출. 플레이리스트를 로드한다.
     */
    fun onSpotifyAuthenticated() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(
                spotifyAuthenticated = true,
                statusMessage = "로그인 성공! 플레이리스트 로딩 중..."
            )

            try {
                val playlists = withContext(Dispatchers.IO) {
                    provider.getPlaylists()
                }
                _ui.value = _ui.value.copy(
                    spotifyPlaylists = playlists,
                    statusMessage = "플레이리스트 ${playlists.size}개 로드됨"
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    statusMessage = "플레이리스트 로딩 실패: ${e.message}"
                )
            }
        }
    }

    /**
     * Spotify 플레이리스트를 선택하여 로드한다.
     */
    fun loadSpotifyPlaylist(playlistId: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(statusMessage = "트랙 로딩 중...")

            try {
                val tracks = withContext(Dispatchers.IO) {
                    provider.getPlaylistTracks(playlistId)
                }

                if (tracks.isEmpty()) {
                    _ui.value = _ui.value.copy(statusMessage = "트랙이 비어있음")
                    return@launch
                }

                // 메타데이터 조회 (Spotify에서)
                val metaMap = withContext(Dispatchers.IO) {
                    tracks.associate { track ->
                        track.id to provider.getTrackMetadata(track.id)
                    }
                }
                val tagged = tagger.tagPlaylist(tracks, metaMap)

                // controller에 로드
                controller.loadPlaylist(playlistId)

                _ui.value = _ui.value.copy(
                    loaded = true,
                    playlist = tagged,
                    statusMessage = "${tracks.size}곡 로드 완료"
                )
                publish()
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(statusMessage = "로딩 실패: ${e.message}")
            }
        }
    }

    // --- 기존 재생 제어 ---

    fun play() = action {
        controller.play()
    }

    fun pause() = action {
        controller.pause()
        if (youtubeEnabled) exoPlayer?.playWhenReady = false
    }

    fun resume() = action {
        controller.resume()
        if (youtubeEnabled) exoPlayer?.playWhenReady = true
    }

    fun next() = action {
        if (youtubeEnabled) exoPlayer?.stop()
        controller.next()
    }

    fun stop() = action {
        controller.stop()
        if (youtubeEnabled) exoPlayer?.stop()
    }

    fun togglePlayMode() {
        val next = if (controller.getPlayMode() == PlayMode.SEQUENTIAL) PlayMode.SHUFFLE else PlayMode.SEQUENTIAL
        controller.setPlayMode(next)
        publish()
    }

    fun applyRule(tag: MusicTag, source: String) {
        controller.setMusicRule(
            MusicRule(preferredTags = listOf(tag), fallbackTag = MusicTag.NORMAL, source = source)
        )
        publish()
    }

    fun clearRule() {
        controller.clearMusicRule()
        publish()
    }

    private fun action(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            publish()
        }
    }

    private var lastYouTubeTrackId: String? = null

    private fun publish() {
        val track = controller.getCurrentTrack()
        _ui.value = _ui.value.copy(
            playbackState = controller.getPlaybackState(),
            playMode = controller.getPlayMode(),
            rule = controller.getActiveMusicRule().source,
            currentTitle = track?.track?.title,
            currentArtist = track?.track?.artist,
            currentTags = track?.tags?.toList() ?: emptyList(),
            isEventTrack = controller.isPlayingEventTrack(),
        )

        // YouTube 모드: 곡이 바뀌었으면 자동으로 YouTube 재생
        if (youtubeEnabled && track != null && controller.getPlaybackState() == PlaybackState.PLAYING) {
            if (track.track.id != lastYouTubeTrackId) {
                lastYouTubeTrackId = track.track.id
                playViaYouTube(track.track.title, track.track.artist)
            }
        }
        if (controller.getPlaybackState() != PlaybackState.PLAYING) {
            lastYouTubeTrackId = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { controller.stop() }
        exoPlayer?.release()
        exoPlayer = null
        (provider as? YouTubePlayerAdapter)?.release()
    }
}
