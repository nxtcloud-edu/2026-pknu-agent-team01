package com.pknu.running.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pknu.running.music.MusicController
import com.pknu.running.music.TrackTagger
import com.pknu.running.music.model.MusicRule
import com.pknu.running.music.model.MusicTag
import com.pknu.running.music.model.PlayMode
import com.pknu.running.music.model.PlaybackState
import com.pknu.running.music.model.TaggedTrack
import com.pknu.running.music.provider.MockMusicAdapter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 음악 화면용 ViewModel. MockMusicAdapter 기반 MusicController를 감싸고,
 * UI가 구독할 상태를 StateFlow로 노출한다.
 *
 * MusicController는 내부 상태를 getter로 노출하므로, 각 액션 후 [publish]로 UI 상태를 갱신한다.
 */
class MusicViewModel : ViewModel() {

    private val provider = MockMusicAdapter(defaultTrackDurationMs = 4_000)
    private val controller = MusicController(provider)
    private val tagger = TrackTagger()

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
    )

    private val _ui = MutableStateFlow(MusicUiState())
    val ui: StateFlow<MusicUiState> = _ui.asStateFlow()

    init {
        // 곡이 끝나면 자동으로 다음 곡 처리
        controller.setOnTrackEndHandler {
            viewModelScope.launch {
                controller.handleTrackEnd()
                publish()
            }
        }
        controller.initialize()
        loadDefault()
    }

    private fun loadDefault() {
        viewModelScope.launch {
            provider.authenticate()
            controller.loadPlaylist("pl1")
            controller.loadEventTracks(provider.getPlaylistTracks("pl2"))

            // UI 목록 표시용으로 플레이리스트를 직접 태깅해 보관
            val tracks = provider.getPlaylistTracks("pl1")
            val metaMap = tracks.associate { it.id to provider.getTrackMetadata(it.id) }
            val tagged = tagger.tagPlaylist(tracks, metaMap)

            _ui.value = _ui.value.copy(loaded = true, playlist = tagged)
            publish()
        }
    }

    fun play() = action { controller.play() }
    fun pause() = action { controller.pause() }
    fun resume() = action { controller.resume() }
    fun next() = action { controller.next() }
    fun stop() = action { controller.stop() }

    fun togglePlayMode() {
        val next = if (controller.getPlayMode() == PlayMode.SEQUENTIAL) PlayMode.SHUFFLE else PlayMode.SEQUENTIAL
        controller.setPlayMode(next)
        publish()
    }

    /** 러닝 상황에 맞는 음악 규칙을 적용한다 (데모: 태그 프리셋). */
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
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch { controller.stop() }
    }
}
